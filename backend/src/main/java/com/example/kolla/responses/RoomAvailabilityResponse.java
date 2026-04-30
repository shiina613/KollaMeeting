package com.example.kolla.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for room availability check.
 * Requirements: 12.8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomAvailabilityResponse {

    private Long roomId;
    private String roomName;

    /** True if the room is free for the requested time range. */
    private boolean available;

    /** Conflicting meetings in the requested time range (if any). */
    private List<BookedSlot> bookedSlots;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookedSlot {
        private Long meetingId;
        private String meetingTitle;
        private String meetingCode;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String status;
    }
}
