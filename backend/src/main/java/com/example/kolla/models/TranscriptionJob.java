package com.example.kolla.models;

import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.enums.TranscriptionPriority;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionJob {
    private String id;
    private Meeting meeting;
    private Long speakerId;
    private String speakerName;
    private String speakerDept;
    private String speakerTurnId;
    private int sequenceNumber;
    private TranscriptionPriority priority;

    @Builder.Default
    private TranscriptionJobStatus status = TranscriptionJobStatus.PENDING;

    private String audioPath;

    @Builder.Default
    private int retryCount = 0;

    private LocalDateTime createdAt;
    private LocalDateTime queuedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
}
