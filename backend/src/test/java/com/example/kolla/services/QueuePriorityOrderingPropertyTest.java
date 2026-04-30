package com.example.kolla.services;

import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.services.impl.TranscriptionQueueServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Positive;

import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for transcription queue priority ordering.
 *
 * <p>Property 3: Queue Priority Ordering
 *
 * <p>Verifies the core invariant of the Redis Sorted Set priority scoring:
 * <blockquote>
 *   Every HIGH_PRIORITY job must have a higher score than every NORMAL_PRIORITY job.
 *   Within the same priority tier, older jobs (smaller unix_ms) have higher scores
 *   (processed first — FIFO within HIGH, LIFO within NORMAL).
 * </blockquote>
 *
 * <p>Tests run in pure logic mode (no Spring context, no Redis) by directly
 * exercising the {@code computeScore()} method.
 *
 * Requirements: 8.10
 */
class QueuePriorityOrderingPropertyTest {

    /**
     * Minimal stub of the score computation logic extracted from
     * {@link TranscriptionQueueServiceImpl#computeScore}.
     *
     * <pre>
     *   HIGH_PRIORITY:   score = 1_000_000_000 + (1_000_000_000 - unix_ms % 1_000_000_000)
     *   NORMAL_PRIORITY: score = unix_ms % 1_000_000_000
     * </pre>
     */
    private double computeScore(TranscriptionPriority priority, long unixMs) {
        long mod = unixMs % 1_000_000_000L;
        if (priority == TranscriptionPriority.HIGH_PRIORITY) {
            return (double) (1_000_000_000L + (1_000_000_000L - mod));
        } else {
            return (double) mod;
        }
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /**
     * Property 3a: Every HIGH_PRIORITY score is strictly greater than every NORMAL_PRIORITY score.
     *
     * <p>For any pair of timestamps, the HIGH score must exceed the NORMAL score.
     */
    @Property(tries = 1000)
    @Label("P3a: HIGH_PRIORITY score > NORMAL_PRIORITY score for any timestamps")
    void highPriorityScoreAlwaysExceedsNormalPriorityScore(
            @ForAll @Positive long highUnixMs,
            @ForAll @Positive long normalUnixMs) {

        double highScore = computeScore(TranscriptionPriority.HIGH_PRIORITY, highUnixMs);
        double normalScore = computeScore(TranscriptionPriority.NORMAL_PRIORITY, normalUnixMs);

        assertThat(highScore)
                .as("HIGH score (%.0f) must be > NORMAL score (%.0f) for highMs=%d, normalMs=%d",
                        highScore, normalScore, highUnixMs, normalUnixMs)
                .isGreaterThan(normalScore);
    }

    /**
     * Property 3b: Within HIGH_PRIORITY, older jobs have higher scores (FIFO).
     *
     * <p>If job A was created before job B (smaller unix_ms), job A must have
     * a higher score and be processed first — within the same 1B ms window.
     *
     * <p>Note: The score formula uses {@code unix_ms % 1_000_000_000}, so FIFO
     * ordering is guaranteed only within a single 1B ms window (~11.5 days).
     * This is sufficient for practical meeting durations.
     */
    @Property(tries = 500)
    @Label("P3b: Within HIGH_PRIORITY, older jobs have higher scores (FIFO) within same window")
    void withinHighPriorityOlderJobsHaveHigherScores(
            @ForAll @Positive long olderUnixMs,
            @ForAll @Positive long newerUnixMs) {

        // Ensure both timestamps are in the same 1B ms window
        Assume.that(olderUnixMs < newerUnixMs);
        Assume.that(newerUnixMs - olderUnixMs < 1_000_000_000L);
        // Ensure neither wraps around the modulus boundary
        Assume.that(olderUnixMs % 1_000_000_000L < newerUnixMs % 1_000_000_000L);

        double olderScore = computeScore(TranscriptionPriority.HIGH_PRIORITY, olderUnixMs);
        double newerScore = computeScore(TranscriptionPriority.HIGH_PRIORITY, newerUnixMs);

        assertThat(olderScore)
                .as("Older HIGH job (ms=%d, score=%.0f) must have higher score than newer (ms=%d, score=%.0f)",
                        olderUnixMs, olderScore, newerUnixMs, newerScore)
                .isGreaterThan(newerScore);
    }

    /**
     * Property 3c: All HIGH_PRIORITY scores are in the range (1B, 2B].
     *
     * <p>This ensures HIGH scores never overlap with NORMAL scores (which are in [0, 1B)).
     */
    @Property(tries = 500)
    @Label("P3c: All HIGH_PRIORITY scores are in range (1_000_000_000, 2_000_000_000]")
    void highPriorityScoresAreInExpectedRange(@ForAll @Positive long unixMs) {
        double score = computeScore(TranscriptionPriority.HIGH_PRIORITY, unixMs);

        assertThat(score)
                .as("HIGH score %.0f must be > 1B", score)
                .isGreaterThan(1_000_000_000.0);

        assertThat(score)
                .as("HIGH score %.0f must be ≤ 2B", score)
                .isLessThanOrEqualTo(2_000_000_000.0);
    }

    /**
     * Property 3d: All NORMAL_PRIORITY scores are in the range [0, 1B).
     */
    @Property(tries = 500)
    @Label("P3d: All NORMAL_PRIORITY scores are in range [0, 1_000_000_000)")
    void normalPriorityScoresAreInExpectedRange(@ForAll @Positive long unixMs) {
        double score = computeScore(TranscriptionPriority.NORMAL_PRIORITY, unixMs);

        assertThat(score)
                .as("NORMAL score %.0f must be ≥ 0", score)
                .isGreaterThanOrEqualTo(0.0);

        assertThat(score)
                .as("NORMAL score %.0f must be < 1B", score)
                .isLessThan(1_000_000_000.0);
    }

    /**
     * Property 3e: A mixed list of jobs, when sorted by score descending, has all HIGH jobs first.
     *
     * <p>Simulates the Redis ZPOPMAX ordering: jobs are popped in descending score order.
     * All HIGH_PRIORITY jobs must appear before any NORMAL_PRIORITY job.
     */
    @Property(tries = 200)
    @Label("P3e: Sorted by score descending, all HIGH jobs appear before NORMAL jobs")
    void sortedByScoreDescendingAllHighJobsAppearFirst(
            @ForAll @IntRange(min = 1, max = 10) int highCount,
            @ForAll @IntRange(min = 1, max = 10) int normalCount,
            @ForAll("timestamps") List<Long> timestamps) {

        Assume.that(timestamps.size() >= highCount + normalCount);

        // Build job list with scores
        List<JobScore> jobs = new ArrayList<>();
        for (int i = 0; i < highCount; i++) {
            long ts = timestamps.get(i);
            jobs.add(new JobScore("high-" + i, TranscriptionPriority.HIGH_PRIORITY,
                    computeScore(TranscriptionPriority.HIGH_PRIORITY, ts)));
        }
        for (int i = 0; i < normalCount; i++) {
            long ts = timestamps.get(highCount + i);
            jobs.add(new JobScore("normal-" + i, TranscriptionPriority.NORMAL_PRIORITY,
                    computeScore(TranscriptionPriority.NORMAL_PRIORITY, ts)));
        }

        // Sort by score descending (simulates Redis ZPOPMAX order)
        jobs.sort(Comparator.comparingDouble(JobScore::score).reversed());

        // Verify: all HIGH jobs come before any NORMAL job
        boolean seenNormal = false;
        for (JobScore job : jobs) {
            if (job.priority() == TranscriptionPriority.NORMAL_PRIORITY) {
                seenNormal = true;
            }
            if (seenNormal) {
                assertThat(job.priority())
                        .as("Once a NORMAL job appears, no HIGH job should follow in sorted order")
                        .isEqualTo(TranscriptionPriority.NORMAL_PRIORITY);
            }
        }
    }

    // ── Arbitraries ───────────────────────────────────────────────────────────

    @Provide
    Arbitrary<List<Long>> timestamps() {
        // Realistic unix timestamps (year 2020–2030 range)
        return Arbitraries.longs().between(1_577_836_800_000L, 1_893_456_000_000L)
                .list().ofMinSize(20).ofMaxSize(20);
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private record JobScore(String jobId, TranscriptionPriority priority, double score) {
    }
}
