package com.example.kolla.repositories;

import com.example.kolla.models.ParticipantSession;
import com.example.kolla.runtime.RuntimeMeetingStateStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ParticipantSessionRepository {
    private final RuntimeMeetingStateStore store;

    public ParticipantSession save(ParticipantSession session) {
        return store.saveSession(session);
    }

    public Optional<ParticipantSession> findActiveSession(Long meetingId, Long userId) {
        return store.findActiveSession(meetingId, userId);
    }

    public Optional<ParticipantSession> findBySessionId(String sessionId) {
        return store.findSessionBySessionId(sessionId);
    }

    public List<ParticipantSession> findByMeetingIdAndIsConnectedTrue(Long meetingId) {
        return store.findStaleConnectedSessions(LocalDateTime.MAX).stream()
                .filter(session -> session.getMeeting() != null
                        && meetingId.equals(session.getMeeting().getId()))
                .toList();
    }

    public List<ParticipantSession> findStaleConnectedSessions(LocalDateTime threshold) {
        return store.findStaleConnectedSessions(threshold);
    }

    public boolean isUserConnected(Long meetingId, Long userId) {
        return store.isUserConnected(meetingId, userId);
    }

    public void disconnectAllInMeeting(Long meetingId) {
        findByMeetingIdAndIsConnectedTrue(meetingId).forEach(session -> {
            session.setConnected(false);
            store.saveSession(session);
        });
    }
}
