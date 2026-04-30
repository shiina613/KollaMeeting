package com.example.kolla.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for the {@code transcription_segment} table.
 *
 * <p>Stores the persisted result of a completed {@link TranscriptionJob}.
 * The UNIQUE KEY on {@code job_id} enforces idempotency: a second callback
 * for the same job will hit a duplicate-key constraint and be ignored.
 *
 * Requirements: 8.12, 8.13, 25.1
 */
@Entity
@Table(
    name = "transcription_segment",
    uniqueConstraints = @UniqueConstraint(name = "uk_ts_job", columnNames = "job_id")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to transcription_job.id (UUID string). */
    @Column(name = "job_id", nullable = false, length = 36)
    private String jobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "speaker_id", nullable = false)
    private Long speakerId;

    @Column(name = "speaker_name", nullable = false, length = 255)
    private String speakerName;

    @Column(name = "speaker_turn_id", nullable = false, length = 36)
    private String speakerTurnId;

    /** Monotonically increasing within a speaker_turn_id, starting at 1. */
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "confidence")
    private Float confidence;

    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;

    /** Wall-clock time when the audio chunk started (UTC+7). */
    @Column(name = "segment_start_time", nullable = false)
    private LocalDateTime segmentStartTime;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
