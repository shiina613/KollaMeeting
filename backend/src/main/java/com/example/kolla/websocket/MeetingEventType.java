package com.example.kolla.websocket;

/**
 * Enumeration of all WebSocket meeting event types broadcast via STOMP.
 *
 * <p>Events are sent to {@code /topic/meeting/{meetingId}} for broadcast events,
 * or to {@code /user/queue/notifications} for personal notifications.
 *
 * Requirements: 10.2–10.4
 */
public enum MeetingEventType {

    // ── Meeting lifecycle ───────────────────────────────────────────────────
    /** Host activates meeting (SCHEDULED → ACTIVE). */
    MEETING_STARTED,

    /** Meeting transitions to ENDED. */
    MEETING_ENDED,

    // ── Mode switching ──────────────────────────────────────────────────────
    /** Host switches between FREE_MODE and MEETING_MODE. */
    MODE_CHANGED,

    // ── Raise hand / speaking permission ───────────────────────────────────
    /** Participant raises hand to request speaking permission. */
    RAISE_HAND,

    /** Participant lowers hand (cancels request). */
    HAND_LOWERED,

    /** Host grants speaking permission to a participant. */
    SPEAKING_PERMISSION_GRANTED,

    /** Speaking permission revoked (by Host, disconnect, or mode switch). */
    SPEAKING_PERMISSION_REVOKED,

    // ── Participant presence ────────────────────────────────────────────────
    /** User joins the active meeting. */
    PARTICIPANT_JOINED,

    /** User leaves the active meeting. */
    PARTICIPANT_LEFT,

    // ── Host authority ──────────────────────────────────────────────────────
    /** Host authority transferred to another user (e.g. original Host disconnected). */
    HOST_TRANSFERRED,

    /** Original Host reconnects and reclaims authority. */
    HOST_RESTORED,

    // ── Waiting timeout ─────────────────────────────────────────────────────
    /** No Host or Secretary present — 10-minute countdown started. */
    WAITING_TIMEOUT_STARTED,

    /** Host or Secretary reconnected — countdown cancelled. */
    WAITING_TIMEOUT_CANCELLED,

    // ── Transcription ───────────────────────────────────────────────────────
    /** New transcription segment received (HIGH_PRIORITY meetings only). */
    TRANSCRIPTION_SEGMENT,

    /** Meeting transcription priority changed. */
    PRIORITY_CHANGED,

    /** Gipformer service is unavailable. */
    TRANSCRIPTION_UNAVAILABLE,

    /** Gipformer service recovered. */
    TRANSCRIPTION_RECOVERED,

    // ── Documents ───────────────────────────────────────────────────────────
    /** A new document was uploaded to the meeting. */
    DOCUMENT_UPLOADED,

    // ── Minutes ─────────────────────────────────────────────────────────────
    /** Draft minutes have been generated and are ready for Host review. */
    MINUTES_READY,

    /** Host confirmed the minutes (digital stamp applied). */
    MINUTES_CONFIRMED,

    /** Secretary confirmed/edited the minutes — minutes published. */
    MINUTES_PUBLISHED
}
