package com.example.kolla.services;

import com.example.kolla.dto.MeetingSearchResult;
import com.example.kolla.dto.SearchMeetingRequest;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.TranscriptionSegment;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.MemberRepository;
import com.example.kolla.repositories.TranscriptionSegmentRepository;
import com.example.kolla.services.impl.SearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private TranscriptionSegmentRepository transcriptionSegmentRepository;

    private SearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchServiceImpl(
                meetingRepository,
                memberRepository,
                transcriptionSegmentRepository);
    }

    @Test
    void searchMeetings_returnsMeetingWhenKeywordMatchesTitle() {
        Meeting titleMatch = meeting(1L, "Bao ve phan bien do an");
        Meeting noMatch = meeting(2L, "Hop giao ban");
        when(meetingRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(titleMatch, noMatch));
        when(transcriptionSegmentRepository.findByTextContainingIgnoreCase(
                eq("phan bien"), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(memberRepository.findByMeetingId(1L)).thenReturn(List.of());

        Page<MeetingSearchResult> result = searchService.searchMeetings(
                request("phan bien"), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(MeetingSearchResult::getTitle)
                .containsExactly("Bao ve phan bien do an");
    }

    @Test
    void searchMeetings_returnsMeetingWhenKeywordMatchesTranscriptionSegment() {
        Meeting meeting = meeting(1L, "Hop hoi dong");
        when(meetingRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(meeting));
        when(transcriptionSegmentRepository.findByTextContainingIgnoreCase(
                eq("phan bien"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(segment(meeting, "can phan bien noi dung nay"))));
        when(memberRepository.findByMeetingId(1L)).thenReturn(List.of());

        Page<MeetingSearchResult> result = searchService.searchMeetings(
                request("phan bien"), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(MeetingSearchResult::getTitle)
                .containsExactly("Hop hoi dong");
    }

    @Test
    void searchMeetings_deduplicatesMeetingMatchedByMultipleTranscriptionSegments() {
        Meeting meeting = meeting(1L, "Hop hoi dong");
        when(meetingRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(meeting));
        when(transcriptionSegmentRepository.findByTextContainingIgnoreCase(
                eq("phan bien"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        segment(meeting, "phan bien lan mot"),
                        segment(meeting, "phan bien lan hai"))));
        when(memberRepository.findByMeetingId(1L)).thenReturn(List.of());

        Page<MeetingSearchResult> result = searchService.searchMeetings(
                request("phan bien"), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(MeetingSearchResult::getTitle)
                .containsExactly("Hop hoi dong");
    }

    private SearchMeetingRequest request(String keyword) {
        return SearchMeetingRequest.builder().keyword(keyword).build();
    }

    private Meeting meeting(Long id, String title) {
        return Meeting.builder()
                .id(id)
                .code("MTG-" + id)
                .title(title)
                .startTime(LocalDateTime.of(2026, 6, 9, 9, 0))
                .endTime(LocalDateTime.of(2026, 6, 9, 10, 0))
                .build();
    }

    private TranscriptionSegment segment(Meeting meeting, String text) {
        return TranscriptionSegment.builder()
                .id(System.nanoTime())
                .jobId("job-" + System.nanoTime())
                .meeting(meeting)
                .text(text)
                .segmentStartTime(LocalDateTime.of(2026, 6, 9, 9, 5))
                .build();
    }
}
