package com.example.kolla.services.impl;

import com.example.kolla.dto.AddMemberRequest;
import com.example.kolla.dto.CreateMeetingRequest;
import com.example.kolla.dto.UpdateMeetingRequest;
import com.example.kolla.enums.MeetingRole;
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
    private final EntityManager entityManager;

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MeetingResponse createMeeting(CreateMeetingRequest request, User creator) {
        validateTimeRange(request.getStartTime(), request.getEndTime());

        String meetingTitle = resolveMeetingTitle(request.getTitle(), request.getName());

        // Validate host/chairperson: any active user may chair a meeting.
        User host = findUserOrThrow(request.getHostUserId());
        validateActiveUser(host, "Host");

        // Validate secretary: must be SECRETARY (Requirement 3.8)
        User secretary = findUserOrThrow(request.getSecretaryUserId());
        if (secretary.getRole() != Role.SECRETARY) {
            throw new BadRequestException(
                    "Secretary must have SECRETARY role, but user '"
                    + secretary.getUsername() + "' has role " + secretary.getRole());
        }
        validateActiveUser(secretary, "Secretary");

        // Resolve room and check scheduling conflicts (Requirement 3.12)
        Room room = null;
        if (request.getRoomId() != null) {
            // Acquire pessimistic lock on the room to prevent race conditions
            // between conflict check and meeting insert
            room = entityManager.find(Room.class, request.getRoomId(), LockModeType.PESSIMISTIC_WRITE);
            if (room == null) {
                throw new ResourceNotFoundException(
                        "Room not found with id: " + request.getRoomId());
            }
            checkSchedulingConflict(request.getRoomId(), request.getStartTime(),
                    request.getEndTime(), null);
        }

        Long departmentId = request.getDepartmentId();
        if (departmentId == null && room != null && room.getDepartment() != null) {
            departmentId = room.getDepartment().getId();
        }
        if (departmentId != null && !departmentRepository.existsById(departmentId)) {
            throw new ResourceNotFoundException("Department not found with id: " + departmentId);
        }

        // Generate unique meeting code (Requirement 3.1)
        String code = generateUniqueCode();

        Meeting meeting = Meeting.builder()
                .code(code)
                .title(meetingTitle)
                .description(request.getDescription())
                .departmentId(departmentId)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .room(room)
                .creator(creator)
                .host(host)
                .secretary(secretary)
                .status(MeetingStatus.SCHEDULED)
                .transcriptionPriority(
                        request.getTranscriptionPriority() != null
                                ? request.getTranscriptionPriority()
                                : com.example.kolla.enums.TranscriptionPriority.NORMAL_PRIORITY)
                .build();

        Meeting saved = meetingRepository.save(meeting);
        log.info("Created meeting '{}' (code={}) by user '{}'",
                saved.getTitle(), saved.getCode(), creator.getUsername());

        // Auto-add creator, host, and secretary as members with meeting-specific roles.
        MeetingRole creatorRole = creator.getId().equals(host.getId())
                ? MeetingRole.HOST
                : creator.getId().equals(secretary.getId())
                    ? MeetingRole.SECRETARY
                    : MeetingRole.MEMBER;
        addMemberIfAbsent(saved, creator, creatorRole);
        if (!host.getId().equals(creator.getId())) {
            addMemberIfAbsent(saved, host, MeetingRole.HOST);
        }
        if (!secretary.getId().equals(creator.getId())
                && !secretary.getId().equals(host.getId())) {
            addMemberIfAbsent(saved, secretary, MeetingRole.SECRETARY);
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

        if (meeting.getStatus() != MeetingStatus.SCHEDULED) {
            throw new BadRequestException("Only scheduled meetings can be updated");
        }

        if ((request.getTitle() != null && !request.getTitle().isBlank())
                || (request.getName() != null && !request.getName().isBlank())) {
            meeting.setTitle(resolveMeetingTitle(request.getTitle(), request.getName()));
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
            // Acquire pessimistic lock on the room to prevent race conditions
            Room room = entityManager.find(Room.class, request.getRoomId(), LockModeType.PESSIMISTIC_WRITE);
            if (room == null) {
                throw new ResourceNotFoundException(
                        "Room not found with id: " + request.getRoomId());
            }
            checkSchedulingConflict(request.getRoomId(), newStart, newEnd, id);
            meeting.setRoom(room);
            if (request.getDepartmentId() == null && room.getDepartment() != null) {
                meeting.setDepartmentId(room.getDepartment().getId());
            }
        } else if (meeting.getRoom() != null
                && (request.getStartTime() != null || request.getEndTime() != null)) {
            // Time changed but room unchanged — lock existing room and re-check conflicts
            entityManager.find(Room.class, meeting.getRoom().getId(), LockModeType.PESSIMISTIC_WRITE);
            checkSchedulingConflict(meeting.getRoom().getId(), newStart, newEnd, id);
        }

        if (request.getDepartmentId() != null) {
            if (!departmentRepository.existsById(request.getDepartmentId())) {
                throw new ResourceNotFoundException(
                        "Department not found with id: " + request.getDepartmentId());
            }
            meeting.setDepartmentId(request.getDepartmentId());
        }

        // Host update
        if (request.getHostUserId() != null) {
            User host = findUserOrThrow(request.getHostUserId());
            validateActiveUser(host, "Host");
            meeting.setHost(host);
            addMemberIfAbsent(meeting, host, MeetingRole.HOST);
        }

        // Secretary update
        if (request.getSecretaryUserId() != null) {
            User secretary = findUserOrThrow(request.getSecretaryUserId());
            if (secretary.getRole() != Role.SECRETARY) {
                throw new BadRequestException("Secretary must have SECRETARY role");
            }
            validateActiveUser(secretary, "Secretary");
            meeting.setSecretary(secretary);
            addMemberIfAbsent(meeting, secretary, MeetingRole.SECRETARY);
        }

        // Transcription priority update (only while SCHEDULED)
        if (request.getTranscriptionPriority() != null) {
            if (meeting.getStatus() == MeetingStatus.ACTIVE) {
                throw new BadRequestException(
                        "Transcription priority cannot be changed while the meeting is active");
            }
            meeting.setTranscriptionPriority(request.getTranscriptionPriority());
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

        boolean isAssignedSecretary = meeting.getSecretary() != null
                && meeting.getSecretary().getId().equals(requester.getId());
        boolean isCreatorSecretary = meeting.getCreator() != null
                && meeting.getCreator().getId().equals(requester.getId())
                && requester.getRole() == Role.SECRETARY;
        if (!isAssignedSecretary && !isCreatorSecretary) {
            throw new ForbiddenException(
                    "Only the meeting creator or assigned Secretary may delete meetings");
        }

        if (meeting.getStatus() != MeetingStatus.SCHEDULED) {
            throw new BadRequestException("Only scheduled meetings can be deleted");
        }

        meetingRepository.delete(meeting);
        log.info("Deleted meeting id={} '{}' by user '{}'",
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
                .meetingRole(request.getMeetingRole() != null
                        ? request.getMeetingRole()
                        : MeetingRole.MEMBER)
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

        Member member = memberRepository.findByMeetingIdAndUserId(meetingId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User with id " + userId + " is not a member of meeting " + meetingId));
        if (member == null) {
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
        if (member.getMeetingRole() == MeetingRole.HOST
                || member.getMeetingRole() == MeetingRole.SECRETARY) {
            throw new BadRequestException("Cannot remove protected meeting role: " + member.getMeetingRole());
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
        // Prevent scheduling meetings in the past
        if (startTime.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Start time cannot be in the past");
        }
        // Prevent unreasonably long meetings (max 24 hours)
        long durationHours = java.time.Duration.between(startTime, endTime).toHours();
        if (durationHours > 24) {
            throw new BadRequestException("Meeting duration cannot exceed 24 hours");
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
     *
     * Uses a retry loop with DataIntegrityViolationException handling to
     * guard against the (unlikely) race condition where two threads generate
     * the same code between the existsByCode check and the INSERT.
     * Requirements: 3.1
     */
    private String generateUniqueCode() {
        int maxAttempts = 10;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Two UUID segments joined = 32 hex chars; take first 20
            String part1 = UUID.randomUUID().toString().replace("-", "");
            String part2 = UUID.randomUUID().toString().replace("-", "");
            String code = (part1 + part2).substring(0, 20).toUpperCase();
            if (!meetingRepository.existsByCode(code)) {
                return code;
            }
            log.debug("Meeting code collision on attempt {}, retrying...", attempt + 1);
        }
        // Fallback: use full UUID (32 chars) which is virtually impossible to collide
        String fallback = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        log.warn("Meeting code generation exhausted {} attempts, using fallback 32-char code", maxAttempts);
        return fallback;
    }

    private void addMemberIfAbsent(Meeting meeting, User user, MeetingRole role) {
        java.util.Optional<Member> existing =
                memberRepository.findByMeetingIdAndUserId(meeting.getId(), user.getId());
        if (existing.isPresent()) {
            Member member = existing.get();
            if (isHigherPriorityRole(role, member.getMeetingRole())) {
                member.setMeetingRole(role);
                memberRepository.save(member);
            }
            return;
        }
        memberRepository.save(Member.builder()
                .meeting(meeting)
                .user(user)
                .meetingRole(role != null ? role : MeetingRole.MEMBER)
                .build());
    }

    private boolean isHigherPriorityRole(MeetingRole candidate, MeetingRole current) {
        return roleRank(candidate) > roleRank(current);
    }

    private int roleRank(MeetingRole role) {
        if (role == null) return 0;
        return switch (role) {
            case MEMBER, GUEST -> 1;
            case REVIEWER, COMMITTEE_MEMBER -> 2;
            case SECRETARY -> 3;
            case HOST -> 4;
        };
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

    private void validateActiveUser(User user, String label) {
        if (!user.isActive()) {
            throw new BadRequestException(label + " must be an active user");
        }
    }

    private String resolveMeetingTitle(String title, String name) {
        String resolved = title != null && !title.isBlank() ? title : name;
        if (resolved == null || resolved.isBlank()) {
            throw new BadRequestException("Meeting name is required");
        }
        return resolved.trim();
    }
}
