package com.example.kolla.services;

import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.SpeakingPermission;
import com.example.kolla.models.User;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Positive;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for concurrent grant safety.
 *
 * <p>Property 2: Concurrent Grant Safety
 *
 * <p>These tests verify that the speaking permission system correctly handles
 * concurrent grant requests, ensuring the exclusivity invariant holds even
 * under race conditions.
 *
 * <p>The tests model the concurrent grant scenario using an in-memory
 * {@link ConcurrentPermissionStore} that simulates the pessimistic locking
 * ({@code SELECT FOR UPDATE}) behavior of the real implementation.
 *
 * <p>Key invariant:
 * <blockquote>
 *   When N concurrent grant requests arrive simultaneously for the same meeting,
 *   exactly one must succeed and at most one participant must hold permission
 *   at any point in time.
 * </blockquote>
 *
 * Requirements: 22.12
 */
class ConcurrentGrantSafetyPropertyTest {

    // ── Concurrent permission store (simulates SELECT FOR UPDATE) ─────────────

    /**
     * Thread-safe in-memory store that simulates the pessimistic locking behavior
     * of {@code SpeakingPermissionRepository.findActivePermissionForUpdate()}.
     *
     * <p>Uses a {@link ReentrantLock} to serialize grant operations, mirroring
     * the database-level {@code SELECT FOR UPDATE} lock.
     */
    static class ConcurrentPermissionStore {

        private final ReentrantLock lock = new ReentrantLock();
        private final List<SpeakingPermission> permissions = new ArrayList<>();
        private final AtomicInteger grantCount = new AtomicInteger(0);
        private final AtomicInteger revokeCount = new AtomicInteger(0);

