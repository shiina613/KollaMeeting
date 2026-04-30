package com.example.kolla.services;

import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.TranscriptionJob;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.TranscriptionJobRepository;
import com.example.kolla.websocket.MeetingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * HTTP client for communicating with the Gipformer FastAPI service.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li>Submit transcription jobs: push to Redis queue, then notify Gipformer via
 *       {@code POST /jobs} so it can start processing immediately.</li>
 *   <li>Perform periodic health checks via {@code GET /health} every 30 s.</li>
 *   <li>Retry Gipformer notification up to {@code gipformer.max-retries} times.</li>
 *   <li>When Gipformer is unavailable: save job with status=PENDING (not in Redis)
 *       and broadcast {@code TRANSCRIPTION_UNAVAILABLE} to all active meetings.</li>
 *   <li>When Gipformer recovers: query all PENDING jobs and re-queue them
 *       via {@link TranscriptionQueueService#requeuePendingJobs}.</li>
 * </ol>
 *
 * Requirements: 8.7, 8.11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GipformerClient {

    // ── Dependencies ─────────────────────────────────────────────────────────

    @Qualifier("gipformerRestTemplate")
    private final RestTemplate gipformerRestTemplate;
    private final TranscriptionJobRepository transcriptionJobRepository;
    private final TranscriptionQueueService transcriptionQueueService;
    private final MeetingRepository meetingRepository;
    private final MeetingEventPublisher meetingEventPublisher;
    private final Clock clock;

    // ── Config ────────────────────────────────────────────────────────────────

    @Value("${gipformer.service-url:http://localhost:8000}")
    private String gipformerServiceUrl;

    @Value("${gipformer.max-retries:3}")
    private int maxRetries;

    @Value("${gipformer.retry-delay-ms:2000}")
    private long retryDelayMs;

    // ── State ─────────────────────────────────────────────────────────────────

    /** Whether Gipformer is currently reachable. */
    private final AtomicBoolean gipformerAvailable = new AtomicBoolean(true);

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Submit a transcription job.
     *
     * <p>Flow when Gipformer is available:
     * <ol>
     *   <li>Push job to Redis Sorted Set (durable; Gipformer polls this)</li>
     *   <li>Notify Gipformer via {@code POST /jobs} for immediate processing</li>
     * </ol>
     *
     * <p>Flow when Gipformer is unavailable:
     * <ol>
     *   <li>Save job with status=PENDING (NOT pushed to Redis)</li>
     *   <li>Broadcast {@code TRANSCRIPTION_UNAVAILABLE} to the meeting</li>
     *   <li>Recovery: when health check detects Gipformer is back, all PENDING
     *       jobs are pushed to Redis and Gipformer is notified</li>
     * </ol>
     *
     * @param job the job to submit (must already be persisted in DB)
     * Requirements: 8.7, 8.11
     */
    public void submitJob(TranscriptionJob job) {
        if (!gipformerAvailable.get()) {
            log.warn("GipformerClient: service unavailable, saving job {} as PENDING", job.getId());
            markJobPending(job, "Gipformer service unavailable");
            return;
        }

        // 1. Push to Redis queue (durable; Gipformer polls this independently)
        transcriptionQueueService.pushJob(job);

        // 2. Notify Gipformer to start processing immediately (best-effort)
        notifyGipformer(job);
    }

    /**
     * Check whether Gipformer is currently available.
     */
    public boolean isAvailable() {
        return gipformerAvailable.get();
    }

    // ── Scheduled health check ────────────────────────────────────────────────

    /**
     * Periodic health check every 30 seconds.
     * On recovery: re-queue all PENDING jobs.
     * Requirements: 8.7
     */
    @Scheduled(fixedDelayString = "${gipformer.health-check-interval-s:30}000")
    public void performHealthCheck() {
        boolean wasAvailable = gipformerAvailable.get();
        boolean nowAvailable = checkHealth();

        if (!wasAvailable && nowAvailable) {
            // Service recovered
            log.info("GipformerClient: service RECOVERED — re-queuing PENDING jobs");
            gipformerAvailable.set(true);
            recoverPendingJobs();
        } else if (wasAvailable && !nowAvailable) {
            log.warn("GipformerClient: service became UNAVAILABLE");
            gipformerAvailable.set(false);
        } else if (!nowAvailable) {
            log.debug("GipformerClient: service still unavailable");
        }
    }

    // ── Recovery ─────────────────────────────────────────────────────────────

    /**
     * Query all PENDING jobs and push them back to the Redis queue.
     * Called when Gipformer comes back online.
     * Requirements: 8.7
     */
    public void recoverPendingJobs() {
        List<TranscriptionJob> pendingJobs =
                transcriptionJobRepository.findByStatus(TranscriptionJobStatus.PENDING);

        if (pendingJobs.isEmpty()) {
            log.info("GipformerClient: no PENDING jobs to recover");
            return;
        }

        log.info("GipformerClient: recovering {} PENDING jobs", pendingJobs.size());
        transcriptionQueueService.requeuePendingJobs(pendingJobs);

        // Broadcast recovery event to all affected meetings
        Map<Long, Long> meetingJobCounts = pendingJobs.stream()
                .collect(Collectors.groupingBy(
                        j -> j.getMeeting().getId(),
                        Collectors.counting()));

        meetingJobCounts.forEach((meetingId, count) -> {
            try {
                meetingEventPublisher.publishTranscriptionRecovered(meetingId, count.intValue());
            } catch (Exception e) {
                log.warn("GipformerClient: failed to broadcast recovery for meeting {}: {}",
                        meetingId, e.getMessage());
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Notify Gipformer of a new job via POST /jobs with retry logic.
     * If all retries fail, the job remains in Redis for Gipformer to poll.
     */
    private void notifyGipformer(TranscriptionJob job) {
        Map<String, Object> requestBody = buildJobRequest(job);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ResponseEntity<Map> response = gipformerRestTemplate.exchange(
                        gipformerServiceUrl + "/jobs",
                        HttpMethod.POST,
                        entity,
                        Map.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("GipformerClient: notified Gipformer of job {} (attempt {})",
                            job.getId(), attempt);
                    return;
                } else {
                    log.warn("GipformerClient: job {} notification returned status {} (attempt {})",
                            job.getId(), response.getStatusCode(), attempt);
                }
            } catch (ResourceAccessException e) {
                log.warn("GipformerClient: connection error on attempt {}/{} for job {}: {}",
                        attempt, maxRetries, job.getId(), e.getMessage());
                handleGipformerUnavailable();
                break; // No point retrying if service is down
            } catch (Exception e) {
                log.warn("GipformerClient: error on attempt {}/{} for job {}: {}",
                        attempt, maxRetries, job.getId(), e.getMessage());
            }

            if (attempt < maxRetries) {
                sleep(retryDelayMs);
            }
        }

        // Notification failed — job is still in Redis queue; Gipformer will poll it
        log.warn("GipformerClient: failed to notify Gipformer for job {} after {} attempts; "
                + "job remains in Redis queue for polling",
                job.getId(), maxRetries);
    }

    /**
     * Perform a single GET /health request.
     *
     * @return true if Gipformer responds with 2xx
     */
    private boolean checkHealth() {
        try {
            ResponseEntity<Map> response = gipformerRestTemplate.getForEntity(
                    gipformerServiceUrl + "/health", Map.class);
            boolean healthy = response.getStatusCode().is2xxSuccessful();
            log.debug("GipformerClient: health check → {}", healthy ? "OK" : "FAIL");
            return healthy;
        } catch (Exception e) {
            log.debug("GipformerClient: health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Called when a connection error is detected during job submission.
     * Marks service as unavailable and broadcasts to all active meetings.
     */
    private void handleGipformerUnavailable() {
        if (gipformerAvailable.compareAndSet(true, false)) {
            log.warn("GipformerClient: marking service as UNAVAILABLE");
            broadcastUnavailableToActiveMeetings();
        }
    }

    /**
     * Broadcast TRANSCRIPTION_UNAVAILABLE to all currently ACTIVE meetings.
     * Requirements: 8.7
     */
    private void broadcastUnavailableToActiveMeetings() {
        try {
            List<Meeting> activeMeetings = meetingRepository.findByStatus(MeetingStatus.ACTIVE);
            for (Meeting meeting : activeMeetings) {
                meetingEventPublisher.publishTranscriptionUnavailable(
                        meeting.getId(), "Transcription service is temporarily unavailable");
            }
            log.info("GipformerClient: broadcast TRANSCRIPTION_UNAVAILABLE to {} active meetings",
                    activeMeetings.size());
        } catch (Exception e) {
            log.error("GipformerClient: failed to broadcast unavailable event: {}", e.getMessage());
        }
    }

    /**
     * Mark a job as PENDING (not queued) and record the error message.
     */
    private void markJobPending(TranscriptionJob job, String errorMessage) {
        job.setStatus(TranscriptionJobStatus.PENDING);
        job.setErrorMessage(errorMessage);
        transcriptionJobRepository.save(job);
    }

    /**
     * Build the JSON request body for POST /jobs.
     */
    private Map<String, Object> buildJobRequest(TranscriptionJob job) {
        Map<String, Object> body = new HashMap<>();
        body.put("jobId", job.getId());
        body.put("meetingId", job.getMeeting().getId());
        body.put("speakerId", job.getSpeakerId());
        body.put("speakerName", job.getSpeakerName());
        body.put("speakerTurnId", job.getSpeakerTurnId());
        body.put("sequenceNumber", job.getSequenceNumber());
        body.put("priority", job.getPriority().name());
        body.put("audioPath", job.getAudioPath());
        body.put("callbackUrl", "/api/v1/transcription/callback");
        return body;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
