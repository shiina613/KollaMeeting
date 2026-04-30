package com.example.kolla.models;

import com.example.kolla.enums.RaiseHandStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for the {@code raise_hand_request} table.
 *
 * <p>Records a participant's request to speak in Meeting_Mode.
 * Requests are ordered chronologically so the Host can grant permission
 * in the order they were received.
 *
 * Requirements: 22.1–22.11
 */
@Entity
@Table(name = "raise_hand_request",
        indexes = {
                @Index(name = "idx_rhr_meeting_pending",
                        columnList = "meeting_id, status, requested_at")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaiseHandRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Timestamp when the participant raised their hand. */
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private RaiseHandStatus status = RaiseHandStatus.PENDING;

    /** Timestamp when the request was resolved (granted, cancelled, or expired). */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
