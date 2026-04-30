package com.example.kolla.services.impl;

import com.example.kolla.dto.TranscriptionCallbackRequest;
import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.TranscriptionJob;
import com.example.kolla.models.TranscriptionSegment;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.TranscriptionJobRepository;
import com.example.kolla.repositories.TranscriptionSegmentRepository;
import com.example.kolla.responses.TranscriptionSegmentResponse;
import com.example.kolla.services.TranscriptionService;
import com.example.kolla.websocket.MeetingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of {@link TranscriptionService}.
 *
 * <h3>Idempotency</h3>
 * The {@code transcription_segment} table has a UNIQUE KEY on {@code job_id}.
 * Before inserting, we check if a segment already exists for the job.
 * If it does, we return the existing segment without any side effects.
 *
 * <h3>Priority routing</h3>
 * <ul>
 *   <li>HIGH_PRIORITY: persist + broadcast {@code TRANSCRIPTION_SEGMENT} via WebSocket</li>
 *   <li>NORMAL_PRIORITY: persist only (no broadcast)</li>
 * </ul>
 *
 * Requirements: 8.12, 8.13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionServiceImpl implements TranscriptionService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TranscriptionJobRepository transcriptionJobRepository;
    private final TranscriptionSegmentRepository transcriptionSegmentRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingEventPublisher meetingEventPublisher;
    private final Clock clock;

    @Override
    @Transactional
    public TranscriptionSegmentResponse processCallback(TranscriptionCallbackRequest request) {
        String jobId = request.getJobId();

        // ── Idempotency check ─────────────────────────────────────────────────
        Optional<TranscriptionSegment> existing =
                transcriptionSegmentRepository.findByJobId(jobId);
        if (existing.isPresent()) {
            log.info("TranscriptionService: duplicate callback for job {} — returning existing segment",
                    jobId);
            return toResponse(existing.get());
        }

        // ── Load job ──────────────────────────────────────────────────────────
        TranscriptionJob job = transcriptionJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TranscriptionJob not found: " + jobId));

        Meeting meeting = job.getMeeting();

        // ── Parse segment start time ──────────────────────────────────────────
        LocalDateTime segmentStartTime = parseSegmentStartTime(request.getSegmentStartTime());

        // ── Persist segment ───────────────────────────────────────────────────
        TranscriptionSegment segment = TranscriptionSegment.builder()
                .jobId(jobId)
                .meeting(meeting)
                .speakerId(job.getSpeakerId())
                .speakerName(job.getSpeakerName())
                .speakerTurnId(job.getSpeakerTurnId())
                .sequenceNumber(job.getSequenceNumber())
                .text(request.getText())
                .confidence(request.getConfidence())
                .processingTimeMs(request.getProcessingTimeMs())
                .segmentStartTime(segmentStartTime)
                .createdAt(LocalDateTime.now(clock))
                .build();

        transcriptionSegmentRepository.save(segment);
        log.info("TranscriptionService: persisted segment for job {} (meeting={}, seq={})",
                jobId, meeting.getId(), job.getSequenceNumber());

        // ── Update job status ─────────────────────────────────────────────────
        job.setStatus(TranscriptionJobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now(clock));
        transcriptionJobRepository.save(job);

        // ── Broadcast for HIGH_PRIORITY meetings ──────────────────────────────
        if (meeting.getTranscriptionPriority() == TranscriptionPriority.HIGH_PRIORITY) {
            ZonedDateTime segmentStartZoned = segmentStartTime.atZone(ZONE);
            meetingEventPublisher.publishTranscriptionSegment(
                    meeting.getId(),
                    job.getSpeakerId(),
                    job.getSpeakerName(),
                    job.getSpeakerTurnId(),
                    job.getSequenceNumber(),
                    request.getText(),
                    segmentStartZoned);
            log.debug("TranscriptionService: broadcast TRANSCRIPTION_SEGMENT for meeting {} (HIGH_PRIORITY)",
                    meeting.getId());
        } else {
            log.debug("TranscriptionService: NORMAL_PRIORITY meeting {} — segment persisted only",
                    meeting.getId());
        }

        return toResponse(segment);
    }

    @Override
    public List<TranscriptionSegmentResponse> getSegmentsForMeeting(Long meetingId) {
        if (!meetingRepository.existsById(meetingId)) {
            throw new ResourceNotFoundException("Meeting not found: " + meetingId);
        }
        return transcriptionSegmentRepository
                .findByMeetingIdOrderedForMinutes(meetingId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocalDateTime parseSegmentStartTime(String isoString) {
        try {
            // Try ZonedDateTime first (e.g. "2025-01-01T10:00:00+07:00")
            ZonedDateTime zdt = ZonedDateTime.parse(isoString);
            return zdt.withZoneSameInstant(ZONE).toLocalDateTime();
        } catch (Exception e) {
            try {
                // Fallback: plain LocalDateTime
                return LocalDateTime.parse(isoString, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception ex) {
                log.warn("TranscriptionService: cannot parse segmentStartTime '{}', using now", isoString);
                return LocalDateTime.now(clock);
            }
        }
    }

    private TranscriptionSegmentResponse toResponse(TranscriptionSegment segment) {
        return TranscriptionSegmentResponse.builder()
                .id(segment.getId())
                .jobId(segment.getJobId())
                .meetingId(segment.getMeeting().getId())
                .speakerId(segment.getSpeakerId())
                .speakerName(segment.getSpeakerName())
                .speakerTurnId(segment.getSpeakerTurnId())
                .sequenceNumber(segment.getSequenceNumber())
                .text(segment.getText())
                .confidence(segment.getConfidence())
                .processingTimeMs(segment.getProcessingTimeMs())
                .segmentStartTime(segment.getSegmentStartTime())
                .createdAt(segment.getCreatedAt())
                .build();
    }
}
