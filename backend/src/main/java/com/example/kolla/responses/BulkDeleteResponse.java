package com.example.kolla.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for bulk delete storage operation.
 * Requirements: 6.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkDeleteResponse {

    private int deletedRecordings;
    private int deletedDocuments;
    private int totalDeleted;
    private long totalSizeDeletedBytes;
    private String totalSizeDeletedMb;
}
