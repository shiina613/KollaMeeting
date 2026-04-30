package com.example.kolla.services;

import com.example.kolla.dto.MeetingSearchResult;
import com.example.kolla.dto.SearchMeetingRequest;
import com.example.kolla.dto.TranscriptionSearchResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for search operations.
 * Requirements: 13.1–13.7
 */
public interface SearchService {

    /**
     * Search meetings with dynamic filters.
     * Supports filtering by keyword (title/description), date range, room, department, and creator.
     * Requirements: 13.1–13.3
     *
     * @param request  filter criteria (all fields optional)
     * @param pageable pagination and sort parameters
     * @return paginated list of matching meeting search results
     */
    Page<MeetingSearchResult> searchMeetings(SearchMeetingRequest request, Pageable pageable);

    /**
     * Full-text search in transcription segment text.
     * Requirements: 13.4–13.7
     *
     * @param keyword   required search term (matched case-insensitively against segment text)
     * @param meetingId optional meeting ID to scope the search
     * @param pageable  pagination and sort parameters
     * @return paginated list of matching transcription segment results
     */
    Page<TranscriptionSearchResult> searchTranscriptions(String keyword, Long meetingId,
                                                          Pageable pageable);
}
