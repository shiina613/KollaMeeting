package com.example.kolla.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for creating a meeting.
 * Requirements: 3.1–3.9
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMeetingRequest {

    @NotBlank(message = "Meeting title is required")
    @Size(max = 500, message = "Title must not exceed 500 characters")
    private String title;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    private LocalDateTime endTime;

    /** Optional room ID. */
    private Long roomId;

    /**
     * Host user ID — must be a SECRETARY or ADMIN role user.
     * Required per Requirement 3.8.
     */
    @NotNull(message = "Host user ID is required")
    private Long hostUserId;

    /**
     * Secretary user ID — must be a SECRETARY role user.
     * Required per Requirement 3.8.
     */
    @NotNull(message = "Secretary user ID is required")
    private Long secretaryUserId;
}
