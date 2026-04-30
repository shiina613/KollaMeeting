package com.example.kolla.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP message handler for client-to-server WebSocket messages.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@code /app/meeting/{meetingId}/heartbeat} — keep-alive ping from participants</li>
 *   <li>{@code /app/meeting/{meetingId}/raise-hand} — raise hand request</li>
 *   <li>{@code /app/meeting/{meetingId}/lower-hand} — cancel raise hand</li>
 * </ul>
 *
 * <p>Raise-hand and lower-hand business logic is delegated to the REST controllers
 * (task 6). This controller only handles the WebSocket transport layer.
 *
 * Requirements: 10.1, 5.3
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MeetingWebSocketController {

    private final HeartbeatMonitor heartbeatMonitor;

    /**
     * Receives heartbeat pings from meeting participants.
     * Updates the {@code last_heartbeat_at} timestamp for the session.
     *
     * <p>Client sends: {@code SEND /app/meeting/{meetingId}/heartbeat}
     * Requirements: 5.3
     */
    @MessageMapping("/meeting/{meetingId}/heartbeat")
    public void handleHeartbeat(@DestinationVariable Long meetingId,
                                 Principal principal,
                                 SimpMessageHeaderAccessor headerAccessor) {
        if (principal == null) {
            log.warn("Heartbeat received without authentication for meetingId={}", meetingId);
            return;
        }

        try {
            Long userId = Long.parseLong(principal.getName());
            heartbeatMonitor.recordHeartbeat(meetingId, userId);
        } catch (NumberFormatException e) {
            log.warn("Invalid userId in heartbeat principal: {}", principal.getName());
        }
    }
}
