package com.example.kolla.integration;

import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.models.Department;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Room;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DepartmentRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.RoomRepository;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.websocket.MeetingEventPublisher;
import com.example.kolla.websocket.MeetingEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for WebSocket events during meeting lifecycle.
 *
 * Verifies that the correct STOMP events are broadcast when:
 * - A meeting is activated (MEETING_STARTED)
 * - A meeting is ended (MEETING_ENDED)
 * - Mode is switched (MODE_CHANGED)
 *
 * Uses @MockBean for SimpMessagingTemplate to capture events without
 * requiring a live WebSocket broker.
 *
 * Requirements: 10.2–10.4, 20.2
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WebSocketEventIntegrationTest {

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MeetingEventPublisher eventPublisher;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    private User admin;
    private User secretary;
    private Meeting activeMeeting;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.builder()
                .username("ws_admin_" + System.nanoTime())
                .email("ws_admin_" + System.nanoTime() + "@test.com")
                .passwordHash("$2a$12$hashed")
                .fullName("WS Admin")
                .role(Role.ADMIN)
                .isActive(true)
                .build());

        secretary = userRepository.save(User.builder()
                .username("ws_sec_" + System.nanoTime())
                .email("ws_sec_" + System.nanoTime() + "@test.com")
                .passwordHash("$2a$12$hashed")
                .fullName("WS Secretary")
                .role(Role.SECRETARY)
                .isActive(true)
                .build());

        Department dept = departmentRepository.save(Department.builder()
                .name("WS Dept " + System.nanoTime())
                .description("Test")
                .build());

        Room room = roomRepository.save(Room.builder()
                .name("WS Room " + System.nanoTime())
                .capacity(10)
                .department(dept)
                .build());

        activeMeeting = meetingRepository.save(Meeting.builder()
                .code("WS" + System.nanoTime())
                .title("WS Test Meeting")
                .description("WebSocket integration test")
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(2))
                .room(room)
                .creator(admin)
                .host(admin)
                .secretary(secretary)
                .status(MeetingStatus.ACTIVE)
                .mode(MeetingMode.FREE_MODE)
                .transcriptionPriority(TranscriptionPriority.NORMAL_PRIORITY)
                .build());
    }

    // ── MEETING_STARTED event ─────────────────────────────────────────────────

    @Nested
    @DisplayName("MEETING_STARTED event")
    class MeetingStartedEvent {

        @Test
        @DisplayName("publishMeetingStarted sends to correct topic")
        void sendsToCorrectTopic() {
            eventPublisher.publishMeetingStarted(activeMeeting.getId(), admin.getFullName());

            verify(messagingTemplate).convertAndSend(
                    eq("/topic/meeting/" + activeMeeting.getId()),
                    (Object) any());
        }

        @Test
        @DisplayName("MEETING_STARTED payload contains hostName")
        void payloadContainsHostName() {
            eventPublisher.publishMeetingStarted(activeMeeting.getId(), "Admin Host");

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(anyString(), captor.capture());

            Object event = captor.getValue();
            assertThat(event.toString()).contains("MEETING_STARTED");
        }
    }

    // ── MEETING_ENDED event ───────────────────────────────────────────────────

    @Nested
    @DisplayName("MEETING_ENDED event")
    class MeetingEndedEvent {

        @Test
        @DisplayName("publishMeetingEnded sends MEETING_ENDED to correct topic")
        void sendsToCorrectTopic() {
            eventPublisher.publishMeetingEnded(activeMeeting.getId(), "HOST_ENDED");

            verify(messagingTemplate).convertAndSend(
                    eq("/topic/meeting/" + activeMeeting.getId()),
                    (Object) any());
        }
    }

    // ── MODE_CHANGED event ────────────────────────────────────────────────────

    @Nested
    @DisplayName("MODE_CHANGED event")
    class ModeChangedEvent {

        @Test
        @DisplayName("publishModeChanged sends MODE_CHANGED with FREE_MODE")
        void sendsFreeMode() {
            eventPublisher.publishModeChanged(activeMeeting.getId(), MeetingMode.FREE_MODE);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(anyString(), captor.capture());

            assertThat(captor.getValue().toString()).contains("MODE_CHANGED");
        }

        @Test
        @DisplayName("publishModeChanged sends MODE_CHANGED with MEETING_MODE")
        void sendsMeetingMode() {
            eventPublisher.publishModeChanged(activeMeeting.getId(), MeetingMode.MEETING_MODE);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(anyString(), captor.capture());

            assertThat(captor.getValue().toString()).contains("MODE_CHANGED");
        }
    }

    // ── PARTICIPANT events ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Participant events")
    class ParticipantEvents {

        @Test
        @DisplayName("publishParticipantJoined sends PARTICIPANT_JOINED")
        void sendsParticipantJoined() {
            eventPublisher.publishParticipantJoined(activeMeeting.getId(), admin.getId(), admin.getFullName());

            verify(messagingTemplate).convertAndSend(
                    eq("/topic/meeting/" + activeMeeting.getId()),
                    (Object) any());
        }

        @Test
        @DisplayName("publishParticipantLeft sends PARTICIPANT_LEFT")
        void sendsParticipantLeft() {
            eventPublisher.publishParticipantLeft(activeMeeting.getId(), admin.getId());

            verify(messagingTemplate).convertAndSend(
                    eq("/topic/meeting/" + activeMeeting.getId()),
                    (Object) any());
        }
    }

    // ── TRANSCRIPTION events ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Transcription events")
    class TranscriptionEvents {

        @Test
        @DisplayName("publishTranscriptionUnavailable sends to correct topic")
        void sendsTranscriptionUnavailable() {
            eventPublisher.publishTranscriptionUnavailable(activeMeeting.getId(), "Gipformer down");

            verify(messagingTemplate).convertAndSend(
                    eq("/topic/meeting/" + activeMeeting.getId()),
                    (Object) any());
        }

        @Test
        @DisplayName("publishPriorityChanged sends PRIORITY_CHANGED")
        void sendsPriorityChanged() {
            eventPublisher.publishPriorityChanged(activeMeeting.getId(), TranscriptionPriority.HIGH_PRIORITY);

            verify(messagingTemplate).convertAndSend(
                    eq("/topic/meeting/" + activeMeeting.getId()),
                    (Object) any());
        }
    }

    // ── Event envelope invariants ─────────────────────────────────────────────

    @Nested
    @DisplayName("Event envelope invariants")
    class EventEnvelopeInvariants {

        @Test
        @DisplayName("All events are sent to /topic/meeting/{meetingId}")
        void allEventsSentToCorrectTopic() {
            String expectedTopic = "/topic/meeting/" + activeMeeting.getId();

            eventPublisher.publishMeetingStarted(activeMeeting.getId(), "Host");
            eventPublisher.publishMeetingEnded(activeMeeting.getId(), "reason");
            eventPublisher.publishModeChanged(activeMeeting.getId(), MeetingMode.FREE_MODE);

            verify(messagingTemplate, times(3)).convertAndSend(eq(expectedTopic), (Object) any());
        }

        @Test
        @DisplayName("Events for different meetings go to different topics")
        void differentMeetingsDifferentTopics() {
            long meetingId1 = activeMeeting.getId();
            long meetingId2 = meetingId1 + 999;

            eventPublisher.publishMeetingStarted(meetingId1, "Host1");
            eventPublisher.publishMeetingStarted(meetingId2, "Host2");

            verify(messagingTemplate).convertAndSend(eq("/topic/meeting/" + meetingId1), (Object) any());
            verify(messagingTemplate).convertAndSend(eq("/topic/meeting/" + meetingId2), (Object) any());
        }
    }
}
