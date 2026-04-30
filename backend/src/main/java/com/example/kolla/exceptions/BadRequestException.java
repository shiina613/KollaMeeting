package com.example.kolla.exceptions;

/**
 * Thrown when the client sends an invalid or malformed request.
 * Maps to HTTP 400 Bad Request.
 * Requirements: 15.1–15.7
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
