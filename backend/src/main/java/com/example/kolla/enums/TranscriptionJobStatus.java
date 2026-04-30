package com.example.kolla.enums;

/**
 * Lifecycle states for a TranscriptionJob.
 * Requirements: 8.7, 8.10, 8.11
 */
public enum TranscriptionJobStatus {
    /** Job created but not yet pushed to Redis queue (e.g. Gipformer unavailable). */
    PENDING,
    /** Job pushed to Redis Sorted Set, waiting for worker to pick it up. */
    QUEUED,
    /** Worker has popped the job and is running inference. */
    PROCESSING,
    /** Gipformer returned a result; segment persisted. */
    COMPLETED,
    /** All retries exhausted; job failed permanently. */
    FAILED
}
