package com.example.kolla.responses;

import com.example.kolla.enums.RecordingStatus;
import com.example.kolla.models.Recording;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for Recording data.
 * Requirements: 7.1–7.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecordingResponse {

    private Long id;
    private Long meetingId;
    private String fileName;
    private Long fileSize;
    private String filePath;
    private String url;
    private RecordingStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;

    /**
     * Convenience factory from entity.
     */
    public static RecordingResponse from(Recording recording) {
        RecordingResponseBuilder builder = RecordingResponse.builder()
                .id(recording.getId())
                .fileName(recording.getFileName())
                .fileSize(recording.getFileSize())
                .filePath(recording.getFilePath())
                .url(recording.getUrl())
                .status(recording.getStatus())
                .startTime(recording.getStartTime())
                .endTime(recording.getEndTime())
                .createdAt(recording.getCreatedAt());

        if (recording.getMeeting() != null) {
            builder.meetingId(recording.getMeeting().getId());
        }
        if (recording.getCreatedBy() != null) {
            builder.createdBy(recording.getCreatedBy().getId())
                   .createdByName(recording.getCreatedBy().getFullName());
        }

        return builder.build();
    }
}
