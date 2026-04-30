package com.example.kolla.services;

import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.models.TranscriptionJob;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing the Redis Sorted Set transcription queue.
 *
 * <h3>Priority scoring</h3>
 * <ul>
 *   <li>HIGH_PRIORITY score  = {@code 1_000_000_000 - unix_ms_mod_1B}
 *       → higher score = processed first; older jobs within HIGH have higher score</li>
 *   <li>NORMAL_PRIORITY score = {@code unix_ms_mod_1B} (inverted relative to HIGH)
 *       → all HIGH jobs are processed before any NORMAL job</li>
 * </ul>
 *
 * <p>The Sorted Set key is {@code transcription:queue}.
 * Job details are stored in a Redis Hash at {@code transcription:job:{jobId}}.
 *
 * Requirements: 8.10
 */
public interface TranscriptionQueueService {

    /**
     * Push a job to the Redis Sorted Set with the appropriate priority score.
     * Also updates the job's DB status to QUEUED and sets {@code queuedAt}.
     *
     * <p>If Gipformer is currently unavailable, the job is left in PENDING status
     * and NOT pushed to Redis (recovery will re-queue it later).
     *
     * @param job the TranscriptionJob to enqueue
     * Requirements: 8.10
     */
    void pushJob(TranscriptionJob job);

    /**
     * Pop the highest-priority job from the queue (ZPOPMAX).
     * Returns empty if the queue is empty.
     *
     * @return the job ID of the popped job, or empty
     * Requirements: 8.10
     */
    Optional<String> popHighestPriorityJobId();

    /**
     * Re-score an existing job in the queue (e.g. when priority changes).
     * If the job is not currently in the queue, this is a no-op.
     *
     * @param jobId       the job UUID
     * @param newPriority the new priority to apply
     * Requirements: 8.10
     */
    void rescoreJob(String jobId, TranscriptionPriority newPriority);

    /**
     * Remove a job from the Redis queue (e.g. on cancellation or failure).
     *
     * @param jobId the job UUID
     */
    void removeJob(String jobId);

    /**
     * Push all PENDING jobs back to the Redis queue (recovery after Gipformer restart).
     * Called by {@link GipformerClient} when the service comes back online.
     *
     * @param pendingJobs list of PENDING jobs to re-queue
     * Requirements: 8.7
     */
    void requeuePendingJobs(List<TranscriptionJob> pendingJobs);

    /**
     * Get the current queue depth (number of jobs waiting).
     */
    long getQueueDepth();

    /**
     * Compute the Redis score for a given priority and creation timestamp.
     *
     * <p>Score formula:
     * <ul>
     *   <li>HIGH_PRIORITY:   {@code 1_000_000_000 - (unix_ms % 1_000_000_000)}</li>
     *   <li>NORMAL_PRIORITY: {@code unix_ms % 1_000_000_000}</li>
     * </ul>
     *
     * @param priority  the job priority
     * @param unixMs    creation time in milliseconds since epoch
     * @return the computed score
     * Requirements: 8.10
     */
    double computeScore(TranscriptionPriority priority, long unixMs);
}
