package com.example.kolla.services.impl;

import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.AttendanceLog;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.User;
import com.example.kolla.repositories.AttendanceLogRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.MemberRepository;
import com.example.kolla.responses.AttendanceLogResponse;
import com.example.kolla.services.AttendanceService;
import com.example.kolla.services.MeetingLifecycleService;
import com.example.kolla.services.SpeakingPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AttendanceService implementation.
 * Tracks join/leave events and delegates lifecycle side-effects to MeetingLifecycleService.
 * Requirements: 5.1–5.8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceLogRepository attendanceLogRepository;
    private final MeetingRepository meetingRepository;
    private final MemberRepository memberRepository;
    private final MeetingLifecycleService meetingLifecycleService;
    private final Clock clock;

    @Autowired
    @Lazy
    private SpeakingPermissionService speakingPermissionService;

    // ── Join ──────────────────────────────────────────────────────────────────

    /**
     * Record a user joining a meeting.
     * Requirements: 5.1, 5.2, 5.8
     */
    @Override
    @Transactional
    public AttendanceLogResponse joinMeeting(Long meetingId, User user,
                                              String ipAddress, String deviceInfo) {
        Meeting meeting = findMeetingOrThrow(meetingId);

        // Only ACTIVE meetings can be joined (Requirement 3.10)
        if (meeting.getStatus() != MeetingStatus.ACTIVE) {
            throw new BadRequestException(
                    "Cannot join a meeting that is not ACTIVE (current: " + meeting.getStatus() + ")");
        }

        // Only members may join (Requirement 3.9)
        if (!memberRepository.isMember(meetingId, user.getId())) {
            throw new ForbiddenException(
                    "You are not a member of this meeting");
        }

        // Close any stale open log (e.g., from a previous disconnection)
        attendanceLogRepository.findOpenLog(meetingId, user.getId())
                .ifPresent(stale -> {
                    stale.setLeaveTime(LocalDateTime.now(clock));
                    long seconds = Duration.between(stale.getJoinTime(), stale.getLeaveTime())
                            .getSeconds();
                    stale.setDurationSeconds(Math.max(0, seconds));
                    attendanceLogRepository.save(stale);
                    log.debug("Closed stale attendance log id={} for user '{}' in meeting id={}",
                            stale.getId(), user.getUsername(), meetingId);
                });

        AttendanceLog attendanceLog = AttendanceLog.builder()
                .meeting(meeting)
                .user(user)
                .joinTime(LocalDateTime.now(clock))
                .ipAddress(ipAddress)
                .deviceInfo(deviceInfo)
                .build();

        AttendanceLog saved = attendanceLogRepository.save(attendanceLog);
        log.info("User '{}' joined meeting id={} from IP {}",
                user.getUsername(), meetingId, ipAddress);

        // Notify lifecycle service — may cancel waiting timeout (Requirement 3.11)
        meetingLifecycleService.onParticipantJoined(meetingId, user);

        return AttendanceLogResponse.from(saved);
    }

    // ── Leave ─────────────────────────────────────────────────────────────────

    /**
     * Record a user leaving a meeting.
     * Requirements: 5.3, 5.4, 5.5
     */
    @Override
    @Transactional
    public AttendanceLogResponse leaveMeeting(Long meetingId, User user) {
        Meeting meeting = findMeetingOrThrow(meetingId);

        // Find the open attendance log
        AttendanceLog openLog = attendanceLogRepository
                .findOpenLog(meetingId, user.getId())
                .orElseThrow(() -> new BadRequestException(
                        "No active attendance record found for user '"
                        + user.getUsername() + "' in meeting " + meetingId));

        LocalDateTime leaveTime = LocalDateTime.now(clock);
        openLog.setLeaveTime(leaveTime);

        // Calculate duration (Requirement 5.5)
        long seconds = Duration.between(openLog.getJoinTime(), leaveTime).getSeconds();
        openLog.setDurationSeconds(Math.max(0, seconds));

        AttendanceLog saved = attendanceLogRepository.save(openLog);
        log.info("User '{}' left meeting id={} after {}s",
                user.getUsername(), meetingId, seconds);

        // Auto-revoke speaking permission if held (Requirement 22.10)
        try {
            speakingPermissionService.revokePermissionOnLeave(meetingId, user.getId());
        } catch (Exception e) {
            log.debug("Could not revoke speaking permission on leave for userId={}: {}",
                    user.getId(), e.getMessage());
        }

        // Notify lifecycle service — may start waiting timeout (Requirement 3.11)
        meetingLifecycleService.onParticipantLeft(meetingId, user);

        return AttendanceLogResponse.from(saved);
    }

    // ── History & active participants ─────────────────────────────────────────

    /**
     * Get all attendance logs for a meeting.
     * Requirements: 5.7
     */
    @Override
    @Transactional(readOnly = true)
    public List<AttendanceLogResponse> getAttendanceHistory(Long meetingId, User requester) {
        findMeetingOrThrow(meetingId); // ensure meeting exists
        return attendanceLogRepository.findByMeetingId(meetingId)
                .stream()
                .map(AttendanceLogResponse::from)
                .toList();
    }

    /**
     * Get currently active participants (no leave_time).
     * Requirements: 5.6
     */
    @Override
    @Transactional(readOnly = true)
    public List<AttendanceLogResponse> getActiveParticipants(Long meetingId, User requester) {
        findMeetingOrThrow(meetingId);
        return attendanceLogRepository.findActiveParticipants(meetingId)
                .stream()
                .map(AttendanceLogResponse::from)
                .toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Meeting findMeetingOrThrow(Long id) {
        return meetingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Meeting not found with id: " + id));
    }
}
