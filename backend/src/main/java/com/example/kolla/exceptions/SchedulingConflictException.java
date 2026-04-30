package com.example.kolla.exceptions;

import lombok.Getter;

/**
 * Thrown when a meeting room scheduling conflict is detected.
 * Maps to HTTP 409 Conflict.
 * Requirements: 3.12, 15.1–15.7
 */
@Getter
public class SchedulingConflictException extends RuntimeException {

    /** ID of the existing meeting that conflicts with the requested time slot. */
    private final Long conflictingMeetingId;

    public SchedulingConflictException(String message, Long conflictingMeetingId) {
        super(message);
        this.conflictingMeetingId = conflictingMeetingId;
    }

    public SchedulingConflictException(Long conflictingMeetingId) {
        super("Room is already booked for the requested time period. Conflicting meeting id: "
                + conflictingMeetingId);
        this.conflictingMeetingId = conflictingMeetingId;
    }
}
