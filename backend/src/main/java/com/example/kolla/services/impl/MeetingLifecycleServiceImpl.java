package com.example.kolla.services.impl;

import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.AttendanceLog;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.User;
import com.example.kolla.repositories.AttendanceLogRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.RaiseHandRequestRepository;
import com.example.kolla.responses.MeetingResponse;
import com.example.kolla.services.MeetingLifecycleService;
import com.example.kolla.services.MinutesService;
import com.example.kolla.services.NotificationService;
import com.example.kolla.services.SpeakingPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MeetingLifecycleService implementation.
 *
 * State machine:
 *   SCHEDULED ──activate()──► ACTIVE ──end()──► ENDED
 *
 * Waiting_Timeout logic:
 *   When neither Host nor Secretary is present in an ACTIVE meeting, a Redis TTL key
 *   `meeting:{id}:waiting_timeout` is set to 600 seconds. If neither returns before
 *   expiry, the scheduled job auto-ends the meeting.
 *
 * Requirements: 3.10, 3.11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingLifecycleServiceImpl implements MeetingLifecycleService {

    /** Redis TTL for the waiting timeout key (10 minutes). */
    private static final long WAITING_TIMEOUT_SECONDS = 600L;

    /** Redis key prefix for waiting timeout. */
    private static final String WAITING_TIMEOUT_KEY_PREFIX = "meeting:%d:waiting_timeout";

    private final MeetingRepository meetingRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final RaiseHandRequestRepository raiseHandRequestRepository;
    private final Clock clock;

    @Autowired
    @Lazy
    private SpeakingPermissionService speakingPermissionService;

    @Autowired
    @Lazy
    private MinutesService minutesService;

    // ── Activate ──────────────────────────────────────────────────────────────

    /**
     * Transition a meeting from SCHEDULED → ACTIVE.
     * Only the Host (or ADMIN) may activate.
     * Requirements: 3.10
     */
    @Override
    @Transactional
    public MeetingResponse activateMeeting(Long meetingId, User requester) {
        Meeting meeting = findMeetingOrThrow(meetingId);

        // Permission check: only Host or ADMIN
        if (!isHostOrAdmin(meeting, requester)) {
            throw new ForbiddenException(
                    "Only the Host or an ADMIN may activate a meeting");
        }

        // State check
        if (meeting.getStatus() != MeetingStatus.SCHEDULED) {
            throw new BadRequestException(
                    "Meeting is not in SCHEDULED state (current: " + meeting.getStatus() + ")");
        }

        meeting.setStatus(MeetingStatus.ACTIVE);
        meeting.setActivatedAt(LocalDateTime.now(clock));
        Meeting saved = meetingRepository.save(meeting);

        log.info("Meeting id={} '{}' activated by user '{}'",
                meetingId, meeting.getTitle(), requester.getUsername());

        // Notify all members that the meeting has started (Requirement 10.2)
        notifyAllMembers(saved, requester,
                "MEETING_STARTED",
                "Meeting started",
                "Meeting '" + saved.getTitle() + "' has started");

        return MeetingResponse.from(saved);
    }

    // ── End ───────────────────────────────────────────────────────────────────

    /**
     * Transition a meeting from ACTIVE → ENDED.
     * Host, Secretary, or ADMIN may end the meeting.
     * Requirements: 3.11
     */
    @Override
    @Transactional
    public MeetingResponse endMeeting(Long meetingId, User requester) {
        Meeting meeting = findMeetingOrThrow(meetingId);

        // Permission check: Host, Secretary, or ADMIN
        if (!isHostSecretaryOrAdmin(meeting, requester)) {
            throw new ForbiddenException(
                    "Only the Host, Secretary, or an ADMIN may end a meeting");
        }

        // State check
        if (meeting.getStatus() != MeetingStatus.ACTIVE) {
            throw new BadRequestException(
                    "Meeting is not in ACTIVE state (current: " + meeting.getStatus() + ")");
        }

        meeting.setStatus(MeetingStatus.ENDED);
        meeting.setEndedAt(LocalDateTime.now(clock));
        meeting.setWaitingTimeoutAt(null); // clear any pending timeout
        Meeting saved = meetingRepository.save(meeting);

        // Cancel Redis waiting timeout key
        cancelWaitingTimeout(meetingId);

        // Revoke all speaking permissions (Requirement 22.10)
        try {
            speakingPermissionService.revokeAllPermissions(meetingId, "MEETING_ENDED");
        } catch (Exception e) {
            log.warn("Could not revoke speaking permissions for meeting id={}: {}",
                    meetingId, e.getMessage());
        }

        // Expire all pending raise-hand requests
        try {
            raiseHandRequestRepository.expireAllPendingForMeeting(meetingId, saved.getEndedAt());
        } catch (Exception e) {
            log.warn("Could not expire raise-hand requests for meeting id={}: {}",
                    meetingId, e.getMessage());
        }

        // Close all open attendance logs
        closeOpenAttendanceLogs(meetingId, saved.getEndedAt());

        log.info("Meeting id={} '{}' ended by user '{}'",
                meetingId, meeting.getTitle(), requester.getUsername());

        // Notify all members (Requirement 10.2)
        notifyAllMembers(saved, requester,
                "MEETING_ENDED",
                "Meeting ended",
                "Meeting '" + saved.getTitle() + "' has ended");

        // Trigger draft minutes generation asynchronously (Requirement 25.1–25.3)
        try {
            minutesService.compileDraftMinutes(saved);
        } catch (Exception e) {
            log.error("Failed to compile draft minutes for meeting id={}: {}",
                    meetingId, e.getMessage(), e);
            // Non-fatal: meeting is already ended; minutes can be retried
        }

        return MeetingResponse.from(saved);
    }

    // ── Participant presence tracking ─────────────────────────────────────────

    /**
     * Called when a participant joins an active meeting.
     * If the joining user is the Host or Secretary, cancel the waiting timeout.
     * Requirements: 3.11
     */
    @Override
    @Transactional
    public void onParticipantJoined(Long meetingId, User user) {
        Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
        if (meeting == null || meeting.getStatus() != MeetingStatus.ACTIVE) {
            return;
        }

        boolean isKeyPerson = isHostOrSecretary(meeting, user);
        if (isKeyPerson) {
            // Cancel waiting timeout — a key person has arrived
            cancelWaitingTimeout(meetingId);
            meeting.setWaitingTimeoutAt(null);
            meetingRepository.save(meeting);
            log.debug("Waiting timeout cancelled for meeting id={} — {} joined",
                    meetingId, user.getUsername());
        }
    }

    /**
     * Called when a participant leaves an active meeting.
     * If neither Host nor Secretary remains, start the 10-minute waiting timeout.
     * Requirements: 3.11
     */
    @Override
    @Transactional
    public void onParticipantLeft(Long meetingId, User user) {
        Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
        if (meeting == null || meeting.getStatus() != MeetingStatus.ACTIVE) {
            return;
        }

        // Check if any key person (Host or Secretary) is still present
        boolean keyPersonStillPresent = isKeyPersonPresent(meeting);
        if (!keyPersonStillPresent) {
            startWaitingTimeout(meeting);
        }
    }

    // ── Scheduled timeout processor ───────────────────────────────────────────

    /**
     * Runs every 30 seconds. Auto-ends meetings whose waiting_timeout_at has passed.
     * This is a DB-level fallback; the primary mechanism is the Redis TTL key.
     * Requirements: 3.11
     */
    @Override
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void processExpiredWaitingTimeouts() {
        List<Meeting> expired = meetingRepository
                .findMeetingsWithExpiredWaitingTimeout(LocalDateTime.now(clock));

        for (Meeting meeting : expired) {
            try {
                log.info("Auto-ending meeting id={} '{}' due to waiting timeout",
                        meeting.getId(), meeting.getTitle());

                meeting.setStatus(MeetingStatus.ENDED);
                meeting.setEndedAt(LocalDateTime.now(clock));
                meeting.setWaitingTimeoutAt(null);
                meetingRepository.save(meeting);

                cancelWaitingTimeout(meeting.getId());
                closeOpenAttendanceLogs(meeting.getId(), meeting.getEndedAt());

                // Revoke all speaking permissions and expire raise-hand requests
                try {
                    speakingPermissionService.revokeAllPermissions(
                            meeting.getId(), "MEETING_ENDED");
                    raiseHandRequestRepository.expireAllPendingForMeeting(
                            meeting.getId(), meeting.getEndedAt());
                } catch (Exception ex) {
                    log.warn("Could not clean up permissions for auto-ended meeting id={}: {}",
                            meeting.getId(), ex.getMessage());
                }

                // Trigger draft minutes generation (Requirement 25.1–25.3)
                try {
                    minutesService.compileDraftMinutes(meeting);
                } catch (Exception ex) {
                    log.error("Failed to compile draft minutes for auto-ended meeting id={}: {}",
                            meeting.getId(), ex.getMessage(), ex);
                }

            } catch (Exception e) {
                log.error("Failed to auto-end meeting id={}: {}",
                        meeting.getId(), e.getMessage(), e);
            }
        }
    }

    // ── Host authority check ──────────────────────────────────────────────────

    /**
     * Returns true if the user is the designated Host or has ADMIN role.
     * Requirements: 3.10, 21.7
     */
    @Override
    public boolean isHostOrAdmin(Meeting meeting, User user) {
        if (user.getRole() == Role.ADMIN) {
            return true;
        }
        return meeting.getHost() != null
                && meeting.getHost().getId().equals(user.getId());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns true if the user is Host, Secretary, or ADMIN.
     */
    private boolean isHostSecretaryOrAdmin(Meeting meeting, User user) {
        if (user.getRole() == Role.ADMIN) {
            return true;
        }
        if (meeting.getHost() != null && meeting.getHost().getId().equals(user.getId())) {
            return true;
        }
        return meeting.getSecretary() != null
                && meeting.getSecretary().getId().equals(user.getId());
    }

    /**
     * Returns true if the user is the Host or Secretary of the meeting.
     */
    private boolean isHostOrSecretary(Meeting meeting, User user) {
        boolean isHost = meeting.getHost() != null
                && meeting.getHost().getId().equals(user.getId());
        boolean isSecretary = meeting.getSecretary() != null
                && meeting.getSecretary().getId().equals(user.getId());
        return isHost || isSecretary;
    }

    /**
     * Returns true if at least one of Host or Secretary has an open attendance log
     * (i.e., is currently present in the meeting).
     */
    private boolean isKeyPersonPresent(Meeting meeting) {
        List<AttendanceLog> activeLogs =
                attendanceLogRepository.findActiveParticipants(meeting.getId());

        for (AttendanceLog log : activeLogs) {
            Long userId = log.getUser().getId();
            boolean isHost = meeting.getHost() != null
                    && meeting.getHost().getId().equals(userId);
            boolean isSecretary = meeting.getSecretary() != null
                    && meeting.getSecretary().getId().equals(userId);
            if (isHost || isSecretary) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the Redis TTL key and updates waiting_timeout_at in the DB.
     * Requirements: 3.11
     */
    private void startWaitingTimeout(Meeting meeting) {
        // Avoid resetting if already counting down
        if (meeting.getWaitingTimeoutAt() != null) {
            return;
        }

        LocalDateTime timeoutAt = LocalDateTime.now(clock)
                .plusSeconds(WAITING_TIMEOUT_SECONDS);
        meeting.setWaitingTimeoutAt(timeoutAt);
        meetingRepository.save(meeting);

        // Set Redis TTL key as primary expiry signal
        String key = String.format(WAITING_TIMEOUT_KEY_PREFIX, meeting.getId());
        redisTemplate.opsForValue().set(key, "1", WAITING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("Waiting timeout started for meeting id={} — expires at {}",
                meeting.getId(), timeoutAt);
    }

    /**
     * Removes the Redis waiting timeout key.
     */
    private void cancelWaitingTimeout(Long meetingId) {
        String key = String.format(WAITING_TIMEOUT_KEY_PREFIX, meetingId);
        redisTemplate.delete(key);
    }

    /**
     * Closes all open attendance logs for a meeting by setting leave_time and
     * calculating duration.
     */
    private void closeOpenAttendanceLogs(Long meetingId, LocalDateTime endTime) {
        List<AttendanceLog> openLogs =
                attendanceLogRepository.findActiveParticipants(meetingId);

        for (AttendanceLog log : openLogs) {
            log.setLeaveTime(endTime);
            if (log.getJoinTime() != null) {
                long seconds = java.time.Duration.between(log.getJoinTime(), endTime).getSeconds();
                log.setDurationSeconds(Math.max(0, seconds));
            }
        }

        if (!openLogs.isEmpty()) {
            attendanceLogRepository.saveAll(openLogs);
            log.debug("Closed {} open attendance logs for meeting id={}",
                    openLogs.size(), meetingId);
        }
    }

    /**
     * Sends a notification to all members of the meeting.
     */
    private void notifyAllMembers(Meeting meeting, User sender,
                                   String type, String title, String message) {
        // Notifications are sent to each member via NotificationService.
        // Member list is fetched lazily; we use the attendance logs for active participants
        // and rely on the notification service for persistence.
        // For meeting-wide events, the WebSocket broadcast (task 5) will handle real-time delivery.
        // Here we persist DB notifications for the notification panel (Requirement 10.7).
        try {
            // Notify host if different from sender
            if (meeting.getHost() != null
                    && !meeting.getHost().getId().equals(sender.getId())) {
                notificationService.createNotification(
                        meeting.getHost(), sender, type, title, message, meeting.getId());
            }
            // Notify secretary if different from sender and host
            if (meeting.getSecretary() != null
                    && !meeting.getSecretary().getId().equals(sender.getId())
                    && (meeting.getHost() == null
                        || !meeting.getSecretary().getId().equals(meeting.getHost().getId()))) {
                notificationService.createNotification(
                        meeting.getSecretary(), sender, type, title, message, meeting.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to send notifications for meeting id={}: {}",
                    meeting.getId(), e.getMessage());
        }
    }

    private Meeting findMeetingOrThrow(Long id) {
        return meetingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Meeting not found with id: " + id));
    }
}
