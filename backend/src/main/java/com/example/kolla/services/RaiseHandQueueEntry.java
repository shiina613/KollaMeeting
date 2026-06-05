package com.example.kolla.services;

import java.time.LocalDateTime;

/**
 * In-memory representation of a pending raise-hand request stored in Redis.
 */
public record RaiseHandQueueEntry(
        Long userId,
        String userName,
        LocalDateTime requestedAt
) {
}
