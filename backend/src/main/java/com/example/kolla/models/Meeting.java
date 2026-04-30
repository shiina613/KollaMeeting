package com.example.kolla.models;

import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.TranscriptionPriority;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * JPA entity for the `meeting` table.
 * Requirements: 3.1–3.12, 21.1
 */
@Entity
@Table(name = "meeting")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    @Builder.Default
    private MeetingMode mode = MeetingMode.FREE_MODE;

    @Enumerated(EnumType.STRING)
    @Column(name = "transcription_priority", nullable = false)
    @Builder.Default
    private TranscriptionPriority transcriptionPriority = TranscriptionPriority.NORMAL_PRIORITY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_user_id")
    private User host;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "secretary_user_id")
    private User secretary;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "waiting_timeout_at")
    private LocalDateTime waitingTimeoutAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
