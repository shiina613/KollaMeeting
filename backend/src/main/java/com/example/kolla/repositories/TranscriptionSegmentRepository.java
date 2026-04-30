package com.example.kolla.repositories;

import com.example.kolla.models.TranscriptionSegment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link TranscriptionSegment}.
 * Requirements: 8.12, 8.13, 13.4–13.7, 25.1
 */
@Repository
public interface TranscriptionSegmentRepository extends JpaRepository<TranscriptionSegment, Long> {

    /**
     * Check idempotency: does a segment already exist for this job?
     * Requirements: 8.12
     */
    Optional<TranscriptionSegment> findByJobId(String jobId);

    /**
     * Retrieve all segments for a meeting ordered for minutes assembly.
     * Sort by (speakerTurnId, sequenceNumber) to reconstruct chronological order.
     * Requirements: 25.1
     */
    @Query("SELECT s FROM TranscriptionSegment s " +
           "WHERE s.meeting.id = :meetingId " +
           "ORDER BY s.speakerTurnId, s.sequenceNumber")
    List<TranscriptionSegment> findByMeetingIdOrderedForMinutes(@Param("meetingId") Long meetingId);

    /**
     * Full-text search within transcription segments for a meeting.
     * Requirements: 13.4
     */
    @Query("SELECT s FROM TranscriptionSegment s " +
           "WHERE s.meeting.id = :meetingId AND LOWER(s.text) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<TranscriptionSegment> searchByMeetingIdAndText(
            @Param("meetingId") Long meetingId,
            @Param("query") String query);

    // ── Paginated search methods for SearchService ────────────────────────────

    /**
     * Paginated full-text search across all segments, optionally filtered by meeting.
     * Requirements: 13.4–13.7
     */
    Page<TranscriptionSegment> findByTextContainingIgnoreCase(String keyword, Pageable pageable);

    /**
     * Paginated full-text search within a specific meeting's segments.
     * Requirements: 13.4–13.7
     */
    Page<TranscriptionSegment> findByTextContainingIgnoreCaseAndMeetingId(
            String keyword, Long meetingId, Pageable pageable);
}
