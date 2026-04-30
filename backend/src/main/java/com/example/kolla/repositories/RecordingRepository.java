package com.example.kolla.repositories;

import com.example.kolla.models.Recording;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Recording entities.
 * Requirements: 7.1–7.7
 */
@Repository
public interface RecordingRepository extends JpaRepository<Recording, Long> {

    /**
     * Find all recordings for a meeting, ordered by start time descending.
     */
    List<Recording> findByMeetingIdOrderByStartTimeDesc(Long meetingId);
}
