package com.example.kolla.repositories;

import com.example.kolla.models.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for AttendanceLog entities.
 * Requirements: 5.1–5.8
 */
@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    List<AttendanceLog> findByMeetingId(Long meetingId);

    List<AttendanceLog> findByMeetingIdAndUserId(Long meetingId, Long userId);

    /**
     * Find the most recent open (no leave_time) attendance log for a user in a meeting.
     * Used to update leave time when a user leaves.
     * Requirements: 5.3, 5.4
     */
    @Query("""
            SELECT a FROM AttendanceLog a
            WHERE a.meeting.id = :meetingId
              AND a.user.id = :userId
              AND a.leaveTime IS NULL
            ORDER BY a.joinTime DESC
            """)
    Optional<AttendanceLog> findOpenLog(@Param("meetingId") Long meetingId,
                                        @Param("userId") Long userId);

    /**
     * Find all currently connected participants (no leave_time) for a meeting.
     * Requirements: 5.6
     */
    @Query("""
            SELECT a FROM AttendanceLog a
            WHERE a.meeting.id = :meetingId
              AND a.leaveTime IS NULL
            """)
    List<AttendanceLog> findActiveParticipants(@Param("meetingId") Long meetingId);
}
