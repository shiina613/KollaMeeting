package com.example.kolla.models;

import com.example.kolla.enums.MeetingRole;
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
@Table(name = "member")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "User_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "MeetingRole", nullable = false)
    @Builder.Default
    private MeetingRole meetingRole = MeetingRole.MEMBER;

    @Transient
    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();
}
