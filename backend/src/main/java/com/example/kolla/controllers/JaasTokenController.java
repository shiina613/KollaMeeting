package com.example.kolla.controllers;

import com.example.kolla.models.User;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.JaasTokenResponse;
import com.example.kolla.services.JaasTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint for generating JaaS JWT tokens for authenticated meeting participants.
 * Protected by JwtAuthenticationFilter — no changes needed in SecurityConfig.
 * Requirements: 1.1, 1.6
 */
@RestController
@RequestMapping("/meetings")
@RequiredArgsConstructor
@Tag(name = "JaaS", description = "JaaS JWT token generation")
@SecurityRequirement(name = "bearerAuth")
public class JaasTokenController {

    private final JaasTokenService jaasTokenService;

    /**
     * GET /api/v1/meetings/{id}/jaas-token
     * Generate a signed JaaS JWT token for the authenticated user in the given meeting.
     * Requires a valid Kolla JWT in the Authorization header (handled by JwtAuthenticationFilter).
     * Requirements: 1.1, 1.6
     */
    @GetMapping("/{id}/jaas-token")
    @Operation(summary = "Generate JaaS JWT token for a meeting")
    public ResponseEntity<ApiResponse<JaasTokenResponse>> getJaasToken(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        JaasTokenResponse response = jaasTokenService.generateToken(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
