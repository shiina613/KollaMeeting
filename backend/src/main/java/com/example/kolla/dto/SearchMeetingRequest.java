package com.example.kolla.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Request DTO for meeting search filters.
 * Requirements: 13.1–13.3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchMeetingRequest {

    /** Free-text keyword to match against meeting title or description. */
    private String keyword;

    /** Filter meetings starting on or after this date (inclusive). */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    /** Filter meetings starting on or before this date (inclusive). */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /** Filter by room ID. */
    private Long roomId;

    /** Filter by department ID (via room.department). */
    private Long departmentId;

    /** Filter by creator user ID. */
    private Long creatorId;
}
