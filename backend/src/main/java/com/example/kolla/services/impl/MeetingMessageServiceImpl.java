package com.example.kolla.services.impl;

import com.example.kolla.dto.CreateMeetingMessageRequest;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.MeetingMessage;
import com.example.kolla.models.Member;
import com.example.kolla.models.User;
import com.example.kolla.repositories.MeetingMessageRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.MemberRepository;
import com.example.kolla.responses.MeetingMessageResponse;
import com.example.kolla.services.MeetingMessageService;
import com.example.kolla.websocket.MeetingEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MeetingMessageServiceImpl implements MeetingMessageService {

    private static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MeetingRepository meetingRepository;
    private final MemberRepository memberRepository;
    private final MeetingMessageRepository meetingMessageRepository;
    private final MeetingEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<MeetingMessageResponse> listMessages(Long meetingId, User requester) {
        ensureMeetingExists(meetingId);
        if (requester.getRole() != Role.ADMIN
                && !memberRepository.existsByMeetingIdAndUserId(meetingId, requester.getId())) {
            throw new ForbiddenException("Only meeting members may view messages");
        }
        return meetingMessageRepository.findByMemberMeetingIdOrderByCreateTimeAscIdAsc(meetingId)
                .stream()
                .map(MeetingMessageResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public MeetingMessageResponse createMessage(
            Long meetingId,
            CreateMeetingMessageRequest request,
            User requester) {
        ensureMeetingExists(meetingId);
        Member member = memberRepository.findByMeetingIdAndUserId(meetingId, requester.getId())
                .orElseThrow(() -> new ForbiddenException("Only meeting members may create messages"));

        MeetingMessage message = MeetingMessage.builder()
                .member(member)
                .content(request.content().trim())
                .createTime(LocalDateTime.now(ZONE_VN))
                .build();
        MeetingMessage saved = meetingMessageRepository.save(message);
        MeetingMessageResponse response = MeetingMessageResponse.from(saved);

        eventPublisher.publishMeetingMessageCreated(
                meetingId,
                saved.getId(),
                member.getId(),
                member.getUser().getFullName(),
                member.getMeetingRole().name(),
                saved.getContent(),
                saved.getCreateTime().atZone(ZONE_VN));
        return response;
    }

    private void ensureMeetingExists(Long meetingId) {
        if (!meetingRepository.existsById(meetingId)) {
            throw new ResourceNotFoundException("Meeting not found with id: " + meetingId);
        }
    }
}
