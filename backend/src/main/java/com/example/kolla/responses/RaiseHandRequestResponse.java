package com.example.kolla.responses;

import com.example.kolla.services.RaiseHandQueueEntry;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for raise-hand queue data.
 * Requirements: 22.2, 22.3, 22.9
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RaiseHandRequestResponse {

    private Long meetingId;
    private Long userId;
    private String userName;
    private LocalDateTime requestedAt;

    public static RaiseHandRequestResponse fromQueueEntry(Long meetingId, RaiseHandQueueEntry entry) {
        return RaiseHandRequestResponse.builder()
                .meetingId(meetingId)
                .userId(entry.userId())
                .userName(entry.userName())
                .requestedAt(entry.requestedAt())
                .build();
    }
}
