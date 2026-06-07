package com.example.kolla.models;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceLog {
    private Long id;
    private Meeting meeting;
    private User user;
    private LocalDateTime joinTime;
    private LocalDateTime leaveTime;
    private Long durationSeconds;
    private String ipAddress;
    private String deviceInfo;
}
