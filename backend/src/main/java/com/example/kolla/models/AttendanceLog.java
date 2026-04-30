package com.example.kolla.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for the `attendance_log` table.
 * Tracks when users join and leave meetings.
 * Requirements: 5.1–5.8
 */
@Entity
@Table(name = "attendance_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "join_time", nullable = false)
    private LocalDateTime joinTime;

    @Column(name = "leave_time")
    private LocalDateTime leaveTime;

    /** Duration in seconds, calculated when leave_time is set. */
    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "device_info", length = 500)
    private String deviceInfo;
}
