package com.example.kolla.services;

import com.example.kolla.models.User;
import com.example.kolla.responses.RaiseHandRequestResponse;

import java.util.List;

/**
 * Service interface for managing raise-hand requests in Meeting_Mode.
 *
 * <p>Participants raise their hand to request speaking permission.
 * The Host sees requests in chronological order and grants permission to one at a time.
 *
 * Requirements: 22.1–22.11
 */
public interface RaiseHandService {

    /**
     * Submit a raise-hand request for the current user.
     *
     * @param meetingId the meeting ID
     * @param requester the participant raising their hand
     * @throws com.example.kolla.exceptions.BadRequestException if meeting is not in MEETING_MODE,
     *         or the user already has a pending request
     * @throws com.example.kolla.exceptions.ForbiddenException if user is not a meeting member
     * Requirements: 22.1, 22.2
     */
    RaiseHandRequestResponse raiseHand(Long meetingId, User requester);

    /**
     * Cancel (lower) the current user's raise-hand request.
     *
     * <p>If the user currently holds speaking permission, it is also revoked.
     *
     * @param meetingId the meeting ID
     * @param requester the participant lowering their hand
     * @throws com.example.kolla.exceptions.BadRequestException if no pending request exists
     * Requirements: 22.7
     */
    void lowerHand(Long meetingId, User requester);

    /**
     * Get all pending raise-hand requests for a meeting, in chronological order.
     * Only the Host (or ADMIN) may view the full list.
     *
     * @param meetingId the meeting ID
     * @param requester the requesting user (must be Host or ADMIN)
     * Requirements: 22.9
     */
    List<RaiseHandRequestResponse> listPendingRequests(Long meetingId, User requester);
}
