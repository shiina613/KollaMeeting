package com.example.kolla.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for updating a meeting.
 * All fields are optional — only non-null fields are applied.
 * Requirements: 3.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMeetingRequest {

    @Size(max = 500, message = "Title must not exceed 500 characters")
    private String title;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** Optional room ID. Null means no change; use a sentinel if you need to clear it. */
    private Long roomId;

    private Long hostUserId;

    private Long secretaryUserId;
}
