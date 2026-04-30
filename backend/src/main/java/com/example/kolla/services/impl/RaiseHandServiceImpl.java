package com.example.kolla.services.impl;

import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.RaiseHandStatus;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.RaiseHandRequest;
import com.example.kolla.models.User;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.MemberRepository;
import com.example.kolla.repositories.RaiseHandRequestRepository;
import com.example.kolla.responses.RaiseHandRequestResponse;
import com.example.kolla.services.MeetingLifecycleService;
import com.example.kolla.services.RaiseHandService;
import com.example.kolla.services.SpeakingPermissionService;
import com.example.kolla.websocket.MeetingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * RaiseHandService implementation.
 *
 * <p>Manages the raise-hand queue in Meeting_Mode. Participants submit requests
 * which are queued chronologically. The Host grants permission to one participant
 * at a time via {@link SpeakingPermissionService}.
 *
 * Requirements: 22.1–22.11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RaiseHandServiceImpl implements RaiseHandService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final RaiseHandRequestRepository raiseHandRequestRepository;
    private final MeetingRepository meetingRepository;
    private final MemberRepository memberRepository;
    private final MeetingLifecycleService meetingLifecycleService;
    private final SpeakingPermissionService speakingPermissionService;
    private final MeetingEventPublisher eventPublisher;
    private final Clock clock;

    // ── Raise hand ────────────────────────────────────────────────────────────

    /**
     * Submit a raise-hand request.
     * Requirements: 22.1, 22.2
     */
    @Override
    @Transactional
    public RaiseHandRequestResponse raiseHand(Long meetingId, User requester) {
        Meeting meeting = findMeetingOrThrow(meetingId);

        // Must be an active meeting in MEETING_MODE (Requirement 22.1)
        if (meeting.getStatus() != MeetingStatus.ACTIVE) {
            throw new BadRequestException(
                    "Cannot raise hand: meeting is not ACTIVE");
        }
        if (meeting.getMode() != MeetingMode.MEETING_MODE) {
            throw new BadRequestException(
                    "Cannot raise hand: meeting is not in MEETING_MODE");
        }

        // Must be a member (Requirement 3.9)
        if (!memberRepository.isMember(meetingId, requester.getId())) {
            throw new ForbiddenException(
                    "You are not a member of this meeting");
        }

        // Prevent duplicate pending requests
        if (raiseHandRequestRepository.hasPendingRequest(meetingId, requester.getId())) {
            throw new BadRequestException(
                    "You already have a pending raise-hand request in this meeting");
        }

        // Prevent raising hand if already holding speaking permission
        if (speakingPermissionService.hasActivePermission(meetingId, requester.getId())) {
            throw new BadRequestException(
                    "You already hold speaking permission in this meeting");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        RaiseHandRequest request = RaiseHandRequest.builder()
                .meeting(meeting)
                .user(requester)
                .requestedAt(now)
                .status(RaiseHandStatus.PENDING)
                .build();

        RaiseHandRequest saved = raiseHandRequestRepository.save(request);
        log.info("User '{}' raised hand in meeting id={}", requester.getUsername(), meetingId);

        // Broadcast to all participants (Host sees the notification) (Requirement 22.2)
        ZonedDateTime requestedAtZoned = now.atZone(VN_ZONE);
        eventPublisher.publishRaiseHand(
                meetingId, requester.getId(), requester.getFullName(), requestedAtZoned);

        return RaiseHandRequestResponse.from(saved);
    }

    // ── Lower hand ────────────────────────────────────────────────────────────

    /**
     * Cancel the current user's raise-hand request.
     * If the user holds speaking permission, it is also revoked.
     * Requirements: 22.7
     */
    @Override
    @Transactional
    public void lowerHand(Long meetingId, User requester) {
        Meeting meeting = findMeetingOrThrow(meetingId);

        LocalDateTime now = LocalDateTime.now(clock);
        boolean actionTaken = false;

        // Cancel pending raise-hand request if exists
        raiseHandRequestRepository
                .findPendingByMeetingIdAndUserId(meetingId, requester.getId())
                .ifPresent(request -> {
                    request.setStatus(RaiseHandStatus.CANCELLED);
                    request.setResolvedAt(LocalDateTime.now(clock));
                    raiseHandRequestRepository.save(request);
                    log.info("User '{}' lowered hand in meeting id={}",
                            requester.getUsername(), meetingId);
                });

        // Revoke speaking permission if held (Requirement 22.7)
        if (speakingPermissionService.hasActivePermission(meetingId, requester.getId())) {
            speakingPermissionService.revokePermissionOnLeave(meetingId, requester.getId());
            log.info("Revoked speaking permission for user '{}' who lowered hand in meeting id={}",
                    requester.getUsername(), meetingId);
        }

        // Broadcast HAND_LOWERED to all participants (Requirement 22.7)
        eventPublisher.publishHandLowered(meetingId, requester.getId());
    }

    // ── List pending requests ─────────────────────────────────────────────────

    /**
     * Get all pending raise-hand requests in chronological order.
     * Only the Host (or ADMIN) may view the full list.
     * Requirements: 22.9
     */
    @Override
    @Transactional(readOnly = true)
    public List<RaiseHandRequestResponse> listPendingRequests(Long meetingId, User requester) {
        Meeting meeting = findMeetingOrThrow(meetingId);

        // Only Host or ADMIN may view the raise-hand queue (Requirement 22.9)
        if (!meetingLifecycleService.isHostOrAdmin(meeting, requester)) {
            throw new ForbiddenException(
                    "Only the Host or an ADMIN may view the raise-hand queue");
        }

        return raiseHandRequestRepository
                .findPendingByMeetingIdOrderByRequestedAt(meetingId)
                .stream()
                .map(RaiseHandRequestResponse::from)
                .toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Meeting findMeetingOrThrow(Long id) {
        return meetingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Meeting not found with id: " + id));
    }
}
