package com.example.kolla.websocket;

import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.TranscriptionPriority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Helper component that broadcasts all meeting WebSocket event types via STOMP.
 *
 * <p>All broadcast events are sent to {@code /topic/meeting/{meetingId}}.
 * Personal notifications are sent to {@code /user/{userId}/queue/notifications}.
 *
 * <p>Each public method corresponds to one {@link MeetingEventType} and accepts
 * only the fields required for that event's payload (as documented in design.md).
 *
 * Requirements: 10.2–10.4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingEventPublisher {

    private static final String MEETING_TOPIC = "/topic/meeting/";
    private static final String USER_NOTIFICATIONS = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    // ── Meeting lifecycle ───────────────────────────────────────────────────

    /**
     * Broadcast MEETING_STARTED when Host activates the meeting.
     * Requirements: 10.2
     */
    public void publishMeetingStarted(Long meetingId, String hostName) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.MEETING_STARTED)
                .meetingId(meetingId)
                .payload(Map.of(
                        "meetingId", meetingId,
                        "hostName", hostName))
                .build());
    }

    /**
     * Broadcast MEETING_ENDED when the meeting transitions to ENDED.
     * @param reason human-readable reason (e.g. "HOST_ENDED", "WAITING_TIMEOUT")
     */
    public void publishMeetingEnded(Long meetingId, String reason) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.MEETING_ENDED)
                .meetingId(meetingId)
                .payload(Map.of(
                        "meetingId", meetingId,
                        "reason", reason))
                .build());
    }

    // ── Mode switching ──────────────────────────────────────────────────────

    /**
     * Broadcast MODE_CHANGED when Host switches between FREE_MODE and MEETING_MODE.
     * Requirements: 21.2, 21.3
     */
    public void publishModeChanged(Long meetingId, MeetingMode newMode) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.MODE_CHANGED)
                .meetingId(meetingId)
                .payload(Map.of("mode", newMode.name()))
                .build());
    }

    // ── Raise hand / speaking permission ───────────────────────────────────

    /**
     * Broadcast RAISE_HAND when a participant submits a raise-hand request.
     * Requirements: 22.2
     */
    public void publishRaiseHand(Long meetingId, Long userId, String userName,
                                  ZonedDateTime requestedAt) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.RAISE_HAND)
                .meetingId(meetingId)
                .payload(Map.of(
                        "userId", userId,
                        "userName", userName,
                        "requestedAt", requestedAt.toString()))
                .build());
    }

    /**
     * Broadcast HAND_LOWERED when a participant cancels their raise-hand request.
     * Requirements: 22.7
     */
    public void publishHandLowered(Long meetingId, Long userId) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.HAND_LOWERED)
                .meetingId(meetingId)
                .payload(Map.of("userId", userId))
                .build());
    }

    /**
     * Broadcast SPEAKING_PERMISSION_GRANTED when Host grants speaking permission.
     * Requirements: 22.4, 22.5
     */
    public void publishSpeakingPermissionGranted(Long meetingId, Long userId,
                                                  String userName, String speakerTurnId) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.SPEAKING_PERMISSION_GRANTED)
                .meetingId(meetingId)
                .payload(Map.of(
                        "userId", userId,
                        "userName", userName,
                        "speakerTurnId", speakerTurnId))
                .build());
    }

    /**
     * Broadcast SPEAKING_PERMISSION_REVOKED when permission is revoked.
     * @param reason e.g. "HOST_REVOKED", "PARTICIPANT_LEFT", "MODE_SWITCHED", "MEETING_ENDED"
     * Requirements: 22.6, 22.10
     */
    public void publishSpeakingPermissionRevoked(Long meetingId, Long userId, String reason) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.SPEAKING_PERMISSION_REVOKED)
                .meetingId(meetingId)
                .payload(Map.of(
                        "userId", userId,
                        "reason", reason))
                .build());
    }

    // ── Participant presence ────────────────────────────────────────────────

    /**
     * Broadcast PARTICIPANT_JOINED when a user joins the active meeting.
     * Requirements: 5.1
     */
    public void publishParticipantJoined(Long meetingId, Long userId, String userName) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.PARTICIPANT_JOINED)
                .meetingId(meetingId)
                .payload(Map.of(
                        "userId", userId,
                        "userName", userName))
                .build());
    }

    /**
     * Broadcast PARTICIPANT_LEFT when a user leaves the active meeting.
     * Requirements: 5.3
     */
    public void publishParticipantLeft(Long meetingId, Long userId) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.PARTICIPANT_LEFT)
                .meetingId(meetingId)
                .payload(Map.of("userId", userId))
                .build());
    }

    // ── Host authority ──────────────────────────────────────────────────────

    /**
     * Broadcast HOST_TRANSFERRED when Host authority is transferred to another user.
     * Requirements: 5.4, 5.5
     */
    public void publishHostTransferred(Long meetingId, Long fromUserId,
                                        Long toUserId, String toUserName) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.HOST_TRANSFERRED)
                .meetingId(meetingId)
                .payload(Map.of(
                        "fromUserId", fromUserId,
                        "toUserId", toUserId,
                        "toUserName", toUserName))
                .build());
    }

    /**
     * Broadcast HOST_RESTORED when the original Host reconnects.
     * Requirements: 5.4, 5.5
     */
    public void publishHostRestored(Long meetingId, Long userId, String userName) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.HOST_RESTORED)
                .meetingId(meetingId)
                .payload(Map.of(
                        "userId", userId,
                        "userName", userName))
                .build());
    }

    // ── Waiting timeout ─────────────────────────────────────────────────────

    /**
     * Broadcast WAITING_TIMEOUT_STARTED when no Host or Secretary is present.
     * Requirements: 3.11
     */
    public void publishWaitingTimeoutStarted(Long meetingId, ZonedDateTime expiresAt) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.WAITING_TIMEOUT_STARTED)
                .meetingId(meetingId)
                .payload(Map.of("expiresAt", expiresAt.toString()))
                .build());
    }

    /**
     * Broadcast WAITING_TIMEOUT_CANCELLED when Host or Secretary reconnects.
     * Requirements: 3.11
     */
    public void publishWaitingTimeoutCancelled(Long meetingId) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.WAITING_TIMEOUT_CANCELLED)
                .meetingId(meetingId)
                .payload(Map.of())
                .build());
    }

    // ── Transcription ───────────────────────────────────────────────────────

    /**
     * Broadcast TRANSCRIPTION_SEGMENT for HIGH_PRIORITY meetings.
     * Requirements: 8.12
     */
    public void publishTranscriptionSegment(Long meetingId, Long speakerId,
                                             String speakerName, String speakerTurnId,
                                             int sequenceNumber, String text,
                                             ZonedDateTime segmentStartTime) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.TRANSCRIPTION_SEGMENT)
                .meetingId(meetingId)
                .payload(Map.of(
                        "speakerId", speakerId,
                        "speakerName", speakerName,
                        "speakerTurnId", speakerTurnId,
                        "sequenceNumber", sequenceNumber,
                        "text", text,
                        "segmentStartTime", segmentStartTime.toString()))
                .build());
    }

    /**
     * Broadcast PRIORITY_CHANGED when meeting transcription priority is updated.
     * Requirements: 8.12, 8.13
     */
    public void publishPriorityChanged(Long meetingId, TranscriptionPriority priority) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.PRIORITY_CHANGED)
                .meetingId(meetingId)
                .payload(Map.of("priority", priority.name()))
                .build());
    }

    /**
     * Broadcast TRANSCRIPTION_UNAVAILABLE when Gipformer service is down.
     * Requirements: 8.7
     */
    public void publishTranscriptionUnavailable(Long meetingId, String message) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.TRANSCRIPTION_UNAVAILABLE)
                .meetingId(meetingId)
                .payload(Map.of("message", message))
                .build());
    }

    /**
     * Broadcast TRANSCRIPTION_RECOVERED when Gipformer service comes back online.
     * Requirements: 8.7
     */
    public void publishTranscriptionRecovered(Long meetingId, int pendingJobCount) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.TRANSCRIPTION_RECOVERED)
                .meetingId(meetingId)
                .payload(Map.of("pendingJobCount", pendingJobCount))
                .build());
    }

    // ── Documents ───────────────────────────────────────────────────────────

    /**
     * Broadcast DOCUMENT_UPLOADED when a new document is added to the meeting.
     * Requirements: 10.4
     */
    public void publishDocumentUploaded(Long meetingId, Long documentId,
                                         String fileName, String uploadedBy) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.DOCUMENT_UPLOADED)
                .meetingId(meetingId)
                .payload(Map.of(
                        "documentId", documentId,
                        "fileName", fileName,
                        "uploadedBy", uploadedBy))
                .build());
    }

    // ── Minutes ─────────────────────────────────────────────────────────────

    /**
     * Broadcast MINUTES_READY when draft minutes are generated.
     * Requirements: 25.3
     */
    public void publishMinutesReady(Long meetingId, Long minutesId) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.MINUTES_READY)
                .meetingId(meetingId)
                .payload(Map.of("minutesId", minutesId))
                .build());
    }

    /**
     * Broadcast MINUTES_CONFIRMED when Host confirms the minutes.
     * Requirements: 25.4
     */
    public void publishMinutesConfirmed(Long meetingId, Long minutesId) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.MINUTES_CONFIRMED)
                .meetingId(meetingId)
                .payload(Map.of("minutesId", minutesId))
                .build());
    }

    /**
     * Broadcast MINUTES_PUBLISHED when Secretary confirms/edits the minutes.
     * Requirements: 25.5
     */
    public void publishMinutesPublished(Long meetingId, Long minutesId) {
        broadcast(meetingId, MeetingEvent.builder()
                .type(MeetingEventType.MINUTES_PUBLISHED)
                .meetingId(meetingId)
                .payload(Map.of("minutesId", minutesId))
                .build());
    }

    // ── Personal notifications ──────────────────────────────────────────────

    /**
     * Send a personal notification to a specific user.
     * Sent to {@code /user/{userId}/queue/notifications}.
     * Requirements: 10.3, 10.5
     */
    public void sendNotificationToUser(String userId, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(userId, USER_NOTIFICATIONS, payload);
            log.debug("Sent personal notification to user {}", userId);
        } catch (Exception e) {
            log.error("Failed to send notification to user {}: {}", userId, e.getMessage());
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Broadcast an event to all subscribers of {@code /topic/meeting/{meetingId}}.
     */
    private void broadcast(Long meetingId, MeetingEvent event) {
        String destination = MEETING_TOPIC + meetingId;
        try {
            messagingTemplate.convertAndSend(destination, event);
            log.debug("Broadcast {} to {}", event.getType(), destination);
        } catch (Exception e) {
            log.error("Failed to broadcast {} to {}: {}", event.getType(), destination, e.getMessage());
        }
    }
}
