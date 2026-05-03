package com.example.kolla.controllers;

import com.example.kolla.dto.AddMemberRequest;
import com.example.kolla.dto.CreateMeetingRequest;
import com.example.kolla.dto.UpdateMeetingRequest;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.models.User;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.AttendanceLogResponse;
import com.example.kolla.responses.MeetingResponse;
import com.example.kolla.responses.MemberResponse;
import com.example.kolla.services.AttendanceService;
import com.example.kolla.services.MeetingLifecycleService;
import com.example.kolla.services.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Meeting management endpoints.
 * Context-path: /api/v1, so mappings here are relative.
 * Requirements: 3.1–3.12
 */
@RestController
@RequestMapping("/meetings")
@RequiredArgsConstructor
@Tag(name = "Meetings", description = "Meeting CRUD, lifecycle, and member management")
@SecurityRequirement(name = "bearerAuth")
public class MeetingController {

    private final MeetingService meetingService;
    private final MeetingLifecycleService meetingLifecycleService;
    private final AttendanceService attendanceService;

    // ── GET /meetings ─────────────────────────────────────────────────────────

    /**
     * GET /api/v1/meetings
     * List meetings with optional filters (paginated).
     * Requirements: 3.4, 13.2
     */
    @GetMapping
    @Operation(summary = "List meetings (paginated, filterable)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of meetings"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<Page<MeetingResponse>>> listMeetings(
            @RequestParam(required = false) MeetingStatus status,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTo,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {

        Page<MeetingResponse> page = meetingService.listMeetings(
                status, roomId, creatorId, startFrom, startTo, pageable, currentUser);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    // ── POST /meetings ────────────────────────────────────────────────────────

    /**
     * POST /api/v1/meetings
     * Create a new meeting. SECRETARY only.
     * Requirements: 3.1, 3.2, 3.8
     */
    @PostMapping
    @PreAuthorize("hasRole('SECRETARY')")
    @Operation(summary = "Create a new meeting (SECRETARY only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Meeting created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Room scheduling conflict")
    })
    public ResponseEntity<ApiResponse<MeetingResponse>> createMeeting(
            @Valid @RequestBody CreateMeetingRequest request,
            @AuthenticationPrincipal User currentUser) {

        MeetingResponse response = meetingService.createMeeting(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Meeting created successfully", response));
    }

    // ── GET /meetings/{id} ────────────────────────────────────────────────────

    /**
     * GET /api/v1/meetings/{id}
     * Get meeting details.
     * Requirements: 3.7
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get meeting by ID")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Meeting details"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<MeetingResponse>> getMeeting(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        MeetingResponse response = meetingService.getMeetingById(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── PUT /meetings/{id} ────────────────────────────────────────────────────

    /**
     * PUT /api/v1/meetings/{id}
     * Update a meeting. SECRETARY only.
     * Requirements: 3.5, 3.12
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SECRETARY')")
    @Operation(summary = "Update a meeting (SECRETARY only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Meeting updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Room scheduling conflict")
    })
    public ResponseEntity<ApiResponse<MeetingResponse>> updateMeeting(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @Valid @RequestBody UpdateMeetingRequest request,
            @AuthenticationPrincipal User currentUser) {

        MeetingResponse response = meetingService.updateMeeting(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Meeting updated successfully", response));
    }

    // ── DELETE /meetings/{id} ─────────────────────────────────────────────────

    /**
     * DELETE /api/v1/meetings/{id}
     * Delete a meeting. ADMIN only.
     * Requirements: 3.6
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a meeting (ADMIN only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Meeting deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<Void>> deleteMeeting(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        meetingService.deleteMeeting(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Meeting deleted successfully", null));
    }

    // ── GET /meetings/{id}/members ────────────────────────────────────────────

    /**
     * GET /api/v1/meetings/{id}/members
     * List members of a meeting.
     * Requirements: 3.9
     */
    @GetMapping("/{id}/members")
    @Operation(summary = "List meeting members")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of members"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<List<MemberResponse>>> listMembers(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        List<MemberResponse> members = meetingService.listMembers(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    // ── POST /meetings/{id}/members ───────────────────────────────────────────

    /**
     * POST /api/v1/meetings/{id}/members
     * Add a member to a meeting. SECRETARY only.
     * Requirements: 3.9, 10.3
     */
    @PostMapping("/{id}/members")
    @PreAuthorize("hasRole('SECRETARY')")
    @Operation(summary = "Add a member to a meeting (SECRETARY only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Member added"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting or user not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "User already a member")
    })
    public ResponseEntity<ApiResponse<MemberResponse>> addMember(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @Valid @RequestBody AddMemberRequest request,
            @AuthenticationPrincipal User currentUser) {

        MemberResponse response = meetingService.addMember(id, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Member added successfully", response));
    }

    // ── DELETE /meetings/{id}/members/{userId} ────────────────────────────────

    /**
     * DELETE /api/v1/meetings/{id}/members/{userId}
     * Remove a member from a meeting. SECRETARY only.
     * Requirements: 3.9
     */
    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasRole('SECRETARY')")
    @Operation(summary = "Remove a member from a meeting (SECRETARY only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Member removed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting or member not found")
    })
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @Parameter(description = "User ID to remove") @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {

        meetingService.removeMember(id, userId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Member removed successfully", null));
    }

    // ── POST /meetings/{id}/activate ──────────────────────────────────────────

    /**
     * POST /api/v1/meetings/{id}/activate
     * Transition meeting from SCHEDULED → ACTIVE. Host or ADMIN only.
     * Requirements: 3.10
     */
    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a meeting (Host/ADMIN only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Meeting activated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Meeting not in SCHEDULED state"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<MeetingResponse>> activateMeeting(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        MeetingResponse response = meetingLifecycleService.activateMeeting(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Meeting activated successfully", response));
    }

    // ── POST /meetings/{id}/end ───────────────────────────────────────────────

    /**
     * POST /api/v1/meetings/{id}/end
     * Transition meeting from ACTIVE → ENDED. Host, Secretary, or ADMIN only.
     * Requirements: 3.11
     */
    @PostMapping("/{id}/end")
    @Operation(summary = "End a meeting (Host/Secretary/ADMIN only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Meeting ended"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Meeting not in ACTIVE state"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<MeetingResponse>> endMeeting(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        MeetingResponse response = meetingLifecycleService.endMeeting(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Meeting ended successfully", response));
    }

    // ── POST /meetings/{id}/join ──────────────────────────────────────────────

    /**
     * POST /api/v1/meetings/{id}/join
     * Record a user joining an active meeting. Members only.
     * Requirements: 5.1, 5.2
     */
    @PostMapping("/{id}/join")
    @Operation(summary = "Join a meeting (members only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Joined meeting"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Meeting not active"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a meeting member"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<AttendanceLogResponse>> joinMeeting(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest httpRequest) {

        String ipAddress = extractClientIp(httpRequest);
        String deviceInfo = httpRequest.getHeader("User-Agent");

        AttendanceLogResponse response = attendanceService.joinMeeting(
                id, currentUser, ipAddress, deviceInfo);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Joined meeting successfully", response));
    }

    // ── POST /meetings/{id}/leave ─────────────────────────────────────────────

    /**
     * POST /api/v1/meetings/{id}/leave
     * Record a user leaving a meeting.
     * Requirements: 5.3, 5.4, 5.5
     */
    @PostMapping("/{id}/leave")
    @Operation(summary = "Leave a meeting")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Left meeting"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<AttendanceLogResponse>> leaveMeeting(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        AttendanceLogResponse response = attendanceService.leaveMeeting(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Left meeting successfully", response));
    }

    // ── GET /meetings/{id}/attendance ─────────────────────────────────────────

    /**
     * GET /api/v1/meetings/{id}/attendance
     * Get attendance history for a meeting.
     * Requirements: 5.7
     */
    @GetMapping("/{id}/attendance")
    @Operation(summary = "Get attendance history for a meeting")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Attendance history"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<List<AttendanceLogResponse>>> getAttendanceHistory(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        List<AttendanceLogResponse> logs =
                attendanceService.getAttendanceHistory(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    // ── GET /meetings/{id}/participants ───────────────────────────────────────

    /**
     * GET /api/v1/meetings/{id}/participants
     * Get currently active participants (real-time).
     * Requirements: 5.6
     */
    @GetMapping("/{id}/participants")
    @Operation(summary = "Get active participants in a meeting")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Active participants"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<List<AttendanceLogResponse>>> getActiveParticipants(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        List<AttendanceLogResponse> participants =
                attendanceService.getActiveParticipants(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(participants));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
