package com.example.kolla.dto;

import com.example.kolla.enums.TranscriptionPriority;
import jakarta.validation.constraints.Future;
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

    @Size(max = 500, message = "Title must not exceed 500 characters")
    private String title;

    @Size(max = 500, message = "Name must not exceed 500 characters")
    private String name;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    private LocalDateTime endTime;

    @NotNull(message = "Room ID is required")
    private Long roomId;

    @NotNull(message = "Department ID is required")
    private Long departmentId;

    /**
     * Host/chairperson user ID. The host only needs to be an active user.
     */
    @NotNull(message = "Host user ID is required")
    private Long hostUserId;

    /**
     * Secretary user ID — must be a SECRETARY role user.
     * Required per Requirement 3.8.
     */
    @NotNull(message = "Secretary user ID is required")
    private Long secretaryUserId;

    /**
     * Transcription priority for the meeting.
     * HIGH_PRIORITY: real-time STT broadcast during meeting + saved to DB.
     * NORMAL_PRIORITY: STT saved to DB only (used for minutes after meeting ends).
     * Defaults to NORMAL_PRIORITY if not specified.
     */
    @Builder.Default
    private TranscriptionPriority transcriptionPriority = TranscriptionPriority.NORMAL_PRIORITY;
}
