package com.example.kolla.responses;

import com.example.kolla.enums.MinutesStatus;
import com.example.kolla.models.Minutes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API response DTO for a {@link Minutes} record.
 * Requirements: 25.1–25.6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinutesResponse {

    private Long id;
    private Long meetingId;
    private MinutesStatus status;

    /** True if a draft PDF is available for download. */
    private boolean draftAvailable;

    /** Relative path to the draft DOCX, if generated. */
    private String draftDocxPath;

    /** True if a draft DOCX is available for download. */
    private boolean draftDocxAvailable;

    /** True if a Host-confirmed PDF is available for download. */
    private boolean confirmedAvailable;

    /** True if a Secretary-edited PDF is available for download. */
    private boolean secretaryAvailable;

    /** Relative path to the Secretary-edited DOCX, if generated. */
    private String secretaryDocxPath;

    /** True if a Secretary-edited DOCX is available for download. */
    private boolean secretaryDocxAvailable;

    /** Rich-text HTML content (Secretary version). May be null before Secretary edits. */
    private String contentHtml;

    private LocalDateTime hostConfirmedAt;
    private LocalDateTime secretaryConfirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MinutesResponse from(Minutes minutes) {
        return MinutesResponse.builder()
                .id(minutes.getId())
                .meetingId(minutes.getMeeting().getId())
                .status(minutes.getStatus())
                .draftAvailable(minutes.getDraftPdfPath() != null
                        && !minutes.getDraftPdfPath().isBlank())
                .draftDocxPath(minutes.getDraftDocxPath())
                .draftDocxAvailable(minutes.getDraftDocxPath() != null
                        && !minutes.getDraftDocxPath().isBlank())
                .confirmedAvailable(minutes.getConfirmedPdfPath() != null
                        && !minutes.getConfirmedPdfPath().isBlank())
                .secretaryAvailable(minutes.getSecretaryPdfPath() != null
                        && !minutes.getSecretaryPdfPath().isBlank())
                .secretaryDocxPath(minutes.getSecretaryDocxPath())
                .secretaryDocxAvailable(minutes.getSecretaryDocxPath() != null
                        && !minutes.getSecretaryDocxPath().isBlank())
                .contentHtml(minutes.getContentHtml())
                .hostConfirmedAt(minutes.getHostConfirmedAt())
                .secretaryConfirmedAt(minutes.getSecretaryConfirmedAt())
                .createdAt(minutes.getCreatedAt())
                .updatedAt(minutes.getUpdatedAt())
                .build();
    }
}
