package com.example.kolla.services.impl;

import com.example.kolla.dto.LoginRequest;
import com.example.kolla.exceptions.UnauthorizedException;
import com.example.kolla.models.User;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.responses.AuthResponse;
import com.example.kolla.services.AuthService;
import com.example.kolla.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * AuthService implementation.
 * Requirements: 2.1–2.4, 11.4, 11.9, 19.7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long jwtRefreshExpirationMs;

    @Value("${app.rate-limit.trust-proxy:false}")
    private boolean trustProxy;

    @Override
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(), request.getPassword()));

            User user = (User) authentication.getPrincipal();

            String accessToken = jwtUtils.generateAccessToken(
                    user, user.getId(), user.getRole().name());
            String refreshToken = jwtUtils.generateRefreshToken(user);

            log.info("User '{}' logged in from IP: {}",
                    user.getUsername(), getClientIp(httpRequest));

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtExpirationMs)
                    .user(AuthResponse.UserInfo.builder()
                            .id(user.getId())
                            .username(user.getUsername())
                            .fullName(user.getFullName())
                            .role(user.getRole())
                            .email(user.getEmail())
                            .build())
                    .build();

        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for username '{}' from IP: {}",
                    request.getUsername(), getClientIp(httpRequest));
            throw new UnauthorizedException("Invalid username or password");
        } catch (AuthenticationException e) {
            log.warn("Authentication failed for '{}': {}", request.getUsername(), e.getMessage());
            throw new UnauthorizedException("Authentication failed: " + e.getMessage());
        }
    }

    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            // Blacklist the access token
            long ttlMs = jwtUtils.getRemainingTtlMs(token);
            if (ttlMs > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_KEY_PREFIX + token,
                        "1",
                        ttlMs,
                        TimeUnit.MILLISECONDS);
                log.debug("Access token blacklisted with TTL {}ms", ttlMs);
            }

            // Also invalidate all tokens for this user (including refresh tokens)
            // by storing a "logged out at" timestamp. The refresh endpoint checks this.
            String username = jwtUtils.getSubjectFromToken(token);
            if (username != null) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_KEY_PREFIX + "user:" + username,
                        String.valueOf(System.currentTimeMillis()),
                        jwtRefreshExpirationMs,
                        TimeUnit.MILLISECONDS);
                log.debug("User '{}' session invalidated (refresh tokens revoked)", username);
            }
        } catch (Exception e) {
            log.warn("Failed to blacklist token: {}", e.getMessage());
        }
    }

    @Override
    public AuthResponse refresh(String refreshToken) {
        if (!jwtUtils.validateToken(refreshToken)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // Refresh token subject is the username (not a claim, but the JWT subject)
        String username;
        try {
            username = jwtUtils.getSubjectFromToken(refreshToken);
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        if (username == null || username.isBlank()) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        // Check if user has logged out (refresh token revoked)
        try {
            String logoutTimestamp = redisTemplate.opsForValue().get(
                    BLACKLIST_KEY_PREFIX + "user:" + username);
            if (logoutTimestamp != null) {
                long logoutAt = Long.parseLong(logoutTimestamp);
                long tokenIssuedAt = jwtUtils.getIssuedAtFromToken(refreshToken);
                if (tokenIssuedAt <= logoutAt) {
                    log.warn("Refresh token used after logout for user '{}'", username);
                    throw new UnauthorizedException("Session has been invalidated. Please login again.");
                }
            }
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            // Redis error — fail-open for refresh to avoid locking out users
            log.warn("Failed to check logout status for '{}': {}", username, e.getMessage());
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        // Reject refresh if user account has been deactivated
        if (!user.isActive()) {
            log.warn("Refresh token used for deactivated user '{}'", username);
            throw new UnauthorizedException("Account is disabled");
        }

        String newAccessToken = jwtUtils.generateAccessToken(
                user, user.getId(), user.getRole().name());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken) // keep the same refresh token
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .role(user.getRole())
                        .email(user.getEmail())
                        .build())
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        if (trustProxy) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
