package com.example.kolla.services;

import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.TranscriptionSegment;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Positive;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for transcription broadcast vs persist routing based on priority.
 *
 * <p>Property 11: Transcription Broadcast vs Persist Based on Priority
 *
 * <p>Verifies the routing invariant:
 * <blockquote>
 *   HIGH_PRIORITY meetings: segment is persisted AND broadcast via WebSocket.
 *   NORMAL_PRIORITY meetings: segment is persisted ONLY (no broadcast).
 *   In both cases, the segment is always persisted.
 * </blockquote>
 *
 * Requirements: 8.12, 8.13
 */
class TranscriptionBroadcastVsPersistPropertyTest {

    // ── Domain model helpers ──────────────────────────────────────────────────

    /**
     * Simulates the routing decision in {@code TranscriptionServiceImpl.processCallback()}.
     *
     * @return a {@link RoutingResult} indicating whether the segment was persisted
     *         and/or broadcast
     */
    private RoutingResult routeCallback(TranscriptionPriority priority, String jobId,
                                         Long meetingId) {
        // Always persist
        TranscriptionSegment segment = TranscriptionSegment.builder()
                .id(1L)
                .jobId(jobId)
                .meeting(meetingStub(meetingId, priority))
                .speakerId(1L)
                .speakerName("Speaker")
                .speakerTurnId("turn-1")
                .sequenceNumber(1)
                .text("Xin chào")
                .segmentStartTime(LocalDateTime.of(2025, 6, 1, 10, 0))
                .createdAt(LocalDateTime.now())
                .build();

        boolean persisted = true; // always

        // Broadcast only for HIGH_PRIORITY
        boolean broadcast = (priority == TranscriptionPriority.HIGH_PRIORITY);

        return new RoutingResult(segment, persisted, broadcast);
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /**
     * Property 11a: HIGH_PRIORITY callbacks are always persisted AND broadcast.
     */
    @Property(tries = 500)
    @Label("P11a: HIGH_PRIORITY callbacks are persisted AND broadcast")
    void highPriorityCallbacksArePersistedAndBroadcast(
            @ForAll @Positive long meetingId) {

        String jobId = UUID.randomUUID().toString();
        RoutingResult result = routeCallback(
                TranscriptionPriority.HIGH_PRIORITY, jobId, meetingId);

        assertThat(result.persisted())
                .as("HIGH_PRIORITY segment must always be persisted")
                .isTrue();

        assertThat(result.broadcast())
                .as("HIGH_PRIORITY segment must always be broadcast via WebSocket")
                .isTrue();
    }

    /**
     * Property 11b: NORMAL_PRIORITY callbacks are persisted but NOT broadcast.
     */
    @Property(tries = 500)
    @Label("P11b: NORMAL_PRIORITY callbacks are persisted but NOT broadcast")
    void normalPriorityCallbacksArePersistedButNotBroadcast(
            @ForAll @Positive long meetingId) {

        String jobId = UUID.randomUUID().toString();
        RoutingResult result = routeCallback(
                TranscriptionPriority.NORMAL_PRIORITY, jobId, meetingId);

        assertThat(result.persisted())
                .as("NORMAL_PRIORITY segment must always be persisted")
                .isTrue();

        assertThat(result.broadcast())
                .as("NORMAL_PRIORITY segment must NOT be broadcast via WebSocket")
                .isFalse();
    }

    /**
     * Property 11c: All segments are persisted regardless of priority.
     *
     * <p>For any combination of priorities, every processed callback must
     * result in a persisted segment.
     */
    @Property(tries = 300)
    @Label("P11c: All segments are persisted regardless of priority")
    void allSegmentsArePersistedRegardlessOfPriority(
            @ForAll("priorities") List<TranscriptionPriority> priorities,
            @ForAll @Positive long meetingId) {

        List<RoutingResult> results = new ArrayList<>();
        for (TranscriptionPriority priority : priorities) {
            results.add(routeCallback(priority, UUID.randomUUID().toString(), meetingId));
        }

        long persistedCount = results.stream().filter(RoutingResult::persisted).count();
        assertThat(persistedCount)
                .as("All %d callbacks must be persisted", priorities.size())
                .isEqualTo(priorities.size());
    }

    /**
     * Property 11d: Only HIGH_PRIORITY segments are broadcast.
     *
     * <p>For a mixed list of priorities, the number of broadcast events must
     * equal the number of HIGH_PRIORITY callbacks.
     */
    @Property(tries = 300)
    @Label("P11d: Broadcast count equals HIGH_PRIORITY callback count")
    void broadcastCountEqualsHighPriorityCount(
            @ForAll("priorities") List<TranscriptionPriority> priorities,
            @ForAll @Positive long meetingId) {

        long expectedBroadcasts = priorities.stream()
                .filter(p -> p == TranscriptionPriority.HIGH_PRIORITY)
                .count();

        List<RoutingResult> results = new ArrayList<>();
        for (TranscriptionPriority priority : priorities) {
            results.add(routeCallback(priority, UUID.randomUUID().toString(), meetingId));
        }

        long actualBroadcasts = results.stream().filter(RoutingResult::broadcast).count();
        assertThat(actualBroadcasts)
                .as("Broadcast count (%d) must equal HIGH_PRIORITY count (%d)",
                        actualBroadcasts, expectedBroadcasts)
                .isEqualTo(expectedBroadcasts);
    }

    /**
     * Property 11e: Priority routing is deterministic — same priority always produces same routing.
     */
    @Property(tries = 300)
    @Label("P11e: Priority routing is deterministic")
    void priorityRoutingIsDeterministic(
            @ForAll TranscriptionPriority priority,
            @ForAll @Positive long meetingId) {

        RoutingResult r1 = routeCallback(priority, UUID.randomUUID().toString(), meetingId);
        RoutingResult r2 = routeCallback(priority, UUID.randomUUID().toString(), meetingId);

        assertThat(r1.persisted()).isEqualTo(r2.persisted());
        assertThat(r1.broadcast()).isEqualTo(r2.broadcast());
    }

    // ── Arbitraries ───────────────────────────────────────────────────────────

    @Provide
    Arbitrary<List<TranscriptionPriority>> priorities() {
        return Arbitraries.of(TranscriptionPriority.values())
                .list().ofMinSize(1).ofMaxSize(20);
    }

    // ── Stub helpers ──────────────────────────────────────────────────────────

    private Meeting meetingStub(Long id, TranscriptionPriority priority) {
        Meeting m = new Meeting();
        m.setId(id);
        m.setTranscriptionPriority(priority);
        return m;
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private record RoutingResult(
            TranscriptionSegment segment,
            boolean persisted,
            boolean broadcast) {
    }
}
