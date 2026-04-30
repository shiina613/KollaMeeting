package com.example.kolla.responses;

import com.example.kolla.models.AttendanceLog;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for AttendanceLog data.
 * Requirements: 5.1–5.8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttendanceLogResponse {

    private Long id;
    private Long meetingId;
    private Long userId;
    private String userFullName;
    private String username;
    private LocalDateTime joinTime;
    private LocalDateTime leaveTime;
    /** Duration in seconds; null if the user has not yet left. */
    private Long durationSeconds;
    private String ipAddress;
    private String deviceInfo;

    /** Convenience factory from entity. */
    public static AttendanceLogResponse from(AttendanceLog log) {
        AttendanceLogResponseBuilder builder = AttendanceLogResponse.builder()
                .id(log.getId())
                .joinTime(log.getJoinTime())
                .leaveTime(log.getLeaveTime())
                .durationSeconds(log.getDurationSeconds())
                .ipAddress(log.getIpAddress())
                .deviceInfo(log.getDeviceInfo());

        if (log.getMeeting() != null) {
            builder.meetingId(log.getMeeting().getId());
        }
        if (log.getUser() != null) {
            builder.userId(log.getUser().getId())
                   .userFullName(log.getUser().getFullName())
                   .username(log.getUser().getUsername());
        }

        return builder.build();
    }
}
