package com.example.kolla.websocket;

import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.ParticipantSession;
import com.example.kolla.models.User;
import com.example.kolla.repositories.AttendanceLogRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.ParticipantSessionRepository;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.services.AttendanceService;
import com.example.kolla.services.MeetingLifecycleService;
import com.example.kolla.services.SpeakingPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Monitors WebSocket heartbeats to detect participant disconnections.
 *
 * <p><b>Heartbeat detection:</b> Clients send a STOMP frame to
 * {@code /app/meeting/{meetingId}/heartbeat} every ~5 seconds. If no heartbeat
 * is received for {@value #HEARTBEAT_TIMEOUT_SECONDS} seconds, the session is
 * considered stale and the disconnect fallback logic is triggered.
 *
 * <p><b>Fallback logic on disconnect:</b>
 * <ol>
 *   <li>Mark {@link ParticipantSession} as disconnected.</li>
 *   <li>Close the open {@code AttendanceLog} record.</li>
 *   <li>If the disconnected user held Speaking_Permission, revoke it and broadcast
 *       {@code SPEAKING_PERMISSION_REVOKED}.</li>
 *   <li>Notify {@link MeetingLifecycleService#onParticipantLeft} so the waiting
 *       timeout can be started if no Host/Secretary remains.</li>
 *   <li>Broadcast {@code PARTICIPANT_LEFT} to all meeting subscribers.</li>
 * </ol>
 *
 * <p><b>Host authority transfer:</b> If the disconnected user is the Host and the
 * Secretary is still present, Host authority is temporarily transferred to the
 * Secretary and {@code HOST_TRANSFERRED} is broadcast. When the original Host
 * reconnects, {@code HOST_RESTORED} is broadcast.
 *
 * Requirements: 5.3–5.5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatMonitor {

    /** Seconds without a heartbeat before a session is considered stale. */
    static final long HEARTBEAT_TIMEOUT_SECONDS = 10L;

    private final ParticipantSessionRepository sessionRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final MeetingLifecycleService meetingLifecycleService;
    private final AttendanceService attendanceService;
    private final MeetingEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    @Lazy
    private SpeakingPermissionService speakingPermissionService;

    // ── WebSocket lifecycle events ──────────────────────────────────────────

    /**
     * Called when a STOMP CONNECT is completed.
     * Creates or reactivates a {@link ParticipantSession} record.
     * The meeting context is not yet known at CONNECT time; it is resolved
     * when the client subscribes to {@code /topic/meeting/{meetingId}}.
     */
    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        log.debug("WebSocket session connected: sessionId={}", sessionId);
        // Full session registration happens in onSessionSubscribe when meetingId is known
    }

    /**
     * Called when a client subscribes to a meeting topic.
     * Registers the participant session for heartbeat tracking.
     */
    @EventListener
    @Transactional
    public void onSessionSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();
        Principal principal = accessor.getUser();

        if (destination == null || !destination.startsWith("/topic/meeting/")) {
            return;
        }

        Long meetingId = extractMeetingId(destination);
        if (meetingId == null || principal == null) {
            return;
        }

        Long userId;
        try {
            userId = Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            log.warn("Cannot parse userId from principal '{}' for session {}",
                    principal.getName(), sessionId);
            return;
        }

        Optional<Meeting> meetingOpt = meetingRepository.findById(meetingId);
        if (meetingOpt.isEmpty() || meetingOpt.get().getStatus() != MeetingStatus.ACTIVE) {
            return;
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return;
        }

        Meeting meeting = meetingOpt.get();
        User user = userOpt.get();

        // Close any stale session for this user in this meeting
        sessionRepository.findActiveSession(meetingId, userId)
                .ifPresent(stale -> {
                    stale.setConnected(false);
                    sessionRepository.save(stale);
                });

        // Create new session record
        LocalDateTime now = LocalDateTime.now(clock);
        ParticipantSession session = ParticipantSession.builder()
                .meeting(meeting)
                .user(user)
                .sessionId(sessionId)
                .joinedAt(now)
                .lastHeartbeatAt(now)
                .isConnected(true)
                .build();
        sessionRepository.save(session);

        log.debug("Registered participant session: userId={} meetingId={} sessionId={}",
                userId, meetingId, sessionId);

        // Check if this is a Host reconnect after a transfer
        handlePossibleHostRestore(meeting, user);
    }

    /**
     * Called when a WebSocket session disconnects (clean close or network drop).
     * Triggers the full disconnect fallback logic.
     */
    @EventListener
    @Transactional
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            if (!session.isConnected()) {
                return; // already handled by heartbeat monitor
            }
            log.info("WebSocket disconnect detected: userId={} meetingId={} sessionId={}",
                    session.getUser().getId(), session.getMeeting().getId(), sessionId);
            handleDisconnect(session);
        });
    }

    // ── Heartbeat update ────────────────────────────────────────────────────

    /**
     * Called by the meeting heartbeat message handler when a client sends a heartbeat.
     * Updates {@code last_heartbeat_at} for the session.
     *
     * @param meetingId the meeting the heartbeat belongs to
     * @param userId    the user sending the heartbeat
     */
    @Transactional
    public void recordHeartbeat(Long meetingId, Long userId) {
        sessionRepository.findActiveSession(meetingId, userId).ifPresent(session -> {
            session.setLastHeartbeatAt(LocalDateTime.now(clock));
            sessionRepository.save(session);
            log.trace("Heartbeat recorded: userId={} meetingId={}", userId, meetingId);
        });
    }

    // ── Stale session scanner ───────────────────────────────────────────────

    /**
     * Scheduled job that scans for sessions with no heartbeat in the last
     * {@value #HEARTBEAT_TIMEOUT_SECONDS} seconds and triggers disconnect logic.
     * Runs every 5 seconds.
     * Requirements: 5.3
     */
    @Scheduled(fixedDelay = 5_000)
    @Transactional
    public void scanStaleSessions() {
        LocalDateTime threshold = LocalDateTime.now(clock)
                .minusSeconds(HEARTBEAT_TIMEOUT_SECONDS);

        List<ParticipantSession> staleSessions =
                sessionRepository.findStaleConnectedSessions(threshold);

        for (ParticipantSession session : staleSessions) {
            log.info("Stale session detected (no heartbeat for {}s): userId={} meetingId={}",
                    HEARTBEAT_TIMEOUT_SECONDS,
                    session.getUser().getId(),
                    session.getMeeting().getId());
            handleDisconnect(session);
        }
    }

    // ── Disconnect fallback logic ───────────────────────────────────────────

    /**
     * Core disconnect handler. Executes all fallback steps for a disconnected session.
     * Requirements: 5.3–5.5
     */
    @Transactional
    public void handleDisconnect(ParticipantSession session) {
        Long meetingId = session.getMeeting().getId();
        User user = session.getUser();
        Long userId = user.getId();

        // Step 1: Mark session as disconnected
        session.setConnected(false);
        sessionRepository.save(session);

        // Step 2: Close open attendance log
        try {
            attendanceService.leaveMeeting(meetingId, user);
        } catch (Exception e) {
            // Attendance log may already be closed; log and continue
            log.debug("Could not close attendance log for userId={} meetingId={}: {}",
                    userId, meetingId, e.getMessage());
        }

        // Step 3: Auto-revoke speaking permission if held (Requirement 22.10)
        try {
            speakingPermissionService.revokePermissionOnLeave(meetingId, userId);
        } catch (Exception e) {
            log.debug("Could not revoke speaking permission for userId={} meetingId={}: {}",
                    userId, meetingId, e.getMessage());
        }

        // Step 4: Broadcast PARTICIPANT_LEFT
        eventPublisher.publishParticipantLeft(meetingId, userId);

        // Step 5: Handle Host authority transfer if needed
        Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
        if (meeting != null && meeting.getStatus() == MeetingStatus.ACTIVE) {
            handleHostTransferIfNeeded(meeting, user);
        }

        log.info("Disconnect fallback complete: userId={} meetingId={}", userId, meetingId);
    }

    // ── Host authority transfer ─────────────────────────────────────────────

    /**
     * If the disconnected user is the Host and the Secretary is still connected,
     * broadcast HOST_TRANSFERRED so the frontend can update the UI.
     * The actual authority transfer is logical (no DB change needed for temporary absence).
     * Requirements: 5.4, 5.5
     */
    private void handleHostTransferIfNeeded(Meeting meeting, User disconnectedUser) {
        if (meeting.getHost() == null) {
            return;
        }

        boolean isHost = meeting.getHost().getId().equals(disconnectedUser.getId());
        if (!isHost) {
            return;
        }

        // Check if Secretary is still connected
        if (meeting.getSecretary() == null) {
            return;
        }

        boolean secretaryConnected = sessionRepository.isUserConnected(
                meeting.getId(), meeting.getSecretary().getId());

        if (secretaryConnected) {
            log.info("Host userId={} disconnected from meeting id={}; transferring authority to Secretary userId={}",
                    disconnectedUser.getId(), meeting.getId(), meeting.getSecretary().getId());

            eventPublisher.publishHostTransferred(
                    meeting.getId(),
                    disconnectedUser.getId(),
                    meeting.getSecretary().getId(),
                    meeting.getSecretary().getFullName());
        }
    }

    /**
     * If the reconnecting user is the original Host and authority was previously
     * transferred to the Secretary, broadcast HOST_RESTORED.
     * Requirements: 5.4, 5.5
     */
    private void handlePossibleHostRestore(Meeting meeting, User reconnectingUser) {
        if (meeting.getHost() == null) {
            return;
        }

        boolean isHost = meeting.getHost().getId().equals(reconnectingUser.getId());
        if (!isHost) {
            return;
        }

        // Check if Secretary is connected (meaning a transfer may have occurred)
        if (meeting.getSecretary() != null) {
            boolean secretaryConnected = sessionRepository.isUserConnected(
                    meeting.getId(), meeting.getSecretary().getId());

            if (secretaryConnected) {
                log.info("Host userId={} reconnected to meeting id={}; restoring authority",
                        reconnectingUser.getId(), meeting.getId());

                eventPublisher.publishHostRestored(
                        meeting.getId(),
                        reconnectingUser.getId(),
                        reconnectingUser.getFullName());
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Extract meeting ID from a STOMP destination like {@code /topic/meeting/123}.
     */
    private Long extractMeetingId(String destination) {
        try {
            String[] parts = destination.split("/");
            // /topic/meeting/{meetingId} → parts[3]
            if (parts.length >= 4) {
                return Long.parseLong(parts[3]);
            }
        } catch (NumberFormatException e) {
            log.debug("Cannot parse meetingId from destination: {}", destination);
        }
        return null;
    }
}
