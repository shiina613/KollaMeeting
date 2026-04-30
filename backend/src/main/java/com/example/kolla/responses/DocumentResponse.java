package com.example.kolla.responses;

import com.example.kolla.models.Document;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for Document data.
 * Requirements: 9.1–9.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentResponse {

    private Long id;
    private Long meetingId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String filePath;
    private Long uploadedBy;
    private String uploadedByName;
    private LocalDateTime uploadedAt;

    /**
     * Convenience factory from entity.
     */
    public static DocumentResponse from(Document document) {
        DocumentResponseBuilder builder = DocumentResponse.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .fileType(document.getFileType())
                .filePath(document.getFilePath())
                .uploadedAt(document.getUploadedAt());

        if (document.getMeeting() != null) {
            builder.meetingId(document.getMeeting().getId());
        }
        if (document.getUploadedBy() != null) {
            builder.uploadedBy(document.getUploadedBy().getId())
                   .uploadedByName(document.getUploadedBy().getFullName());
        }

        return builder.build();
    }
}
