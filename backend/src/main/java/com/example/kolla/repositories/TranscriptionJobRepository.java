package com.example.kolla.repositories;

import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.models.TranscriptionJob;
import com.example.kolla.runtime.RuntimeMeetingStateStore;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TranscriptionJobRepository {
    private final RuntimeMeetingStateStore store;

    public TranscriptionJob save(TranscriptionJob job) {
        return store.saveTranscriptionJob(job);
    }

    public Optional<TranscriptionJob> findById(String jobId) {
        return store.findTranscriptionJob(jobId);
    }

    public List<TranscriptionJob> findByStatus(TranscriptionJobStatus status) {
        return store.findTranscriptionJobsByStatus(status);
    }

    public List<TranscriptionJob> findByMeetingIdAndStatus(Long meetingId, TranscriptionJobStatus status) {
        return store.findTranscriptionJobsByMeetingId(meetingId).stream()
                .filter(job -> job.getStatus() == status)
                .toList();
    }

    public long countByMeeting_IdAndStatus(Long meetingId, TranscriptionJobStatus status) {
        return findByMeetingIdAndStatus(meetingId, status).size();
    }

    public List<TranscriptionJob> findByMeetingIdOrdered(Long meetingId) {
        return store.findTranscriptionJobsByMeetingId(meetingId);
    }
}
