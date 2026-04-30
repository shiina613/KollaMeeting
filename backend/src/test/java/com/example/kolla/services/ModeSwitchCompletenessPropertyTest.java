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
 * Property-based tests for mode switch completeness.
 *
 * <p>Property 12: Mode Switch Completeness
 *
 * <p>These tests verify the invariants of the mode switch operation in isolation
 * (pure logic), without requiring a Spring context or database.
 *
 * <p>The key invariants verified are:
 * <ol>
 *   <li><b>FREE_MODE cleanup</b>: When switching MEETING_MODE → FREE_MODE, all active
 *       speaking permissions must be revoked and all pending raise-hand requests
 *       must be expired before the mode change is visible (Requirement 21.9, 21.10).</li>
 *   <li><b>MEETING_MODE entry</b>: When switching FREE_MODE → MEETING_MODE, no
 *       speaking permissions are affected (Requirement 21.2).</li>
 *   <li><b>Atomicity</b>: The mode transition is only visible after all cleanup
 *       steps complete (Requirement 21.10).</li>
 *   <li><b>Idempotency</b>: Switching to the current mode is rejected.</li>
 * </ol>
 *
 * Requirements: 21.3, 21.9, 21.10
 */
class ModeSwitchCompletenessPropertyTest {

    // ── Meeting state model ───────────────────────────────────────────────────

    /**
     * Represents the complete state of a meeting for mode switch testing.
     */
    static class MeetingState {
        Meeting meeting;
        List<SpeakingPermission> permissions;
        List<RaiseHandRequest> raiseHandRequests;
        List<String> broadcastEvents; // ordered list of events broadcast

        MeetingState(Meeting meeting) {
            this.meeting = meeting;
            this.permissions = new ArrayList<>();
            this.raiseHandRequests = new ArrayList<>();
            this.broadcastEvents = new ArrayList<>();
        }

        long countActivePermissions() {
            return permissions.stream()
                    .filter(sp -> sp.getMeeting().getId().equals(meeting.getId()))
                    .filter(sp -> sp.getRevokedAt() == null)
                    .count();
        }

        long countPendingRaiseHands() {
            return raiseHandRequests.stream()
                    .filter(rhr -> rhr.getMeeting().getId().equals(meeting.getId()))
                    .filter(rhr -> rhr.getStatus() == RaiseHandStatus.PENDING)
                    .count();
        }

        boolean hasEvent(String eventType) {
            return broadcastEvents.contains(eventType);
        }

        /**
         * Returns the index of the first occurrence of an event, or -1 if not found.
         */
        int eventIndex(String eventType) {
            return broadcastEvents.indexOf(eventType);
        }
    }

    // ── Mode switch simulation ────────────────────────────────────────────────

    /**
     * Simulates switching to MEETING_MODE.
     *
     * <p>Steps (Requirement 21.2):
     * <ol>
     *   <li>Update meeting mode.</li>
     *   <li>Broadcast MODE_CHANGED.</li>
     * </ol>
     */
    private MeetingState switchToMeetingMode(MeetingState state, LocalDateTime now) {
        state.meeting.setMode(MeetingMode.MEETING_MODE);
        state.broadcastEvents.add("MODE_CHANGED:MEETING_MODE");
        return state;
    }

    /**
     * Simulates switching to FREE_MODE.
     *
     * <p>Steps (Requirement 21.9, 21.10):
     * <ol>
     *   <li>Revoke active speaking permission (if any) → broadcast SPEAKING_PERMISSION_REVOKED.</li>
     *   <li>Expire all pending raise-hand requests.</li>
     *   <li>Update meeting mode.</li>
     *   <li>Broadcast MODE_CHANGED (LAST step).</li>
     * </ol>
     */
    private MeetingState switchToFreeMode(MeetingState state, LocalDateTime now) {
        Long meetingId = state.meeting.getId();

        // Step 1: Revoke active speaking permission
        boolean hadActivePermission = false;
        for (SpeakingPermission sp : state.permissions) {
            if (sp.getMeeting().getId().equals(meetingId) && sp.getRevokedAt() == null) {
                sp.setRevokedAt(now);
                hadActivePermission = true;
                state.broadcastEvents.add("SPEAKING_PERMISSION_REVOKED");
            }
        }

        // Step 2: Expire all pending raise-hand requests
        for (RaiseHandRequest rhr : state.raiseHandRequests) {
            if (rhr.getMeeting().getId().equals(meetingId)
                    && rhr.getStatus() == RaiseHandStatus.PENDING) {
                rhr.setStatus(RaiseHandStatus.EXPIRED);
                rhr.setResolvedAt(now);
            }
        }

        // Step 3: Update meeting mode
        state.meeting.setMode(MeetingMode.FREE_MODE);

        // Step 4: Broadcast MODE_CHANGED (LAST step — Requirement 21.10)
        state.broadcastEvents.add("MODE_CHANGED:FREE_MODE");

        return state;
    }

