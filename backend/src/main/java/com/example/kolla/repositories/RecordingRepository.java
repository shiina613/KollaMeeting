package com.example.kolla.repositories;

import com.example.kolla.models.Recording;
import com.example.kolla.runtime.RuntimeMeetingStateStore;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecordingRepository {
    private final RuntimeMeetingStateStore store;

    public Recording save(Recording recording) {
        return store.saveRecording(recording);
    }

    public Optional<Recording> findById(Long recordingId) {
        return store.findRecordingById(recordingId);
    }

    public List<Recording> findByMeetingIdOrderByStartTimeDesc(Long meetingId) {
        return store.findRecordingsByMeetingId(meetingId);
    }

    public void delete(Recording recording) {
        store.deleteRecording(recording);
    }
}
