package com.example.kolla.services;

import com.example.kolla.models.User;
import com.example.kolla.responses.AttendanceLogResponse;

import java.util.List;

/**
 * Service interface for attendance tracking.
 * Handles join/leave events and attendance log management.
 * Requirements: 5.1–5.8
 */
public interface AttendanceService {

    /**
     * Record a user joining a meeting.
     * Creates an AttendanceLog with join_time, IP address, and device info.
     * Triggers lifecycle notification (waiting timeout cancellation if Host/Secretary).
     * Requirements: 5.1, 5.2, 5.8
     *
     * @param meetingId  the meeting being joined
     * @param user       the joining user
     * @param ipAddress  client IP address
     * @param deviceInfo user-agent / device info string
     * @return the created attendance log response
     */
    AttendanceLogResponse joinMeeting(Long meetingId, User user,
                                      String ipAddress, String deviceInfo);

    /**
     * Record a user leaving a meeting.
     * Updates the open AttendanceLog with leave_time and calculates duration.
     * Triggers lifecycle notification (waiting timeout start if no key person remains).
     * Requirements: 5.3, 5.4, 5.5
     *
     * @param meetingId the meeting being left
     * @param user      the leaving user
     * @return the updated attendance log response
     */
    AttendanceLogResponse leaveMeeting(Long meetingId, User user);

    /**
     * Get all attendance logs for a meeting (history view).
     * Requirements: 5.7
     */
    List<AttendanceLogResponse> getAttendanceHistory(Long meetingId, User requester);

    /**
     * Get currently active participants (no leave_time) for a meeting.
     * Requirements: 5.6
     */
    List<AttendanceLogResponse> getActiveParticipants(Long meetingId, User requester);
}
