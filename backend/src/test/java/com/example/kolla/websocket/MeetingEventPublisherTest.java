package com.example.kolla.websocket;

import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.TranscriptionPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.ZonedDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MeetingEventPublisher.
 *
 * <p>Verifies that each publish method:
 * <ol>
 *   <li>Sends to the correct STOMP destination.</li>
 *   <li>Includes the correct event type in the payload.</li>
 *   <li>Includes the expected payload fields.</li>
 *   <li>Does not throw when the messaging template throws (fail-safe).</li>
 * </ol>
 *
 * Requirements: 10.2–10.4, 20.2
 */
@ExtendWith(MockitoExtension.class)
class MeetingEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MeetingEventPublisher publisher;

    private static final Long MEETING_ID = 42L;
    private static final String EXPECTED_TOPIC = "/topic/meeting/42";

    // ── Helper ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private MeetingEvent captureEvent() {
        ArgumentCaptor<MeetingEvent> captor = ArgumentCaptor.forClass(MeetingEvent.class);
        verify(messagingTemplate).convertAndSend(eq(EXPECTED_TOPIC), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(MeetingEvent event) {
        return (Map<String, Object>) event.getPayload();
    }

    // ── Meeting lifecycle ───────────────────────────────────────────────────

    @Nested
    @DisplayName("publishMeetingStarted()")
    class PublishMeetingStarted {

        @Test
        @DisplayName("Sends to /topic/meeting/{id} with MEETING_STARTED type")
        void sendsToCorrectTopic() {
            publisher.publishMeetingStarted(MEETING_ID, "Alice");

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.MEETING_STARTED);
            assertThat(event.getMeetingId()).isEqualTo(MEETING_ID);
        }

        @Test
        @DisplayName("Payload contains hostName")
        void payloadContainsHostName() {
            publisher.publishMeetingStarted(MEETING_ID, "Alice");

            MeetingEvent event = captureEvent();
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("hostName", "Alice");
        }

        @Test
        @DisplayName("Does not throw when messaging template fails")
        void doesNotThrowOnMessagingFailure() {
            doThrow(new RuntimeException("broker down"))
                    .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

            assertThatNoException().isThrownBy(
                    () -> publisher.publishMeetingStarted(MEETING_ID, "Alice"));
        }
    }

    @Nested
    @DisplayName("publishMeetingEnded()")
    class PublishMeetingEnded {

        @Test
        @DisplayName("Sends MEETING_ENDED with reason")
        void sendsWithReason() {
            publisher.publishMeetingEnded(MEETING_ID, "HOST_ENDED");

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.MEETING_ENDED);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("reason", "HOST_ENDED");
        }
    }

    // ── Mode switching ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("publishModeChanged()")
    class PublishModeChanged {

        @Test
        @DisplayName("Sends MODE_CHANGED with FREE_MODE")
        void sendsFreeMode() {
            publisher.publishModeChanged(MEETING_ID, MeetingMode.FREE_MODE);

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.MODE_CHANGED);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("mode", "FREE_MODE");
        }

        @Test
        @DisplayName("Sends MODE_CHANGED with MEETING_MODE")
        void sendsMeetingMode() {
            publisher.publishModeChanged(MEETING_ID, MeetingMode.MEETING_MODE);

            MeetingEvent event = captureEvent();
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("mode", "MEETING_MODE");
        }
    }

    // ── Raise hand / speaking permission ───────────────────────────────────

    @Nested
    @DisplayName("publishRaiseHand()")
    class PublishRaiseHand {

        @Test
        @DisplayName("Sends RAISE_HAND with userId, userName, requestedAt")
        void sendsRaiseHand() {
            ZonedDateTime now = ZonedDateTime.now();
            publisher.publishRaiseHand(MEETING_ID, 7L, "Bob", now);

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.RAISE_HAND);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("userId", 7L);
            assertThat(payload).containsEntry("userName", "Bob");
            assertThat(payload).containsKey("requestedAt");
        }
    }

    @Nested
    @DisplayName("publishHandLowered()")
    class PublishHandLowered {

        @Test
        @DisplayName("Sends HAND_LOWERED with userId")
        void sendsHandLowered() {
            publisher.publishHandLowered(MEETING_ID, 7L);

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.HAND_LOWERED);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("userId", 7L);
        }
    }

    @Nested
    @DisplayName("publishSpeakingPermissionGranted()")
    class PublishSpeakingPermissionGranted {

        @Test
        @DisplayName("Sends SPEAKING_PERMISSION_GRANTED with userId, userName, speakerTurnId")
        void sendsGranted() {
            publisher.publishSpeakingPermissionGranted(MEETING_ID, 7L, "Bob", "turn-uuid-123");

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.SPEAKING_PERMISSION_GRANTED);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("userId", 7L);
            assertThat(payload).containsEntry("userName", "Bob");
            assertThat(payload).containsEntry("speakerTurnId", "turn-uuid-123");
        }
    }

    @Nested
    @DisplayName("publishSpeakingPermissionRevoked()")
    class PublishSpeakingPermissionRevoked {

        @Test
        @DisplayName("Sends SPEAKING_PERMISSION_REVOKED with userId and reason")
        void sendsRevoked() {
            publisher.publishSpeakingPermissionRevoked(MEETING_ID, 7L, "HOST_REVOKED");

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.SPEAKING_PERMISSION_REVOKED);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("userId", 7L);
            assertThat(payload).containsEntry("reason", "HOST_REVOKED");
        }
    }

    // ── Participant presence ────────────────────────────────────────────────

    @Nested
    @DisplayName("publishParticipantJoined()")
    class PublishParticipantJoined {

        @Test
        @DisplayName("Sends PARTICIPANT_JOINED with userId and userName")
        void sendsJoined() {
            publisher.publishParticipantJoined(MEETING_ID, 5L, "Carol");

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.PARTICIPANT_JOINED);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("userId", 5L);
            assertThat(payload).containsEntry("userName", "Carol");
        }
    }

    @Nested
    @DisplayName("publishParticipantLeft()")
    class PublishParticipantLeft {

        @Test
        @DisplayName("Sends PARTICIPANT_LEFT with userId")
        void sendsLeft() {
            publisher.publishParticipantLeft(MEETING_ID, 5L);

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.PARTICIPANT_LEFT);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("userId", 5L);
        }
    }

    // ── Host authority ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("publishHostTransferred()")
    class PublishHostTransferred {

        @Test
        @DisplayName("Sends HOST_TRANSFERRED with from/to user info")
        void sendsTransferred() {
            publisher.publishHostTransferred(MEETING_ID, 1L, 2L, "Secretary Dave");

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.HOST_TRANSFERRED);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("fromUserId", 1L);
            assertThat(payload).containsEntry("toUserId", 2L);
            assertThat(payload).containsEntry("toUserName", "Secretary Dave");
        }
    }

    @Nested
    @DisplayName("publishHostRestored()")
    class PublishHostRestored {

        @Test
        @DisplayName("Sends HOST_RESTORED with userId and userName")
        void sendsRestored() {
            publisher.publishHostRestored(MEETING_ID, 1L, "Host Alice");

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.HOST_RESTORED);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("userId", 1L);
            assertThat(payload).containsEntry("userName", "Host Alice");
        }
    }

    // ── Waiting timeout ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("publishWaitingTimeoutStarted()")
    class PublishWaitingTimeoutStarted {

        @Test
        @DisplayName("Sends WAITING_TIMEOUT_STARTED with expiresAt")
        void sendsTimeoutStarted() {
            ZonedDateTime expiresAt = ZonedDateTime.now().plusMinutes(10);
            publisher.publishWaitingTimeoutStarted(MEETING_ID, expiresAt);

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.WAITING_TIMEOUT_STARTED);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsKey("expiresAt");
        }
    }

    @Nested
    @DisplayName("publishWaitingTimeoutCancelled()")
    class PublishWaitingTimeoutCancelled {

        @Test
        @DisplayName("Sends WAITING_TIMEOUT_CANCELLED with empty payload")
        void sendsTimeoutCancelled() {
            publisher.publishWaitingTimeoutCancelled(MEETING_ID);

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.WAITING_TIMEOUT_CANCELLED);
        }
    }

    // ── Transcription ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("publishTranscriptionSegment()")
    class PublishTranscriptionSegment {

        @Test
        @DisplayName("Sends TRANSCRIPTION_SEGMENT with all required fields")
        void sendsSegment() {
            ZonedDateTime segmentTime = ZonedDateTime.now();
            publisher.publishTranscriptionSegment(
                    MEETING_ID, 3L, "Eve", "turn-abc", 2, "Xin chào", segmentTime);

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.TRANSCRIPTION_SEGMENT);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("speakerId", 3L);
            assertThat(payload).containsEntry("speakerName", "Eve");
            assertThat(payload).containsEntry("speakerTurnId", "turn-abc");
            assertThat(payload).containsEntry("sequenceNumber", 2);
            assertThat(payload).containsEntry("text", "Xin chào");
        }
    }

    @Nested
    @DisplayName("publishPriorityChanged()")
    class PublishPriorityChanged {

        @Test
        @DisplayName("Sends PRIORITY_CHANGED with HIGH_PRIORITY")
        void sendsHighPriority() {
            publisher.publishPriorityChanged(MEETING_ID, TranscriptionPriority.HIGH_PRIORITY);

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.PRIORITY_CHANGED);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("priority", "HIGH_PRIORITY");
        }
    }

    @Nested
    @DisplayName("publishTranscriptionUnavailable()")
    class PublishTranscriptionUnavailable {

        @Test
        @DisplayName("Sends TRANSCRIPTION_UNAVAILABLE with message")
        void sendsUnavailable() {
            publisher.publishTranscriptionUnavailable(MEETING_ID, "Gipformer is down");

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.TRANSCRIPTION_UNAVAILABLE);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("message", "Gipformer is down");
        }
    }

    @Nested
    @DisplayName("publishTranscriptionRecovered()")
    class PublishTranscriptionRecovered {

        @Test
        @DisplayName("Sends TRANSCRIPTION_RECOVERED with pendingJobCount")
        void sendsRecovered() {
            publisher.publishTranscriptionRecovered(MEETING_ID, 5);

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.TRANSCRIPTION_RECOVERED);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("pendingJobCount", 5);
        }
    }

    // ── Documents ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("publishDocumentUploaded()")
    class PublishDocumentUploaded {

        @Test
        @DisplayName("Sends DOCUMENT_UPLOADED with documentId, fileName, uploadedBy")
        void sendsDocumentUploaded() {
            publisher.publishDocumentUploaded(MEETING_ID, 99L, "agenda.pdf", "Alice");

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.DOCUMENT_UPLOADED);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("documentId", 99L);
            assertThat(payload).containsEntry("fileName", "agenda.pdf");
            assertThat(payload).containsEntry("uploadedBy", "Alice");
        }
    }

    // ── Minutes ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("publishMinutesReady()")
    class PublishMinutesReady {

        @Test
        @DisplayName("Sends MINUTES_READY with minutesId")
        void sendsMinutesReady() {
            publisher.publishMinutesReady(MEETING_ID, 10L);

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.MINUTES_READY);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("minutesId", 10L);
        }
    }

    @Nested
    @DisplayName("publishMinutesConfirmed()")
    class PublishMinutesConfirmed {

        @Test
        @DisplayName("Sends MINUTES_CONFIRMED with minutesId")
        void sendsMinutesConfirmed() {
            publisher.publishMinutesConfirmed(MEETING_ID, 10L);

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.MINUTES_CONFIRMED);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("minutesId", 10L);
        }
    }

    @Nested
    @DisplayName("publishMinutesPublished()")
    class PublishMinutesPublished {

        @Test
        @DisplayName("Sends MINUTES_PUBLISHED with minutesId")
        void sendsMinutesPublished() {
            publisher.publishMinutesPublished(MEETING_ID, 10L);

            MeetingEvent event = captureEvent();
            assertThat(event.getType()).isEqualTo(MeetingEventType.MINUTES_PUBLISHED);
            Map<String, Object> payload = payload(event);
            assertThat(payload).containsEntry("minutesId", 10L);
        }
    }

    // ── Personal notifications ──────────────────────────────────────────────

    @Nested
    @DisplayName("sendNotificationToUser()")
    class SendNotificationToUser {

        @Test
        @DisplayName("Sends to /user/{userId}/queue/notifications")
        void sendsToUserQueue() {
            publisher.sendNotificationToUser("42", Map.of("message", "Hello"));

            verify(messagingTemplate).convertAndSendToUser(
                    eq("42"), eq("/queue/notifications"), any());
        }

        @Test
        @DisplayName("Does not throw when messaging template fails")
        void doesNotThrowOnFailure() {
            doThrow(new RuntimeException("broker down"))
                    .when(messagingTemplate)
                    .convertAndSendToUser(anyString(), anyString(), any());

            assertThatNoException().isThrownBy(
                    () -> publisher.sendNotificationToUser("42", Map.of("msg", "test")));
        }
    }

    // ── Event envelope ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event envelope")
    class EventEnvelope {

        @Test
        @DisplayName("Every event has a non-null timestamp")
        void everyEventHasTimestamp() {
            publisher.publishMeetingStarted(MEETING_ID, "Alice");

            MeetingEvent event = captureEvent();
            assertThat(event.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("Every event has the correct meetingId")
        void everyEventHasCorrectMeetingId() {
            publisher.publishParticipantJoined(MEETING_ID, 1L, "Alice");

            MeetingEvent event = captureEvent();
            assertThat(event.getMeetingId()).isEqualTo(MEETING_ID);
        }
    }
}
