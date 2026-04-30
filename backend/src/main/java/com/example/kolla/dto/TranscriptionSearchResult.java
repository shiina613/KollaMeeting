package com.example.kolla.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing a transcription segment search result.
 * Requirements: 13.4–13.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionSearchResult {

    /** ID of the transcription segment. */
    private Long segmentId;

    /** ID of the meeting this segment belongs to. */
    private Long meetingId;

    /** Title of the meeting this segment belongs to. */
    private String meetingTitle;

    /** Name of the speaker for this segment. */
    private String speakerName;

    /** Speaker turn identifier (groups consecutive segments by the same speaker). */
    private String speakerTurnId;

    /** Monotonically increasing sequence number within the speaker turn. */
    private int sequenceNumber;

    /** The transcribed text content. */
    private String text;

    /** Wall-clock time when the audio chunk started (UTC+7). */
    private LocalDateTime segmentStartTime;
}
