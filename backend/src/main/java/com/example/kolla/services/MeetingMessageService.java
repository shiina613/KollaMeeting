package com.example.kolla.services;

import com.example.kolla.dto.CreateMeetingMessageRequest;
import com.example.kolla.models.User;
import com.example.kolla.responses.MeetingMessageResponse;

import java.util.List;

public interface MeetingMessageService {

    List<MeetingMessageResponse> listMessages(Long meetingId, User requester);

    MeetingMessageResponse createMessage(Long meetingId, CreateMeetingMessageRequest request, User requester);
}
