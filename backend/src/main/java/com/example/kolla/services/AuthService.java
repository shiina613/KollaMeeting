package com.example.kolla.services;

import com.example.kolla.dto.LoginRequest;
import com.example.kolla.responses.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Authentication service interface.
 * Requirements: 2.1–2.4, 11.4, 11.9
 */
public interface AuthService {

    /**
     * Authenticate user credentials and return JWT tokens.
     *
     * @param request     login credentials
     * @param httpRequest the HTTP request (for IP logging)
     * @return AuthResponse containing access token, refresh token, and user info
     */
    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest);

    /**
     * Invalidate the given access token by adding it to the Redis blacklist.
     *
     * @param token the raw JWT access token to blacklist
     */
    void logout(String token);

    /**
     * Validate a refresh token and issue a new access token.
     *
     * @param refreshToken the refresh token
     * @return AuthResponse with new access token
     */
    AuthResponse refresh(String refreshToken);
}
