package com.example.kolla.services;

import com.example.kolla.models.Meeting;
import com.example.kolla.models.User;
import com.example.kolla.responses.MinutesResponse;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * Service interface for the Minutes workflow.
 *
 * <p>Lifecycle:
 * <pre>
 *   Meeting ends
 *     → compileDraftMinutes()  → DRAFT PDF generated, Host notified
 *     → confirmMinutes()       → HOST_CONFIRMED PDF with digital stamp, Secretary notified
 *     → editMinutes()          → SECRETARY_CONFIRMED PDF published, all members notified
 * </pre>
 *
 * Requirements: 25.1–25.7
 */
public interface MinutesService {

    /**
     * Compile TranscriptionSegments for the meeting into a draft PDF.
     * Segments are sorted by (speakerTurnId, sequenceNumber).
     * Saves the PDF to /storage/minutes/{meetingId}/draft_{minutesId}.pdf.
     * Inserts a Minutes record with status=DRAFT and notifies the Host.
     *
     * <p>Called automatically when a meeting transitions to ENDED.
     *
     * Requirements: 25.1–25.3
     *
     * @param meeting the ended meeting
     * @throws IOException if PDF generation or file storage fails
     */
    void compileDraftMinutes(Meeting meeting) throws IOException;

    /**
     * Get the minutes for a meeting.
     * Requirements: 25.1
     */
    MinutesResponse getMinutes(Long meetingId, User requester);

    /**
     * Host confirms the draft minutes.
     * Embeds a digital stamp: hostName + ISO8601 timestamp + SHA-256(JWT + PDF content).
     * Saves the confirmed PDF; updates status to HOST_CONFIRMED; notifies Secretary.
     *
     * Requirements: 25.4
     *
     * @param meetingId   the meeting ID
     * @param requester   the Host user
     * @param jwtToken    the raw JWT token (used in the confirmation hash)
     * @throws IOException if PDF generation or file storage fails
     */
    MinutesResponse confirmMinutes(Long meetingId, User requester, String jwtToken)
            throws IOException;

    /**
     * Secretary edits and publishes the minutes.
     * Renders contentHtml → PDF via PDFBox + jsoup.
     * Saves the secretary PDF; updates status to SECRETARY_CONFIRMED;
     * broadcasts MINUTES_PUBLISHED to all participants.
     *
     * Requirements: 25.5
     *
     * @param meetingId   the meeting ID
     * @param contentHtml rich-text HTML from the Secretary's editor
     * @param requester   the Secretary user
     * @throws IOException if PDF generation or file storage fails
     */
    MinutesResponse editMinutes(Long meetingId, String contentHtml, User requester)
            throws IOException;

    /**
     * Download a specific version of the minutes PDF.
     *
     * @param meetingId the meeting ID
     * @param version   "draft", "confirmed", or "secretary"
     * @param requester the requesting user (must be a meeting member)
     * @return a Spring {@link Resource} pointing to the PDF file
     * @throws IOException if the file cannot be read
     */
    Resource downloadMinutes(Long meetingId, String version, User requester) throws IOException;
}
