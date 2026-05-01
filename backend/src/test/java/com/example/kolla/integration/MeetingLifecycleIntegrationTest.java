package com.example.kolla.integration;

import com.example.kolla.dto.CreateMeetingRequest;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.models.Department;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Room;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DepartmentRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.RoomRepository;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.responses.MeetingResponse;
import com.example.kolla.services.MeetingLifecycleService;
import com.example.kolla.services.MeetingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Meeting Lifecycle API.
 *
 * Covers:
 * - SCHEDULED → ACTIVE transition (activate)
 * - ACTIVE → ENDED transition (end)
 * - Lifecycle state invariants
 *
 * Uses H2 in-memory database via @ActiveProfiles("test").
 * Requirements: 3.10, 3.11, 20.2
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MeetingLifecycleIntegrationTest {

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private MeetingLifecycleService meetingLifecycleService;

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
    private Room room;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.builder()
                .username("lifecycle_admin_" + System.nanoTime())
                .email("lifecycle_admin_" + System.nanoTime() + "@test.com")
                .passwordHash("$2a$12$hashed")
                .fullName("Lifecycle Admin")
                .role(Role.ADMIN)
                .isActive(true)
                .build());

        secretary = userRepository.save(User.builder()
                .username("lifecycle_sec_" + System.nanoTime())
                .email("lifecycle_sec_" + System.nanoTime() + "@test.com")
                .passwordHash("$2a$12$hashed")
                .fullName("Lifecycle Secretary")
                .role(Role.SECRETARY)
                .isActive(true)
                .build());

        Department dept = departmentRepository.save(Department.builder()
                .name("Lifecycle Dept " + System.nanoTime())
                .description("Test")
                .build());

        room = roomRepository.save(Room.builder()
                .name("Lifecycle Room " + System.nanoTime())
                .capacity(10)
                .department(dept)
                .build());
    }

    // ── Create meeting ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Create meeting")
    class CreateMeeting {

        @Test
        @DisplayName("New meeting starts in SCHEDULED status")
        void newMeetingIsScheduled() {
            MeetingResponse response = createMeeting("Lifecycle Test Meeting");

            assertThat(response.getStatus()).isEqualTo(MeetingStatus.SCHEDULED);
        }

        @Test
        @DisplayName("New meeting has a unique meeting code")
        void newMeetingHasUniqueCode() {
            MeetingResponse r1 = createMeeting("Meeting Alpha");
            MeetingResponse r2 = createMeeting("Meeting Beta");

            assertThat(r1.getCode()).isNotBlank();
            assertThat(r2.getCode()).isNotBlank();
            assertThat(r1.getCode()).isNotEqualTo(r2.getCode());
        }

        @Test
        @DisplayName("New meeting stores host and secretary correctly")
        void newMeetingStoresHostAndSecretary() {
            MeetingResponse response = createMeeting("Host Secretary Test");

            assertThat(response.getHostId()).isEqualTo(admin.getId());
            assertThat(response.getSecretaryId()).isEqualTo(secretary.getId());
        }
    }

    // ── Activate meeting ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Activate meeting (SCHEDULED → ACTIVE)")
    class ActivateMeeting {

        @Test
        @DisplayName("Activating a SCHEDULED meeting transitions it to ACTIVE")
        void activatesScheduledMeeting() {
            MeetingResponse created = createMeeting("Activate Test");
            Meeting meeting = meetingRepository.findById(created.getId()).orElseThrow();

            meetingLifecycleService.activateMeeting(meeting.getId(), admin);

            Meeting activated = meetingRepository.findById(meeting.getId()).orElseThrow();
            assertThat(activated.getStatus()).isEqualTo(MeetingStatus.ACTIVE);
            assertThat(activated.getActivatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Activating an already ACTIVE meeting throws BadRequestException")
        void activatingActiveMeetingThrows() {
            MeetingResponse created = createMeeting("Already Active Test");
            Meeting meeting = meetingRepository.findById(created.getId()).orElseThrow();

            // First activation
            meetingLifecycleService.activateMeeting(meeting.getId(), admin);

            // Second activation should fail
            assertThatThrownBy(() -> meetingLifecycleService.activateMeeting(meeting.getId(), admin))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("Activating an ENDED meeting throws BadRequestException")
        void activatingEndedMeetingThrows() {
            MeetingResponse created = createMeeting("Ended Meeting Test");
            Meeting meeting = meetingRepository.findById(created.getId()).orElseThrow();

            // Activate then end
            meetingLifecycleService.activateMeeting(meeting.getId(), admin);
            meetingLifecycleService.endMeeting(meeting.getId(), admin);

            // Re-activation should fail
            assertThatThrownBy(() -> meetingLifecycleService.activateMeeting(meeting.getId(), admin))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ── End meeting ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("End meeting (ACTIVE → ENDED)")
    class EndMeeting {

        @Test
        @DisplayName("Ending an ACTIVE meeting transitions it to ENDED")
        void endsActiveMeeting() {
            MeetingResponse created = createMeeting("End Test");
            Meeting meeting = meetingRepository.findById(created.getId()).orElseThrow();

            meetingLifecycleService.activateMeeting(meeting.getId(), admin);
            meetingLifecycleService.endMeeting(meeting.getId(), admin);

            Meeting ended = meetingRepository.findById(meeting.getId()).orElseThrow();
            assertThat(ended.getStatus()).isEqualTo(MeetingStatus.ENDED);
            assertThat(ended.getEndedAt()).isNotNull();
        }

        @Test
        @DisplayName("Ending a SCHEDULED meeting throws BadRequestException")
        void endingScheduledMeetingThrows() {
            MeetingResponse created = createMeeting("End Scheduled Test");
            Meeting meeting = meetingRepository.findById(created.getId()).orElseThrow();

            assertThatThrownBy(() -> meetingLifecycleService.endMeeting(meeting.getId(), admin))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("Ending an already ENDED meeting throws BadRequestException")
        void endingEndedMeetingThrows() {
            MeetingResponse created = createMeeting("Double End Test");
            Meeting meeting = meetingRepository.findById(created.getId()).orElseThrow();

            meetingLifecycleService.activateMeeting(meeting.getId(), admin);
            meetingLifecycleService.endMeeting(meeting.getId(), admin);

            assertThatThrownBy(() -> meetingLifecycleService.endMeeting(meeting.getId(), admin))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ── Full lifecycle ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Full lifecycle: SCHEDULED → ACTIVE → ENDED")
    class FullLifecycle {

        @Test
        @DisplayName("Meeting passes through all three states in order")
        void fullLifecycleTransitions() {
            MeetingResponse created = createMeeting("Full Lifecycle Test");
            Meeting meeting = meetingRepository.findById(created.getId()).orElseThrow();

            // SCHEDULED
            assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.SCHEDULED);

            // → ACTIVE
            meetingLifecycleService.activateMeeting(meeting.getId(), admin);
            meeting = meetingRepository.findById(meeting.getId()).orElseThrow();
            assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.ACTIVE);
            assertThat(meeting.getActivatedAt()).isNotNull();

            // → ENDED
            meetingLifecycleService.endMeeting(meeting.getId(), admin);
            meeting = meetingRepository.findById(meeting.getId()).orElseThrow();
            assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.ENDED);
            assertThat(meeting.getEndedAt()).isNotNull();
        }

        @Test
        @DisplayName("endedAt is after activatedAt")
        void endedAtIsAfterActivatedAt() throws InterruptedException {
            MeetingResponse created = createMeeting("Timestamp Order Test");
            Meeting meeting = meetingRepository.findById(created.getId()).orElseThrow();

            meetingLifecycleService.activateMeeting(meeting.getId(), admin);
            Thread.sleep(10); // ensure time difference
            meetingLifecycleService.endMeeting(meeting.getId(), admin);

            meeting = meetingRepository.findById(meeting.getId()).orElseThrow();
            assertThat(meeting.getEndedAt()).isAfterOrEqualTo(meeting.getActivatedAt());
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private MeetingResponse createMeeting(String title) {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        return meetingService.createMeeting(
                CreateMeetingRequest.builder()
                        .title(title)
                        .description("Integration test meeting")
                        .startTime(start)
                        .endTime(start.plusHours(1))
                        .roomId(room.getId())
                        .hostUserId(admin.getId())
                        .secretaryUserId(secretary.getId())
                        .build(),
                admin);
    }
}
