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
            long ttlMs = jwtUtils.getRemainingTtlMs(token);
            if (ttlMs > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_KEY_PREFIX + token,
                        "1",
                        ttlMs,
                        TimeUnit.MILLISECONDS);
                log.debug("Token blacklisted with TTL {}ms", ttlMs);
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
            throw new UnauthorizedException("Cannot extract user from refresh token");
        }
        if (username == null || username.isBlank()) {
            throw new UnauthorizedException("Cannot extract user from refresh token");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("User not found: " + username));

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
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
