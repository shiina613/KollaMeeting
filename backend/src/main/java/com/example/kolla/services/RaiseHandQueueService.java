package com.example.kolla.services;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ephemeral raise-hand queue backed by Redis (ZSET + HASH).
 * Requirements: 22.1–22.11
 */
public interface RaiseHandQueueService {

    void enqueue(Long meetingId, Long userId, String userName, LocalDateTime requestedAt);

    boolean hasPending(Long meetingId, Long userId);

    void remove(Long meetingId, Long userId);

    List<RaiseHandQueueEntry> listPendingOrdered(Long meetingId);

    void clearAll(Long meetingId);
}
