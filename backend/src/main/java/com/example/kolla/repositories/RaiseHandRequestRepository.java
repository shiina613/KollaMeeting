package com.example.kolla.repositories;

import com.example.kolla.enums.RaiseHandStatus;
import com.example.kolla.models.RaiseHandRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link RaiseHandRequest} entities.
 * Requirements: 22.1–22.11
 */
@Repository
public interface RaiseHandRequestRepository extends JpaRepository<RaiseHandRequest, Long> {

    /**
     * Find all PENDING raise-hand requests for a meeting, ordered chronologically.
     * Used by the Host to see the queue in order.
     * Requirements: 22.9
     */
    @Query("""
            SELECT r FROM RaiseHandRequest r
            WHERE r.meeting.id = :meetingId
              AND r.status = com.example.kolla.enums.RaiseHandStatus.PENDING
            ORDER BY r.requestedAt ASC
            """)
    List<RaiseHandRequest> findPendingByMeetingIdOrderByRequestedAt(
            @Param("meetingId") Long meetingId);

    /**
     * Find the active (PENDING) raise-hand request for a specific user in a meeting.
     */
    @Query("""
            SELECT r FROM RaiseHandRequest r
            WHERE r.meeting.id = :meetingId
              AND r.user.id = :userId
              AND r.status = com.example.kolla.enums.RaiseHandStatus.PENDING
            """)
    Optional<RaiseHandRequest> findPendingByMeetingIdAndUserId(
            @Param("meetingId") Long meetingId,
            @Param("userId") Long userId);

    /**
     * Check whether a user has a pending raise-hand request in a meeting.
     */
    @Query("""
            SELECT COUNT(r) > 0 FROM RaiseHandRequest r
            WHERE r.meeting.id = :meetingId
              AND r.user.id = :userId
              AND r.status = com.example.kolla.enums.RaiseHandStatus.PENDING
            """)
    boolean hasPendingRequest(@Param("meetingId") Long meetingId, @Param("userId") Long userId);

    /**
     * Expire all PENDING requests for a meeting (used when meeting ends or mode switches).
     * Requirements: 21.3
     */
    @Modifying
    @Query("""
            UPDATE RaiseHandRequest r
            SET r.status = com.example.kolla.enums.RaiseHandStatus.EXPIRED,
                r.resolvedAt = :resolvedAt
            WHERE r.meeting.id = :meetingId
              AND r.status = com.example.kolla.enums.RaiseHandStatus.PENDING
            """)
    int expireAllPendingForMeeting(@Param("meetingId") Long meetingId,
                                    @Param("resolvedAt") LocalDateTime resolvedAt);
}
