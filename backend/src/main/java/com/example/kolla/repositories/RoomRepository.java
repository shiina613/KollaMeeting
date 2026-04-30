package com.example.kolla.repositories;

import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.models.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA repository for Room entities.
 * Requirements: 12.1–12.8
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByDepartmentId(Long departmentId);

    /**
     * Returns true if the room has any SCHEDULED or ACTIVE meetings.
     * Used to prevent deletion of rooms with scheduled meetings.
     * Requirements: 12.7
     */
    @Query("""
            SELECT COUNT(m) > 0
            FROM Meeting m
            WHERE m.room.id = :roomId
              AND m.status IN :statuses
            """)
    boolean hasScheduledOrActiveMeetings(
            @Param("roomId") Long roomId,
            @Param("statuses") List<MeetingStatus> statuses);

    /**
     * Find meetings in a room that overlap with the given time range.
     * Used for room availability checks.
     * Requirements: 3.12, 12.8
     */
    @Query("""
            SELECT m FROM Meeting m
            WHERE m.room.id = :roomId
              AND m.status IN :statuses
              AND m.startTime < :endTime
              AND m.endTime > :startTime
            """)
    List<com.example.kolla.models.Meeting> findOverlappingMeetings(
            @Param("roomId") Long roomId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("statuses") List<MeetingStatus> statuses);
}
