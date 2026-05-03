package com.example.kolla.controllers;

import com.example.kolla.models.User;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.DocumentResponse;
import com.example.kolla.services.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Document management endpoints.
 * Context-path: /api/v1, so mappings here are relative.
 * Requirements: 9.1–9.7
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Meeting document upload, list, download, and delete")
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final DocumentService documentService;

    // ── POST /meetings/{meetingId}/documents ──────────────────────────────────

    /**
     * POST /api/v1/meetings/{meetingId}/documents
     * Upload a document to a meeting. Any authenticated member may upload.
     * Requirements: 9.1, 9.2, 9.3
     */
    @PostMapping("/meetings/{meetingId}/documents")
    @Operation(summary = "Upload a document to a meeting")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Document uploaded"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file or file too large"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @Parameter(description = "Meeting ID") @PathVariable Long meetingId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) throws IOException {

        DocumentResponse response = documentService.uploadDocument(meetingId, file, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document uploaded successfully", response));
    }

    // ── GET /meetings/{meetingId}/documents ───────────────────────────────────

    /**
     * GET /api/v1/meetings/{meetingId}/documents
     * List all documents for a meeting. Meeting members only.
     * Requirements: 9.4
     */
    @GetMapping("/meetings/{meetingId}/documents")
    @Operation(summary = "List documents for a meeting")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of documents"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> listDocuments(
            @Parameter(description = "Meeting ID") @PathVariable Long meetingId,
            @AuthenticationPrincipal User currentUser) {

        List<DocumentResponse> documents = documentService.listDocuments(meetingId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    // ── GET /documents/{id} ───────────────────────────────────────────────────

    /**
     * GET /api/v1/documents/{id}
     * Get a document by ID. Meeting members only.
     * Requirements: 9.4
     */
    @GetMapping("/documents/{id}")
    @Operation(summary = "Get document by ID")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document details"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<ApiResponse<DocumentResponse>> getDocumentById(
            @Parameter(description = "Document ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        DocumentResponse response = documentService.getDocumentById(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── GET /documents/{id}/download ──────────────────────────────────────────

    /**
     * GET /api/v1/documents/{id}/download
     * Download a document file. Meeting members only.
     * Requirements: 9.5
     */
    @GetMapping("/documents/{id}/download")
    @Operation(summary = "Download a document file")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document file stream"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<Resource> downloadDocument(
            @Parameter(description = "Document ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) throws IOException {

        Resource resource = documentService.downloadDocument(id, currentUser);

        // Use the resource filename for Content-Disposition; fall back to generic name
        String filename = resource.getFilename() != null ? resource.getFilename() : "document";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    // ── DELETE /documents/{id} ────────────────────────────────────────────────

    /**
     * DELETE /api/v1/documents/{id}
     * Delete a document. SECRETARY only.
     * Requirements: 9.6
     */
    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasRole('SECRETARY')")
    @Operation(summary = "Delete a document (SECRETARY only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @Parameter(description = "Document ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        documentService.deleteDocument(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Document deleted successfully", null));
    }
}
