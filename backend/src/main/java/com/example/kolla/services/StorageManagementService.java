package com.example.kolla.services;

import com.example.kolla.dto.BulkDeleteRequest;
import com.example.kolla.models.User;
import com.example.kolla.responses.BulkDeleteResponse;
import com.example.kolla.responses.StorageStatsResponse;

/**
 * Service interface for storage management operations.
 * Requirements: 6.7
 */
public interface StorageManagementService {

    /**
     * Get storage statistics per file type.
     *
     * @return storage stats with per-type bytes and human-readable MB values
     */
    StorageStatsResponse getStorageStats();

    /**
     * Bulk delete recordings and/or documents.
     * Logs the operation to storage_log.
     *
     * @param request   the bulk delete request containing IDs to delete
     * @param adminUser the admin user performing the deletion
     * @return summary of the deletion operation
     */
    BulkDeleteResponse bulkDelete(BulkDeleteRequest request, User adminUser);
}
