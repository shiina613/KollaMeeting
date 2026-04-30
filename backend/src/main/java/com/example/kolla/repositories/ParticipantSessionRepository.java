package com.example.kolla.repositories;

import com.example.kolla.models.ParticipantSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ParticipantSession} entities.
 * Requirements: 5.3–5.5
 */
@Repository
public interface ParticipantSessionRepository extends JpaRepository<ParticipantSession, Long> {

    /**
     * Find the active session for a user in a meeting.
     */
    @Query("""
            SELECT ps FROM ParticipantSession ps
            WHERE ps.meeting.id = :meetingId
              AND ps.user.id = :userId
              AND ps.isConnected = true
            ORDER BY ps.joinedAt DESC
            """)
    Optional<ParticipantSession> findActiveSession(@Param("meetingId") Long meetingId,
                                                    @Param("userId") Long userId);

    /**
     * Find a session by its WebSocket session ID.
     */
    Optional<ParticipantSession> findBySessionId(String sessionId);

    /**
     * Find all connected sessions for a meeting.
     */
    List<ParticipantSession> findByMeetingIdAndIsConnectedTrue(Long meetingId);

    /**
     * Find sessions whose last heartbeat is older than the given threshold
     * and are still marked as connected — these are stale/disconnected sessions.
     * Requirements: 5.3
     */
    @Query("""
            SELECT ps FROM ParticipantSession ps
            WHERE ps.isConnected = true
              AND ps.lastHeartbeatAt < :threshold
            """)
    List<ParticipantSession> findStaleConnectedSessions(@Param("threshold") LocalDateTime threshold);

    /**
     * Check whether a specific user is currently connected to a meeting.
     */
    @Query("""
            SELECT COUNT(ps) > 0 FROM ParticipantSession ps
            WHERE ps.meeting.id = :meetingId
              AND ps.user.id = :userId
              AND ps.isConnected = true
            """)
    boolean isUserConnected(@Param("meetingId") Long meetingId, @Param("userId") Long userId);

    /**
     * Mark all sessions for a meeting as disconnected (used when meeting ends).
     */
    @Modifying
    @Query("""
            UPDATE ParticipantSession ps
            SET ps.isConnected = false
            WHERE ps.meeting.id = :meetingId
              AND ps.isConnected = true
            """)
    void disconnectAllInMeeting(@Param("meetingId") Long meetingId);
}
