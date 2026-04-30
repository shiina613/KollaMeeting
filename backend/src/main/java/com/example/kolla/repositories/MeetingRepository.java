package com.example.kolla.repositories;

import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.models.Meeting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Meeting entities.
 * Requirements: 3.1–3.12, 13.1–13.3
 */
@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long>, JpaSpecificationExecutor<Meeting> {

    Optional<Meeting> findByCode(String code);

    boolean existsByCode(String code);

    // ── Filtered list queries ─────────────────────────────────────────────────

    /**
     * Paginated list of meetings with optional filters.
     * All filter parameters are optional (null = no filter).
     * Requirements: 3.4, 13.2
     */
    @Query("""
            SELECT m FROM Meeting m
            WHERE (:status IS NULL OR m.status = :status)
              AND (:roomId IS NULL OR m.room.id = :roomId)
              AND (:creatorId IS NULL OR m.creator.id = :creatorId)
              AND (:startFrom IS NULL OR m.startTime >= :startFrom)
              AND (:startTo IS NULL OR m.startTime <= :startTo)
            ORDER BY m.startTime DESC
            """)
    Page<Meeting> findAllFiltered(
            @Param("status") MeetingStatus status,
            @Param("roomId") Long roomId,
            @Param("creatorId") Long creatorId,
            @Param("startFrom") LocalDateTime startFrom,
            @Param("startTo") LocalDateTime startTo,
            Pageable pageable);

    /**
     * Meetings where the user is a member (paginated).
     * Requirements: 3.4
     */
    @Query("""
            SELECT m FROM Meeting m
            JOIN Member mb ON mb.meeting.id = m.id
            WHERE mb.user.id = :userId
              AND (:status IS NULL OR m.status = :status)
            ORDER BY m.startTime DESC
            """)
    Page<Meeting> findByMemberUserId(
            @Param("userId") Long userId,
            @Param("status") MeetingStatus status,
            Pageable pageable);

    // ── Scheduling conflict detection ─────────────────────────────────────────

    /**
     * Find meetings in the same room that overlap with the given time range,
     * excluding a specific meeting ID (used during updates).
     * Overlap condition: existing.startTime < newEndTime AND existing.endTime > newStartTime
     * Requirements: 3.12
     */
    @Query("""
            SELECT m FROM Meeting m
            WHERE m.room.id = :roomId
              AND m.status IN :statuses
              AND m.startTime < :endTime
              AND m.endTime > :startTime
              AND (:excludeId IS NULL OR m.id <> :excludeId)
            """)
    List<Meeting> findConflictingMeetings(
            @Param("roomId") Long roomId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("statuses") List<MeetingStatus> statuses,
            @Param("excludeId") Long excludeId);

    // ── Lifecycle queries ─────────────────────────────────────────────────────

    /**
     * Find all ACTIVE meetings where neither host nor secretary is currently connected.
     * Used by the waiting timeout monitor.
     * Requirements: 3.11
     */
    @Query("""
            SELECT m FROM Meeting m
            WHERE m.status = com.example.kolla.enums.MeetingStatus.ACTIVE
              AND m.waitingTimeoutAt IS NOT NULL
              AND m.waitingTimeoutAt <= :now
            """)
    List<Meeting> findMeetingsWithExpiredWaitingTimeout(@Param("now") LocalDateTime now);

    /**
     * Find meetings by status.
     * Requirements: 3.11
     */
    List<Meeting> findByStatus(MeetingStatus status);
}
