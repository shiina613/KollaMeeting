package com.example.kolla.exceptions;

/**
 * Thrown when a required external service or configuration is unavailable.
 * Maps to HTTP 503 Service Unavailable.
 * Requirements: 1.19, 2.5
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
