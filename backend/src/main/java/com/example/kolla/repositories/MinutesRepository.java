package com.example.kolla.repositories;

import com.example.kolla.enums.MinutesStatus;
import com.example.kolla.models.Minutes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Minutes}.
 * Requirements: 25.1–25.7
 */
@Repository
public interface MinutesRepository extends JpaRepository<Minutes, Long> {

    /**
     * Find the minutes record for a specific meeting.
     * Returns empty if no minutes have been generated yet.
     */
    Optional<Minutes> findByMeetingId(Long meetingId);

    /**
     * Check whether minutes already exist for a meeting.
     */
    boolean existsByMeetingId(Long meetingId);

    /**
     * Find all DRAFT minutes where the meeting ended more than {@code cutoff} ago
     * and no reminder has been sent yet.
     * Used by the 24-hour reminder scheduler.
     * Requirements: 25.7
     */
    @Query("""
            SELECT m FROM Minutes m
            WHERE m.status = com.example.kolla.enums.MinutesStatus.DRAFT
              AND m.reminderSentAt IS NULL
              AND m.createdAt <= :cutoff
            """)
    List<Minutes> findDraftMinutesNeedingReminder(@Param("cutoff") LocalDateTime cutoff);
}
