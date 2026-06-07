package com.example.kolla.repositories;

import com.example.kolla.models.Minutes;
import com.example.kolla.runtime.RuntimeMeetingStateStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MinutesRepository {
    private final RuntimeMeetingStateStore store;

    public Optional<Minutes> findByMeetingId(Long meetingId) {
        return store.findMinutesByMeetingId(meetingId);
    }

    public boolean existsByMeetingId(Long meetingId) {
        return store.minutesExists(meetingId);
    }

    public Minutes save(Minutes minutes) {
        return store.saveMinutes(minutes);
    }

    public List<Minutes> findDraftMinutesNeedingReminder(LocalDateTime cutoff) {
        return store.findDraftMinutesNeedingReminder(cutoff);
    }
}
