package com.example.kolla.controllers;

import com.example.kolla.dto.CreateUserRequest;
import com.example.kolla.dto.ResetPasswordRequest;
import com.example.kolla.dto.UpdateUserRequest;
import com.example.kolla.models.User;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.UserResponse;
import com.example.kolla.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * User management endpoints.
 * Context-path: /api/v1, so mappings here are relative.
 * Requirements: 11.1–11.10
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User CRUD and password management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    // ── GET /users/me ─────────────────────────────────────────────────────────

    /**
     * GET /api/v1/users/me
     * Returns the profile of the currently authenticated user.
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current user profile"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal User currentUser) {

        UserResponse response = userService.getCurrentUser(currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── GET /users/candidates ─────────────────────────────────────────────────

    /**
     * GET /api/v1/users/candidates
     * List active SECRETARY users for meeting host/secretary dropdowns.
     * Accessible by any authenticated user.
     */
    @GetMapping("/candidates")
    @Operation(summary = "List meeting host/secretary candidates")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of candidates"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<java.util.List<UserResponse>>> listMeetingCandidates() {
        return ResponseEntity.ok(ApiResponse.success(userService.listMeetingCandidates()));
    }

    // ── GET /users/search ─────────────────────────────────────────────────────

    /**
     * GET /api/v1/users/search?q=...
     * Search active users by name or username (partial match, max 20 results).
     * Accessible by any authenticated user (used to add meeting members).
     */
    @GetMapping("/search")
    @Operation(summary = "Search users by name or username")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Matching users"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<java.util.List<UserResponse>>> searchUsers(
            @Parameter(description = "Search query (name or username)") @RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success(userService.searchUsers(q)));
    }

    // ── GET /users/active ─────────────────────────────────────────────────────

    /**
     * GET /api/v1/users/active
     * List all active users ordered by full name.
     * Accessible by any authenticated user (used to populate member picker).
     */
    @GetMapping("/active")
    @Operation(summary = "List all active users")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All active users"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<java.util.List<UserResponse>>> listAllActiveUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.listAllActiveUsers()));
    }

    // ── GET /users ────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/users
     * List all users with pagination. ADMIN only.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users (ADMIN only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paginated list of users"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<Page<UserResponse>>> listUsers(
            @PageableDefault(size = 20, sort = "username") Pageable pageable) {

        Page<UserResponse> page = userService.listUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    // ── GET /users/{id} ───────────────────────────────────────────────────────

    /**
     * GET /api/v1/users/{id}
     * Get a user by ID. ADMIN may fetch any user; others may only fetch themselves.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User details"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @Parameter(description = "User ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        UserResponse response = userService.getUserById(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── POST /users ───────────────────────────────────────────────────────────

    /**
     * POST /api/v1/users
     * Create a new user. ADMIN only.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new user (ADMIN only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Username or email already exists")
    })
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {

        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", response));
    }

    // ── PUT /users/{id} ───────────────────────────────────────────────────────

    /**
     * PUT /api/v1/users/{id}
     * Update a user. ADMIN may update any user; others may only update themselves.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update user")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @Parameter(description = "User ID") @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal User currentUser) {

        UserResponse response = userService.updateUser(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", response));
    }

    // ── DELETE /users/{id} ────────────────────────────────────────────────────

    /**
     * DELETE /api/v1/users/{id}
     * Delete a user. ADMIN only.
     * Returns 400 if the user has active meeting memberships.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete user (ADMIN only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "User has active meeting memberships"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @Parameter(description = "User ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        userService.deleteUser(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }

    // ── POST /users/{id}/reset-password ───────────────────────────────────────

    /**
     * POST /api/v1/users/{id}/reset-password
     * Reset a user's password. ADMIN only.
     * Returns the generated temporary password in the response (shown once).
     * Requirements: 11.8, 11.9, 11.10
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reset user password (ADMIN only)",
               description = "Generates a temporary password and invalidates all existing tokens for the user.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset, temporary password returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPassword(
            @Parameter(description = "User ID") @PathVariable Long id,
            @Valid @RequestBody(required = false) ResetPasswordRequest request,
            @AuthenticationPrincipal User currentUser) {

        String tempPassword = userService.resetPassword(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(
                "Password reset successfully",
                Map.of("temporaryPassword", tempPassword)));
    }
}
