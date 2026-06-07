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
import com.example.kolla.responses.MeetingResponse;
import com.example.kolla.services.MeetingLifecycleService;
import com.example.kolla.services.MeetingModeService;
import com.example.kolla.services.RaiseHandQueueService;
import com.example.kolla.services.SpeakingPermissionService;
import com.example.kolla.websocket.MeetingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * MeetingModeService implementation.
 *
 * <p>Handles FREE_MODE ↔ MEETING_MODE transitions with the following guarantees:
 * <ul>
 *   <li>Only the Host may switch modes (Requirement 21.7 — ADMIN/Secretary removed per TASK-001).</li>
 *   <li>Switching to FREE_MODE while a speaker holds permission:
 *       finalize audio chunk → push to Redis → revoke permission → expire raise-hand
 *       requests → broadcast MODE_CHANGED. The broadcast is the last step, so
 *       participants do not see the transition until cleanup is complete
 *       (Requirement 21.9, 21.10).</li>
 *   <li>Switching to MEETING_MODE: update DB → broadcast MODE_CHANGED
 *       (Requirement 21.2).</li>
 * </ul>
 *
 * Requirements: 21.1–21.10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingModeServiceImpl implements MeetingModeService {

    /**
     * Redis key prefix for signalling audio chunk finalization.
     * The audio stream handler listens for this key to flush the current chunk.
     * Format: {@code meeting:{meetingId}:finalize_chunk:{speakerTurnId}}
     */
    private static final String FINALIZE_CHUNK_KEY_PREFIX =
            "meeting:%d:finalize_chunk:%s";

    private final MeetingRepository meetingRepository;
    private final SpeakingPermissionRepository speakingPermissionRepository;
    private final RaiseHandQueueService raiseHandQueueService;
    private final MeetingLifecycleService meetingLifecycleService;
    private final SpeakingPermissionService speakingPermissionService;
    private final MeetingEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    // ── Switch mode ───────────────────────────────────────────────────────────

    /**
     * Switch the meeting mode.
     *
     * <p>FREE_MODE → MEETING_MODE: straightforward DB update + broadcast.
     * <p>MEETING_MODE → FREE_MODE: finalize audio chunk, revoke permission,
     *    expire raise-hand requests, then broadcast (Requirement 21.9, 21.10).
     *
     * Requirements: 21.1–21.10
     */
    @Override
    @Transactional
    public MeetingResponse switchMode(Long meetingId, MeetingMode targetMode, User requester) {
        Meeting meeting = findMeetingOrThrow(meetingId);

        // Permission check: Host only (TASK-001 — ADMIN and Secretary removed)
        if (!meetingLifecycleService.hasHostAuthority(meeting, requester)) {
            throw new ForbiddenException(
                    "Only the Host may switch meeting modes");
        }

        // Meeting must be ACTIVE
        if (meeting.getStatus() != MeetingStatus.ACTIVE) {
            throw new BadRequestException(
                    "Cannot switch mode: meeting is not ACTIVE (current: "
                    + meeting.getStatus() + ")");
        }

        // No-op if already in the requested mode
        if (meeting.getMode() == targetMode) {
            throw new BadRequestException(
                    "Meeting is already in " + targetMode + " mode");
        }

        if (targetMode == MeetingMode.MEETING_MODE) {
            return switchToMeetingMode(meeting, requester);
        } else {
            return switchToFreeMode(meeting, requester);
        }
    }

    // ── System-initiated mode switch (no permission check) ──────────────────────

    /**
     * Switch mode without permission check — for system-initiated transitions
     * (e.g., when both Host and Secretary disconnect).
     * NOT exposed via REST. (TASK-002)
     */
    @Override
    @Transactional
    public void switchModeInternal(Long meetingId, MeetingMode mode) {
        Meeting meeting = findMeetingOrThrow(meetingId);
        if (meeting.getStatus() != MeetingStatus.ACTIVE) return;
        if (meeting.getMode() == mode) return;

        if (mode == MeetingMode.FREE_MODE) {
            switchToFreeMode(meeting, null); // null requester = system
        } else {
            switchToMeetingMode(meeting, null);
        }
    }

    // ── Get current mode ──────────────────────────────────────────────────────

    /**
     * Get the current mode of an active meeting.
     * Requirements: 21.1
     */
    @Override
    @Transactional(readOnly = true)
    public MeetingMode getCurrentMode(Long meetingId, User requester) {
        Meeting meeting = findMeetingOrThrow(meetingId);
        return meeting.getMode();
    }

    // ── Private: switch to MEETING_MODE ──────────────────────────────────────

    /**
     * FREE_MODE → MEETING_MODE.
     *
     * <p>Steps:
     * <ol>
     *   <li>Update meeting mode in DB.</li>
     *   <li>Broadcast MODE_CHANGED — frontend mutes all participants.</li>
     * </ol>
     *
     * Requirements: 21.2, 21.4
     */
    private MeetingResponse switchToMeetingMode(Meeting meeting, User requester) {
        meeting.setMode(MeetingMode.MEETING_MODE);
        Meeting saved = meetingRepository.save(meeting);

        String requesterName = requester != null ? requester.getUsername() : "system";
        log.info("Meeting id={} switched to MEETING_MODE by '{}'",
                meeting.getId(), requesterName);

        // Broadcast — frontend will mute all participants (Requirement 21.4)
        eventPublisher.publishModeChanged(meeting.getId(), MeetingMode.MEETING_MODE);

        return MeetingResponse.from(saved);
    }

    // ── Private: switch to FREE_MODE ─────────────────────────────────────────

    /**
     * MEETING_MODE → FREE_MODE.
     *
     * <p>Steps (Requirement 21.9, 21.10):
     * <ol>
     *   <li>Find active speaking permission (if any).</li>
     *   <li>Signal audio chunk finalization via Redis key.</li>
     *   <li>Revoke speaking permission (broadcasts SPEAKING_PERMISSION_REVOKED).</li>
     *   <li>Expire all pending raise-hand requests.</li>
     *   <li>Update meeting mode in DB.</li>
     *   <li>Broadcast MODE_CHANGED — this is the last step so participants do not
     *       see the transition until cleanup is complete.</li>
     * </ol>
     *
     * Requirements: 21.3, 21.5, 21.9, 21.10
     */
    private MeetingResponse switchToFreeMode(Meeting meeting, User requester) {
        Long meetingId = meeting.getId();
        LocalDateTime now = LocalDateTime.now(clock);

        // Step 1: Check for active speaking permission
        Optional<SpeakingPermission> activePermission =
                speakingPermissionRepository.findActivePermission(meetingId);

        if (activePermission.isPresent()) {
            SpeakingPermission sp = activePermission.get();
            String speakerTurnId = sp.getSpeakerTurnId();

            // Step 2: Signal audio chunk finalization via Redis
            signalChunkFinalization(meetingId, speakerTurnId);

            // Step 3: Revoke speaking permission (broadcasts SPEAKING_PERMISSION_REVOKED)
            speakingPermissionService.revokeAllPermissions(meetingId, "MODE_SWITCHED");

            log.info("Revoked speaking permission (turn={}) for mode switch in meeting id={}",
                    speakerTurnId, meetingId);
        }

        // Step 4: Clear raise-hand queue (Requirement 21.3)
        raiseHandQueueService.clearAll(meetingId);
        log.debug("Cleared raise-hand queue for meeting id={} (mode switch)", meetingId);

        // Step 5: Update meeting mode in DB
        meeting.setMode(MeetingMode.FREE_MODE);
        Meeting saved = meetingRepository.save(meeting);

        String requesterName = requester != null ? requester.getUsername() : "system";
        log.info("Meeting id={} switched to FREE_MODE by '{}'",
                meetingId, requesterName);

        // Step 6: Broadcast MODE_CHANGED — LAST step (Requirement 21.10)
        eventPublisher.publishModeChanged(meetingId, MeetingMode.FREE_MODE);

        return MeetingResponse.from(saved);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Publish a Redis key to signal the AudioStreamHandler to finalize the current
     * audio chunk for the given speaker turn.
     *
     * <p>The key has a short TTL (30s) — if the handler doesn't pick it up in time,
     * it will be cleaned up automatically.
     *
     * Requirements: 21.9
     */
    private void signalChunkFinalization(Long meetingId, String speakerTurnId) {
        String key = String.format(FINALIZE_CHUNK_KEY_PREFIX, meetingId, speakerTurnId);
        try {
            redisTemplate.opsForValue().set(key, "1",
                    30, java.util.concurrent.TimeUnit.SECONDS);
            log.debug("Signalled chunk finalization: key={}", key);
        } catch (Exception e) {
            // Non-fatal: the audio handler will finalize on disconnect as a fallback
            log.warn("Failed to signal chunk finalization for meeting id={}, turn={}: {}",
                    meetingId, speakerTurnId, e.getMessage());
        }
    }

    /**
     * Check whether the given user is the designated Secretary of this meeting.
     */
    private boolean isSecretaryOfMeeting(Meeting meeting, User user) {
        return meeting.getSecretary() != null
                && meeting.getSecretary().getId().equals(user.getId());
    }

    private Meeting findMeetingOrThrow(Long id) {
        return meetingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Meeting not found with id: " + id));
    }
}
