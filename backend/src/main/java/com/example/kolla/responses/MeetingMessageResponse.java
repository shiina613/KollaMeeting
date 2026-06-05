package com.example.kolla.responses;

import com.example.kolla.enums.MeetingRole;
import com.example.kolla.models.MeetingMessage;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeetingMessageResponse {

    private Long id;
    private Long meetingId;
    private Long memberId;
    private Long userId;
    private String senderName;
    private MeetingRole meetingRole;
    private String content;
    private LocalDateTime createdAt;

    public static MeetingMessageResponse from(MeetingMessage message) {
        return MeetingMessageResponse.builder()
                .id(message.getId())
                .meetingId(message.getMember().getMeeting().getId())
                .memberId(message.getMember().getId())
                .userId(message.getMember().getUser().getId())
                .senderName(message.getMember().getUser().getFullName())
                .meetingRole(message.getMember().getMeetingRole())
                .content(message.getContent())
                .createdAt(message.getCreateTime())
                .build();
    }
}
