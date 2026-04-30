package com.example.kolla.services.impl;

import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.SpeakingPermission;
import com.example.kolla.models.User;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.SpeakingPermissionRepository;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.responses.SpeakingPermissionResponse;
import com.example.kolla.services.MeetingLifecycleService;
import com.example.kolla.services.SpeakingPermissionService;
import com.example.kolla.websocket.MeetingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * SpeakingPermissionService implementation.
 *
 * <p>Enforces the invariant: at most one participant holds speaking permission
 * at any given time within a meeting. Uses pessimistic write lock
 * ({@code SELECT FOR UPDATE}) on the active-permission query to prevent
 * concurrent grants from violating this invariant.
 *
 * Requirements: 22.4, 22.8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakingPermissionServiceImpl implements SpeakingPermissionService {

    private final SpeakingPermissionRepository speakingPermissionRepository;
    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final MeetingLifecycleService meetingLifecycleService;
    private final MeetingEventPublisher eventPublisher;
    private final Clock clock;

    // ── Grant ─────────────────────────────────────────────────────────────────

    /**
     * Grant speaking permission to a participant.
     *
     * <p>Uses {@code SELECT FOR UPDATE} to prevent concurrent grants.
     * If another participant currently holds permission, it is revoked first.
     * A new {@code speakerTurnId} UUID is generated for each grant.
     *
     * Requirements: 22.4, 22.8
     */
    @Override
    @Transactional
    public SpeakingPermissionResponse grantPermission(Long meetingId, Long targetUserId,
                                                       User granter) {
        Meeting meeting = findMeetingOrThrow(meetingId);

        // Only Host (or ADMIN) may grant permission (Requirement 22.4)
        if (!meetingLifecycleService.isHostOrAdmin(meeting, granter)) {
            throw new ForbiddenException(
                    "Only the Host or an ADMIN may grant speaking permission");
        }

        // Meeting must be ACTIVE and in MEETING_MODE (Requirement 22.4)
        if (meeting.getStatus() != MeetingStatus.ACTIVE) {
            throw new BadRequestException(
                    "Cannot grant speaking permission: meeting is not ACTIVE");
        }
        if (meeting.getMode() != MeetingMode.MEETING_MODE) {
            throw new BadRequestException(
                    "Cannot grant speaking permission: meeting is not in MEETING_MODE");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + targetUserId));

        LocalDateTime now = LocalDateTime.now(clock);

        // Acquire pessimistic write lock — prevents concurrent grants (Requirement 22.8)
        Optional<SpeakingPermission> existing =
                speakingPermissionRepository.findActivePermissionForUpdate(meetingId);

        // Revoke existing permission if held by a different user
        if (existing.isPresent()) {
            SpeakingPermission current = existing.get();
            if (current.getUser().getId().equals(targetUserId)) {
                // Already holds permission — idempotent, return current
                log.debug("User id={} already holds speaking permission in meeting id={}",
                        targetUserId, meetingId);
                return SpeakingPermissionResponse.from(current);
            }
            // Revoke the existing holder
            current.setRevokedAt(now);
            speakingPermissionRepository.save(current);
            log.info("Revoked speaking permission from user id={} in meeting id={} (new grant)",
                    current.getUser().getId(), meetingId);
            eventPublisher.publishSpeakingPermissionRevoked(
                    meetingId, current.getUser().getId(), "HOST_REVOKED_FOR_NEW_GRANT");
        }

        // Grant new permission with a fresh speakerTurnId
        String speakerTurnId = UUID.randomUUID().toString();
        SpeakingPermission newPermission = SpeakingPermission.builder()
                .meeting(meeting)
                .user(targetUser)
                .grantedAt(now)
                .speakerTurnId(speakerTurnId)
                .build();

        SpeakingPermission saved = speakingPermissionRepository.save(newPermission);
        log.info("Granted speaking permission to user id={} (turn={}) in meeting id={}",
                targetUserId, speakerTurnId, meetingId);

        // Broadcast to all participants (Requirement 22.4, 22.5)
        eventPublisher.publishSpeakingPermissionGranted(
                meetingId, targetUserId, targetUser.getFullName(), speakerTurnId);

        return SpeakingPermissionResponse.from(saved);
    }

    // ── Revoke (Host-initiated) ───────────────────────────────────────────────

    /**
     * Revoke the current speaking permission. Host-initiated.
     * Requirements: 22.6
     */
    @Override
    @Transactional
    public SpeakingPermissionResponse revokePermission(Long meetingId, User revoker) {
        Meeting meeting = findMeetingOrThrow(meetingId);

        // Only Host (or ADMIN) may revoke (Requirement 22.6)
        if (!meetingLifecycleService.isHostOrAdmin(meeting, revoker)) {
            throw new ForbiddenException(
                    "Only the Host or an ADMIN may revoke speaking permission");
        }

        // Acquire lock and find active permission
        SpeakingPermission active = speakingPermissionRepository
                .findActivePermissionForUpdate(meetingId)
                .orElseThrow(() -> new BadRequestException(
                        "No active speaking permission to revoke in meeting " + meetingId));

        LocalDateTime now = LocalDateTime.now(clock);
        active.setRevokedAt(now);
        SpeakingPermission saved = speakingPermissionRepository.save(active);

        log.info("Host revoked speaking permission from user id={} in meeting id={}",
                active.getUser().getId(), meetingId);

        // Broadcast revocation (Requirement 22.6)
        eventPublisher.publishSpeakingPermissionRevoked(
                meetingId, active.getUser().getId(), "HOST_REVOKED");

        return SpeakingPermissionResponse.from(saved);
    }

    // ── Revoke on leave/disconnect ────────────────────────────────────────────

    /**
     * Revoke speaking permission when a participant leaves or disconnects.
     * Called internally — no permission check.
     * Requirements: 22.10
     */
    @Override
    @Transactional
    public void revokePermissionOnLeave(Long meetingId, Long userId) {
        if (!speakingPermissionRepository.hasActivePermission(meetingId, userId)) {
            return; // User doesn't hold permission — nothing to do
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int revoked = speakingPermissionRepository.revokeAllForMeeting(meetingId, now);

        if (revoked > 0) {
            log.info("Auto-revoked speaking permission for user id={} in meeting id={} (left/disconnected)",
                    userId, meetingId);
            eventPublisher.publishSpeakingPermissionRevoked(
                    meetingId, userId, "PARTICIPANT_LEFT");
        }
    }

    // ── Revoke all (mode switch / meeting end) ────────────────────────────────

    /**
     * Revoke all active permissions for a meeting.
     * Called internally on mode switch or meeting end.
     * Requirements: 21.3, 21.9
     */
    @Override
    @Transactional
    public void revokeAllPermissions(Long meetingId, String reason) {
        // Find active permission to get the user ID for the broadcast
        Optional<SpeakingPermission> active =
                speakingPermissionRepository.findActivePermission(meetingId);

        if (active.isEmpty()) {
            return; // Nothing to revoke
        }

        Long userId = active.get().getUser().getId();
        LocalDateTime now = LocalDateTime.now(clock);
        int revoked = speakingPermissionRepository.revokeAllForMeeting(meetingId, now);

        if (revoked > 0) {
            log.info("Revoked {} speaking permission(s) in meeting id={} (reason: {})",
                    revoked, meetingId, reason);
            eventPublisher.publishSpeakingPermissionRevoked(meetingId, userId, reason);
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Get the current active speaking permission, or null if none.
     * Requirements: 22.5
     */
    @Override
    @Transactional(readOnly = true)
    public SpeakingPermissionResponse getCurrentPermission(Long meetingId) {
        return speakingPermissionRepository.findActivePermission(meetingId)
                .map(SpeakingPermissionResponse::from)
                .orElse(null);
    }

    /**
     * Check whether a specific user currently holds speaking permission.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean hasActivePermission(Long meetingId, Long userId) {
        return speakingPermissionRepository.hasActivePermission(meetingId, userId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Meeting findMeetingOrThrow(Long id) {
        return meetingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Meeting not found with id: " + id));
    }
}
