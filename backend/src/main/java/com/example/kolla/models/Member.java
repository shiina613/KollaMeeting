package com.example.kolla.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for the `member` table (meeting ↔ user many-to-many).
 * Requirements: 3.9
 */
@Entity
@Table(name = "member",
        uniqueConstraints = @UniqueConstraint(columnNames = {"meeting_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "added_at", nullable = false)
    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();
}
