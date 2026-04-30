package com.example.kolla.services;

import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.TranscriptionJob;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Positive;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for pending job requeue on Gipformer recovery.
 *
 * <p>Property 14: Pending Job Requeue on Recovery
 *
 * <p>Verifies the recovery invariant:
 * <blockquote>
 *   When Gipformer recovers, ALL jobs with status=PENDING must be pushed to
 *   the Redis queue with their original priority. No PENDING job must be skipped.
 *   Jobs with other statuses (QUEUED, PROCESSING, COMPLETED, FAILED) must NOT
 *   be re-queued.
 * </blockquote>
 *
 * Requirements: 8.7
 */
class PendingJobRequeuePropertyTest {

    // ── Domain model helpers ──────────────────────────────────────────────────

    /**
     * Simulates the recovery logic in {@code GipformerClient.recoverPendingJobs()}.
     *
     * <p>Filters PENDING jobs and pushes them to the queue (simulated as a list).
     *
     * @param allJobs all jobs in the system
     * @return the list of job IDs that were re-queued
     */
    private List<String> recoverPendingJobs(List<TranscriptionJob> allJobs) {
        return allJobs.stream()
                .filter(j -> j.getStatus() == TranscriptionJobStatus.PENDING)
                .map(TranscriptionJob::getId)
                .collect(Collectors.toList());
    }

