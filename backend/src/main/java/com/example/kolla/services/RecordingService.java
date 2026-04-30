package com.example.kolla.services;

import com.example.kolla.models.User;
import com.example.kolla.responses.RecordingResponse;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

/**
 * Service interface for meeting recording management.
 * Requirements: 7.1–7.7
 */
public interface RecordingService {

    /**
     * Start recording for a meeting (HOST/SECRETARY/ADMIN only).
     * Creates a Recording record with status=RECORDING and startTime=now.
     * Actual video capture is handled by Jitsi.
     * Requirements: 7.1
     */
    RecordingResponse startRecording(Long meetingId, User currentUser);

    /**
     * Stop an active recording (HOST/SECRETARY/ADMIN only).
     * Sets endTime=now and status=COMPLETED.
     * Requirements: 7.4
     */
    RecordingResponse stopRecording(Long recordingId, User currentUser);

    /**
     * List all recordings for a meeting.
     * Requires the user to be a member of the meeting.
     * Requirements: 7.6
     */
    List<RecordingResponse> listRecordings(Long meetingId, User currentUser);

    /**
     * Get a single recording by ID.
     * Requires the user to be a member of the meeting.
     * Requirements: 7.6
     */
    RecordingResponse getRecordingById(Long recordingId, User currentUser);

    /**
     * Delete a recording (ADMIN only).
     * Removes the file from filesystem and the DB record.
     * Requirements: 7.3
     */
    void deleteRecording(Long recordingId, User currentUser);

    /**
     * Download a recording file.
     * Requires the user to be a member of the meeting.
     * Requirements: 7.7
     */
    Resource downloadRecording(Long recordingId, User currentUser) throws IOException;
}
