package com.example.kolla.models;

import com.example.kolla.enums.MinutesStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Minutes {
    private Long id;
    private Meeting meeting;

    @Builder.Default
    private MinutesStatus status = MinutesStatus.DRAFT;

    private String draftPdfPath;
    private String draftDocxPath;
    private String confirmedPdfPath;
    private String secretaryPdfPath;
    private String secretaryDocxPath;
    private String contentHtml;
    private LocalDateTime hostConfirmedAt;
    private String hostConfirmationHash;
    private LocalDateTime secretaryConfirmedAt;
    private LocalDateTime reminderSentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
