package com.example.kolla.services.impl;

import com.example.kolla.dto.MeetingSearchResult;
import com.example.kolla.dto.SearchMeetingRequest;
import com.example.kolla.dto.TranscriptionSearchResult;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.TranscriptionSegment;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.MemberRepository;
import com.example.kolla.repositories.TranscriptionSegmentRepository;
import com.example.kolla.services.SearchService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Implementation of {@link SearchService}.
 * Requirements: 13.1–13.7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final MeetingRepository meetingRepository;
    private final MemberRepository memberRepository;
    private final TranscriptionSegmentRepository transcriptionSegmentRepository;

    // ── Meeting search ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<MeetingSearchResult> searchMeetings(SearchMeetingRequest request,
                                                     Pageable pageable) {
        String keyword = normalizeKeyword(request.getKeyword());
        Specification<Meeting> spec = buildMeetingFilterSpec(request);

        if (keyword == null) {
            Page<Meeting> meetings = meetingRepository.findAll(spec, pageable);
            return meetings.map(this::toMeetingSearchResult);
        }

        List<Meeting> candidates = meetingRepository.findAll(spec);
        Set<Long> transcriptionMeetingIds = findMeetingIdsWithMatchingTranscriptions(keyword);

        List<MeetingSearchResult> matched = candidates.stream()
                .filter(meeting -> matchesTitle(meeting, keyword)
                        || transcriptionMeetingIds.contains(meeting.getId()))
                .map(this::toMeetingSearchResult)
                .toList();

        return page(matched, pageable);
    }

    /**
     * Builds a JPA Specification from the search request.
     * All predicates are AND-combined; null/blank fields are ignored.
     * Requirements: 13.1–13.3
     */
    private Specification<Meeting> buildMeetingFilterSpec(SearchMeetingRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Date range: filter on start_time (indexed column)
            if (request.getStartDate() != null) {
                LocalDateTime startOfDay = request.getStartDate().atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), startOfDay));
            }
            if (request.getEndDate() != null) {
                LocalDateTime endOfDay = request.getEndDate().atTime(LocalTime.MAX);
                predicates.add(cb.lessThanOrEqualTo(root.get("startTime"), endOfDay));
            }

            // Room filter (indexed FK)
            if (request.getRoomId() != null) {
                Join<Object, Object> room = root.join("room", JoinType.LEFT);
                predicates.add(cb.equal(room.get("id"), request.getRoomId()));
            }

            // Department filter: join room → department
            if (request.getDepartmentId() != null) {
                Join<Object, Object> room = root.join("room", JoinType.LEFT);
                Join<Object, Object> dept = room.join("department", JoinType.LEFT);
                predicates.add(cb.equal(dept.get("id"), request.getDepartmentId()));
            }

            // Creator filter (indexed FK)
            if (request.getCreatorId() != null) {
                Join<Object, Object> creator = root.join("creator", JoinType.LEFT);
                predicates.add(cb.equal(creator.get("id"), request.getCreatorId()));
            }

            // Avoid duplicate rows when multiple joins are present
            if (query != null) {
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesTitle(Meeting meeting, String keyword) {
        return meeting.getTitle() != null
                && meeting.getTitle().toLowerCase(Locale.ROOT).contains(keyword);
    }

    private Set<Long> findMeetingIdsWithMatchingTranscriptions(String keyword) {
        Page<TranscriptionSegment> segments = transcriptionSegmentRepository
                .findByTextContainingIgnoreCase(keyword, Pageable.unpaged());
        Set<Long> meetingIds = new HashSet<>();
        for (TranscriptionSegment segment : segments.getContent()) {
            if (segment.getMeeting() != null && segment.getMeeting().getId() != null) {
                meetingIds.add(segment.getMeeting().getId());
            }
        }
        return meetingIds;
    }

    private Page<MeetingSearchResult> page(List<MeetingSearchResult> results, Pageable pageable) {
        int start = Math.toIntExact(Math.min(pageable.getOffset(), results.size()));
        int end = Math.min(start + pageable.getPageSize(), results.size());
        return new PageImpl<>(results.subList(start, end), pageable, results.size());
    }

    /**
     * Maps a {@link Meeting} entity to a {@link MeetingSearchResult} DTO.
     * Fetches member count via a separate count query.
     */
    private MeetingSearchResult toMeetingSearchResult(Meeting meeting) {
        long memberCount = memberRepository.findByMeetingId(meeting.getId()).size();

        String roomName = null;
        String departmentName = null;
        if (meeting.getRoom() != null) {
            roomName = meeting.getRoom().getName();
            if (meeting.getRoom().getDepartment() != null) {
                departmentName = meeting.getRoom().getDepartment().getName();
            }
        }

        String creatorName = null;
        if (meeting.getCreator() != null) {
            creatorName = meeting.getCreator().getFullName();
        }

        return MeetingSearchResult.builder()
                .id(meeting.getId())
                .title(meeting.getTitle())
                .description(meeting.getDescription())
                .startTime(meeting.getStartTime())
                .endTime(meeting.getEndTime())
                .status(meeting.getStatus())
                .mode(meeting.getMode())
                .roomName(roomName)
                .departmentName(departmentName)
                .creatorName(creatorName)
                .memberCount(memberCount)
                .build();
    }

    // ── Transcription search ──────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<TranscriptionSearchResult> searchTranscriptions(String keyword,
                                                                  Long meetingId,
                                                                  Pageable pageable) {
        Page<TranscriptionSegment> segments;

        if (meetingId != null) {
            segments = transcriptionSegmentRepository
                    .findByTextContainingIgnoreCaseAndMeetingId(keyword, meetingId, pageable);
        } else {
            segments = transcriptionSegmentRepository
                    .findByTextContainingIgnoreCase(keyword, pageable);
        }

        return segments.map(this::toTranscriptionSearchResult);
    }

    /**
     * Maps a {@link TranscriptionSegment} entity to a {@link TranscriptionSearchResult} DTO.
     */
    private TranscriptionSearchResult toTranscriptionSearchResult(TranscriptionSegment segment) {
        String meetingTitle = null;
        Long segmentMeetingId = null;
        if (segment.getMeeting() != null) {
            segmentMeetingId = segment.getMeeting().getId();
            meetingTitle = segment.getMeeting().getTitle();
        }

        return TranscriptionSearchResult.builder()
                .segmentId(segment.getId())
                .jobId(segment.getJobId())
                .meetingId(segmentMeetingId)
                .meetingTitle(meetingTitle)
                .speakerName(segment.getSpeakerName())
                .speakerTurnId(segment.getSpeakerTurnId())
                .sequenceNumber(segment.getSequenceNumber())
                .text(segment.getText())
                .segmentStartTime(segment.getSegmentStartTime())
                .build();
    }
}
