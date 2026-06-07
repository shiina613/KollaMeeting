package com.example.kolla.models;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionSegment {
    private Long id;
    private String jobId;
    private Meeting meeting;
    private Long speakerId;
    private String speakerName;
    private String speakerTurnId;
    private int sequenceNumber;
    private String text;
    private Float confidence;
    private Integer processingTimeMs;
    private LocalDateTime segmentStartTime;
    private LocalDateTime createdAt;
}
