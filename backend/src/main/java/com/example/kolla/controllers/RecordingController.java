package com.example.kolla.controllers;

import com.example.kolla.models.User;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.RecordingResponse;
import com.example.kolla.services.RecordingService;
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

import java.io.IOException;
import java.util.List;

/**
 * Recording management endpoints.
 * Context-path: /api/v1, so mappings here are relative.
 * Requirements: 7.1–7.7
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Recordings", description = "Meeting recording start/stop, list, download, and delete")
@SecurityRequirement(name = "bearerAuth")
public class RecordingController {

    private final RecordingService recordingService;

    // ── POST /meetings/{meetingId}/recordings/start ───────────────────────────

    /**
     * POST /api/v1/meetings/{meetingId}/recordings/start
     * Start recording a meeting. SECRETARY only.
     * Requirements: 7.1
     */
    @PostMapping("/meetings/{meetingId}/recordings/start")
    @PreAuthorize("hasRole('SECRETARY')")
    @Operation(summary = "Start recording a meeting (SECRETARY only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Recording started"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Meeting not active"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<RecordingResponse>> startRecording(
            @Parameter(description = "Meeting ID") @PathVariable Long meetingId,
            @AuthenticationPrincipal User currentUser) {

        RecordingResponse response = recordingService.startRecording(meetingId, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Recording started successfully", response));
    }

    // ── POST /recordings/{id}/stop ────────────────────────────────────────────

    /**
     * POST /api/v1/recordings/{id}/stop
     * Stop an active recording. SECRETARY only.
     * Requirements: 7.4
     */
    @PostMapping("/recordings/{id}/stop")
    @PreAuthorize("hasRole('SECRETARY')")
    @Operation(summary = "Stop an active recording (SECRETARY only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Recording stopped"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Recording not active"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Recording not found")
    })
    public ResponseEntity<ApiResponse<RecordingResponse>> stopRecording(
            @Parameter(description = "Recording ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        RecordingResponse response = recordingService.stopRecording(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Recording stopped successfully", response));
    }

    // ── GET /meetings/{meetingId}/recordings ──────────────────────────────────

    /**
     * GET /api/v1/meetings/{meetingId}/recordings
     * List all recordings for a meeting. Meeting members only.
     * Requirements: 7.6
     */
    @GetMapping("/meetings/{meetingId}/recordings")
    @Operation(summary = "List recordings for a meeting")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of recordings"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<List<RecordingResponse>>> listRecordings(
            @Parameter(description = "Meeting ID") @PathVariable Long meetingId,
            @AuthenticationPrincipal User currentUser) {

        List<RecordingResponse> recordings = recordingService.listRecordings(meetingId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(recordings));
    }

    // ── GET /recordings/{id} ──────────────────────────────────────────────────

    /**
     * GET /api/v1/recordings/{id}
     * Get a recording by ID. Meeting members only.
     * Requirements: 7.6
     */
    @GetMapping("/recordings/{id}")
    @Operation(summary = "Get recording by ID")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Recording details"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Recording not found")
    })
    public ResponseEntity<ApiResponse<RecordingResponse>> getRecordingById(
            @Parameter(description = "Recording ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        RecordingResponse response = recordingService.getRecordingById(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── GET /recordings/{id}/download ─────────────────────────────────────────

    /**
     * GET /api/v1/recordings/{id}/download
     * Download a recording file. Meeting members only.
     * Requirements: 7.7
     */
    @GetMapping("/recordings/{id}/download")
    @Operation(summary = "Download a recording file")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Recording file stream"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Recording not found")
    })
    public ResponseEntity<Resource> downloadRecording(
            @Parameter(description = "Recording ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) throws IOException {

        Resource resource = recordingService.downloadRecording(id, currentUser);

        // Retrieve the filename from the resource for the Content-Disposition header
        String filename = resource.getFilename() != null ? resource.getFilename() : "recording";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    // ── DELETE /recordings/{id} ───────────────────────────────────────────────

    /**
     * DELETE /api/v1/recordings/{id}
     * Delete a recording. ADMIN only.
     * Requirements: 7.3
     */
    @DeleteMapping("/recordings/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a recording (ADMIN only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Recording deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Recording not found")
    })
    public ResponseEntity<ApiResponse<Void>> deleteRecording(
            @Parameter(description = "Recording ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        recordingService.deleteRecording(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Recording deleted successfully", null));
    }
}
