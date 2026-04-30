package com.example.kolla.dto;

import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.MeetingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing a meeting search result.
 * Requirements: 13.1–13.3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingSearchResult {

    private Long id;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private MeetingStatus status;
    private MeetingMode mode;

    /** Name of the room where the meeting is held (may be null for virtual meetings). */
    private String roomName;

    /** Name of the department that owns the room (may be null). */
    private String departmentName;

    /** Full name of the meeting creator. */
    private String creatorName;

    /** Number of members in the meeting. */
    private long memberCount;
}
