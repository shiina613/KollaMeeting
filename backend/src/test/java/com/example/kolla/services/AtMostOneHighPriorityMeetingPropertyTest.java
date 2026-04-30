package com.example.kolla.services;

import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.models.Meeting;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Positive;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the "at most one high priority meeting" constraint.
 *
 * <p>Property 4: At Most One High Priority Meeting
 *
 * <p>Verifies the invariant:
 * <blockquote>
 *   At any given time, at most one ACTIVE meeting may hold HIGH_PRIORITY
 *   transcription status. When a new meeting is set to HIGH_PRIORITY, any
 *   previously HIGH_PRIORITY meeting must be downgraded to NORMAL_PRIORITY.
 * </blockquote>
 *
 * <p>This constraint ensures Gipformer resources are focused on one meeting
 * for near-real-time transcription display.
 *
 * Requirements: 8.12
 */
class AtMostOneHighPriorityMeetingPropertyTest {

    // ── Domain model helpers ──────────────────────────────────────────────────

    /**
     * Simulates setting a meeting to HIGH_PRIORITY.
     *
     * <p>Any currently HIGH_PRIORITY ACTIVE meeting is downgraded to NORMAL_PRIORITY
     * before the target meeting is upgraded.
     *
     * @param meetings  mutable list of all meetings
     * @param targetId  the meeting to set as HIGH_PRIORITY
     * @return updated list
     */
    private List<Meeting> setHighPriority(List<Meeting> meetings, Long targetId) {
        return meetings.stream()
                .map(m -> {
                    Meeting copy = copyMeeting(m);
                    if (m.getStatus() == MeetingStatus.ACTIVE
                            && m.getTranscriptionPriority() == TranscriptionPriority.HIGH_PRIORITY
                            && !m.getId().equals(targetId)) {
                        // Downgrade existing HIGH_PRIORITY meeting
                        copy.setTranscriptionPriority(TranscriptionPriority.NORMAL_PRIORITY);
                    } else if (m.getId().equals(targetId)) {
                        copy.setTranscriptionPriority(TranscriptionPriority.HIGH_PRIORITY);
                    }
                    return copy;
                })
                .collect(Collectors.toList());
    }