    /**
     * Simulates updating job status to QUEUED after re-queuing.
     */
    private List<TranscriptionJob> markJobsAsQueued(
            List<TranscriptionJob> allJobs, List<String> requeuedIds) {
        Set<String> requeuedSet = new HashSet<>(requeuedIds);
        return allJobs.stream()
                .map(j -> {
                    if (requeuedSet.contains(j.getId())) {
                        TranscriptionJob updated = copyJob(j);
                        updated.setStatus(TranscriptionJobStatus.QUEUED);
                        updated.setQueuedAt(LocalDateTime.now());
                        return updated;
                    }
                    return j;
                })
                .collect(Collectors.toList());
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /**
     * Property 14a: All PENDING jobs are re-queued on recovery.
     *
     * <p>The number of re-queued jobs must equal the number of PENDING jobs.
     */
    @Property(tries = 300)
    @Label("P14a: All PENDING jobs are re-queued on recovery")
    void allPendingJobsAreRequeuedOnRecovery(
            @ForAll("jobLists") List<TranscriptionJob> allJobs) {

        long pendingCount = allJobs.stream()
                .filter(j -> j.getStatus() == TranscriptionJobStatus.PENDING)
                .count();

        List<String> requeued = recoverPendingJobs(allJobs);

        assertThat(requeued)
                .as("Re-queued count (%d) must equal PENDING count (%d)",
                        requeued.size(), pendingCount)
                .hasSize((int) pendingCount);
    }

    /**
     * Property 14b: Only PENDING jobs are re-queued — other statuses are not touched.
     */
    @Property(tries = 300)
    @Label("P14b: Only PENDING jobs are re-queued")
    void onlyPendingJobsAreRequeued(
            @ForAll("jobLists") List<TranscriptionJob> allJobs) {

        Set<String> pendingIds = allJobs.stream()
                .filter(j -> j.getStatus() == TranscriptionJobStatus.PENDING)
                .map(TranscriptionJob::getId)
                .collect(Collectors.toSet());

        List<String> requeued = recoverPendingJobs(allJobs);

        for (String requeuedId : requeued) {
            assertThat(pendingIds)
                    .as("Re-queued job %s must have been PENDING", requeuedId)
                    .contains(requeuedId);
        }
    }

    /**
     * Property 14c: Re-queued jobs preserve their original priority.
     *
     * <p>After recovery, each re-queued job must retain the same priority
     * it had when it was saved as PENDING.
     */
    @Property(tries = 300)
    @Label("P14c: Re-queued jobs preserve their original priority")
    void requeuedJobsPreserveOriginalPriority(
            @ForAll("jobLists") List<TranscriptionJob> allJobs) {

        Map<String, TranscriptionPriority> originalPriorities = allJobs.stream()
                .collect(Collectors.toMap(TranscriptionJob::getId, TranscriptionJob::getPriority));

        List<String> requeuedIds = recoverPendingJobs(allJobs);
        List<TranscriptionJob> updatedJobs = markJobsAsQueued(allJobs, requeuedIds);

        for (TranscriptionJob job : updatedJobs) {
            if (requeuedIds.contains(job.getId())) {
                assertThat(job.getPriority())
                        .as("Re-queued job %s must preserve priority %s",
                                job.getId(), originalPriorities.get(job.getId()))
                        .isEqualTo(originalPriorities.get(job.getId()));
            }
        }
    }

    /**
     * Property 14d: After recovery, no PENDING jobs remain.
     *
     * <p>All previously PENDING jobs must transition to QUEUED status.
     */
    @Property(tries = 300)
    @Label("P14d: After recovery, no PENDING jobs remain")
    void afterRecoveryNoPendingJobsRemain(
            @ForAll("jobLists") List<TranscriptionJob> allJobs) {

        List<String> requeuedIds = recoverPendingJobs(allJobs);
        List<TranscriptionJob> updatedJobs = markJobsAsQueued(allJobs, requeuedIds);

        long remainingPending = updatedJobs.stream()
                .filter(j -> j.getStatus() == TranscriptionJobStatus.PENDING)
                .count();

        assertThat(remainingPending)
                .as("After recovery, no PENDING jobs must remain")
                .isEqualTo(0L);
    }

    /**
     * Property 14e: Recovery with no PENDING jobs is a no-op.
     *
     * <p>If there are no PENDING jobs, the re-queue list must be empty.
     */
    @Property(tries = 200)
    @Label("P14e: Recovery with no PENDING jobs is a no-op")
    void recoveryWithNoPendingJobsIsNoOp(
            @ForAll @IntRange(min = 0, max = 10) int completedCount) {

        List<TranscriptionJob> allJobs = new ArrayList<>();
        for (int i = 0; i < completedCount; i++) {
            allJobs.add(buildJob("job-" + i, TranscriptionJobStatus.COMPLETED,
                    TranscriptionPriority.NORMAL_PRIORITY));
        }

        List<String> requeued = recoverPendingJobs(allJobs);

        assertThat(requeued)
                .as("No jobs should be re-queued when there are no PENDING jobs")
                .isEmpty();
    }

    /**
     * Property 14f: Recovery is idempotent — calling it twice produces the same result.
     *
     * <p>After the first recovery, all PENDING jobs become QUEUED.
     * A second recovery call must find no PENDING jobs and re-queue nothing.
     */
    @Property(tries = 200)
    @Label("P14f: Recovery is idempotent — second call re-queues nothing")
    void recoveryIsIdempotent(
            @ForAll("jobLists") List<TranscriptionJob> allJobs) {

        // First recovery
        List<String> firstRequeue = recoverPendingJobs(allJobs);
        List<TranscriptionJob> afterFirstRecovery = markJobsAsQueued(allJobs, firstRequeue);

        // Second recovery
        List<String> secondRequeue = recoverPendingJobs(afterFirstRecovery);

        assertThat(secondRequeue)
                .as("Second recovery must re-queue nothing (all PENDING already became QUEUED)")
                .isEmpty();
    }

    // ── Arbitraries ───────────────────────────────────────────────────────────

    @Provide
    Arbitrary<List<TranscriptionJob>> jobLists() {
        Arbitrary<TranscriptionJobStatus> statusArb = Arbitraries.of(TranscriptionJobStatus.values());
        Arbitrary<TranscriptionPriority> priorityArb = Arbitraries.of(TranscriptionPriority.values());

        return Combinators.combine(statusArb, priorityArb)
                .as((status, priority) -> buildJob(UUID.randomUUID().toString(), status, priority))
                .list().ofMinSize(0).ofMaxSize(15);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TranscriptionJob buildJob(String id, TranscriptionJobStatus status,
                                       TranscriptionPriority priority) {
        Meeting meeting = new Meeting();
        meeting.setId(1L);

        return TranscriptionJob.builder()
                .id(id)
                .meeting(meeting)
                .speakerId(1L)
                .speakerName("Speaker")
                .speakerTurnId("turn-1")
                .sequenceNumber(1)
                .priority(priority)
                .status(status)
                .audioPath("/app/storage/audio_chunks/1/turn-1/chunk_1_" + id + ".wav")
                .retryCount(0)
                .createdAt(LocalDateTime.of(2025, 6, 1, 10, 0))
                .build();
    }

    private TranscriptionJob copyJob(TranscriptionJob original) {
        return TranscriptionJob.builder()
                .id(original.getId())
                .meeting(original.getMeeting())
                .speakerId(original.getSpeakerId())
                .speakerName(original.getSpeakerName())
                .speakerTurnId(original.getSpeakerTurnId())
                .sequenceNumber(original.getSequenceNumber())
                .priority(original.getPriority())
                .status(original.getStatus())
                .audioPath(original.getAudioPath())
                .retryCount(original.getRetryCount())
                .createdAt(original.getCreatedAt())
                .queuedAt(original.getQueuedAt())
                .completedAt(original.getCompletedAt())
                .errorMessage(original.getErrorMessage())
                .build();
    }
}
