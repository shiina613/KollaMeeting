package com.example.kolla.repositories;

import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.models.TranscriptionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link TranscriptionJob}.
 * Requirements: 8.7, 8.10, 8.11
 */
@Repository
public interface TranscriptionJobRepository extends JpaRepository<TranscriptionJob, String> {

    /**
     * Find all jobs with a given status (used for recovery: PENDING → re-queue).
     * Requirements: 8.7
     */
    List<TranscriptionJob> findByStatus(TranscriptionJobStatus status);

    /**
     * Find all PENDING jobs for a specific meeting (used for recovery).
     * Requirements: 8.7
     */
    @Query("SELECT j FROM TranscriptionJob j WHERE j.meeting.id = :meetingId AND j.status = :status")
    List<TranscriptionJob> findByMeetingIdAndStatus(
            @Param("meetingId") Long meetingId,
            @Param("status") TranscriptionJobStatus status);

    /**
     * Count jobs by meeting and status.
     */
    long countByMeeting_IdAndStatus(Long meetingId, TranscriptionJobStatus status);
}
