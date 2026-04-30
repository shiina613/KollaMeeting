package com.example.kolla.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO for a persisted transcription segment.
 * Requirements: 8.12, 8.13
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TranscriptionSegmentResponse {

    private Long id;
    private String jobId;
    private Long meetingId;
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
