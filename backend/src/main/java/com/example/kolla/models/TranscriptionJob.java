package com.example.kolla.models;

import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.enums.TranscriptionPriority;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for the {@code transcription_job} table.
 *
 * <p>Each audio chunk produced by a speaker turn maps to exactly one job.
 * The job ID (UUID string) is also used as the member in the Redis Sorted Set.
 *
 * <p>Status lifecycle: PENDING → QUEUED → PROCESSING → COMPLETED | FAILED
 *
 * Requirements: 8.7, 8.9–8.13
 */
@Entity
@Table(name = "transcription_job")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionJob {

    /** UUID string — also used as Redis Sorted Set member. */
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "speaker_id", nullable = false)
    private Long speakerId;

    @Column(name = "speaker_name", nullable = false, length = 255)
    private String speakerName;

    /** UUID identifying the current speaker turn (new UUID per Speaking_Permission grant). */
    @Column(name = "speaker_turn_id", nullable = false, length = 36)
    private String speakerTurnId;

    /** Monotonically increasing within a speaker_turn_id, starting at 1. */
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private TranscriptionPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TranscriptionJobStatus status = TranscriptionJobStatus.PENDING;

    /** Path to the WAV file under /app/storage/audio_chunks/{meetingId}/{turnId}/. */
    @Column(name = "audio_path", length = 500)
    private String audioPath;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "queued_at")
    private LocalDateTime queuedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
