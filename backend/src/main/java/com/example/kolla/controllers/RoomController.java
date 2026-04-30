package com.example.kolla.controllers;

import com.example.kolla.dto.CreateRoomRequest;
import com.example.kolla.dto.UpdateRoomRequest;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.RoomAvailabilityResponse;
import com.example.kolla.responses.RoomResponse;
import com.example.kolla.services.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Room management endpoints.
 * Context-path: /api/v1
 * Requirements: 12.1–12.8
 */
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "Room CRUD and availability")
@SecurityRequirement(name = "bearerAuth")
public class RoomController {

    private final RoomService roomService;

    /**
     * GET /api/v1/rooms
     * List all rooms. Any authenticated user.
     * Optional filter: ?departmentId=1
     */
    @GetMapping
    @Operation(summary = "List all rooms")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of rooms"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<List<RoomResponse>>> listRooms(
            @Parameter(description = "Filter by department ID") @RequestParam(required = false) Long departmentId) {

        List<RoomResponse> rooms = (departmentId != null)
                ? roomService.listRoomsByDepartment(departmentId)
                : roomService.listRooms();
        return ResponseEntity.ok(ApiResponse.success(rooms));
    }

    /**
     * GET /api/v1/rooms/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get room by ID")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Room details"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Room not found")
    })
    public ResponseEntity<ApiResponse<RoomResponse>> getRoomById(@Parameter(description = "Room ID") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(roomService.getRoomById(id)));
    }

    /**
     * GET /api/v1/rooms/{id}/availability
     * Check room availability for a time range.
     * Query params: startTime, endTime (ISO 8601 format)
     * Requirements: 12.8
     */
    @GetMapping("/{id}/availability")
    @Operation(summary = "Check room availability",
               description = "Returns booked slots overlapping with the requested time range. "
                             + "Pass startTime and endTime as ISO 8601 datetime strings.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Room availability info"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Room not found")
    })
    public ResponseEntity<ApiResponse<RoomAvailabilityResponse>> checkAvailability(
            @Parameter(description = "Room ID") @PathVariable Long id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        RoomAvailabilityResponse response = roomService.checkAvailability(id, startTime, endTime);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/rooms
     * Create a room. ADMIN only.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create room (ADMIN only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Room created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Room name already exists")
    })
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
            @Valid @RequestBody CreateRoomRequest request) {

        RoomResponse response = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Room created successfully", response));
    }

    /**
     * PUT /api/v1/rooms/{id}
     * Update a room. ADMIN only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update room (ADMIN only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Room updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Room not found")
    })
    public ResponseEntity<ApiResponse<RoomResponse>> updateRoom(
            @Parameter(description = "Room ID") @PathVariable Long id,
            @Valid @RequestBody UpdateRoomRequest request) {

        RoomResponse response = roomService.updateRoom(id, request);
        return ResponseEntity.ok(ApiResponse.success("Room updated successfully", response));
    }

    /**
     * DELETE /api/v1/rooms/{id}
     * Delete a room. ADMIN only.
     * Returns 400 if the room has scheduled or active meetings.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete room (ADMIN only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Room deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Room has scheduled or active meetings"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Room not found")
    })
    public ResponseEntity<ApiResponse<Void>> deleteRoom(@Parameter(description = "Room ID") @PathVariable Long id) {
        roomService.deleteRoom(id);
        return ResponseEntity.ok(ApiResponse.success("Room deleted successfully", null));
    }
}
