package com.example.kolla.controllers;

import com.example.kolla.dto.LoginRequest;
import com.example.kolla.dto.RefreshTokenRequest;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.AuthResponse;
import com.example.kolla.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints.
 * Note: context-path is /api/v1, so mappings here are relative.
 * Requirements: 2.1–2.4
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, logout, and token refresh")
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/v1/auth/login
     * Authenticate with username/password and receive JWT tokens.
     */
    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate with username and password")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many login attempts")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse authResponse = authService.login(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));
    }

    /**
     * POST /api/v1/auth/logout
     * Invalidate the current JWT token (requires valid JWT in Authorization header).
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidate the current JWT token")
    @SecurityRequirement(name = "bearerAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String token = extractToken(request);
        authService.logout(token);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    /**
     * POST /api/v1/auth/refresh
     * Exchange a refresh token for a new access token.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Get a new access token using a refresh token")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        AuthResponse authResponse = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", authResponse));
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
