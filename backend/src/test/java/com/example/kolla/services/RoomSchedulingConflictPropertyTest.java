package com.example.kolla.services;

import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Room;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for room scheduling conflict detection logic.
 *
 * Property 10: Room Scheduling Conflict Detection
 *
 * These tests verify the overlap detection algorithm in isolation (pure logic),
 * without requiring a Spring context or database. The overlap predicate is:
 *
 *   overlap(A, B) ⟺ A.start < B.end AND A.end > B.start
 *
 * Requirements: 3.12
 */
class RoomSchedulingConflictPropertyTest {

    // ── Overlap predicate (mirrors MeetingRepository.findConflictingMeetings) ──

    /**
     * Returns true if two time intervals [start1, end1) and [start2, end2) overlap.
     * This is the exact condition used in the JPQL query.
     */
    private boolean overlaps(LocalDateTime start1, LocalDateTime end1,
                              LocalDateTime start2, LocalDateTime end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    /**
     * Simulates the conflict check: given a list of existing meetings in a room,
     * returns true if any of them overlap with the candidate [newStart, newEnd).
     */
    private boolean hasConflict(List<Meeting> existing,
                                 LocalDateTime newStart, LocalDateTime newEnd,
                                 Long excludeId) {
        return existing.stream()
                .filter(m -> excludeId == null || !m.getId().equals(excludeId))
                .filter(m -> m.getStatus() == MeetingStatus.SCHEDULED
                          || m.getStatus() == MeetingStatus.ACTIVE)
                .anyMatch(m -> overlaps(m.getStartTime(), m.getEndTime(), newStart, newEnd));
    }

    // ── Helper to build a minimal Meeting stub ────────────────────────────────

    private Meeting meetingStub(long id, LocalDateTime start, LocalDateTime end,
                                 MeetingStatus status) {
        Meeting m = new Meeting();
        m.setId(id);
        m.setStartTime(start);
        m.setEndTime(end);
        m.setStatus(status);
        return m;
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /**
     * Property 10a: Reflexivity — a meeting always conflicts with itself.
     * If we do NOT exclude the meeting's own ID, it must detect a conflict.
     */
    @Property
    @Label("P10a: A meeting always overlaps with itself (same time slot)")
    void meetingAlwaysConflictsWithItself(
            @ForAll("validStartOffsets") int startOffsetHours,
            @ForAll @IntRange(min = 1, max = 8) int durationHours) {

        LocalDateTime base = LocalDateTime.of(2025, 6, 1, 8, 0);
        LocalDateTime start = base.plusHours(startOffsetHours);
        LocalDateTime end = start.plusHours(durationHours);

        Meeting existing = meetingStub(1L, start, end, MeetingStatus.SCHEDULED);

        // Without exclusion → must detect conflict
        boolean conflict = hasConflict(List.of(existing), start, end, null);
        assertThat(conflict)
                .as("A meeting must conflict with an identical time slot in the same room")
                .isTrue();
    }

    /**
     * Property 10b: Exclusion — when the same meeting ID is excluded, no self-conflict.
     */
    @Property
    @Label("P10b: Excluding own ID prevents self-conflict (update scenario)")
    void excludingOwnIdPreventsConflict(
            @ForAll("validStartOffsets") int startOffsetHours,
            @ForAll @IntRange(min = 1, max = 8) int durationHours) {

        LocalDateTime base = LocalDateTime.of(2025, 6, 1, 8, 0);
        LocalDateTime start = base.plusHours(startOffsetHours);
        LocalDateTime end = start.plusHours(durationHours);

        Meeting existing = meetingStub(42L, start, end, MeetingStatus.SCHEDULED);

        // Exclude the same ID → no conflict
        boolean conflict = hasConflict(List.of(existing), start, end, 42L);
        assertThat(conflict)
                .as("Excluding own meeting ID must not produce a self-conflict")
                .isFalse();
    }

    /**
     * Property 10c: Non-overlapping intervals never conflict.
     * For any two non-overlapping intervals [s1,e1) and [s2,e2) where e1 <= s2,
     * there must be no conflict.
     */
    @Property
    @Label("P10c: Non-overlapping intervals (sequential) never conflict")
    void nonOverlappingIntervalsNeverConflict(
            @ForAll("validStartOffsets") int startOffsetHours,
            @ForAll @IntRange(min = 1, max = 4) int duration1Hours,
            @ForAll @IntRange(min = 0, max = 4) int gapHours,
            @ForAll @IntRange(min = 1, max = 4) int duration2Hours) {

        LocalDateTime base = LocalDateTime.of(2025, 6, 1, 8, 0);
        LocalDateTime start1 = base.plusHours(startOffsetHours);
        LocalDateTime end1 = start1.plusHours(duration1Hours);
        // start2 begins at or after end1 (gap >= 0 means adjacent or later)
        LocalDateTime start2 = end1.plusHours(gapHours);
        LocalDateTime end2 = start2.plusHours(duration2Hours);

        Meeting existing = meetingStub(1L, start1, end1, MeetingStatus.SCHEDULED);

        boolean conflict = hasConflict(List.of(existing), start2, end2, null);
        assertThat(conflict)
                .as("Intervals [%s,%s) and [%s,%s) must not conflict when sequential",
                        start1, end1, start2, end2)
                .isFalse();
    }

    /**
     * Property 10d: Overlap symmetry — if A overlaps B, then B overlaps A.
     */
    @Property
    @Label("P10d: Overlap is symmetric")
    void overlapIsSymmetric(
            @ForAll("validStartOffsets") int s1Offset,
            @ForAll @IntRange(min = 1, max = 6) int d1,
            @ForAll("validStartOffsets") int s2Offset,
            @ForAll @IntRange(min = 1, max = 6) int d2) {

        LocalDateTime base = LocalDateTime.of(2025, 6, 1, 0, 0);
        LocalDateTime start1 = base.plusHours(s1Offset);
        LocalDateTime end1 = start1.plusHours(d1);
        LocalDateTime start2 = base.plusHours(s2Offset);
        LocalDateTime end2 = start2.plusHours(d2);

        boolean ab = overlaps(start1, end1, start2, end2);
        boolean ba = overlaps(start2, end2, start1, end1);

        assertThat(ab)
                .as("Overlap must be symmetric: overlaps(A,B) == overlaps(B,A)")
                .isEqualTo(ba);
    }

    /**
     * Property 10e: ENDED meetings are never considered for conflict.
     */
    @Property
    @Label("P10e: ENDED meetings are excluded from conflict detection")
    void endedMeetingsNeverConflict(
            @ForAll("validStartOffsets") int startOffsetHours,
            @ForAll @IntRange(min = 1, max = 8) int durationHours) {

        LocalDateTime base = LocalDateTime.of(2025, 6, 1, 8, 0);
        LocalDateTime start = base.plusHours(startOffsetHours);
        LocalDateTime end = start.plusHours(durationHours);

        // Existing meeting is ENDED — must not count as conflict
        Meeting ended = meetingStub(1L, start, end, MeetingStatus.ENDED);

        boolean conflict = hasConflict(List.of(ended), start, end, null);
        assertThat(conflict)
                .as("ENDED meetings must not be considered for scheduling conflicts")
                .isFalse();
    }

    /**
     * Property 10f: Partial overlap always detected.
     * If new meeting starts strictly inside an existing meeting, conflict must be detected.
     */
    @Property
    @Label("P10f: Partial overlap (new starts inside existing) is always detected")
    void partialOverlapAlwaysDetected(
            @ForAll("validStartOffsets") int startOffsetHours,
            @ForAll @IntRange(min = 2, max = 8) int existingDurationHours,
            @ForAll @IntRange(min = 1, max = 1) int overlapOffsetHours) {

        LocalDateTime base = LocalDateTime.of(2025, 6, 1, 8, 0);
        LocalDateTime existStart = base.plusHours(startOffsetHours);
        LocalDateTime existEnd = existStart.plusHours(existingDurationHours);

        // New meeting starts strictly inside the existing meeting
        LocalDateTime newStart = existStart.plusHours(overlapOffsetHours);
        LocalDateTime newEnd = existEnd.plusHours(1); // extends beyond

        Assume.that(newStart.isBefore(existEnd)); // guard: newStart is inside existing

        Meeting existing = meetingStub(1L, existStart, existEnd, MeetingStatus.SCHEDULED);

        boolean conflict = hasConflict(List.of(existing), newStart, newEnd, null);
        assertThat(conflict)
                .as("Partial overlap must always be detected as a conflict")
                .isTrue();
    }

    /**
     * Property 10g: Containment — a meeting that fully contains another always conflicts.
     */
    @Property
    @Label("P10g: Containing interval always conflicts with contained interval")
    void containmentAlwaysConflicts(
            @ForAll("validStartOffsets") int innerStartOffset,
            @ForAll @IntRange(min = 1, max = 4) int innerDuration,
            @ForAll @IntRange(min = 1, max = 3) int outerPaddingHours) {

        LocalDateTime base = LocalDateTime.of(2025, 6, 1, 8, 0);
        LocalDateTime innerStart = base.plusHours(innerStartOffset);
        LocalDateTime innerEnd = innerStart.plusHours(innerDuration);

        // Outer interval fully contains inner
        LocalDateTime outerStart = innerStart.minusHours(outerPaddingHours);
        LocalDateTime outerEnd = innerEnd.plusHours(outerPaddingHours);

        Meeting existing = meetingStub(1L, innerStart, innerEnd, MeetingStatus.SCHEDULED);

        boolean conflict = hasConflict(List.of(existing), outerStart, outerEnd, null);
        assertThat(conflict)
                .as("A containing interval must always conflict with the contained interval")
                .isTrue();
    }

    // ── Arbitraries ───────────────────────────────────────────────────────────

    @Provide
    Arbitrary<Integer> validStartOffsets() {
        return Arbitraries.integers().between(0, 16);
    }
}
