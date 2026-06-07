package com.example.kolla.repositories;

import com.example.kolla.models.TranscriptionSegment;
import com.example.kolla.runtime.RuntimeMeetingStateStore;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TranscriptionSegmentRepository {
    private final RuntimeMeetingStateStore store;

    public TranscriptionSegment save(TranscriptionSegment segment) {
        return store.saveTranscriptionSegment(segment);
    }

    public Optional<TranscriptionSegment> findByJobId(String jobId) {
        return store.findSegmentByJobId(jobId);
    }

    public List<TranscriptionSegment> findByMeetingIdOrderedForMinutes(Long meetingId) {
        return store.findSegmentsByMeetingId(meetingId);
    }

    public List<TranscriptionSegment> searchByMeetingIdAndText(Long meetingId, String query) {
        return store.searchSegments(query, meetingId, Pageable.unpaged()).getContent();
    }

    public Page<TranscriptionSegment> findByTextContainingIgnoreCase(String keyword, Pageable pageable) {
        return store.searchSegments(keyword, null, pageable);
    }

    public Page<TranscriptionSegment> findByTextContainingIgnoreCaseAndMeetingId(
            String keyword, Long meetingId, Pageable pageable) {
        return store.searchSegments(keyword, meetingId, pageable);
    }
}
