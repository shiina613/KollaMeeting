package com.example.kolla.services;

import com.example.kolla.dto.CreateUserRequest;
import com.example.kolla.dto.ResetPasswordRequest;
import com.example.kolla.dto.UpdateUserRequest;
import com.example.kolla.models.User;
import com.example.kolla.responses.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * User management service interface.
 * Requirements: 11.1–11.10
 */
public interface UserService {

    /**
     * List all users with pagination.
     * Accessible by ADMIN only.
     */
    Page<UserResponse> listUsers(Pageable pageable);

    /**
     * Get a user by ID.
     * ADMIN may fetch any user; non-admin may only fetch themselves.
     *
     * @param id         target user ID
     * @param requester  the authenticated user making the request
     */
    UserResponse getUserById(Long id, User requester);

    /**
     * Get the profile of the currently authenticated user.
     */
    UserResponse getCurrentUser(User requester);

    /**
     * Create a new user. ADMIN only.
     */
    UserResponse createUser(CreateUserRequest request);

    /**
     * Update an existing user.
     * ADMIN may update any user; non-admin may only update themselves (limited fields).
     * Role changes are restricted to ADMIN.
     *
     * @param id        target user ID
     * @param request   fields to update
     * @param requester the authenticated user making the request
     */
    UserResponse updateUser(Long id, UpdateUserRequest request, User requester);

    /**
     * Delete a user by ID. ADMIN only.
     * Throws BadRequestException if the user has active meeting memberships.
     *
     * @param id        target user ID
     * @param requester the authenticated user making the request
     */
    void deleteUser(Long id, User requester);

    /**
     * Reset a user's password. ADMIN only.
     * Generates a temporary password if none is provided.
     * Invalidates all existing JWT tokens for the target user.
     *
     * @param id        target user ID
     * @param request   optional explicit new password
     * @param requester the authenticated user making the request
     * @return the plain-text temporary password (shown once)
     */
    String resetPassword(Long id, ResetPasswordRequest request, User requester);
}
