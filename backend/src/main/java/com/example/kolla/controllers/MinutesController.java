package com.example.kolla.controllers;

import com.example.kolla.dto.EditMinutesRequest;
import com.example.kolla.models.User;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.MinutesResponse;
import com.example.kolla.services.MinutesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * Minutes workflow endpoints.
 *
 * <pre>
 *   GET    /api/v1/meetings/{id}/minutes                          – get minutes info
 *   POST   /api/v1/meetings/{id}/minutes/confirm                  – Host confirms
 *   PUT    /api/v1/meetings/{id}/minutes/edit                     – Secretary edits
 *   GET    /api/v1/meetings/{id}/minutes/download?version=...     – download PDF
 * </pre>
 *
 * Requirements: 25.1–25.6
 */
@RestController
@RequestMapping("/meetings/{meetingId}/minutes")
@RequiredArgsConstructor
@Tag(name = "Minutes", description = "Meeting minutes workflow: draft → confirm → publish")
@SecurityRequirement(name = "bearerAuth")
public class MinutesController {

    private final MinutesService minutesService;

    // ── GET /meetings/{meetingId}/minutes ─────────────────────────────────────

    /**
     * GET /api/v1/meetings/{meetingId}/minutes
     * Get the minutes record for a meeting (members only).
     * Requirements: 25.1
     */
    @GetMapping
    @Operation(summary = "Get minutes for a meeting (members only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Minutes record"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<MinutesResponse>> getMinutes(
            @Parameter(description = "Meeting ID") @PathVariable Long meetingId,
            @AuthenticationPrincipal User currentUser) {

        MinutesResponse response = minutesService.getMinutes(meetingId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── POST /meetings/{meetingId}/minutes/confirm ────────────────────────────

    /**
     * POST /api/v1/meetings/{meetingId}/minutes/confirm
     * Host confirms the draft minutes with a digital stamp.
     * Requirements: 25.4
     */
    @PostMapping("/confirm")
    @Operation(summary = "Host confirms the draft minutes (Host only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Minutes confirmed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Minutes not in DRAFT state"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<MinutesResponse>> confirmMinutes(
            @Parameter(description = "Meeting ID") @PathVariable Long meetingId,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest httpRequest) throws IOException {

        // Extract raw JWT token from Authorization header for the confirmation hash
        String jwtToken = extractJwtToken(httpRequest);

        MinutesResponse response = minutesService.confirmMinutes(meetingId, currentUser, jwtToken);
        return ResponseEntity.ok(
                ApiResponse.success("Minutes confirmed successfully", response));
    }

    // ── PUT /meetings/{meetingId}/minutes/edit ────────────────────────────────

    /**
     * PUT /api/v1/meetings/{meetingId}/minutes/edit
     * Secretary edits and publishes the minutes.
     * Requirements: 25.5
     */
    @PutMapping("/edit")
    @Operation(summary = "Secretary edits and publishes the minutes (Secretary only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Minutes published"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<MinutesResponse>> editMinutes(
            @Parameter(description = "Meeting ID") @PathVariable Long meetingId,
            @Valid @RequestBody EditMinutesRequest request,
            @AuthenticationPrincipal User currentUser) throws IOException {

        MinutesResponse response = minutesService.editMinutes(
                meetingId, request.getContentHtml(), currentUser);
        return ResponseEntity.ok(
                ApiResponse.success("Minutes published successfully", response));
    }

    // ── GET /meetings/{meetingId}/minutes/download ────────────────────────────

    /**
     * GET /api/v1/meetings/{meetingId}/minutes/download?version=draft|confirmed|secretary
     * Download a specific version of the minutes PDF.
     * Requirements: 25.6
     */
    @GetMapping("/download")
    @Operation(summary = "Download a minutes PDF version (members only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Minutes PDF file"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid version parameter"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting or minutes not found")
    })
    public ResponseEntity<Resource> downloadMinutes(
            @Parameter(description = "Meeting ID") @PathVariable Long meetingId,
            @Parameter(description = "PDF version: draft, confirmed, or secretary")
            @RequestParam(defaultValue = "draft") String version,
            @AuthenticationPrincipal User currentUser) throws IOException {

        Resource resource = minutesService.downloadMinutes(meetingId, version, currentUser);

        String filename = "minutes_" + meetingId + "_" + version + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Extract the raw JWT token from the Authorization header.
     * Returns an empty string if the header is absent (fallback for hash computation).
     */
    private String extractJwtToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return "";
    }
}
