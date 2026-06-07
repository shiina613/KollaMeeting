package com.example.kolla.services.impl;

import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.User;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.MemberRepository;
import com.example.kolla.responses.RaiseHandRequestResponse;
import com.example.kolla.services.MeetingLifecycleService;
import com.example.kolla.services.RaiseHandQueueEntry;
import com.example.kolla.services.RaiseHandQueueService;
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
 * <p>Manages the raise-hand queue in Meeting_Mode via Redis. Participants submit requests
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

    private final RaiseHandQueueService raiseHandQueueService;
    private final MeetingRepository meetingRepository;
    private final MemberRepository memberRepository;
    private final MeetingLifecycleService meetingLifecycleService;
    private final SpeakingPermissionService speakingPermissionService;
    private final MeetingEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public RaiseHandRequestResponse raiseHand(Long meetingId, User requester) {
        Meeting meeting = findMeetingOrThrow(meetingId);

        if (meeting.getStatus() != MeetingStatus.ACTIVE) {
            throw new BadRequestException(
                    "Cannot raise hand: meeting is not ACTIVE");
        }
        if (meeting.getMode() != MeetingMode.MEETING_MODE) {
            throw new BadRequestException(
                    "Cannot raise hand: meeting is not in MEETING_MODE");
        }

        if (!memberRepository.isMember(meetingId, requester.getId())) {
            throw new ForbiddenException(
                    "You are not a member of this meeting");
        }

        if (raiseHandQueueService.hasPending(meetingId, requester.getId())) {
            throw new BadRequestException(
                    "You already have a pending raise-hand request in this meeting");
        }

        if (speakingPermissionService.hasActivePermission(meetingId, requester.getId())) {
            throw new BadRequestException(
                    "You already hold speaking permission in this meeting");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        raiseHandQueueService.enqueue(
                meetingId, requester.getId(), requester.getFullName(), now);

        log.info("User '{}' raised hand in meeting id={}", requester.getUsername(), meetingId);

        ZonedDateTime requestedAtZoned = now.atZone(VN_ZONE);
        eventPublisher.publishRaiseHand(
                meetingId, requester.getId(), requester.getFullName(), requestedAtZoned);

        return RaiseHandRequestResponse.fromQueueEntry(
                meetingId,
                new RaiseHandQueueEntry(requester.getId(), requester.getFullName(), now));
    }

    @Override
    @Transactional(readOnly = true)
    public void lowerHand(Long meetingId, User requester) {
        findMeetingOrThrow(meetingId);

        if (raiseHandQueueService.hasPending(meetingId, requester.getId())) {
            raiseHandQueueService.remove(meetingId, requester.getId());
            log.info("User '{}' lowered hand in meeting id={}",
                    requester.getUsername(), meetingId);
        }

        if (speakingPermissionService.hasActivePermission(meetingId, requester.getId())) {
            speakingPermissionService.revokePermissionOnLeave(meetingId, requester.getId());
            log.info("Revoked speaking permission for user '{}' who lowered hand in meeting id={}",
                    requester.getUsername(), meetingId);
        }

        eventPublisher.publishHandLowered(meetingId, requester.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaiseHandRequestResponse> listPendingRequests(Long meetingId, User requester) {
        Meeting meeting = findMeetingOrThrow(meetingId);

        if (!meetingLifecycleService.hasHostAuthority(meeting, requester)) {
            throw new ForbiddenException(
                    "Only the Host may view the raise-hand queue");
        }

        return raiseHandQueueService.listPendingOrdered(meetingId).stream()
                .map(entry -> RaiseHandRequestResponse.fromQueueEntry(meetingId, entry))
                .toList();
    }

    private Meeting findMeetingOrThrow(Long id) {
        return meetingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Meeting not found with id: " + id));
    }
}
