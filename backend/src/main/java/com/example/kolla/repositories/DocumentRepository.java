package com.example.kolla.repositories;

import com.example.kolla.models.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Document entities.
 * Requirements: 9.1–9.7
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByMeetingIdOrderByUploadedAtDesc(Long meetingId);
}
