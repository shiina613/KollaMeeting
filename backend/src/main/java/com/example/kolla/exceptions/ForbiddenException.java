package com.example.kolla.exceptions;

/**
 * Thrown when an authenticated user attempts an action they are not authorized to perform.
 * Maps to HTTP 403 Forbidden.
 * Requirements: 15.1–15.7
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
