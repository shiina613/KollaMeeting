package com.example.kolla.services;

import com.example.kolla.models.Meeting;
import com.example.kolla.models.User;
import com.example.kolla.responses.SpeakingPermissionResponse;

/**
 * Service interface for managing speaking permissions in Meeting_Mode.
 *
 * <p>Enforces the invariant that at most one participant holds speaking permission
 * at any given time within a meeting. Uses pessimistic locking (SELECT FOR UPDATE)
 * to prevent race conditions when multiple grant requests arrive concurrently.
 *
 * Requirements: 22.4, 22.8
 */
public interface SpeakingPermissionService {

    /**
     * Grant speaking permission to a participant.
     *
     * <p>If another participant currently holds permission, it is revoked first.
     * A new {@code speakerTurnId} UUID is generated for each grant.
     * Uses {@code SELECT FOR UPDATE} to prevent concurrent grants.
     *
     * @param meetingId the meeting ID
     * @param targetUserId the user to grant permission to
     * @param granter the Host performing the grant
     * @return the new SpeakingPermission response
     * @throws com.example.kolla.exceptions.ForbiddenException if granter is not the Host
     * @throws com.example.kolla.exceptions.BadRequestException if meeting is not in MEETING_MODE
     * Requirements: 22.4, 22.8
     */
    SpeakingPermissionResponse grantPermission(Long meetingId, Long targetUserId, User granter);

    /**
     * Revoke the current speaking permission in a meeting.
     *
     * @param meetingId the meeting ID
     * @param revoker the Host performing the revocation
     * @throws com.example.kolla.exceptions.ForbiddenException if revoker is not the Host
     * @throws com.example.kolla.exceptions.BadRequestException if no active permission exists
     * Requirements: 22.6
     */
    SpeakingPermissionResponse revokePermission(Long meetingId, User revoker);

    /**
     * Revoke speaking permission when a participant disconnects or leaves.
     * No permission check — called internally by lifecycle/heartbeat services.
     *
     * @param meetingId the meeting ID
     * @param userId the user who disconnected/left
     * Requirements: 22.10
     */
    void revokePermissionOnLeave(Long meetingId, Long userId);

    /**
     * Revoke all active permissions for a meeting (used on mode switch or meeting end).
     * No permission check — called internally.
     *
     * @param meetingId the meeting ID
     * @param reason broadcast reason string (e.g. "MODE_SWITCHED", "MEETING_ENDED")
     * Requirements: 21.3, 21.9
     */
    void revokeAllPermissions(Long meetingId, String reason);

    /**
     * Get the current active speaking permission for a meeting, or null if none.
     *
     * @param meetingId the meeting ID
     * Requirements: 22.5
     */
    SpeakingPermissionResponse getCurrentPermission(Long meetingId);

    /**
     * Check whether a specific user currently holds speaking permission.
     *
     * @param meetingId the meeting ID
     * @param userId the user to check
     */
    boolean hasActivePermission(Long meetingId, Long userId);
}
