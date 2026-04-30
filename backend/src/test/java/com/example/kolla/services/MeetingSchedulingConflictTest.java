package com.example.kolla.services;

import com.example.kolla.dto.CreateMeetingRequest;
import com.example.kolla.dto.UpdateMeetingRequest;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.SchedulingConflictException;
import com.example.kolla.models.Department;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Room;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DepartmentRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.RoomRepository;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.responses.MeetingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for room scheduling conflict detection.
 * Requirements: 3.12
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MeetingSchedulingConflictTest {

    @Autowired
    private MeetingService meetingService;

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
    private LocalDateTime baseTime;

    @BeforeEach
    void setUp() {
        // Create test users
        admin = userRepository.save(User.builder()
                .username("admin_conflict_test")
                .email("admin_conflict@test.com")
                .passwordHash("password")
                .fullName("Admin User")
                .role(Role.ADMIN)
                .isActive(true)
                .build());

        secretary = userRepository.save(User.builder()
                .username("secretary_conflict_test")
                .email("secretary_conflict@test.com")
                .passwordHash("password")
                .fullName("Secretary User")
                .role(Role.SECRETARY)
                .isActive(true)
                .build());

        // Create test department and room
        Department department = departmentRepository.save(Department.builder()
                .name("Test Department")
                .description("Test")
                .build());

        room = roomRepository.save(Room.builder()
                .name("Conference Room A")
                .capacity(10)
                .department(department)
                .build());

        baseTime = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    @Test
    @DisplayName("Should detect conflict when creating meeting with exact same time slot")
    void testCreateMeeting_ExactTimeConflict() {
        // Given: existing meeting from 10:00 to 11:00
        createTestMeeting(baseTime, baseTime.plusHours(1));

        // When/Then: attempt to create another meeting at exact same time
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .title("Conflicting Meeting")
                .description("Should fail")
                .startTime(baseTime)
                .endTime(baseTime.plusHours(1))
                .roomId(room.getId())
                .hostUserId(admin.getId())
                .secretaryUserId(secretary.getId())
                .build();

        assertThatThrownBy(() -> meetingService.createMeeting(request, admin))
                .isInstanceOf(SchedulingConflictException.class)
                .hasMessageContaining("Room is already booked");
    }

    @Test
    @DisplayName("Should detect conflict when new meeting starts during existing meeting")
    void testCreateMeeting_StartsWithinExistingMeeting() {
        // Given: existing meeting from 10:00 to 12:00
        createTestMeeting(baseTime, baseTime.plusHours(2));

        // When/Then: attempt to create meeting from 11:00 to 13:00 (overlaps)
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .title("Overlapping Meeting")
                .description("Starts during existing meeting")
                .startTime(baseTime.plusHours(1))
                .endTime(baseTime.plusHours(3))
                .roomId(room.getId())
                .hostUserId(admin.getId())
                .secretaryUserId(secretary.getId())
                .build();

        assertThatThrownBy(() -> meetingService.createMeeting(request, admin))
                .isInstanceOf(SchedulingConflictException.class);
    }

    @Test
    @DisplayName("Should detect conflict when new meeting ends during existing meeting")
    void testCreateMeeting_EndsWithinExistingMeeting() {
        // Given: existing meeting from 10:00 to 12:00
        createTestMeeting(baseTime, baseTime.plusHours(2));

        // When/Then: attempt to create meeting from 09:00 to 11:00 (overlaps)
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .title("Overlapping Meeting")
                .description("Ends during existing meeting")
                .startTime(baseTime.minusHours(1))
                .endTime(baseTime.plusHours(1))
                .roomId(room.getId())
                .hostUserId(admin.getId())
                .secretaryUserId(secretary.getId())
                .build();

        assertThatThrownBy(() -> meetingService.createMeeting(request, admin))
                .isInstanceOf(SchedulingConflictException.class);
    }

    @Test
    @DisplayName("Should detect conflict when new meeting completely contains existing meeting")
    void testCreateMeeting_ContainsExistingMeeting() {
        // Given: existing meeting from 10:00 to 11:00
        createTestMeeting(baseTime, baseTime.plusHours(1));

        // When/Then: attempt to create meeting from 09:00 to 12:00 (contains existing)
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .title("Containing Meeting")
                .description("Contains existing meeting")
                .startTime(baseTime.minusHours(1))
                .endTime(baseTime.plusHours(2))
                .roomId(room.getId())
                .hostUserId(admin.getId())
                .secretaryUserId(secretary.getId())
                .build();

        assertThatThrownBy(() -> meetingService.createMeeting(request, admin))
                .isInstanceOf(SchedulingConflictException.class);
    }

    @Test
    @DisplayName("Should allow creating meeting when no time overlap exists")
    void testCreateMeeting_NoConflict_BeforeExisting() {
        // Given: existing meeting from 10:00 to 11:00
        createTestMeeting(baseTime, baseTime.plusHours(1));

        // When: create meeting from 08:00 to 09:00 (before existing)
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .title("Non-conflicting Meeting")
                .description("Before existing meeting")
                .startTime(baseTime.minusHours(2))
                .endTime(baseTime.minusHours(1))
                .roomId(room.getId())
                .hostUserId(admin.getId())
                .secretaryUserId(secretary.getId())
                .build();

        // Then: should succeed
        MeetingResponse response = meetingService.createMeeting(request, admin);
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Non-conflicting Meeting");
    }

    @Test
    @DisplayName("Should allow creating meeting when no time overlap exists (after)")
    void testCreateMeeting_NoConflict_AfterExisting() {
        // Given: existing meeting from 10:00 to 11:00
        createTestMeeting(baseTime, baseTime.plusHours(1));

        // When: create meeting from 12:00 to 13:00 (after existing)
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .title("Non-conflicting Meeting")
                .description("After existing meeting")
                .startTime(baseTime.plusHours(2))
                .endTime(baseTime.plusHours(3))
                .roomId(room.getId())
                .hostUserId(admin.getId())
                .secretaryUserId(secretary.getId())
                .build();

        // Then: should succeed
        MeetingResponse response = meetingService.createMeeting(request, admin);
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Non-conflicting Meeting");
    }

    @Test
    @DisplayName("Should allow creating meeting in different room at same time")
    void testCreateMeeting_NoConflict_DifferentRoom() {
        // Given: existing meeting in room A from 10:00 to 11:00
        createTestMeeting(baseTime, baseTime.plusHours(1));

        // Create another room
        Room room2 = roomRepository.save(Room.builder()
                .name("Conference Room B")
                .capacity(10)
                .department(room.getDepartment())
                .build());

        // When: create meeting in room B at same time
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .title("Meeting in Different Room")
                .description("Same time, different room")
                .startTime(baseTime)
                .endTime(baseTime.plusHours(1))
                .roomId(room2.getId())
                .hostUserId(admin.getId())
                .secretaryUserId(secretary.getId())
                .build();

        // Then: should succeed
        MeetingResponse response = meetingService.createMeeting(request, admin);
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Meeting in Different Room");
    }

    @Test
    @DisplayName("Should only check conflicts for SCHEDULED and ACTIVE meetings")
    void testCreateMeeting_NoConflict_EndedMeeting() {
        // Given: existing ENDED meeting from 10:00 to 11:00
        Meeting endedMeeting = createTestMeeting(baseTime, baseTime.plusHours(1));
        endedMeeting.setStatus(MeetingStatus.ENDED);
        meetingRepository.save(endedMeeting);

        // When: create meeting at same time (should not conflict with ENDED meeting)
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .title("New Meeting")
                .description("Same time as ended meeting")
                .startTime(baseTime)
                .endTime(baseTime.plusHours(1))
                .roomId(room.getId())
                .hostUserId(admin.getId())
                .secretaryUserId(secretary.getId())
                .build();

        // Then: should succeed
        MeetingResponse response = meetingService.createMeeting(request, admin);
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("New Meeting");
    }

    @Test
    @DisplayName("Should detect conflict when updating meeting time to overlap with another")
    void testUpdateMeeting_TimeChangeCreatesConflict() {
        // Given: two non-overlapping meetings
        Meeting meeting1 = createTestMeeting(baseTime, baseTime.plusHours(1));
        Meeting meeting2 = createTestMeeting(baseTime.plusHours(2), baseTime.plusHours(3));

        // When/Then: attempt to update meeting2 to overlap with meeting1
        UpdateMeetingRequest request = UpdateMeetingRequest.builder()
                .startTime(baseTime.plusMinutes(30))
                .endTime(baseTime.plusHours(2).plusMinutes(30))
                .build();

        assertThatThrownBy(() -> meetingService.updateMeeting(meeting2.getId(), request, admin))
                .isInstanceOf(SchedulingConflictException.class);
    }

    @Test
    @DisplayName("Should detect conflict when updating meeting room to one with existing booking")
    void testUpdateMeeting_RoomChangeCreatesConflict() {
        // Given: meeting in room A and meeting in room B at same time
        Room room2 = roomRepository.save(Room.builder()
                .name("Conference Room B")
                .capacity(10)
                .department(room.getDepartment())
                .build());

        Meeting meetingInRoomA = createTestMeeting(baseTime, baseTime.plusHours(1));
        Meeting meetingInRoomB = createTestMeetingInRoom(room2, baseTime, baseTime.plusHours(1));

        // When/Then: attempt to move meetingInRoomB to room A (conflict)
        UpdateMeetingRequest request = UpdateMeetingRequest.builder()
                .roomId(room.getId())
                .build();

        assertThatThrownBy(() -> meetingService.updateMeeting(meetingInRoomB.getId(), request, admin))
                .isInstanceOf(SchedulingConflictException.class);
    }

    @Test
    @DisplayName("Should allow updating meeting without creating conflict with itself")
    void testUpdateMeeting_NoConflictWithSelf() {
        // Given: existing meeting from 10:00 to 11:00
        Meeting meeting = createTestMeeting(baseTime, baseTime.plusHours(1));

        // When: update the same meeting's title (time unchanged)
        UpdateMeetingRequest request = UpdateMeetingRequest.builder()
                .title("Updated Title")
                .build();

        // Then: should succeed
        MeetingResponse response = meetingService.updateMeeting(meeting.getId(), request, admin);
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Updated Title");
    }

    @Test
    @DisplayName("Should allow updating meeting time when no other meetings conflict")
    void testUpdateMeeting_TimeChangeNoConflict() {
        // Given: existing meeting from 10:00 to 11:00
        Meeting meeting = createTestMeeting(baseTime, baseTime.plusHours(1));

        // When: update to 14:00 to 15:00 (no conflict)
        UpdateMeetingRequest request = UpdateMeetingRequest.builder()
                .startTime(baseTime.plusHours(4))
                .endTime(baseTime.plusHours(5))
                .build();

        // Then: should succeed
        MeetingResponse response = meetingService.updateMeeting(meeting.getId(), request, admin);
        assertThat(response).isNotNull();
        assertThat(response.getStartTime()).isEqualTo(baseTime.plusHours(4));
        assertThat(response.getEndTime()).isEqualTo(baseTime.plusHours(5));
    }

    @Test
    @DisplayName("Should include conflicting meeting details in exception")
    void testConflictException_ContainsMeetingDetails() {
        // Given: existing meeting
        Meeting existing = createTestMeeting(baseTime, baseTime.plusHours(1));

        // When/Then: attempt to create conflicting meeting
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .title("Conflicting Meeting")
                .description("Should fail")
                .startTime(baseTime)
                .endTime(baseTime.plusHours(1))
                .roomId(room.getId())
                .hostUserId(admin.getId())
                .secretaryUserId(secretary.getId())
                .build();

        assertThatThrownBy(() -> meetingService.createMeeting(request, admin))
                .isInstanceOf(SchedulingConflictException.class)
                .hasMessageContaining(existing.getTitle())
                .satisfies(ex -> {
                    SchedulingConflictException sce = (SchedulingConflictException) ex;
                    assertThat(sce.getConflictingMeetingId()).isEqualTo(existing.getId());
                });
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    private Meeting createTestMeeting(LocalDateTime start, LocalDateTime end) {
        return createTestMeetingInRoom(room, start, end);
    }

    private Meeting createTestMeetingInRoom(Room targetRoom, LocalDateTime start, LocalDateTime end) {
        Meeting meeting = Meeting.builder()
                .code("TEST" + System.currentTimeMillis())
                .title("Test Meeting")
                .description("Test")
                .startTime(start)
                .endTime(end)
                .room(targetRoom)
                .creator(admin)
                .host(admin)
                .secretary(secretary)
                .status(MeetingStatus.SCHEDULED)
                .build();
        return meetingRepository.save(meeting);
    }
}
