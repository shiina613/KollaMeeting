package com.example.kolla.controllers;

import com.example.kolla.dto.TranscriptionCallbackRequest;
import com.example.kolla.models.User;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.TranscriptionSegmentResponse;
import com.example.kolla.services.TranscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for transcription operations.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>POST {@code /transcription/callback} — internal callback from Gipformer</li>
 *   <li>GET  {@code /meetings/{id}/transcription} — list segments for a meeting</li>
 * </ul>
 *
 * <h3>Callback authentication</h3>
 * The callback endpoint is secured with an internal API key passed in the
 * {@code X-Callback-Api-Key} header. This prevents external callers from
 * injecting fake transcription results.
 *
 * Requirements: 8.12, 8.13
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Transcription", description = "Transcription callback and segment retrieval")
public class TranscriptionController {

    private final TranscriptionService transcriptionService;

    @Value("${gipformer.callback-api-key:internal-callback-key-change-me}")
    private String expectedCallbackApiKey;

    // ── Gipformer callback ────────────────────────────────────────────────────

    /**
     * POST /api/v1/transcription/callback
     *
     * <p>Receives a transcription result from Gipformer after it finishes
     * processing an audio chunk.
     *
     * <p>Idempotency: if a segment already exists for the given {@code jobId},
     * returns 200 with the existing segment (no duplicate created).
     *
     * <p>Priority routing:
     * <ul>
     *   <li>HIGH_PRIORITY meeting → persist + broadcast {@code TRANSCRIPTION_SEGMENT}</li>
     *   <li>NORMAL_PRIORITY meeting → persist only</li>
     * </ul>
     *
     * Requirements: 8.12, 8.13
     */
    @PostMapping("/transcription/callback")
    @Operation(
            summary = "Receive transcription result from Gipformer (internal)",
            description = "Called by Gipformer after completing inference on an audio chunk. "
                    + "Requires X-Callback-Api-Key header. "
                    + "Idempotent: duplicate calls for the same jobId return 200 without side effects.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transcription segment processed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid or missing X-Callback-Api-Key")
    })
    public ResponseEntity<ApiResponse<TranscriptionSegmentResponse>> receiveCallback(
            @RequestHeader(value = "X-Callback-Api-Key", required = false) String apiKey,
            @Valid @RequestBody TranscriptionCallbackRequest request) {

        // Validate internal API key
        if (!expectedCallbackApiKey.equals(apiKey)) {
            log.warn("TranscriptionController: invalid or missing X-Callback-Api-Key");
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Unauthorized: invalid callback API key"));
        }

        log.debug("TranscriptionController: received callback for job {}", request.getJobId());

        TranscriptionSegmentResponse response = transcriptionService.processCallback(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Transcription segment processed", response));
    }

    // ── Segment retrieval ─────────────────────────────────────────────────────

    /**
     * GET /api/v1/meetings/{id}/transcription
     *
     * <p>Returns all transcription segments for a meeting, ordered by
     * (speakerTurnId, sequenceNumber) for correct display order.
     *
     * Requirements: 8.12
     */
    @GetMapping("/meetings/{id}/transcription")
    @Operation(
            summary = "Get transcription segments for a meeting",
            description = "Returns all persisted transcription segments ordered by "
                    + "(speakerTurnId, sequenceNumber). Available to all authenticated members.")
    @SecurityRequirement(name = "bearerAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of transcription segments"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Meeting not found")
    })
    public ResponseEntity<ApiResponse<List<TranscriptionSegmentResponse>>> getTranscriptionSegments(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        List<TranscriptionSegmentResponse> segments =
                transcriptionService.getSegmentsForMeeting(id);
        return ResponseEntity.ok(ApiResponse.success(segments));
    }
}
