package com.example.kolla.services;

import com.example.kolla.dto.TranscriptionCallbackRequest;
import com.example.kolla.responses.TranscriptionSegmentResponse;

import java.util.List;

/**
 * Service interface for handling transcription callbacks and segment retrieval.
 * Requirements: 8.12, 8.13
 */
public interface TranscriptionService {

    /**
     * Process a transcription callback from Gipformer.
     *
     * <p>Idempotency: if a segment already exists for the given {@code jobId},
     * returns the existing segment without creating a duplicate.
     *
     * <p>For HIGH_PRIORITY meetings: persists the segment AND broadcasts
     * {@code TRANSCRIPTION_SEGMENT} via WebSocket.
     *
     * <p>For NORMAL_PRIORITY meetings: persists the segment only (no broadcast).
     *
     * @param request the callback payload from Gipformer
     * @return the persisted (or existing) TranscriptionSegmentResponse
     * Requirements: 8.12, 8.13
     */
    TranscriptionSegmentResponse processCallback(TranscriptionCallbackRequest request);

    /**
     * Retrieve all transcription segments for a meeting, ordered for display.
     *
     * @param meetingId the meeting ID
     * @return segments ordered by (speakerTurnId, sequenceNumber)
     * Requirements: 8.12
     */
    List<TranscriptionSegmentResponse> getSegmentsForMeeting(Long meetingId);
}