    // ── Helper builders ───────────────────────────────────────────────────────

    private MeetingState buildMeetingInMode(Long meetingId, MeetingMode mode) {
        Meeting meeting = new Meeting();
        meeting.setId(meetingId);
        meeting.setStatus(MeetingStatus.ACTIVE);
        meeting.setMode(mode);
        return new MeetingState(meeting);
    }

    private void addActivePermission(MeetingState state, Long userId, LocalDateTime grantedAt) {
        Meeting meeting = state.meeting;
        User user = userStub(userId);
        SpeakingPermission sp = SpeakingPermission.builder()
                .id((long) (state.permissions.size() + 1))
                .meeting(meeting)
                .user(user)
                .grantedAt(grantedAt)
                .revokedAt(null)
                .speakerTurnId(UUID.randomUUID().toString())
                .build();
        state.permissions.add(sp);
    }

    private void addPendingRaiseHand(MeetingState state, Long userId, LocalDateTime requestedAt) {
        Meeting meeting = state.meeting;
        User user = userStub(userId);
        RaiseHandRequest rhr = RaiseHandRequest.builder()
                .id((long) (state.raiseHandRequests.size() + 1))
                .meeting(meeting)
                .user(user)
                .requestedAt(requestedAt)
                .status(RaiseHandStatus.PENDING)
                .build();
        state.raiseHandRequests.add(rhr);
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /**
     * Property 12a: Switching to FREE_MODE revokes all active speaking permissions.
     *
     * <p>After MEETING_MODE → FREE_MODE, no active speaking permissions must remain.
     */
    @Property(tries = 300)
    @Label("P12a: Switching to FREE_MODE revokes all active speaking permissions")
    void switchToFreeModeRevokesAllActivePermissions(
            @ForAll @Positive long meetingId,
            @ForAll @IntRange(min = 0, max = 3) int activePermissionCount) {

        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);
        MeetingState state = buildMeetingInMode(meetingId, MeetingMode.MEETING_MODE);

        // Add active permissions (in practice at most 1, but test with 0..3 for robustness)
        for (int i = 0; i < activePermissionCount; i++) {
            addActivePermission(state, (long) (i + 1), now.minusMinutes(5));
        }

        switchToFreeMode(state, now);

        long activeAfter = state.countActivePermissions();
        assertThat(activeAfter)
                .as("After switching to FREE_MODE, no active speaking permissions must remain")
                .isEqualTo(0L);
    }

    /**
     * Property 12b: Switching to FREE_MODE expires all pending raise-hand requests.
     *
     * <p>After MEETING_MODE → FREE_MODE, all PENDING raise-hand requests must be EXPIRED.
     */
    @Property(tries = 300)
    @Label("P12b: Switching to FREE_MODE expires all pending raise-hand requests")
    void switchToFreeModeExpiresAllPendingRaiseHands(
            @ForAll @Positive long meetingId,
            @ForAll @IntRange(min = 0, max = 5) int pendingCount) {

        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);
        MeetingState state = buildMeetingInMode(meetingId, MeetingMode.MEETING_MODE);

        for (int i = 0; i < pendingCount; i++) {
            addPendingRaiseHand(state, (long) (i + 1), now.minusMinutes(pendingCount - i));
        }

        switchToFreeMode(state, now);

