package com.example.kolla.dto;

import lombok.Data;

import java.util.List;

/**
 * Request body for bulk delete storage operation.
 * Requirements: 6.7
 */
@Data
public class BulkDeleteRequest {

    /** List of recording IDs to delete (optional). */
    private List<Long> recordingIds;

    /** List of document IDs to delete (optional). */
    private List<Long> documentIds;

    /** Optional description/reason for the deletion. */
    private String description;
}
