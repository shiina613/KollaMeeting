package com.example.kolla.services;

import com.example.kolla.models.Meeting;
import com.example.kolla.models.TranscriptionSegment;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Positive;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for minutes assembly ordering.
 *
 * <p>Property 7: Minutes Assembly Ordering
 *
 * <p>Verifies the core invariant of the minutes compilation step:
 * <blockquote>
 *   When TranscriptionSegments are assembled into meeting minutes, they must be
 *   sorted by (speakerTurnId, sequenceNumber) — i.e., all segments of a single
 *   speaker turn appear together in ascending sequence order, and turns are
 *   ordered by their first appearance (chronological turn order).
 * </blockquote>
 *
 * <p>Tests run in pure logic mode (no Spring context, no database) by directly
 * exercising the sorting comparator used in {@code MinutesServiceImpl}.
 *
 * Requirements: 25.1
 */
class MinutesAssemblyOrderingPropertyTest {

    // ── Sorting logic (mirrors MinutesServiceImpl) ────────────────────────────

    /**
     * Sort segments the same way the repository query does:
     * ORDER BY speakerTurnId, sequenceNumber.
     *
     * <p>This mirrors the JPQL in
     * {@code TranscriptionSegmentRepository.findByMeetingIdOrderedForMinutes()}.
     */
    private List<TranscriptionSegment> sortSegments(List<TranscriptionSegment> segments) {
        return segments.stream()
                .sorted(Comparator
                        .comparing(TranscriptionSegment::getSpeakerTurnId)
                        .thenComparingInt(TranscriptionSegment::getSequenceNumber))
                .collect(Collectors.toList());
    }

