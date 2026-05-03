package com.example.kolla.services.impl;

import com.example.kolla.dto.AddMemberRequest;
import com.example.kolla.dto.CreateMeetingRequest;
import com.example.kolla.dto.UpdateMeetingRequest;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.exceptions.SchedulingConflictException;
import com.example.kolla.models.Department;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Member;
import com.example.kolla.models.Notification;
import com.example.kolla.models.Room;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DepartmentRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.MemberRepository;
import com.example.kolla.repositories.RoomRepository;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.responses.MeetingResponse;
import com.example.kolla.responses.MemberResponse;
import com.example.kolla.services.MeetingService;
import com.example.kolla.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MeetingService implementation.
 * Requirements: 3.1–3.12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingServiceImpl implements MeetingService {

    private static final List<MeetingStatus> CONFLICT_CHECK_STATUSES =
            List.of(MeetingStatus.SCHEDULED, MeetingStatus.ACTIVE);

    private final MeetingRepository meetingRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;
    private final NotificationService notificationService;

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MeetingResponse createMeeting(CreateMeetingRequest request, User creator) {
        validateTimeRange(request.getStartTime(), request.getEndTime());

        // Validate host: must be SECRETARY (Requirement 3.8)
        User host = findUserOrThrow(request.getHostUserId());
        if (host.getRole() != Role.SECRETARY) {
            throw new BadRequestException(
                    "Host must have SECRETARY role, but user '"
                    + host.getUsername() + "' has role " + host.getRole());
        }

        // Validate secretary: must be SECRETARY (Requirement 3.8)
        User secretary = findUserOrThrow(request.getSecretaryUserId());
        if (secretary.getRole() != Role.SECRETARY) {
            throw new BadRequestException(
                    "Secretary must have SECRETARY role, but user '"
                    + secretary.getUsername() + "' has role " + secretary.getRole());
        }

        // Resolve room and check scheduling conflicts (Requirement 3.12)
        Room room = null;
        if (request.getRoomId() != null) {
            room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Room not found with id: " + request.getRoomId()));
            checkSchedulingConflict(request.getRoomId(), request.getStartTime(),
                    request.getEndTime(), null);
        }

        // Generate unique meeting code (Requirement 3.1)
        String code = generateUniqueCode();

        Meeting meeting = Meeting.builder()
                .code(code)
                .title(request.getTitle())
                .description(request.getDescription())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .room(room)
                .creator(creator)
                .host(host)
                .secretary(secretary)
                .status(MeetingStatus.SCHEDULED)
                .build();

        Meeting saved = meetingRepository.save(meeting);
        log.info("Created meeting '{}' (code={}) by user '{}'",
                saved.getTitle(), saved.getCode(), creator.getUsername());

        // Auto-add creator, host, and secretary as members
        addMemberIfAbsent(saved, creator);
        if (!host.getId().equals(creator.getId())) {
            addMemberIfAbsent(saved, host);
        }
        if (!secretary.getId().equals(creator.getId())
                && !secretary.getId().equals(host.getId())) {
            addMemberIfAbsent(saved, secretary);
        }

        return MeetingResponse.from(saved);
    }

    // ── Get by ID ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public MeetingResponse getMeetingById(Long id, User requester) {
        Meeting meeting = findMeetingOrThrow(id);
        return toResponseWithDepartments(meeting);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<MeetingResponse> listMeetings(MeetingStatus status,
                                               Long roomId,
                                               Long creatorId,
                                               LocalDateTime startFrom,
                                               LocalDateTime startTo,
                                               Pageable pageable,
                                               User requester) {
        Page<Meeting> page = meetingRepository
                .findAllFiltered(status, roomId, creatorId, startFrom, startTo, pageable);

        // Batch-load departments for all host/secretary users in this page
        Set<Long> deptIds = page.getContent().stream()
                .flatMap(m -> {
                    java.util.stream.Stream.Builder<Long> ids = java.util.stream.Stream.builder();
                    if (m.getHost() != null && m.getHost().getDepartmentId() != null)
                        ids.add(m.getHost().getDepartmentId());
                    if (m.getSecretary() != null && m.getSecretary().getDepartmentId() != null)
                        ids.add(m.getSecretary().getDepartmentId());
                    return ids.build();
                })
                .collect(Collectors.toSet());
        Map<Long, String> deptNames = departmentRepository.findAllById(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));

        return page.map(m -> MeetingResponse.from(m,
                resolveDeptName(m.getHost(), deptNames),
                resolveDeptName(m.getSecretary(), deptNames)));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MeetingResponse updateMeeting(Long id, UpdateMeetingRequest request, User requester) {
        Meeting meeting = findMeetingOrThrow(id);

        // Only SECRETARY may update meetings (Requirement 3.5)
        if (requester.getRole() != Role.SECRETARY) {
            throw new ForbiddenException("Only SECRETARY may update meetings");
        }

        // Cannot update an ENDED meeting
        if (meeting.getStatus() == MeetingStatus.ENDED) {
            throw new BadRequestException("Cannot update an ended meeting");
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            meeting.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            meeting.setDescription(request.getDescription());
        }

        // Time range update — re-validate and re-check conflicts
        LocalDateTime newStart = request.getStartTime() != null
                ? request.getStartTime() : meeting.getStartTime();
        LocalDateTime newEnd = request.getEndTime() != null
                ? request.getEndTime() : meeting.getEndTime();

        if (request.getStartTime() != null || request.getEndTime() != null) {
            validateTimeRange(newStart, newEnd);
            meeting.setStartTime(newStart);
            meeting.setEndTime(newEnd);
        }

        // Room change — check conflicts (Requirement 3.12)
        if (request.getRoomId() != null) {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Room not found with id: " + request.getRoomId()));
            checkSchedulingConflict(request.getRoomId(), newStart, newEnd, id);
            meeting.setRoom(room);
        } else if (meeting.getRoom() != null
                && (request.getStartTime() != null || request.getEndTime() != null)) {
            // Time changed but room unchanged — re-check conflicts for existing room
            checkSchedulingConflict(meeting.getRoom().getId(), newStart, newEnd, id);
        }

        // Host update
        if (request.getHostUserId() != null) {
            User host = findUserOrThrow(request.getHostUserId());
            if (host.getRole() != Role.SECRETARY) {
                throw new BadRequestException(
                        "Host must have SECRETARY role");
            }
            meeting.setHost(host);
        }

        // Secretary update
        if (request.getSecretaryUserId() != null) {
            User secretary = findUserOrThrow(request.getSecretaryUserId());
            if (secretary.getRole() != Role.SECRETARY) {
                throw new BadRequestException("Secretary must have SECRETARY role");
            }
            meeting.setSecretary(secretary);
        }

        Meeting saved = meetingRepository.save(meeting);
        log.info("Updated meeting id={} by user '{}'", id, requester.getUsername());
        return MeetingResponse.from(saved);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteMeeting(Long id, User requester) {
        Meeting meeting = findMeetingOrThrow(id);

        // Only ADMIN may delete (Requirement 3.6)
        if (requester.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only ADMIN may delete meetings");
        }

        // Cannot delete an ACTIVE meeting
        if (meeting.getStatus() == MeetingStatus.ACTIVE) {
            throw new BadRequestException("Cannot delete an active meeting. End it first.");
        }

        meetingRepository.delete(meeting);
        log.info("Deleted meeting id={} '{}' by admin '{}'",
                id, meeting.getTitle(), requester.getUsername());
    }

    // ── Members ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long meetingId, User requester) {
        findMeetingOrThrow(meetingId); // ensure meeting exists
        List<Member> memberList = memberRepository.findByMeetingId(meetingId);

        // Batch-load department names
        Set<Long> deptIds = memberList.stream()
                .map(m -> m.getUser().getDepartmentId())
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : departmentRepository.findAllById(deptIds).stream()
                        .collect(Collectors.toMap(Department::getId, Department::getName));

        return memberList.stream()
                .map(m -> MemberResponse.from(m,
                        m.getUser().getDepartmentId() != null
                                ? deptNames.get(m.getUser().getDepartmentId())
                                : null))
                .toList();
    }

    @Override
    @Transactional
    public MemberResponse addMember(Long meetingId, AddMemberRequest request, User requester) {
        Meeting meeting = findMeetingOrThrow(meetingId);

        // Only SECRETARY may add members (Requirement 3.9)
        if (requester.getRole() != Role.SECRETARY) {
            throw new ForbiddenException("Only SECRETARY may add members to a meeting");
        }

        // Cannot add members to an ENDED meeting
        if (meeting.getStatus() == MeetingStatus.ENDED) {
            throw new BadRequestException("Cannot add members to an ended meeting");
        }

        User user = findUserOrThrow(request.getUserId());

        if (memberRepository.existsByMeetingIdAndUserId(meetingId, request.getUserId())) {
            throw new BadRequestException("User '" + user.getUsername()
                    + "' is already a member of this meeting");
        }

        Member member = Member.builder()
                .meeting(meeting)
                .user(user)
                .build();

        Member saved = memberRepository.save(member);
        log.info("Added user '{}' to meeting id={}", user.getUsername(), meetingId);

        // Notify the added user (Requirement 10.3)
        notificationService.createNotification(
                user, requester,
                "MEMBER_ADDED",
                "You have been added to a meeting",
                "You have been added to meeting: " + meeting.getTitle(),
                meetingId);

        return MemberResponse.from(saved);
    }

    @Override
    @Transactional
    public void removeMember(Long meetingId, Long userId, User requester) {
        Meeting meeting = findMeetingOrThrow(meetingId);

        // Only SECRETARY may remove members (Requirement 3.9)
        if (requester.getRole() != Role.SECRETARY) {
            throw new ForbiddenException("Only SECRETARY may remove members from a meeting");
        }

        // Cannot remove members from an ENDED meeting
        if (meeting.getStatus() == MeetingStatus.ENDED) {
            throw new BadRequestException("Cannot remove members from an ended meeting");
        }

        if (!memberRepository.existsByMeetingIdAndUserId(meetingId, userId)) {
            throw new ResourceNotFoundException(
                    "User with id " + userId + " is not a member of meeting " + meetingId);
        }

        // Prevent removing the host or secretary
        if (meeting.getHost() != null && meeting.getHost().getId().equals(userId)) {
            throw new BadRequestException("Cannot remove the Host from the meeting");
        }
        if (meeting.getSecretary() != null && meeting.getSecretary().getId().equals(userId)) {
            throw new BadRequestException("Cannot remove the Secretary from the meeting");
        }

        memberRepository.deleteByMeetingIdAndUserId(meetingId, userId);
        log.info("Removed user id={} from meeting id={} by '{}'",
                userId, meetingId, requester.getUsername());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMember(Long meetingId, Long userId) {
        return memberRepository.isMember(meetingId, userId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Checks for scheduling conflicts in the given room for the given time range.
     * Excludes the meeting with excludeId (used during updates).
     * Throws SchedulingConflictException (409) if a conflict is found.
     * Requirements: 3.12
     */
    private void checkSchedulingConflict(Long roomId, LocalDateTime startTime,
                                          LocalDateTime endTime, Long excludeId) {
        List<Meeting> conflicts = meetingRepository.findConflictingMeetings(
                roomId, startTime, endTime, CONFLICT_CHECK_STATUSES, excludeId);

        if (!conflicts.isEmpty()) {
            Meeting conflicting = conflicts.get(0);
            throw new SchedulingConflictException(
                    "Room is already booked from " + conflicting.getStartTime()
                    + " to " + conflicting.getEndTime()
                    + " by meeting '" + conflicting.getTitle() + "'",
                    conflicting.getId());
        }
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new BadRequestException("End time must be after start time");
        }
    }

    private Meeting findMeetingOrThrow(Long id) {
        return meetingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found with id: " + id));
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    /**
     * Generates a unique 20-character alphanumeric meeting code.
     *
     * 20 chars is long enough to avoid meet.jit.si's "members-only" policy
     * that is triggered for short/guessable room names on the public instance.
     * Requirements: 3.1
     */
    private String generateUniqueCode() {
        String code;
        do {
            // Two UUID segments joined = 32 hex chars; take first 20
            String part1 = UUID.randomUUID().toString().replace("-", "");
            String part2 = UUID.randomUUID().toString().replace("-", "");
            code = (part1 + part2).substring(0, 20).toUpperCase();
        } while (meetingRepository.existsByCode(code));
        return code;
    }

    private void addMemberIfAbsent(Meeting meeting, User user) {
        if (!memberRepository.existsByMeetingIdAndUserId(meeting.getId(), user.getId())) {
            memberRepository.save(Member.builder()
                    .meeting(meeting)
                    .user(user)
                    .build());
        }
    }

    /**
     * Build a MeetingResponse with resolved department names for host and secretary.
     */
    private MeetingResponse toResponseWithDepartments(Meeting meeting) {
        Set<Long> deptIds = new java.util.HashSet<>();
        if (meeting.getHost() != null && meeting.getHost().getDepartmentId() != null)
            deptIds.add(meeting.getHost().getDepartmentId());
        if (meeting.getSecretary() != null && meeting.getSecretary().getDepartmentId() != null)
            deptIds.add(meeting.getSecretary().getDepartmentId());

        Map<Long, String> deptNames = deptIds.isEmpty()
                ? Map.of()
                : departmentRepository.findAllById(deptIds).stream()
                        .collect(Collectors.toMap(Department::getId, Department::getName));

        return MeetingResponse.from(meeting,
                resolveDeptName(meeting.getHost(), deptNames),
                resolveDeptName(meeting.getSecretary(), deptNames));
    }

    private String resolveDeptName(User user, Map<Long, String> deptNames) {
        if (user == null || user.getDepartmentId() == null) return null;
        return deptNames.get(user.getDepartmentId());
    }
}
