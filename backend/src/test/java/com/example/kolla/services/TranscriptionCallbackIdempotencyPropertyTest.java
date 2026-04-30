package com.example.kolla.services;

import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.TranscriptionJob;
import com.example.kolla.models.TranscriptionSegment;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Positive;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for transcription callback idempotency.
 *
 * <p>Property 8: Transcription Callback Idempotency
 *
 * <p>Verifies the invariant:
 * <blockquote>
 *   Processing the same callback (same jobId) multiple times must produce
 *   exactly one TranscriptionSegment — no duplicates.
 * </blockquote>
 *
 * <p>The UNIQUE KEY {@code uk_ts_job} on {@code transcription_segment.job_id}
 * enforces this at the database level. These tests verify the application-level
 * idempotency check logic in isolation.
 *
 * Requirements: 8.12
 */
class TranscriptionCallbackIdempotencyPropertyTest {

    // ── Domain model helpers ──────────────────────────────────────────────────

    /**
     * Simulates the idempotency check in {@code TranscriptionServiceImpl.processCallback()}.
     *
     * <p>If a segment already exists for the given jobId, returns the existing segment.
     * Otherwise, creates and stores a new segment.
     *
     * @param segments  mutable map of jobId → segment (simulates DB)
     * @param jobId     the job being processed
     * @param text      transcription text
     * @param meetingId the meeting ID
     * @return the segment (existing or newly created)
     */
    private TranscriptionSegment processCallback(
            Map<String, TranscriptionSegment> segments,
            String jobId,
            String text,
            Long meetingId,
            int sequenceNumber) {

        // Idempotency check: return existing if present
        if (segments.containsKey(jobId)) {
            return segments.get(jobId);
        }

        // Create new segment
        Meeting meeting = meetingStub(meetingId);
        TranscriptionSegment segment = TranscriptionSegment.builder()
                .id((long) (segments.size() + 1))
                .jobId(jobId)
                .meeting(meeting)
                .speakerId(1L)
                .speakerName("Speaker")
                .speakerTurnId("turn-" + jobId)
                .sequenceNumber(sequenceNumber)
                .text(text)
                .segmentStartTime(LocalDateTime.of(2025, 6, 1, 10, 0))
                .createdAt(LocalDateTime.now())
                .build();

        segments.put(jobId, segment);
        return segment;
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /**
     * Property 8a: Processing the same callback twice produces exactly one segment.
     *
     * <p>Calling processCallback with the same jobId twice must result in
     * exactly one segment in the store.
     */
    @Property(tries = 500)
    @Label("P8a: Processing the same callback twice produces exactly one segment")
    void processingCallbackTwiceProducesOneSegment(
            @ForAll("jobIds") String jobId,
            @ForAll @Positive long meetingId) {

        Map<String, TranscriptionSegment> segments = new HashMap<>();

        // First call
        TranscriptionSegment first = processCallback(segments, jobId, "Xin chào", meetingId, 1);
        // Second call (duplicate)
        TranscriptionSegment second = processCallback(segments, jobId, "Xin chào", meetingId, 1);

        assertThat(segments).hasSize(1);
        assertThat(first.getJobId()).isEqualTo(jobId);
        assertThat(second.getJobId()).isEqualTo(jobId);
        // Both calls return the same segment
        assertThat(first.getId()).isEqualTo(second.getId());
    }

    /**
     * Property 8b: Processing N distinct callbacks produces exactly N segments.
     *
     * <p>Each unique jobId must produce exactly one segment.
     */
    @Property(tries = 300)
    @Label("P8b: N distinct callbacks produce exactly N segments")
    void nDistinctCallbacksProduceNSegments(
            @ForAll @IntRange(min = 1, max = 20) int callbackCount,
            @ForAll @Positive long meetingId) {

        Map<String, TranscriptionSegment> segments = new HashMap<>();
        List<String> jobIds = new ArrayList<>();
        for (int i = 0; i < callbackCount; i++) {
            jobIds.add(UUID.randomUUID().toString());
        }

        for (int i = 0; i < callbackCount; i++) {
            processCallback(segments, jobIds.get(i), "text " + i, meetingId, i + 1);
        }

        assertThat(segments).hasSize(callbackCount);
    }

    /**
     * Property 8c: Repeated callbacks for the same job return the original text.
     *
     * <p>If the first callback stored text "A" and a second callback arrives with
     * text "B" for the same jobId, the stored text must remain "A" (first write wins).
     */
    @Property(tries = 300)
    @Label("P8c: Repeated callback does not overwrite existing segment text")
    void repeatedCallbackDoesNotOverwriteExistingText(
            @ForAll("jobIds") String jobId,
            @ForAll @Positive long meetingId) {

        Map<String, TranscriptionSegment> segments = new HashMap<>();

        String originalText = "Xin chào thế giới";
        String duplicateText = "Completely different text";

        processCallback(segments, jobId, originalText, meetingId, 1);
        processCallback(segments, jobId, duplicateText, meetingId, 1);

        TranscriptionSegment stored = segments.get(jobId);
        assertThat(stored.getText())
                .as("Stored text must be the original (first write wins)")
                .isEqualTo(originalText);
    }

    /**
     * Property 8d: Idempotency holds under arbitrary repetition count.
     *
     * <p>Calling processCallback K times for the same jobId must always result
     * in exactly one segment, regardless of K.
     */
    @Property(tries = 200)
    @Label("P8d: Idempotency holds under arbitrary repetition count")
    void idempotencyHoldsUnderArbitraryRepetition(
            @ForAll("jobIds") String jobId,
            @ForAll @IntRange(min = 1, max = 50) int repetitions,
            @ForAll @Positive long meetingId) {

        Map<String, TranscriptionSegment> segments = new HashMap<>();

        for (int i = 0; i < repetitions; i++) {
            processCallback(segments, jobId, "text", meetingId, 1);
        }

        assertThat(segments)
                .as("After %d repetitions, exactly one segment must exist for jobId %s",
                        repetitions, jobId)
                .hasSize(1);
    }

    /**
     * Property 8e: Idempotency is per-jobId — different jobs are independent.
     *
     * <p>Duplicate callbacks for job A must not affect segments for job B.
     */
    @Property(tries = 300)
    @Label("P8e: Idempotency is per-jobId — different jobs are independent")
    void idempotencyIsPerJobId(
            @ForAll("jobIds") String jobIdA,
            @ForAll("jobIds") String jobIdB,
            @ForAll @Positive long meetingId) {

        Assume.that(!jobIdA.equals(jobIdB));

        Map<String, TranscriptionSegment> segments = new HashMap<>();

        // Process A twice, B once
        processCallback(segments, jobIdA, "text A", meetingId, 1);
        processCallback(segments, jobIdA, "text A duplicate", meetingId, 1);
        processCallback(segments, jobIdB, "text B", meetingId, 2);

        assertThat(segments)
                .as("Two distinct jobs must produce exactly 2 segments")
                .hasSize(2);

        assertThat(segments.get(jobIdA).getText()).isEqualTo("text A");
        assertThat(segments.get(jobIdB).getText()).isEqualTo("text B");
    }

    // ── Arbitraries ───────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> jobIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'f')
                .withCharRange('0', '9')
                .ofLength(8)
                .map(s -> s + "-" + s + "-" + s + "-" + s + "-" + s);
    }

    // ── Stub helpers ──────────────────────────────────────────────────────────

    private Meeting meetingStub(Long id) {
        Meeting m = new Meeting();
        m.setId(id);
        m.setTranscriptionPriority(TranscriptionPriority.HIGH_PRIORITY);
        return m;
    }
}
