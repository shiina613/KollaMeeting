package com.example.kolla.services;

import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.RaiseHandStatus;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.RaiseHandRequest;
import com.example.kolla.models.SpeakingPermission;
import com.example.kolla.models.User;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Positive;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for speaking permission exclusivity.
 *
 * <p>Property 1: Speaking Permission Exclusivity
 *
 * <p>These tests verify the core invariant of the speaking permission system in
 * isolation (pure logic), without requiring a Spring context or database:
 *
 * <blockquote>
 *   At any given point in time, at most one participant holds an active
 *   (non-revoked) speaking permission within a single meeting.
 * </blockquote>
 *
 * <p>The tests model the permission lifecycle as a list of {@link SpeakingPermission}
 * records and verify that the invariant holds after every possible sequence of
 * grant and revoke operations.
 *
 * Requirements: 22.4, 22.8
 */
class SpeakingPermissionExclusivityPropertyTest {

    // ── Domain model helpers ──────────────────────────────────────────────────

    /**
     * Simulates the grant operation:
     * <ol>
     *   <li>Revoke any currently active permission.</li>
     *   <li>Add a new active permission for {@code targetUserId}.</li>
     * </ol>
     *
     * <p>This mirrors {@code SpeakingPermissionServiceImpl.grantPermission()}.
     */
    private List<SpeakingPermission> grantPermission(
            List<SpeakingPermission> permissions,
            Long meetingId,
            Long targetUserId,
            LocalDateTime now) {

        List<SpeakingPermission> updated = new ArrayList<>(permissions);

        // Revoke any currently active permission (revokedAt == null)
        for (SpeakingPermission sp : updated) {
            if (sp.getMeeting().getId().equals(meetingId) && sp.getRevokedAt() == null) {
                sp.setRevokedAt(now);
            }
        }

        // Grant new permission with a fresh speakerTurnId
        Meeting meeting = meetingStub(meetingId);
        User user = userStub(targetUserId);
        SpeakingPermission newPerm = SpeakingPermission.builder()
                .id((long) (updated.size() + 1))
                .meeting(meeting)
                .user(user)
                .grantedAt(now)
                .revokedAt(null)
                .speakerTurnId(UUID.randomUUID().toString())
                .build();
        updated.add(newPerm);

        return updated;
    }

    /**
     * Simulates the revoke operation: sets {@code revokedAt} on the active permission.
     *
     * <p>This mirrors {@code SpeakingPermissionServiceImpl.revokePermission()}.
     */
    private List<SpeakingPermission> revokePermission(
            List<SpeakingPermission> permissions,
            Long meetingId,
            LocalDateTime now) {

        List<SpeakingPermission> updated = new ArrayList<>(permissions);
        for (SpeakingPermission sp : updated) {
            if (sp.getMeeting().getId().equals(meetingId) && sp.getRevokedAt() == null) {
                sp.setRevokedAt(now);
            }
        }
        return updated;
    }

    /**
     * Counts the number of active (non-revoked) permissions for a meeting.
     */
    private long countActivePermissions(List<SpeakingPermission> permissions, Long meetingId) {
        return permissions.stream()
                .filter(sp -> sp.getMeeting().getId().equals(meetingId))
                .filter(sp -> sp.getRevokedAt() == null)
                .count();
    }

    /**
     * Returns the set of user IDs that currently hold active permission.
     */
    private Set<Long> activePermissionHolders(List<SpeakingPermission> permissions,
                                               Long meetingId) {
        return permissions.stream()
                .filter(sp -> sp.getMeeting().getId().equals(meetingId))
                .filter(sp -> sp.getRevokedAt() == null)
                .map(sp -> sp.getUser().getId())
                .collect(Collectors.toSet());
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /**
     * Property 1a: After any single grant, exactly one participant holds permission.
     *
     * <p>Starting from an empty permission list, granting permission to any user
     * must result in exactly one active permission.
     */
    @Property(tries = 500)
    @Label("P1a: After a single grant, exactly one participant holds permission")
    void afterSingleGrantExactlyOneHoldsPermission(
            @ForAll @Positive long meetingId,
            @ForAll @Positive long userId) {

        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);
        List<SpeakingPermission> permissions = new ArrayList<>();

        permissions = grantPermission(permissions, meetingId, userId, now);

        long activeCount = countActivePermissions(permissions, meetingId);
        assertThat(activeCount)
                .as("After a single grant, exactly one participant must hold permission")
                .isEqualTo(1L);
    }

