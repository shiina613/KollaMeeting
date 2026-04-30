package com.example.kolla.repositories;

import com.example.kolla.models.SpeakingPermission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link SpeakingPermission} entities.
 *
 * <p>Uses {@code SELECT FOR UPDATE} (pessimistic write lock) on the active-permission
 * query to prevent concurrent grants from violating the exclusivity invariant.
 *
 * Requirements: 22.4, 22.8
 */
@Repository
public interface SpeakingPermissionRepository extends JpaRepository<SpeakingPermission, Long> {

    /**
     * Find the currently active (not yet revoked) speaking permission for a meeting.
     * Uses a pessimistic write lock to prevent concurrent grants.
     * Requirements: 22.4, 22.8
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT sp FROM SpeakingPermission sp
            WHERE sp.meeting.id = :meetingId
              AND sp.revokedAt IS NULL
            """)
    Optional<SpeakingPermission> findActivePermissionForUpdate(@Param("meetingId") Long meetingId);

    /**
     * Find the currently active speaking permission for a meeting (read-only, no lock).
     * Use this for read-only queries where locking is not needed.
     */
    @Query("""
            SELECT sp FROM SpeakingPermission sp
            WHERE sp.meeting.id = :meetingId
              AND sp.revokedAt IS NULL
            """)
    Optional<SpeakingPermission> findActivePermission(@Param("meetingId") Long meetingId);

    /**
     * Check whether a specific user currently holds speaking permission in a meeting.
     */
    @Query("""
            SELECT COUNT(sp) > 0 FROM SpeakingPermission sp
            WHERE sp.meeting.id = :meetingId
              AND sp.user.id = :userId
              AND sp.revokedAt IS NULL
            """)
    boolean hasActivePermission(@Param("meetingId") Long meetingId, @Param("userId") Long userId);

    /**
     * Revoke all active permissions for a meeting (used when meeting ends or mode switches).
     * Requirements: 21.3, 22.6
     */
    @Modifying
    @Query("""
            UPDATE SpeakingPermission sp
            SET sp.revokedAt = :revokedAt
            WHERE sp.meeting.id = :meetingId
              AND sp.revokedAt IS NULL
            """)
    int revokeAllForMeeting(@Param("meetingId") Long meetingId,
                             @Param("revokedAt") LocalDateTime revokedAt);
}
