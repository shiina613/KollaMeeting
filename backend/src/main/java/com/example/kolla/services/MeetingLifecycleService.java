package com.example.kolla.services;

import com.example.kolla.models.Meeting;
import com.example.kolla.models.User;
import com.example.kolla.responses.MeetingResponse;

/**
 * Service interface for meeting lifecycle transitions.
 * Handles SCHEDULED → ACTIVE → ENDED state machine and Waiting_Timeout logic.
 * Requirements: 3.10, 3.11
 */
public interface MeetingLifecycleService {

    /**
     * Activate a meeting: SCHEDULED → ACTIVE.
     * Only the Host or the designated Secretary of this meeting may activate.
     * (ADMIN authority removed — TASK-001)
     * Sets activatedAt timestamp and broadcasts MEETING_STARTED event.
     * Requirements: 3.10
     */
    MeetingResponse activateMeeting(Long meetingId, User requester);

    /**
     * End a meeting: ACTIVE → ENDED.
     * Only the Host or Secretary of this meeting may end it.
     * (ADMIN authority removed — TASK-001)
     * Revokes any active speaking permission, closes attendance logs, and
     * triggers minutes generation.
     * Requirements: 3.11
     */
    MeetingResponse endMeeting(Long meetingId, User requester);

    /**
     * Called when a participant joins an active meeting.
     * Cancels the waiting timeout if Host or Secretary has arrived.
     * Requirements: 3.11
     */
    void onParticipantJoined(Long meetingId, User user);

    /**
     * Called when a participant leaves an active meeting.
     * Starts the 5-minute waiting timeout if neither Host nor Secretary remains.
     * (Timeout reduced from 10 min → 5 min — TASK-002)
     * Requirements: 3.11
     */
    void onParticipantLeft(Long meetingId, User user);

    /**
     * Scheduled job: auto-end meetings whose waiting_timeout has expired.
     * Runs every 30 seconds.
     * Requirements: 3.11
     */
    void processExpiredWaitingTimeouts();

    /**
     * Check whether the given user has Host authority over the meeting.
     * Host authority = user is the designated Host only.
     * NOTE: ADMIN role no longer confers host authority (TASK-001).
     * Requirements: 3.10, 21.7
     */
    boolean isHostOrAdmin(Meeting meeting, User user);
}
