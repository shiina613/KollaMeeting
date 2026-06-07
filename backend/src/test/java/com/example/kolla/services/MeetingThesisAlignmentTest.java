package com.example.kolla.services;

import com.example.kolla.dto.AddMemberRequest;
import com.example.kolla.dto.CreateMeetingRequest;
import com.example.kolla.enums.MeetingRole;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Member;
import com.example.kolla.models.Room;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DepartmentRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.MemberRepository;
import com.example.kolla.repositories.RoomRepository;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.responses.MemberResponse;
import com.example.kolla.services.impl.MeetingServiceImpl;
import com.example.kolla.dto.UpdateMeetingRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

@ExtendWith(MockitoExtension.class)
class MeetingThesisAlignmentTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private NotificationService notificationService;
    @Mock private EntityManager entityManager;

    private MeetingServiceImpl service;

    private User secretary;
    private User employeeHost;
    private User reviewer;

    @BeforeEach
    void setUp() {
        service = new MeetingServiceImpl(
                meetingRepository,
                memberRepository,
                userRepository,
                roomRepository,
                departmentRepository,
                notificationService,
                entityManager);

        secretary = user(2L, "SEC001", Role.SECRETARY);
        employeeHost = user(3L, "EMP001", Role.USER);
        reviewer = user(4L, "EMP002", Role.USER);
    }

    @Test
    void createMeeting_allowsRegularEmployeeAsHostAndStoresMeetingRoles() {
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .title("Họp nghiệm thu")
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .roomId(9L)
                .departmentId(8L)
                .hostUserId(employeeHost.getId())
                .secretaryUserId(secretary.getId())
                .build();

        when(userRepository.findById(employeeHost.getId())).thenReturn(Optional.of(employeeHost));
        when(userRepository.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(entityManager.find(Room.class, 9L, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(Room.builder().id(9L).build());
        when(departmentRepository.existsById(8L)).thenReturn(true);
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> {
            Meeting meeting = invocation.getArgument(0);
            meeting.setId(100L);
            return meeting;
        });
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createMeeting(request, secretary);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository, org.mockito.Mockito.times(2)).save(memberCaptor.capture());
        List<Member> savedMembers = memberCaptor.getAllValues();

        assertThat(savedMembers)
                .extracting(member -> member.getUser().getId(), Member::getMeetingRole)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(secretary.getId(), MeetingRole.SECRETARY),
                        org.assertj.core.groups.Tuple.tuple(employeeHost.getId(), MeetingRole.HOST));
    }

    @Test
    void createMeeting_requiresRoomId() {
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .title("Hop nghiem thu")
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .departmentId(8L)
                .hostUserId(employeeHost.getId())
                .secretaryUserId(secretary.getId())
                .build();

        assertThatThrownBy(() -> service.createMeeting(request, secretary))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Room ID is required");
    }

    @Test
    void createMeeting_requiresDepartmentId() {
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .title("Hop nghiem thu")
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .roomId(9L)
                .hostUserId(employeeHost.getId())
                .secretaryUserId(secretary.getId())
                .build();

        assertThatThrownBy(() -> service.createMeeting(request, secretary))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Department ID is required");
    }

    @Test
    void createMeeting_generatesMeetingCodeFromPersistedId() {
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .title("Hop nghiem thu")
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .roomId(9L)
                .departmentId(8L)
                .hostUserId(employeeHost.getId())
                .secretaryUserId(secretary.getId())
                .build();

        when(userRepository.findById(employeeHost.getId())).thenReturn(Optional.of(employeeHost));
        when(userRepository.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(entityManager.find(Room.class, 9L, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(Room.builder().id(9L).build());
        when(departmentRepository.existsById(8L)).thenReturn(true);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> {
            Meeting meeting = invocation.getArgument(0);
            if (meeting.getId() == null) {
                meeting.setId(42L);
            }
            return meeting;
        });

        service.createMeeting(request, secretary);

        ArgumentCaptor<Meeting> meetingCaptor = ArgumentCaptor.forClass(Meeting.class);
        verify(meetingRepository, atLeastOnce()).save(meetingCaptor.capture());
        assertThat(meetingCaptor.getAllValues().get(meetingCaptor.getAllValues().size() - 1).getCode())
                .isEqualTo("MTG-000042");
    }

    @Test
    void addMember_persistsRequestedMeetingRoleAndReturnsIt() {
        Meeting meeting = Meeting.builder()
                .id(100L)
                .title("Họp nghiệm thu")
                .status(MeetingStatus.SCHEDULED)
                .secretary(secretary)
                .build();
        AddMemberRequest request = AddMemberRequest.builder()
                .userId(reviewer.getId())
                .meetingRole(MeetingRole.REVIEWER)
                .build();

        when(meetingRepository.findById(100L)).thenReturn(Optional.of(meeting));
        when(userRepository.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
        when(memberRepository.existsByMeetingIdAndUserId(100L, reviewer.getId())).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            member.setId(99L);
            return member;
        });

        MemberResponse response = service.addMember(100L, request, secretary);

        assertThat(response.getMeetingRole()).isEqualTo(MeetingRole.REVIEWER);
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getMeetingRole()).isEqualTo(MeetingRole.REVIEWER);
    }

    @Test
    void deleteMeeting_allowsAssignedSecretaryToDeleteScheduledMeeting() {
        Meeting meeting = Meeting.builder()
                .id(100L)
                .title("Họp nghiệm thu")
                .status(MeetingStatus.SCHEDULED)
                .secretary(secretary)
                .build();

        when(meetingRepository.findById(100L)).thenReturn(Optional.of(meeting));

        assertThatCode(() -> service.deleteMeeting(100L, secretary))
                .doesNotThrowAnyException();
        verify(meetingRepository).delete(meeting);
    }

    @Test
    void deleteMeeting_blocksUnassignedSecretary() {
        Meeting meeting = Meeting.builder()
                .id(100L)
                .title("Họp nghiệm thu")
                .status(MeetingStatus.SCHEDULED)
                .secretary(secretary)
                .build();
        User otherSecretary = user(5L, "SEC002", Role.SECRETARY);

        when(meetingRepository.findById(100L)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> service.deleteMeeting(100L, otherSecretary))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateMeeting_blocksActiveMeetingBecauseSecretaryOnlyEditsScheduledMeetings() {
        Meeting meeting = Meeting.builder()
                .id(100L)
                .title("Hop dang dien ra")
                .status(MeetingStatus.ACTIVE)
                .secretary(secretary)
                .startTime(LocalDateTime.now().minusMinutes(30))
                .endTime(LocalDateTime.now().plusMinutes(30))
                .build();
        UpdateMeetingRequest request = UpdateMeetingRequest.builder()
                .title("Khong duoc sua")
                .build();

        when(meetingRepository.findById(100L)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> service.updateMeeting(100L, request, secretary))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only scheduled meetings can be updated");
    }

    private User user(Long id, String employeeCode, Role role) {
        return User.builder()
                .id(id)
                .username(employeeCode)
                .employeeCode(employeeCode)
                .passwordHash("$2a$12$hashed")
                .fullName(employeeCode + " Full Name")
                .email(employeeCode.toLowerCase() + "@example.com")
                .role(role)
                .isActive(true)
                .build();
    }
}
