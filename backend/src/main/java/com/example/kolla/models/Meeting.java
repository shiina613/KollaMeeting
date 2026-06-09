package com.example.kolla.models;

import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.TranscriptionPriority;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "MeetingCode", nullable = false, length = 50)
    private String code;

    @Column(name = "Name", nullable = false, length = 500)
    private String title;

    @Transient
    private String description;

    @Column(name = "DepartmentId", nullable = false)
    private Long departmentId;

    @Column(name = "StartTime", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "Endtime", nullable = false)
    private LocalDateTime endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Room_id", nullable = false)
    private Room room;

    @Transient
    private User creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false)
    @Builder.Default
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(name = "Mode", nullable = false)
    @Builder.Default
    private MeetingMode mode = MeetingMode.FREE_MODE;

    @Enumerated(EnumType.STRING)
    @Column(name = "TranscriptionPriority", nullable = false)
    @Builder.Default
    private TranscriptionPriority transcriptionPriority = TranscriptionPriority.NORMAL_PRIORITY;

    @Transient
    private User host;

    @Transient
    private User secretary;

    @Transient
    private LocalDateTime activatedAt;

    @Transient
    private LocalDateTime endedAt;

    @Transient
    private LocalDateTime waitingTimeoutAt;

    @Transient
    private LocalDateTime createdAt;

    @Transient
    private LocalDateTime updatedAt;
}
