package com.example.kolla.responses;

import com.example.kolla.enums.RaiseHandStatus;
import com.example.kolla.models.RaiseHandRequest;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for RaiseHandRequest data.
 * Requirements: 22.2, 22.3, 22.9
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RaiseHandRequestResponse {

    private Long id;
    private Long meetingId;
    private Long userId;
    private String userName;
    private LocalDateTime requestedAt;
    private RaiseHandStatus status;
    private LocalDateTime resolvedAt;

    public static RaiseHandRequestResponse from(RaiseHandRequest r) {
        return RaiseHandRequestResponse.builder()
                .id(r.getId())
                .meetingId(r.getMeeting().getId())
                .userId(r.getUser().getId())
                .userName(r.getUser().getFullName())
                .requestedAt(r.getRequestedAt())
                .status(r.getStatus())
                .resolvedAt(r.getResolvedAt())
                .build();
    }
}
