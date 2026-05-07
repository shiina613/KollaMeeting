package com.example.kolla.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for a TranscriptionJob used in the audio-chunks listing.
 * Includes transcription result text when available (COMPLETED status).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AudioJobResponse {

    private String jobId;
    private String speakerName;
    private String speakerDept;
    private String speakerTurnId;
    private int sequenceNumber;
    private String status;       // PENDING | QUEUED | PROCESSING | COMPLETED | FAILED
    private String createdAt;    // ISO string — when the audio chunk was captured
    private String text;         // transcription result (null if not yet completed)
    private Float confidence;
}
