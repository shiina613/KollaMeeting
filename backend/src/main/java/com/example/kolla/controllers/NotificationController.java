package com.example.kolla.controllers;

import com.example.kolla.models.User;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.NotificationResponse;
import com.example.kolla.services.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Notification endpoints.
 * Context-path: /api/v1
 * Requirements: 10.5–10.7
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "List and manage user notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * GET /api/v1/notifications
     * List notifications for the current user, newest first.
     * Supports pagination: ?page=0&size=20
     */
    @GetMapping
    @Operation(summary = "List notifications for current user")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paginated list of notifications"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> listNotifications(
            @AuthenticationPrincipal User currentUser,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<NotificationResponse> page = notificationService.listNotifications(currentUser, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    /**
     * GET /api/v1/notifications/unread-count
     * Returns the count of unread notifications for the current user.
     */
    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Unread count"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal User currentUser) {

        long count = notificationService.countUnread(currentUser);
        return ResponseEntity.ok(ApiResponse.success(Map.of("unreadCount", count)));
    }

    /**
     * PUT /api/v1/notifications/{id}/read
     * Mark a single notification as read.
     */
    @PutMapping("/{id}/read")
    @Operation(summary = "Mark notification as read")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Notification marked as read"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @Parameter(description = "Notification ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        NotificationResponse response = notificationService.markAsRead(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", response));
    }

    /**
     * PUT /api/v1/notifications/read-all
     * Mark all notifications for the current user as read.
     */
    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All notifications marked as read"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(
            @AuthenticationPrincipal User currentUser) {

        int updated = notificationService.markAllAsRead(currentUser);
        return ResponseEntity.ok(ApiResponse.success(
                "All notifications marked as read",
                Map.of("updatedCount", updated)));
    }
}
