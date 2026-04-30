package com.example.kolla.responses;

import com.example.kolla.models.Notification;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for Notification data.
 * Requirements: 10.5–10.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationResponse {

    private Long id;
    private String type;
    private String title;
    private String message;
    private boolean isRead;
    private Long meetingId;
    private Long senderId;
    private String senderName;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .isRead(notification.isRead())
                .meetingId(notification.getMeeting() != null ? notification.getMeeting().getId() : null)
                .senderId(notification.getSender() != null ? notification.getSender().getId() : null)
                .senderName(notification.getSender() != null ? notification.getSender().getFullName() : null)
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
