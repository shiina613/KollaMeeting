package com.example.kolla.enums;

/**
 * Status of a raise-hand request in Meeting_Mode.
 * Requirements: 22.1–22.11
 */
public enum RaiseHandStatus {
    /** Request is waiting for Host action. */
    PENDING,
    /** Host granted speaking permission. */
    GRANTED,
    /** Participant cancelled their own request. */
    CANCELLED,
    /** Request expired (e.g., participant left or meeting ended). */
    EXPIRED
}
