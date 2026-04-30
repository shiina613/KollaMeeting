package com.example.kolla.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for storage statistics.
 * Requirements: 6.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageStatsResponse {

    private long recordingsTotalBytes;
    private long documentsTotalBytes;
    private long audioChunksTotalBytes;
    private long minutesTotalBytes;
    private long totalBytes;

    // Human-readable sizes
    private String recordingsTotalMb;
    private String documentsTotalMb;
    private String audioChunksTotalMb;
    private String minutesTotalMb;
    private String totalMb;
}
