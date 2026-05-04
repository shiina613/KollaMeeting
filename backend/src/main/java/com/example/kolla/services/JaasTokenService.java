package com.example.kolla.services;

import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.exceptions.ServiceUnavailableException;
import com.example.kolla.models.User;
import com.example.kolla.responses.JaasTokenResponse;

/**
 * Service interface for generating JaaS JWT tokens.
 * Requirements: 1.1–1.19, 2.4, 6.1, 6.2, 6.3, 6.4
 */
public interface JaasTokenService {

    /**
     * Generate a JaaS JWT token for the given user in the given meeting.
     *
     * @param meetingId   the ID of the meeting
     * @param currentUser the authenticated user requesting the token
     * @return a {@link JaasTokenResponse} containing the signed JWT and room name
     * @throws ServiceUnavailableException if JaaS is not configured (JAAS_APP_ID is blank)
     * @throws ResourceNotFoundException   if the meeting does not exist
     * @throws ForbiddenException          if the user is not a member of the meeting
     */
    JaasTokenResponse generateToken(Long meetingId, User currentUser);
}