    /**
     * Property 1b: After granting to a second user, the first user's permission is revoked.
     *
     * <p>Granting permission to user B while user A holds it must result in:
     * - user A's permission revoked
     * - user B holding the only active permission
     */
    @Property(tries = 500)
    @Label("P1b: Granting to a second user revokes the first user's permission")
    void grantingToSecondUserRevokesFirst(
            @ForAll @Positive long meetingId,
            @ForAll @Positive long userId1,
            @ForAll @Positive long userId2) {

        // Ensure distinct users
        Assume.that(userId1 != userId2);

        LocalDateTime t1 = LocalDateTime.of(2025, 6, 1, 10, 0);
        LocalDateTime t2 = t1.plusMinutes(5);

        List<SpeakingPermission> permissions = new ArrayList<>();

        // Grant to user 1
        permissions = grantPermission(permissions, meetingId, userId1, t1);
        // Grant to user 2 (should revoke user 1)
        permissions = grantPermission(permissions, meetingId, userId2, t2);

        long activeCount = countActivePermissions(permissions, meetingId);
        Set<Long> holders = activePermissionHolders(permissions, meetingId);

        assertThat(activeCount)
                .as("After granting to user2, exactly one permission must be active")
                .isEqualTo(1L);

        assertThat(holders)
                .as("Only user2 must hold the active permission")
                .containsExactly(userId2);
    }

    /**
     * Property 1c: After revoke, no participant holds permission.
     *
     * <p>Revoking the active permission must result in zero active permissions.
     */
    @Property(tries = 500)
    @Label("P1c: After revoke, no participant holds permission")
    void afterRevokeNoOneHoldsPermission(
            @ForAll @Positive long meetingId,
            @ForAll @Positive long userId) {

        LocalDateTime t1 = LocalDateTime.of(2025, 6, 1, 10, 0);
        LocalDateTime t2 = t1.plusMinutes(10);

        List<SpeakingPermission> permissions = new ArrayList<>();

        permissions = grantPermission(permissions, meetingId, userId, t1);
        permissions = revokePermission(permissions, meetingId, t2);

        long activeCount = countActivePermissions(permissions, meetingId);
        assertThat(activeCount)
                .as("After revoke, no participant must hold permission")
                .isEqualTo(0L);
    }

    /**
     * Property 1d: Exclusivity invariant holds after any sequence of grant/revoke operations.
     *
     * <p>For any sequence of N grant operations (possibly interleaved with revokes),
     * the number of active permissions must never exceed 1.
     */
    @Property(tries = 300)
    @Label("P1d: Exclusivity invariant holds after any sequence of grant/revoke operations")
    void exclusivityInvariantHoldsAfterAnySequence(
            @ForAll @IntRange(min = 1, max = 20) int operationCount,
            @ForAll("userIds") List<Long> userIds,
            @ForAll("operationFlags") List<Boolean> isGrantOps) {

        Assume.that(userIds.size() >= operationCount);
        Assume.that(isGrantOps.size() >= operationCount);

        Long meetingId = 1L;
        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);
        List<SpeakingPermission> permissions = new ArrayList<>();