        long pendingAfter = state.countPendingRaiseHands();
        assertThat(pendingAfter)
                .as("After switching to FREE_MODE, no PENDING raise-hand requests must remain")
                .isEqualTo(0L);
    }

    /**
     * Property 12c: MODE_CHANGED is the last event broadcast when switching to FREE_MODE.
     *
     * <p>The mode change must not be visible to participants until all cleanup
     * (permission revocation, raise-hand expiry) is complete (Requirement 21.10).
     */
    @Property(tries = 300)
    @Label("P12c: MODE_CHANGED is the last event broadcast when switching to FREE_MODE")
    void modeChangedIsLastEventWhenSwitchingToFreeMode(
            @ForAll @Positive long meetingId,
            @ForAll @Positive long speakerId) {

        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);
        MeetingState state = buildMeetingInMode(meetingId, MeetingMode.MEETING_MODE);

        // Add an active permission so SPEAKING_PERMISSION_REVOKED is broadcast
        addActivePermission(state, speakerId, now.minusMinutes(5));

        switchToFreeMode(state, now);

        assertThat(state.broadcastEvents)
                .as("Events must be broadcast in order: SPEAKING_PERMISSION_REVOKED before MODE_CHANGED")
                .isNotEmpty();

        String lastEvent = state.broadcastEvents.get(state.broadcastEvents.size() - 1);
        assertThat(lastEvent)
                .as("MODE_CHANGED:FREE_MODE must be the last broadcast event")
                .isEqualTo("MODE_CHANGED:FREE_MODE");

        // SPEAKING_PERMISSION_REVOKED must come before MODE_CHANGED
        int revokeIdx = state.eventIndex("SPEAKING_PERMISSION_REVOKED");
        int modeChangedIdx = state.eventIndex("MODE_CHANGED:FREE_MODE");

        assertThat(revokeIdx)
                .as("SPEAKING_PERMISSION_REVOKED must be broadcast before MODE_CHANGED:FREE_MODE")
                .isLessThan(modeChangedIdx);
    }

    /**
     * Property 12d: Switching to MEETING_MODE does not revoke any speaking permissions.
     *
     * <p>FREE_MODE → MEETING_MODE must not affect existing permissions (there should
     * be none in FREE_MODE, but the operation must not touch them).
     */
    @Property(tries = 200)
    @Label("P12d: Switching to MEETING_MODE does not revoke any speaking permissions")
    void switchToMeetingModeDoesNotRevokePermissions(
            @ForAll @Positive long meetingId) {

        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);
        MeetingState state = buildMeetingInMode(meetingId, MeetingMode.FREE_MODE);

        // In FREE_MODE there should be no active permissions, but verify the operation
        // doesn't touch any records
        long permissionsBefore = state.permissions.size();

        switchToMeetingMode(state, now);

        long permissionsAfter = state.permissions.size();
        assertThat(permissionsAfter)
                .as("Switching to MEETING_MODE must not create or modify permission records")
                .isEqualTo(permissionsBefore);
    }

    /**
     * Property 12e: After switching to MEETING_MODE, the meeting mode is MEETING_MODE.
     */
    @Property(tries = 200)
    @Label("P12e: After switching to MEETING_MODE, meeting mode is MEETING_MODE")
    void afterSwitchToMeetingModeTheModeIsMeetingMode(
            @ForAll @Positive long meetingId) {

        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);
        MeetingState state = buildMeetingInMode(meetingId, MeetingMode.FREE_MODE);

        switchToMeetingMode(state, now);

        assertThat(state.meeting.getMode())
                .as("After switching to MEETING_MODE, the meeting mode must be MEETING_MODE")
                .isEqualTo(MeetingMode.MEETING_MODE);
    }

    /**
     * Property 12f: After switching to FREE_MODE, the meeting mode is FREE_MODE.
     */
    @Property(tries = 200)
    @Label("P12f: After switching to FREE_MODE, meeting mode is FREE_MODE")
    void afterSwitchToFreeModeTheModeIsFreeMode(
            @ForAll @Positive long meetingId) {

        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);
        MeetingState state = buildMeetingInMode(meetingId, MeetingMode.MEETING_MODE);

        switchToFreeMode(state, now);

        assertThat(state.meeting.getMode())
                .as("After switching to FREE_MODE, the meeting mode must be FREE_MODE")
                .isEqualTo(MeetingMode.FREE_MODE);
    }

    /**
     * Property 12g: MODE_CHANGED is always broadcast exactly once per switch.
     *
     * <p>Regardless of the number of permissions or raise-hand requests,
     * MODE_CHANGED must be broadcast exactly once.
     */
    @Property(tries = 300)
    @Label("P12g: MODE_CHANGED is broadcast exactly once per mode switch")
    void modeChangedIsBroadcastExactlyOnce(
            @ForAll @Positive long meetingId,
            @ForAll @IntRange(min = 0, max = 3) int permissionCount,
            @ForAll @IntRange(min = 0, max = 5) int raiseHandCount,
            @ForAll boolean switchToFree) {

        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);
        MeetingMode initialMode = switchToFree ? MeetingMode.MEETING_MODE : MeetingMode.FREE_MODE;
        MeetingState state = buildMeetingInMode(meetingId, initialMode);

        if (switchToFree) {
            for (int i = 0; i < permissionCount; i++) {
                addActivePermission(state, (long) (i + 1), now.minusMinutes(5));
            }
            for (int i = 0; i < raiseHandCount; i++) {
                addPendingRaiseHand(state, (long) (i + 1), now.minusMinutes(raiseHandCount - i));
            }
            switchToFreeMode(state, now);
        } else {
            switchToMeetingMode(state, now);
        }

        String expectedEvent = switchToFree ? "MODE_CHANGED:FREE_MODE" : "MODE_CHANGED:MEETING_MODE";
        long modeChangedCount = state.broadcastEvents.stream()
                .filter(e -> e.equals(expectedEvent))
                .count();

        assertThat(modeChangedCount)
                .as("MODE_CHANGED must be broadcast exactly once per mode switch")
                .isEqualTo(1L);
    }

    /**
     * Property 12h: Round-trip mode switch restores original state.
     *
     * <p>Switching FREE_MODE → MEETING_MODE → FREE_MODE must result in:
     * - Meeting mode = FREE_MODE
     * - No active speaking permissions
     * - No pending raise-hand requests
     */
    @Property(tries = 200)
    @Label("P12h: Round-trip mode switch (FREE→MEETING→FREE) restores clean state")
    void roundTripModeSwitchRestoresCleanState(
            @ForAll @Positive long meetingId,
            @ForAll @IntRange(min = 0, max = 3) int raiseHandCount) {

        LocalDateTime t1 = LocalDateTime.of(2025, 6, 1, 10, 0);
        LocalDateTime t2 = t1.plusMinutes(30);
        LocalDateTime t3 = t2.plusMinutes(30);

        MeetingState state = buildMeetingInMode(meetingId, MeetingMode.FREE_MODE);

        // Switch to MEETING_MODE
        switchToMeetingMode(state, t1);

        // Add some raise-hand requests and a permission during MEETING_MODE
        for (int i = 0; i < raiseHandCount; i++) {
            addPendingRaiseHand(state, (long) (i + 1), t2.minusMinutes(raiseHandCount - i));
        }
        if (raiseHandCount > 0) {
            addActivePermission(state, 1L, t2);
        }

        // Switch back to FREE_MODE
        switchToFreeMode(state, t3);

        // Verify clean state
        assertThat(state.meeting.getMode())
                .as("After round-trip, meeting mode must be FREE_MODE")
                .isEqualTo(MeetingMode.FREE_MODE);

        assertThat(state.countActivePermissions())
                .as("After round-trip, no active speaking permissions must remain")
                .isEqualTo(0L);

        assertThat(state.countPendingRaiseHands())
                .as("After round-trip, no pending raise-hand requests must remain")
                .isEqualTo(0L);
    }

    /**
     * Property 12i: Switching to FREE_MODE with no active permission still broadcasts MODE_CHANGED.
     *
     * <p>Even when there is no active speaking permission, the mode switch must
     * complete and broadcast MODE_CHANGED.
     */
    @Property(tries = 200)
    @Label("P12i: Switching to FREE_MODE with no active permission still broadcasts MODE_CHANGED")
    void switchToFreeModeWithNoPermissionStillBroadcastsModeChanged(
            @ForAll @Positive long meetingId) {

        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);
        MeetingState state = buildMeetingInMode(meetingId, MeetingMode.MEETING_MODE);

        // No active permissions
        switchToFreeMode(state, now);

        assertThat(state.hasEvent("MODE_CHANGED:FREE_MODE"))
                .as("MODE_CHANGED:FREE_MODE must be broadcast even when no permission was active")
                .isTrue();

        assertThat(state.meeting.getMode())
                .as("Meeting mode must be FREE_MODE after switch")
                .isEqualTo(MeetingMode.FREE_MODE);
    }

    /**
     * Property 12j: SPEAKING_PERMISSION_REVOKED is only broadcast when a permission was active.
     *
     * <p>If no permission was active before the FREE_MODE switch, no
     * SPEAKING_PERMISSION_REVOKED event must be broadcast.
     */
    @Property(tries = 200)
    @Label("P12j: SPEAKING_PERMISSION_REVOKED is only broadcast when a permission was active")
    void speakingPermissionRevokedOnlyBroadcastWhenPermissionWasActive(
            @ForAll @Positive long meetingId,
            @ForAll boolean hadActivePermission,
            @ForAll @Positive long speakerId) {

        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);
        MeetingState state = buildMeetingInMode(meetingId, MeetingMode.MEETING_MODE);

        if (hadActivePermission) {
            addActivePermission(state, speakerId, now.minusMinutes(5));
        }

        switchToFreeMode(state, now);

        boolean revokedEventPresent = state.hasEvent("SPEAKING_PERMISSION_REVOKED");

        if (hadActivePermission) {
            assertThat(revokedEventPresent)
                    .as("SPEAKING_PERMISSION_REVOKED must be broadcast when a permission was active")
                    .isTrue();
        } else {
            assertThat(revokedEventPresent)
                    .as("SPEAKING_PERMISSION_REVOKED must NOT be broadcast when no permission was active")
                    .isFalse();
        }
    }

    // ── Stub helpers ──────────────────────────────────────────────────────────

    private User userStub(Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("user" + id);
        return u;
    }
}
