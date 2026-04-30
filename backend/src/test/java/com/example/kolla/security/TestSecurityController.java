package com.example.kolla.security;

import com.example.kolla.dto.LoginRequest;
import com.example.kolla.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Minimal controller used only in RBAC tests.
 * Provides endpoints that mirror the real application's access control rules:
 * - POST /auth/login  → public
 * - GET  /users/me    → any authenticated user
 * - GET  /admin/users → ADMIN only
 * - POST /admin/users → ADMIN only
 * - GET  /secretary/meetings → SECRETARY or ADMIN
 *
 * This controller is NOT loaded in production — it lives in src/test only.
 */
@RestController
@Profile("rbac-test")
class TestSecurityController {

    /** Public endpoint — mirrors POST /api/v1/auth/login */
    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<String>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ok"));
    }

    /** Any authenticated user can access their own profile */
    @GetMapping("/users/me")
    public ResponseEntity<ApiResponse<String>> getMe() {
        return ResponseEntity.ok(ApiResponse.success("me"));
    }

    /** ADMIN-only: list all users */
    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> listUsers() {
        return ResponseEntity.ok(ApiResponse.success("users"));
    }

    /** ADMIN-only: create user */
    @PostMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> createUser(@RequestBody(required = false) String body) {
        return ResponseEntity.ok(ApiResponse.success("created"));
    }

    /** SECRETARY or ADMIN: list meetings for secretary workflow */
    @GetMapping("/secretary/meetings")
    @PreAuthorize("hasAnyRole('SECRETARY', 'ADMIN')")
    public ResponseEntity<ApiResponse<String>> secretaryMeetings() {
        return ResponseEntity.ok(ApiResponse.success("meetings"));
    }
}
