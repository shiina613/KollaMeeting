package com.example.kolla.repositories;

import com.example.kolla.models.SpeakingPermission;
import com.example.kolla.runtime.RuntimeMeetingStateStore;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpeakingPermissionRepository {
    private final RuntimeMeetingStateStore store;

    public Optional<SpeakingPermission> findActivePermissionForUpdate(Long meetingId) {
        return store.findActiveSpeakingPermission(meetingId);
    }

    public Optional<SpeakingPermission> findActivePermission(Long meetingId) {
        return store.findActiveSpeakingPermission(meetingId);
    }

    public boolean hasActivePermission(Long meetingId, Long userId) {
        return store.hasActiveSpeakingPermission(meetingId, userId);
    }

    public int revokeAllForMeeting(Long meetingId, LocalDateTime revokedAt) {
        return store.revokeAllSpeakingPermissions(meetingId, revokedAt);
    }

    public SpeakingPermission save(SpeakingPermission permission) {
        return store.saveSpeakingPermission(permission);
    }
}
