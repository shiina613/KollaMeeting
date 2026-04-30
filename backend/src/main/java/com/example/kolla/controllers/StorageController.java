package com.example.kolla.controllers;

import com.example.kolla.dto.BulkDeleteRequest;
import com.example.kolla.models.User;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.BulkDeleteResponse;
import com.example.kolla.responses.StorageStatsResponse;
import com.example.kolla.services.StorageManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Storage management endpoints (ADMIN only).
 * Context-path: /api/v1, so mappings here are relative.
 * Requirements: 6.7
 */
@RestController
@RequestMapping("/storage")
@RequiredArgsConstructor
@Tag(name = "Storage", description = "Storage statistics and bulk delete operations (ADMIN only)")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class StorageController {

    private final StorageManagementService storageManagementService;

    /**
     * GET /api/v1/storage/stats
     * Returns storage usage statistics per file type.
     * Requirements: 6.7
     */
    @GetMapping("/stats")
    @Operation(summary = "Get storage statistics (ADMIN only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Storage statistics"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<StorageStatsResponse>> getStorageStats() {
        StorageStatsResponse stats = storageManagementService.getStorageStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * POST /api/v1/storage/bulk-delete
     * Bulk delete recordings and/or documents. Logs the operation to storage_log.
     * Requirements: 6.7
     */
    @PostMapping("/bulk-delete")
    @Operation(summary = "Bulk delete recordings and/or documents (ADMIN only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bulk delete completed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<BulkDeleteResponse>> bulkDelete(
            @RequestBody BulkDeleteRequest request,
            @AuthenticationPrincipal User currentUser) {

        BulkDeleteResponse response = storageManagementService.bulkDelete(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Bulk delete completed successfully", response));
    }
}
