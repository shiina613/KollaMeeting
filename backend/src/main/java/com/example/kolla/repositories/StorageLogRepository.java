package com.example.kolla.repositories;

import com.example.kolla.models.StorageLog;
import com.example.kolla.runtime.RuntimeMeetingStateStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StorageLogRepository {
    private final RuntimeMeetingStateStore store;

    public StorageLog save(StorageLog storageLog) {
        return store.saveStorageLog(storageLog);
    }
}
