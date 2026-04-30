package com.example.kolla.services;

import com.example.kolla.enums.MeetingMode;
import com.example.kolla.models.User;
import com.example.kolla.responses.MeetingResponse;

/**
 * Service interface for switching meeting modes (FREE_MODE ↔ MEETING_MODE).
 *
 * <p>Mode switch rules:
 * <ul>
 *   <li>Only the Host (or ADMIN) may switch modes.</li>
 *   <li>Switching to MEETING_MODE: mutes all participants via WebSocket event.</li>
 *   <li>Switching to FREE_MODE: finalizes any active audio chunk, pushes it to
 *       the Redis queue, revokes speaking permission, then broadcasts the mode change.
 *       The transition is not visible to participants until finalization completes.</li>
 * </ul>
 *
 * Requirements: 21.1–21.10
 */
public interface MeetingModeService {

    /**
     * Switch the meeting mode between FREE_MODE and MEETING_MODE.
     *
     * <p>When switching to FREE_MODE while a participant holds Speaking_Permission:
     * <ol>
     *   <li>Finalize the current audio chunk (signal Gipformer to flush)</li>
     *   <li>Push the finalized chunk to the Redis transcription queue</li>
     *   <li>Revoke speaking permission</li>
     *   <li>Expire all pending raise-hand requests</li>
     *   <li>Broadcast MODE_CHANGED event</li>
     * </ol>
     *
     * @param meetingId the meeting to switch
     * @param targetMode the desired mode
     * @param requester the user requesting the switch (must be Host or ADMIN)
     * @return updated MeetingResponse with new mode
     * @throws com.example.kolla.exceptions.ForbiddenException if requester is not Host/ADMIN
     * @throws com.example.kolla.exceptions.BadRequestException if meeting is not ACTIVE or
     *         already in the requested mode
     * Requirements: 21.1–21.10
     */
    MeetingResponse switchMode(Long meetingId, MeetingMode targetMode, User requester);

    /**
     * Get the current mode of an active meeting.
     *
     * @param meetingId the meeting ID
     * @param requester the requesting user
     * Requirements: 21.1
     */
    MeetingMode getCurrentMode(Long meetingId, User requester);
}
