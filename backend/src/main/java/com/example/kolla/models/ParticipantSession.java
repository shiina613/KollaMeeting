package com.example.kolla.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for the {@code participant_session} table.
 *
 * <p>Tracks which users are currently connected to an active meeting via WebSocket.
 * Used by {@code HeartbeatMonitor} to detect disconnections and trigger fallback logic.
 *
 * Requirements: 5.3–5.5
 */
@Entity
@Table(name = "participant_session",
        indexes = {
                @Index(name = "idx_ps_meeting_connected",
                        columnList = "meeting_id, is_connected")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** STOMP/WebSocket session ID assigned by Spring. */
    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    /** Updated on each heartbeat frame received from the client. */
    @Column(name = "last_heartbeat_at", nullable = false)
    private LocalDateTime lastHeartbeatAt;

    /** {@code true} while the WebSocket session is alive. */
    @Column(name = "is_connected", nullable = false)
    @Builder.Default
    private boolean isConnected = true;
}
