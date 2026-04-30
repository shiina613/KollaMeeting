package com.example.kolla.exceptions;

/**
 * Thrown when authentication is required but missing or invalid.
 * Maps to HTTP 401 Unauthorized.
 * Requirements: 15.1–15.7
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
