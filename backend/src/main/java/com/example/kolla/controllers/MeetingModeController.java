package com.example.kolla.controllers;

import com.example.kolla.dto.SwitchModeRequest;
import com.example.kolla.enums.MeetingMode;
import com.example.kolla.models.User;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.MeetingResponse;
import com.example.kolla.responses.RaiseHandRequestResponse;
import com.example.kolla.responses.SpeakingPermissionResponse;
import com.example.kolla.services.MeetingModeService;
import com.example.kolla.services.RaiseHandService;
import com.example.kolla.services.SpeakingPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for meeting mode switching, raise-hand, and speaking permission.
 *
 * <p>All endpoints are under {@code /meetings/{id}} (context-path: /api/v1).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST   /meetings/{id}/mode                          — switch FREE_MODE ↔ MEETING_MODE</li>
 *   <li>GET    /meetings/{id}/mode                          — get current mode</li>
 *   <li>POST   /meetings/{id}/raise-hand                    — participant raises hand</li>
 *   <li>DELETE /meetings/{id}/raise-hand                    — participant lowers hand</li>
 *   <li>GET    /meetings/{id}/raise-hand                    — Host views pending queue</li>
 *   <li>POST   /meetings/{id}/speaking-permission/{userId}  — Host grants permission</li>
 *   <li>DELETE /meetings/{id}/speaking-permission           — Host revokes permission</li>
 *   <li>GET    /meetings/{id}/speaking-permission           — get current speaker</li>
 * </ul>
 *
 * Requirements: 21.1–21.10, 22.1–22.11
 */
@RestController
@RequestMapping("/meetings")
@RequiredArgsConstructor
@Tag(name = "Meeting Mode & Speaking Permission",
        description = "Mode switching, raise-hand queue, and speaking permission management")
@SecurityRequirement(name = "bearerAuth")
public class MeetingModeController {

    private final MeetingModeService meetingModeService;
    private final RaiseHandService raiseHandService;
    private final SpeakingPermissionService speakingPermissionService;

    // ── Mode switching ────────────────────────────────────────────────────────

    /**
     * POST /api/v1/meetings/{id}/mode
     * Switch meeting mode between FREE_MODE and MEETING_MODE.
     * Only the Host (or ADMIN) may switch modes.
     * Requirements: 21.1–21.10
     */
    @PostMapping("/{id}/mode")
    @Operation(summary = "Switch meeting mode (Host/ADMIN only)",
            description = "Switch between FREE_MODE and MEETING_MODE. "
                    + "Switching to FREE_MODE finalizes any active audio chunk, "
                    + "revokes speaking permission, and expires raise-hand requests "
                    + "before broadcasting the mode change.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Mode switched"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid mode value"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<MeetingResponse>> switchMode(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @Valid @RequestBody SwitchModeRequest request,
            @AuthenticationPrincipal User currentUser) {

        MeetingResponse response = meetingModeService.switchMode(id, request.getMode(), currentUser);
        return ResponseEntity.ok(ApiResponse.success(
                "Meeting mode switched to " + request.getMode(), response));
    }

    /**
     * GET /api/v1/meetings/{id}/mode
     * Get the current mode of a meeting.
     * Requirements: 21.1, 21.6
     */
    @GetMapping("/{id}/mode")
    @Operation(summary = "Get current meeting mode")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current mode"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<MeetingMode>> getCurrentMode(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        MeetingMode mode = meetingModeService.getCurrentMode(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(mode));
    }

    // ── Raise hand ────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/meetings/{id}/raise-hand
     * Participant raises their hand to request speaking permission.
     * Only valid while meeting is in MEETING_MODE.
     * Requirements: 22.1, 22.2
     */
    @PostMapping("/{id}/raise-hand")
    @Operation(summary = "Raise hand to request speaking permission",
            description = "Submit a raise-hand request. Only valid in MEETING_MODE. "
                    + "Notifies the Host via WebSocket.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Raise-hand request submitted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Not in MEETING_MODE"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<RaiseHandRequestResponse>> raiseHand(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        RaiseHandRequestResponse response = raiseHandService.raiseHand(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(
                "Raise-hand request submitted", response));
    }

    /**
     * DELETE /api/v1/meetings/{id}/raise-hand
     * Participant lowers their hand (cancels raise-hand request).
     * If the participant holds speaking permission, it is also revoked.
     * Requirements: 22.7
     */
    @DeleteMapping("/{id}/raise-hand")
    @Operation(summary = "Lower hand (cancel raise-hand request)",
            description = "Cancel a pending raise-hand request. "
                    + "If the participant holds speaking permission, it is also revoked.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hand lowered"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<Void>> lowerHand(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        raiseHandService.lowerHand(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Hand lowered", null));
    }

    /**
     * GET /api/v1/meetings/{id}/raise-hand
     * Get all pending raise-hand requests in chronological order.
     * Only the Host (or ADMIN) may view the queue.
     * Requirements: 22.9
     */
    @GetMapping("/{id}/raise-hand")
    @Operation(summary = "List pending raise-hand requests (Host/ADMIN only)",
            description = "Returns all pending raise-hand requests in chronological order.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of pending requests"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<List<RaiseHandRequestResponse>>> listRaiseHandRequests(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        List<RaiseHandRequestResponse> requests =
                raiseHandService.listPendingRequests(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    // ── Speaking permission ───────────────────────────────────────────────────

    /**
     * POST /api/v1/meetings/{id}/speaking-permission/{userId}
     * Host grants speaking permission to a participant.
     * Revokes any existing permission before granting.
     * Requirements: 22.4, 22.5, 22.8
     */
    @PostMapping("/{id}/speaking-permission/{userId}")
    @Operation(summary = "Grant speaking permission (Host/ADMIN only)",
            description = "Grant speaking permission to a participant. "
                    + "Any existing permission is revoked first. "
                    + "Uses SELECT FOR UPDATE to prevent concurrent grants.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Permission granted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting or user not found")
    })
    public ResponseEntity<ApiResponse<SpeakingPermissionResponse>> grantSpeakingPermission(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @Parameter(description = "User ID to grant permission") @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {

        SpeakingPermissionResponse response =
                speakingPermissionService.grantPermission(id, userId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(
                "Speaking permission granted", response));
    }

    /**
     * DELETE /api/v1/meetings/{id}/speaking-permission
     * Host revokes the current speaking permission.
     * Requirements: 22.6
     */
    @DeleteMapping("/{id}/speaking-permission")
    @Operation(summary = "Revoke speaking permission (Host/ADMIN only)",
            description = "Revoke the current speaking permission. "
                    + "Broadcasts SPEAKING_PERMISSION_REVOKED to all participants.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Permission revoked"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<SpeakingPermissionResponse>> revokeSpeakingPermission(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        SpeakingPermissionResponse response =
                speakingPermissionService.revokePermission(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(
                "Speaking permission revoked", response));
    }

    /**
     * GET /api/v1/meetings/{id}/speaking-permission
     * Get the current active speaking permission for a meeting.
     * Returns null data if no participant currently holds permission.
     * Requirements: 22.5
     */
    @GetMapping("/{id}/speaking-permission")
    @Operation(summary = "Get current speaking permission",
            description = "Returns the participant currently holding speaking permission, "
                    + "or null if no one holds it.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current speaking permission (may be null)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<SpeakingPermissionResponse>> getCurrentSpeakingPermission(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        SpeakingPermissionResponse response =
                speakingPermissionService.getCurrentPermission(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
