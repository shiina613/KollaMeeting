package com.example.kolla.services;

import com.example.kolla.models.User;
import com.example.kolla.responses.JaasTokenResponse;

/**
 * Service for generating JaaS JWT tokens for authenticated meeting participants.
 * Requirements: 1.1–1.19, 2.4, 6.1, 6.2, 6.3, 6.4
 */
public interface JaasTokenService {

    /**
     * Generates a signed JaaS JWT token for the given user and meeting.
     *
     * @param meetingId   the ID of the meeting to join
     * @param currentUser the authenticated user requesting the token
     * @return a {@link JaasTokenResponse} containing the signed token and room name
     */
    JaasTokenResponse generateToken(Long meetingId, User currentUser);
}