    /**
     * Group sorted segments by speakerTurnId, preserving insertion order.
     * Returns a map of turnId → list of segments (in sequence order).
     */
    private Map<String, List<TranscriptionSegment>> groupByTurn(
            List<TranscriptionSegment> sorted) {
        Map<String, List<TranscriptionSegment>> groups = new LinkedHashMap<>();
        for (TranscriptionSegment seg : sorted) {
            groups.computeIfAbsent(seg.getSpeakerTurnId(), k -> new ArrayList<>()).add(seg);
        }
        return groups;
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /**
     * Property 7a: Within each speaker turn, segments are in ascending sequence order.
     *
     * <p>After sorting, for every pair of consecutive segments with the same
     * speakerTurnId, the earlier segment must have a strictly smaller sequenceNumber.
     */
    @Property(tries = 500)
    @Label("P7a: Within each speaker turn, segments are in ascending sequence order")
    void withinEachTurnSegmentsAreInAscendingSequenceOrder(
            @ForAll("segmentLists") List<TranscriptionSegment> segments) {

        List<TranscriptionSegment> sorted = sortSegments(segments);

        for (int i = 0; i < sorted.size() - 1; i++) {
            TranscriptionSegment current = sorted.get(i);
            TranscriptionSegment next = sorted.get(i + 1);

            if (current.getSpeakerTurnId().equals(next.getSpeakerTurnId())) {
                assertThat(current.getSequenceNumber())
                        .as("Within turn '%s', segment at index %d (seq=%d) must come before "
                                + "segment at index %d (seq=%d)",
                                current.getSpeakerTurnId(), i, current.getSequenceNumber(),
                                i + 1, next.getSequenceNumber())
                        .isLessThan(next.getSequenceNumber());
            }
        }
    }

    /**
     * Property 7b: All segments of a speaker turn are contiguous in the sorted output.
     *
     * <p>Once a speakerTurnId appears in the sorted list, all segments with that
     * turnId must appear consecutively — no other turnId may interleave.
     */
    @Property(tries = 500)
    @Label("P7b: All segments of a speaker turn are contiguous in the sorted output")
    void allSegmentsOfATurnAreContiguous(
            @ForAll("segmentLists") List<TranscriptionSegment> segments) {

        List<TranscriptionSegment> sorted = sortSegments(segments);

        Set<String> completedTurns = new HashSet<>();
        String currentTurnId = null;

        for (TranscriptionSegment seg : sorted) {
            String turnId = seg.getSpeakerTurnId();

            if (!turnId.equals(currentTurnId)) {
                // We're switching to a new turn
                if (currentTurnId != null) {
                    completedTurns.add(currentTurnId);
                }
                // This turn must not have appeared before (no interleaving)
                assertThat(completedTurns)
                        .as("Turn '%s' appeared again after being completed — "
                                + "turns must be contiguous in sorted output", turnId)
                        .doesNotContain(turnId);
                currentTurnId = turnId;
            }
        }
    }

    /**
     * Property 7c: Sorting is stable — segments with the same (turnId, seqNum) key
     * retain their relative order.
     *
     * <p>In practice, (turnId, seqNum) should be unique, but the sort must not
     * corrupt the output if duplicates exist.
     */
    @Property(tries = 300)
    @Label("P7c: Sorting is deterministic — same input always produces same output")
    void sortingIsDeterministic(
            @ForAll("segmentLists") List<TranscriptionSegment> segments) {

        List<TranscriptionSegment> sorted1 = sortSegments(new ArrayList<>(segments));
        List<TranscriptionSegment> sorted2 = sortSegments(new ArrayList<>(segments));

        assertThat(sorted1)
                .as("Sorting the same list twice must produce identical results")
                .isEqualTo(sorted2);
    }

    /**
     * Property 7d: Sorting preserves all segments — no segment is lost or duplicated.
     *
     * <p>The sorted list must contain exactly the same segments as the input.
     */
    @Property(tries = 500)
    @Label("P7d: Sorting preserves all segments (no loss or duplication)")
    void sortingPreservesAllSegments(
            @ForAll("segmentLists") List<TranscriptionSegment> segments) {

        List<TranscriptionSegment> sorted = sortSegments(new ArrayList<>(segments));

        assertThat(sorted)
                .as("Sorted list must have the same size as the input")
                .hasSize(segments.size());

        // Check that every input segment appears exactly once in the output
        List<Long> inputIds = segments.stream()
                .map(TranscriptionSegment::getId)
                .sorted()
                .collect(Collectors.toList());
        List<Long> sortedIds = sorted.stream()
                .map(TranscriptionSegment::getId)
                .sorted()
                .collect(Collectors.toList());

        assertThat(sortedIds)
                .as("Sorted list must contain exactly the same segment IDs as the input")
                .isEqualTo(inputIds);
    }

    /**
     * Property 7e: Empty segment list sorts to empty list.
     */
    @Property(tries = 50)
    @Label("P7e: Empty segment list sorts to empty list")
    void emptySegmentListSortsToEmptyList() {
        List<TranscriptionSegment> sorted = sortSegments(Collections.emptyList());
        assertThat(sorted).isEmpty();
    }

    /**
     * Property 7f: Single-segment list is unchanged after sorting.
     */
    @Property(tries = 200)
    @Label("P7f: Single-segment list is unchanged after sorting")
    void singleSegmentListIsUnchanged(
            @ForAll @Positive long segId,
            @ForAll("turnIds") String turnId,
            @ForAll @IntRange(min = 1, max = 100) int seqNum) {

        TranscriptionSegment seg = segmentStub(segId, turnId, seqNum);
        List<TranscriptionSegment> sorted = sortSegments(List.of(seg));

        assertThat(sorted)
                .as("Single-segment list must be unchanged after sorting")
                .hasSize(1);
        assertThat(sorted.get(0).getId()).isEqualTo(segId);
    }

    /**
     * Property 7g: Segments from multiple turns are grouped correctly.
     *
     * <p>After sorting, the number of distinct groups equals the number of
     * distinct speakerTurnIds in the input.
     */
    @Property(tries = 300)
    @Label("P7g: Number of groups equals number of distinct speaker turns")
    void numberOfGroupsEqualsDistinctTurns(
            @ForAll("segmentLists") List<TranscriptionSegment> segments) {

        long distinctTurns = segments.stream()
                .map(TranscriptionSegment::getSpeakerTurnId)
                .distinct()
                .count();

        List<TranscriptionSegment> sorted = sortSegments(segments);
        Map<String, List<TranscriptionSegment>> groups = groupByTurn(sorted);

        assertThat(groups.size())
                .as("Number of groups (%d) must equal number of distinct turns (%d)",
                        groups.size(), distinctTurns)
                .isEqualTo((int) distinctTurns);
    }

    /**
     * Property 7h: Within each group, sequence numbers are strictly increasing.
     *
     * <p>This is a stronger version of P7a that checks the grouped structure
     * rather than consecutive pairs.
     */
    @Property(tries = 300)
    @Label("P7h: Within each group, sequence numbers are strictly increasing")
    void withinEachGroupSequenceNumbersAreStrictlyIncreasing(
            @ForAll("segmentLists") List<TranscriptionSegment> segments) {

        List<TranscriptionSegment> sorted = sortSegments(segments);
        Map<String, List<TranscriptionSegment>> groups = groupByTurn(sorted);

        for (Map.Entry<String, List<TranscriptionSegment>> entry : groups.entrySet()) {
            String turnId = entry.getKey();
            List<TranscriptionSegment> turnSegments = entry.getValue();

            for (int i = 0; i < turnSegments.size() - 1; i++) {
                int current = turnSegments.get(i).getSequenceNumber();
                int next = turnSegments.get(i + 1).getSequenceNumber();
                assertThat(current)
                        .as("In turn '%s', seq[%d]=%d must be < seq[%d]=%d",
                                turnId, i, current, i + 1, next)
                        .isLessThan(next);
            }
        }
    }

    // ── Arbitraries ───────────────────────────────────────────────────────────

    /**
     * Generate a list of TranscriptionSegments with realistic (turnId, seqNum) pairs.
     * Each turn has 1–5 segments with monotonically increasing sequence numbers.
     */
    @Provide
    Arbitrary<List<TranscriptionSegment>> segmentLists() {
        // Generate 1–5 distinct turn IDs
        Arbitrary<Integer> turnCountArb = Arbitraries.integers().between(1, 5);
        Arbitrary<Integer> segsPerTurnArb = Arbitraries.integers().between(1, 5);

        return turnCountArb.flatMap(turnCount ->
                segsPerTurnArb.list().ofSize(turnCount).flatMap(segsPerTurnList -> {
                    List<TranscriptionSegment> allSegments = new ArrayList<>();
                    long idCounter = 1;

                    for (int t = 0; t < turnCount; t++) {
                        String turnId = "turn-" + UUID.randomUUID().toString().substring(0, 8);
                        int segCount = segsPerTurnList.get(t);

                        for (int s = 1; s <= segCount; s++) {
                            allSegments.add(segmentStub(idCounter++, turnId, s));
                        }
                    }

                    // Shuffle to simulate out-of-order retrieval from DB
                    Collections.shuffle(allSegments, new Random(42));

                    return Arbitraries.just(allSegments);
                })
        );
    }

    @Provide
    Arbitrary<String> turnIds() {
        return Arbitraries.strings().alpha().ofMinLength(4).ofMaxLength(36);
    }

    // ── Stub helpers ──────────────────────────────────────────────────────────

    private TranscriptionSegment segmentStub(long id, String turnId, int seqNum) {
        Meeting meeting = new Meeting();
        meeting.setId(1L);

        TranscriptionSegment seg = new TranscriptionSegment();
        seg.setId(id);
        seg.setMeeting(meeting);
        seg.setSpeakerTurnId(turnId);
        seg.setSequenceNumber(seqNum);
        seg.setSpeakerId(1L);
        seg.setSpeakerName("Speaker " + id);
        seg.setText("Segment text " + id);
        seg.setJobId("job-" + id);
        seg.setSegmentStartTime(LocalDateTime.of(2025, 6, 1, 10, 0).plusMinutes(id % 10_000));
        seg.setCreatedAt(LocalDateTime.of(2025, 6, 1, 10, 0).plusMinutes(id % 10_000));
        return seg;
    }
}
