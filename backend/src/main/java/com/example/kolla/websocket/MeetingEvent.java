package com.example.kolla.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

/**
 * Generic WebSocket event envelope sent to STOMP topics.
 *
 * <p>Serialized as:
 * <pre>{@code
 * {
 *   "type": "MEETING_STARTED",
 *   "meetingId": 123,
 *   "timestamp": "2025-01-01T10:00:00+07:00",
 *   "payload": { ... }
 * }
 * }</pre>
 *
 * Requirements: 10.2–10.4
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeetingEvent {

    /** The event type discriminator. */
    private MeetingEventType type;

    /** The meeting this event belongs to. */
    private Long meetingId;

    /** ISO-8601 timestamp with UTC+7 offset. */
    @Builder.Default
    private ZonedDateTime timestamp = ZonedDateTime.now(
            java.time.ZoneId.of("Asia/Ho_Chi_Minh"));

    /**
     * Event-specific payload object.
     * Serialized inline as a nested JSON object.
     */
    private Object payload;
}
