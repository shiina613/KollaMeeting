package com.example.kolla.controllers;

import com.example.kolla.dto.MeetingSearchResult;
import com.example.kolla.dto.SearchMeetingRequest;
import com.example.kolla.dto.TranscriptionSearchResult;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.services.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Search endpoints for meetings and transcriptions.
 * Context-path: /api/v1, so mappings here are relative.
 * Requirements: 13.1–13.7
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Search meetings and transcription segments")
@SecurityRequirement(name = "bearerAuth")
public class SearchController {

    private final SearchService searchService;

    // ── GET /search/meetings ──────────────────────────────────────────────────

    /**
     * GET /api/v1/search/meetings
     * Search meetings with optional filters. Requires JWT authentication.
     * Filters: keyword (title/description), startDate, endDate, roomId, departmentId, creatorId.
     * Requirements: 13.1–13.3
     */
    @GetMapping("/meetings")
    @Operation(summary = "Search meetings by keyword, date range, room, department, or creator")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paginated search results"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<Page<MeetingSearchResult>>> searchMeetings(
            @Parameter(description = "Search keyword (title/description)") @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long creatorId,
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable) {

        SearchMeetingRequest request = SearchMeetingRequest.builder()
                .keyword(keyword)
                .startDate(startDate)
                .endDate(endDate)
                .roomId(roomId)
                .departmentId(departmentId)
                .creatorId(creatorId)
                .build();

        Page<MeetingSearchResult> results = searchService.searchMeetings(request, pageable);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    // ── GET /search/transcriptions ────────────────────────────────────────────

    /**
     * GET /api/v1/search/transcriptions
     * Full-text search in transcription segment text. Requires JWT authentication.
     * The {@code keyword} parameter is required.
     * Requirements: 13.4–13.7
     */
    @GetMapping("/transcriptions")
    @Operation(summary = "Full-text search in transcription segments")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paginated transcription search results"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "keyword is required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<Page<TranscriptionSearchResult>>> searchTranscriptions(
            @Parameter(description = "Search keyword (required)") @RequestParam String keyword,
            @Parameter(description = "Filter by meeting ID") @RequestParam(required = false) Long meetingId,
            @PageableDefault(size = 20, sort = "segmentStartTime") Pageable pageable) {

        if (keyword == null || keyword.isBlank()) {
            throw new BadRequestException("keyword is required and must not be blank");
        }

        Page<TranscriptionSearchResult> results =
                searchService.searchTranscriptions(keyword, meetingId, pageable);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
