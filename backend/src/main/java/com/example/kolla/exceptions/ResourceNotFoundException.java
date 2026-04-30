package com.example.kolla.exceptions;

/**
 * Thrown when a requested resource does not exist in the database.
 * Maps to HTTP 404 Not Found.
 * Requirements: 15.1–15.7
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " not found with id: " + id);
    }

    public ResourceNotFoundException(String resourceName, String field, Object value) {
        super(resourceName + " not found with " + field + ": " + value);
    }
}
