package com.example.kolla.responses;

import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.models.Meeting;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for Meeting data.
 * Requirements: 3.1–3.12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeetingResponse {

    private Long id;
    private String code;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private MeetingStatus status;
    private MeetingMode mode;
    private TranscriptionPriority transcriptionPriority;

    // Room info
    private Long roomId;
    private String roomName;

    // Creator info
    private Long creatorId;
    private String creatorName;

    // Host info
    private Long hostId;
    private String hostName;
    private String hostDepartmentName;

    // Secretary info
    private Long secretaryId;
    private String secretaryName;
    private String secretaryDepartmentName;

    private LocalDateTime activatedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Convenience factory from entity (no department names). */
    public static MeetingResponse from(Meeting meeting) {
        return from(meeting, null, null);
    }

    /** Convenience factory from entity with resolved department names. */
    public static MeetingResponse from(Meeting meeting, String hostDepartmentName, String secretaryDepartmentName) {
        MeetingResponse.MeetingResponseBuilder builder = MeetingResponse.builder()
                .id(meeting.getId())
                .code(meeting.getCode())
                .title(meeting.getTitle())
                .description(meeting.getDescription())
                .startTime(meeting.getStartTime())
                .endTime(meeting.getEndTime())
                .status(meeting.getStatus())
                .mode(meeting.getMode())
                .transcriptionPriority(meeting.getTranscriptionPriority())
                .activatedAt(meeting.getActivatedAt())
                .endedAt(meeting.getEndedAt())
                .createdAt(meeting.getCreatedAt())
                .updatedAt(meeting.getUpdatedAt());

        if (meeting.getRoom() != null) {
            builder.roomId(meeting.getRoom().getId())
                   .roomName(meeting.getRoom().getName());
        }
        if (meeting.getCreator() != null) {
            builder.creatorId(meeting.getCreator().getId())
                   .creatorName(meeting.getCreator().getFullName());
        }
        if (meeting.getHost() != null) {
            builder.hostId(meeting.getHost().getId())
                   .hostName(meeting.getHost().getFullName())
                   .hostDepartmentName(hostDepartmentName);
        }
        if (meeting.getSecretary() != null) {
            builder.secretaryId(meeting.getSecretary().getId())
                   .secretaryName(meeting.getSecretary().getFullName())
                   .secretaryDepartmentName(secretaryDepartmentName);
        }

        return builder.build();
    }
}