        for (int i = 0; i < operationCount; i++) {
            boolean isGrant = isGrantOps.get(i);
            now = now.plusMinutes(1);

            if (isGrant) {
                Long userId = userIds.get(i % userIds.size());
                permissions = grantPermission(permissions, meetingId, userId, now);
            } else {
                permissions = revokePermission(permissions, meetingId, now);
            }

            // Invariant: at most one active permission at all times
            long activeCount = countActivePermissions(permissions, meetingId);
            assertThat(activeCount)
                    .as("After operation %d, active permission count must be ≤ 1 (was %d)",
                            i + 1, activeCount)
                    .isLessThanOrEqualTo(1L);
        }
    }

    /**
     * Property 1e: Each grant produces a unique speakerTurnId.
     *
     * <p>Every time permission is granted, a new UUID must be generated.
     * No two grants should share the same speakerTurnId.
     */
    @Property(tries = 200)
    @Label("P1e: Each grant produces a unique speakerTurnId")
    void eachGrantProducesUniqueSpeakerTurnId(
            @ForAll @IntRange(min = 2, max = 10) int grantCount,
            @ForAll("userIds") List<Long> userIds) {

        Assume.that(userIds.size() >= grantCount);

        Long meetingId = 1L;
        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);
        List<SpeakingPermission> permissions = new ArrayList<>();

        for (int i = 0; i < grantCount; i++) {
            now = now.plusMinutes(5);
            Long userId = userIds.get(i % userIds.size());
            permissions = grantPermission(permissions, meetingId, userId, now);
        }

        // Collect all speakerTurnIds
        List<String> turnIds = permissions.stream()
                .map(SpeakingPermission::getSpeakerTurnId)
                .collect(Collectors.toList());

        Set<String> uniqueTurnIds = new HashSet<>(turnIds);

        assertThat(uniqueTurnIds.size())
                .as("Each grant must produce a unique speakerTurnId; found duplicates in %s",
                        turnIds)
                .isEqualTo(turnIds.size());
    }

    /**
     * Property 1f: Granting to the same user twice is idempotent.
     *
     * <p>If user A already holds permission and the Host grants to user A again,
     * the result must still be exactly one active permission held by user A.
     */
    @Property(tries = 300)
    @Label("P1f: Granting to the same user twice is idempotent (still exactly one active)")
    void grantingToSameUserTwiceIsIdempotent(
            @ForAll @Positive long meetingId,
            @ForAll @Positive long userId) {

        LocalDateTime t1 = LocalDateTime.of(2025, 6, 1, 10, 0);
        LocalDateTime t2 = t1.plusMinutes(5);

        List<SpeakingPermission> permissions = new ArrayList<>();

        permissions = grantPermission(permissions, meetingId, userId, t1);
        permissions = grantPermission(permissions, meetingId, userId, t2);

        long activeCount = countActivePermissions(permissions, meetingId);
        Set<Long> holders = activePermissionHolders(permissions, meetingId);

        assertThat(activeCount)
                .as("Granting to the same user twice must result in exactly one active permission")
                .isEqualTo(1L);

        assertThat(holders)
                .as("The same user must still hold the permission after a duplicate grant")
                .containsExactly(userId);
    }

    /**
     * Property 1g: Permissions in different meetings are independent.
     *
     * <p>Granting permission in meeting A must not affect the permission state
     * of meeting B.
     */
    @Property(tries = 300)
    @Label("P1g: Permissions in different meetings are independent")
    void permissionsInDifferentMeetingsAreIndependent(
            @ForAll @Positive long userId1,
            @ForAll @Positive long userId2) {

        Long meetingA = 100L;
        Long meetingB = 200L;
        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);

        List<SpeakingPermission> permissions = new ArrayList<>();

        // Grant in meeting A
        permissions = grantPermission(permissions, meetingA, userId1, now);
        // Grant in meeting B
        permissions = grantPermission(permissions, meetingB, userId2, now.plusMinutes(1));
        // Revoke in meeting A
        permissions = revokePermission(permissions, meetingA, now.plusMinutes(2));

        // Meeting B should still have exactly one active permission
        long activeBCount = countActivePermissions(permissions, meetingB);
        assertThat(activeBCount)
                .as("Revoking permission in meeting A must not affect meeting B")
                .isEqualTo(1L);

        // Meeting A should have zero active permissions
        long activeACount = countActivePermissions(permissions, meetingA);
        assertThat(activeACount)
                .as("Meeting A must have zero active permissions after revoke")
                .isEqualTo(0L);
    }

    /**
     * Property 1h: Revoking when no permission is active is a no-op.
     *
     * <p>Calling revoke on an empty permission list must not throw and must
     * leave the list unchanged (zero active permissions).
     */
    @Property(tries = 200)
    @Label("P1h: Revoking when no permission is active is a no-op")
    void revokingWithNoActivePermissionIsNoOp(
            @ForAll @Positive long meetingId) {

        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);
        List<SpeakingPermission> permissions = new ArrayList<>();

        // Revoke on empty list — must not throw
        permissions = revokePermission(permissions, meetingId, now);

        long activeCount = countActivePermissions(permissions, meetingId);
        assertThat(activeCount)
                .as("Revoking with no active permission must leave zero active permissions")
                .isEqualTo(0L);
    }

    // ── Arbitraries ───────────────────────────────────────────────────────────

    @Provide
    Arbitrary<List<Long>> userIds() {
        return Arbitraries.longs().between(1L, 100L).list().ofMinSize(5).ofMaxSize(25);
    }

    @Provide
    Arbitrary<List<Boolean>> operationFlags() {
        return Arbitraries.of(true, false).list().ofMinSize(20).ofMaxSize(20);
    }

    // ── Stub helpers ──────────────────────────────────────────────────────────

    private Meeting meetingStub(Long id) {
        Meeting m = new Meeting();
        m.setId(id);
        m.setStatus(MeetingStatus.ACTIVE);
        m.setMode(MeetingMode.MEETING_MODE);
        return m;
    }

    private User userStub(Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("user" + id);
        return u;
    }
}