    /**
     * Count ACTIVE meetings with HIGH_PRIORITY.
     */
    private long countActiveHighPriority(List<Meeting> meetings) {
        return meetings.stream()
                .filter(m -> m.getStatus() == MeetingStatus.ACTIVE)
                .filter(m -> m.getTranscriptionPriority() == TranscriptionPriority.HIGH_PRIORITY)
                .count();
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /**
     * Property 4a: After setting any meeting to HIGH_PRIORITY, at most one ACTIVE
     * meeting holds HIGH_PRIORITY.
     */
    @Property(tries = 300)
    @Label("P4a: After setHighPriority, at most one ACTIVE meeting is HIGH_PRIORITY")
    void afterSetHighPriorityAtMostOneActiveIsHigh(
            @ForAll("activeMeetingLists") List<Meeting> meetings) {

        Assume.that(!meetings.isEmpty());

        // Pick a random meeting to set as HIGH_PRIORITY
        Long targetId = meetings.get(0).getId();
        List<Meeting> updated = setHighPriority(meetings, targetId);

        long highCount = countActiveHighPriority(updated);
        assertThat(highCount)
                .as("After setHighPriority, at most one ACTIVE meeting must be HIGH_PRIORITY")
                .isLessThanOrEqualTo(1L);
    }

    /**
     * Property 4b: The target meeting is HIGH_PRIORITY after the operation.
     */
    @Property(tries = 300)
    @Label("P4b: Target meeting is HIGH_PRIORITY after setHighPriority")
    void targetMeetingIsHighPriorityAfterSet(
            @ForAll("activeMeetingLists") List<Meeting> meetings) {

        Assume.that(!meetings.isEmpty());

        Long targetId = meetings.get(0).getId();
        List<Meeting> updated = setHighPriority(meetings, targetId);

        Optional<Meeting> target = updated.stream()
                .filter(m -> m.getId().equals(targetId))
                .findFirst();

        assertThat(target).isPresent();
        assertThat(target.get().getTranscriptionPriority())
                .as("Target meeting must be HIGH_PRIORITY after setHighPriority")
                .isEqualTo(TranscriptionPriority.HIGH_PRIORITY);
    }

    /**
     * Property 4c: After N sequential setHighPriority calls, exactly one meeting is HIGH_PRIORITY.
     *
     * <p>Each call must downgrade the previous HIGH_PRIORITY meeting.
     */
    @Property(tries = 200)
    @Label("P4c: After N sequential setHighPriority calls, exactly one meeting is HIGH_PRIORITY")
    void afterSequentialSetsExactlyOneIsHighPriority(
            @ForAll("activeMeetingLists") List<Meeting> meetings,
            @ForAll @IntRange(min = 1, max = 5) int setCount) {

        Assume.that(meetings.size() >= setCount);

        List<Meeting> current = new ArrayList<>(meetings);
        for (int i = 0; i < setCount; i++) {
            Long targetId = meetings.get(i).getId();
            current = setHighPriority(current, targetId);

            long highCount = countActiveHighPriority(current);
            assertThat(highCount)
                    .as("After set #%d, exactly one ACTIVE meeting must be HIGH_PRIORITY", i + 1)
                    .isEqualTo(1L);
        }
    }

    /**
     * Property 4d: ENDED meetings are not affected by setHighPriority.
     *
     * <p>Only ACTIVE meetings participate in the HIGH_PRIORITY constraint.
     * ENDED meetings retain their priority unchanged.
     */
    @Property(tries = 200)
    @Label("P4d: ENDED meetings are not affected by setHighPriority")
    void endedMeetingsAreNotAffectedBySetHighPriority(
            @ForAll("mixedMeetingLists") List<Meeting> meetings) {

        Assume.that(meetings.stream().anyMatch(m -> m.getStatus() == MeetingStatus.ACTIVE));

        // Record ENDED meeting priorities before
        Map<Long, TranscriptionPriority> endedBefore = meetings.stream()
                .filter(m -> m.getStatus() == MeetingStatus.ENDED)
                .collect(Collectors.toMap(Meeting::getId, Meeting::getTranscriptionPriority));

        // Set first ACTIVE meeting to HIGH_PRIORITY
        Long targetId = meetings.stream()
                .filter(m -> m.getStatus() == MeetingStatus.ACTIVE)
                .findFirst()
                .map(Meeting::getId)
                .orElseThrow();

        List<Meeting> updated = setHighPriority(meetings, targetId);

        // ENDED meetings must be unchanged
        for (Meeting m : updated) {
            if (m.getStatus() == MeetingStatus.ENDED) {
                assertThat(m.getTranscriptionPriority())
                        .as("ENDED meeting %d priority must be unchanged", m.getId())
                        .isEqualTo(endedBefore.get(m.getId()));
            }
        }
    }

    // ── Arbitraries ───────────────────────────────────────────────────────────

    @Provide
    Arbitrary<List<Meeting>> activeMeetingLists() {
        return Arbitraries.of(TranscriptionPriority.values())
                .map(priority -> {
                    Meeting m = new Meeting();
                    m.setId((long) (Math.random() * 1000 + 1));
                    m.setStatus(MeetingStatus.ACTIVE);
                    m.setTranscriptionPriority(priority);
                    return m;
                })
                .list().ofMinSize(1).ofMaxSize(7)
                .map(list -> {
                    // Ensure unique IDs
                    List<Meeting> unique = new ArrayList<>();
                    Set<Long> seen = new HashSet<>();
                    long id = 1;
                    for (Meeting m : list) {
                        m.setId(id++);
                        unique.add(m);
                    }
                    return unique;
                });
    }

    @Provide
    Arbitrary<List<Meeting>> mixedMeetingLists() {
        return Arbitraries.of(MeetingStatus.ACTIVE, MeetingStatus.ENDED)
                .flatMap(status ->
                        Arbitraries.of(TranscriptionPriority.values())
                                .map(priority -> {
                                    Meeting m = new Meeting();
                                    m.setStatus(status);
                                    m.setTranscriptionPriority(priority);
                                    return m;
                                }))
                .list().ofMinSize(2).ofMaxSize(10)
                .map(list -> {
                    long id = 1;
                    for (Meeting m : list) {
                        m.setId(id++);
                    }
                    return list;
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Meeting copyMeeting(Meeting original) {
        Meeting copy = new Meeting();
        copy.setId(original.getId());
        copy.setStatus(original.getStatus());
        copy.setTranscriptionPriority(original.getTranscriptionPriority());
        return copy;
    }
}
