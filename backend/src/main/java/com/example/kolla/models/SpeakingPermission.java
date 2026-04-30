package com.example.kolla.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for the {@code speaking_permission} table.
 *
 * <p>Tracks which participant currently holds (or previously held) the exclusive
 * right to speak in Meeting_Mode. At any given time, at most one row per meeting
 * should have {@code revokedAt == null}.
 *
 * <p>A new {@code speakerTurnId} (UUID) is generated each time permission is granted,
 * so audio chunks can be correlated to a specific speaking turn.
 *
 * Requirements: 22.4, 22.8
 */
@Entity
@Table(name = "speaking_permission",
        indexes = {
                @Index(name = "idx_sp_meeting_active",
                        columnList = "meeting_id, revoked_at")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakingPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Timestamp when permission was granted. */
    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    /** Timestamp when permission was revoked; {@code null} while still active. */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /**
     * UUID identifying this specific speaking turn.
     * A new UUID is created each time permission is granted.
     * Used to correlate audio chunks and transcription segments.
     */
    @Column(name = "speaker_turn_id", nullable = false, length = 36)
    private String speakerTurnId;
}