        /**
         * Grant permission to {@code targetUserId} in {@code meetingId}.
         * Serialized via lock to prevent concurrent grants.
         *
         * @return the new SpeakingPermission, or null if the user already holds it
         */
        SpeakingPermission grantPermission(Long meetingId, Long targetUserId,
                                            LocalDateTime now) {
            lock.lock();
            try {
                // Revoke any existing active permission
                for (SpeakingPermission sp : permissions) {
                    if (sp.getMeeting().getId().equals(meetingId)
                            && sp.getRevokedAt() == null) {
                        if (sp.getUser().getId().equals(targetUserId)) {
                            // Idempotent: already holds permission
                            return sp;
                        }
                        sp.setRevokedAt(now);
                        revokeCount.incrementAndGet();
                    }
                }

                // Grant new permission
                Meeting meeting = buildMeetingStub(meetingId);
                User user = buildUserStub(targetUserId);
                SpeakingPermission newPerm = SpeakingPermission.builder()
                        .id((long) (permissions.size() + 1))
                        .meeting(meeting)
                        .user(user)
                        .grantedAt(now)
                        .revokedAt(null)
                        .speakerTurnId(UUID.randomUUID().toString())
                        .build();
                permissions.add(newPerm);
                grantCount.incrementAndGet();
                return newPerm;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Returns the number of currently active (non-revoked) permissions for a meeting.
         */
        long countActive(Long meetingId) {
            lock.lock();
            try {
                return permissions.stream()
                        .filter(sp -> sp.getMeeting().getId().equals(meetingId))
                        .filter(sp -> sp.getRevokedAt() == null)
                        .count();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Returns the set of user IDs currently holding active permission.
         */
        Set<Long> activeHolders(Long meetingId) {
            lock.lock();
            try {
                return permissions.stream()
                        .filter(sp -> sp.getMeeting().getId().equals(meetingId))
                        .filter(sp -> sp.getRevokedAt() == null)
                        .map(sp -> sp.getUser().getId())
                        .collect(Collectors.toSet());
            } finally {
                lock.unlock();
            }
        }

        List<SpeakingPermission> allPermissions() {
            lock.lock();
            try {
                return Collections.unmodifiableList(new ArrayList<>(permissions));
            } finally {
                lock.unlock();
            }
        }

        int getGrantCount() { return grantCount.get(); }
        int getRevokeCount() { return revokeCount.get(); }

        // ── Static stub helpers (accessible from inner static class) ──────────

        private static Meeting buildMeetingStub(Long id) {
            Meeting m = new Meeting();
            m.setId(id);
            m.setStatus(MeetingStatus.ACTIVE);
            m.setMode(MeetingMode.MEETING_MODE);
            return m;
        }

        private static User buildUserStub(Long id) {
            User u = new User();
            u.setId(id);
            u.setUsername("user" + id);
            return u;
        }
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /**
     * Property 2a: After N concurrent grants to different users, exactly one holds permission.
     *
     * <p>Simulates N threads simultaneously calling grantPermission for different users.
     * After all threads complete, exactly one user must hold the active permission.
     */
    @Property(tries = 50)
    @Label("P2a: After N concurrent grants to different users, exactly one holds permission")
    void afterConcurrentGrantsExactlyOneHoldsPermission(
            @ForAll @IntRange(min = 2, max = 8) int threadCount) throws InterruptedException {

        Long meetingId = 1L;
        ConcurrentPermissionStore store = new ConcurrentPermissionStore();
        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Submit N concurrent grant requests for different users
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1L;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    store.grantPermission(meetingId, userId, now);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads simultaneously
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        long activeCount = store.countActive(meetingId);
        assertThat(activeCount)
                .as("After %d concurrent grants, exactly one permission must be active",
                        threadCount)
                .isEqualTo(1L);
    }

    /**
     * Property 2b: Concurrent grants never produce more than one active permission.
     *
     * <p>Runs multiple rounds of concurrent grants and verifies the invariant
     * holds after each round.
     */
    @Property(tries = 30)
    @Label("P2b: Concurrent grants never produce more than one active permission (multiple rounds)")
    void concurrentGrantsNeverExceedOneActivePermission(
            @ForAll @IntRange(min = 2, max = 6) int threadCount,
            @ForAll @IntRange(min = 2, max = 5) int rounds) throws InterruptedException {

        Long meetingId = 1L;
        ConcurrentPermissionStore store = new ConcurrentPermissionStore();

        for (int round = 0; round < rounds; round++) {
            final int roundOffset = round * threadCount;
            LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0).plusMinutes(round * 10L);

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final long userId = roundOffset + i + 1L;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        store.grantPermission(meetingId, userId, now);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            long activeCount = store.countActive(meetingId);
            assertThat(activeCount)
                    .as("After round %d with %d concurrent grants, active count must be ≤ 1",
                            round + 1, threadCount)
                    .isLessThanOrEqualTo(1L);
        }
    }

    /**
     * Property 2c: All speakerTurnIds generated by concurrent grants are unique.
     *
     * <p>Even under concurrent access, each grant must produce a distinct UUID
     * for the speakerTurnId.
     */
    @Property(tries = 50)
    @Label("P2c: All speakerTurnIds generated by concurrent grants are unique")
    void concurrentGrantsProduceUniqueSpeakerTurnIds(
            @ForAll @IntRange(min = 2, max = 10) int threadCount) throws InterruptedException {

        Long meetingId = 1L;
        ConcurrentPermissionStore store = new ConcurrentPermissionStore();
        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1L;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    store.grantPermission(meetingId, userId, now);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        List<String> turnIds = store.allPermissions().stream()
                .map(SpeakingPermission::getSpeakerTurnId)
                .collect(Collectors.toList());

        Set<String> uniqueTurnIds = new HashSet<>(turnIds);

        assertThat(uniqueTurnIds.size())
                .as("All speakerTurnIds must be unique even under concurrent access; found: %s",
                        turnIds)
                .isEqualTo(turnIds.size());
    }

    /**
     * Property 2d: Concurrent grants to the same user are idempotent.
     *
     * <p>If N threads simultaneously try to grant permission to the same user,
     * the result must still be exactly one active permission.
     */
    @Property(tries = 50)
    @Label("P2d: Concurrent grants to the same user are idempotent")
    void concurrentGrantsToSameUserAreIdempotent(
            @ForAll @IntRange(min = 2, max = 8) int threadCount,
            @ForAll @Positive long targetUserId) throws InterruptedException {

        Long meetingId = 1L;
        ConcurrentPermissionStore store = new ConcurrentPermissionStore();
        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    store.grantPermission(meetingId, targetUserId, now);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        long activeCount = store.countActive(meetingId);
        Set<Long> holders = store.activeHolders(meetingId);

        assertThat(activeCount)
                .as("Concurrent grants to the same user must result in exactly one active permission")
                .isEqualTo(1L);

        assertThat(holders)
                .as("Only the target user must hold the permission")
                .containsExactly(targetUserId);
    }

    /**
     * Property 2e: Concurrent grants across different meetings do not interfere.
     *
     * <p>Granting permissions in meeting A and meeting B concurrently must not
     * cause cross-meeting interference.
     */
    @Property(tries = 50)
    @Label("P2e: Concurrent grants across different meetings do not interfere")
    void concurrentGrantsAcrossMeetingsDoNotInterfere(
            @ForAll @IntRange(min = 2, max = 5) int threadsPerMeeting) throws InterruptedException {

        Long meetingA = 100L;
        Long meetingB = 200L;
        ConcurrentPermissionStore store = new ConcurrentPermissionStore();
        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);

        int totalThreads = threadsPerMeeting * 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);

        // Threads for meeting A
        for (int i = 0; i < threadsPerMeeting; i++) {
            final long userId = i + 1L;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    store.grantPermission(meetingA, userId, now);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Threads for meeting B
        for (int i = 0; i < threadsPerMeeting; i++) {
            final long userId = threadsPerMeeting + i + 1L;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    store.grantPermission(meetingB, userId, now);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        long activeA = store.countActive(meetingA);
        long activeB = store.countActive(meetingB);

        assertThat(activeA)
                .as("Meeting A must have exactly one active permission after concurrent grants")
                .isEqualTo(1L);

        assertThat(activeB)
                .as("Meeting B must have exactly one active permission after concurrent grants")
                .isEqualTo(1L);
    }

    /**
     * Property 2f: Total grant count equals total permission records created.
     *
     * <p>The number of SpeakingPermission records created must equal the number
     * of successful grant operations (no phantom records from race conditions).
     */
    @Property(tries = 50)
    @Label("P2f: Total permission records equals total successful grant operations")
    void totalPermissionRecordsEqualsGrantCount(
            @ForAll @IntRange(min = 2, max = 8) int threadCount) throws InterruptedException {

        Long meetingId = 1L;
        ConcurrentPermissionStore store = new ConcurrentPermissionStore();
        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 10, 0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1L;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    store.grantPermission(meetingId, userId, now);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        int totalRecords = store.allPermissions().size();
        int grantCount = store.getGrantCount();

        assertThat(totalRecords)
                .as("Total permission records (%d) must equal grant count (%d)",
                        totalRecords, grantCount)
                .isEqualTo(grantCount);
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
