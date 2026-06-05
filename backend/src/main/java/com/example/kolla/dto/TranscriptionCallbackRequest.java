package com.example.kolla.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Request body sent by ASR service to POST /api/v1/transcription/callback.
 *
 * <p>ASR service calls this endpoint after completing inference on an audio chunk.
 * The {@code jobId} is used for idempotency: if a segment already exists for
 * this job, the callback returns 200 without creating a duplicate.
 *
 * <p>Jackson maps the snake_case keys sent by the ASR FastAPI service
 * to the camelCase Java fields via {@code @JsonProperty}.
 *
 * Requirements: 8.11, 8.12, 8.13
 */
@Data
public class TranscriptionCallbackRequest {

    /** UUID of the TranscriptionJob that was processed. */
    @NotBlank(message = "jobId is required")
    @JsonProperty("job_id")
    private String jobId;

    /** Transcribed text (Vietnamese). */
    @NotBlank(message = "text is required")
    private String text;

    /** Confidence score from the ASR model (0.0–1.0). Nullable. */
    private Float confidence;

    /** Time taken by ASR service to process this chunk, in milliseconds. */
    @Positive
    @JsonProperty("processing_time_ms")
    private Integer processingTimeMs;

    /**
     * ISO-8601 timestamp (UTC+7) of when the audio chunk started.
     * Used as {@code segment_start_time} in the persisted segment.
     */
    @NotNull(message = "segmentStartTime is required")
    @JsonProperty("segment_start_time")
    private String segmentStartTime;
}
