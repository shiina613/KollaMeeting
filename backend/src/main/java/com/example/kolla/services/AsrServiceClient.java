package com.example.kolla.services;

import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.models.TranscriptionJob;
import com.example.kolla.repositories.TranscriptionJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Submits transcription jobs to the Redis queue when the ASR microservice is healthy.
 */
@Slf4j
@Service
public class AsrServiceClient {

    private final TranscriptionQueueService transcriptionQueueService;
    private final TranscriptionJobRepository transcriptionJobRepository;
    private final RestTemplate restTemplate;
    private final String asrServiceUrl;
    private final AtomicBoolean asrAvailable = new AtomicBoolean(false);

    public AsrServiceClient(
            TranscriptionQueueService transcriptionQueueService,
            TranscriptionJobRepository transcriptionJobRepository,
            @Qualifier("asrServiceRestTemplate") RestTemplate restTemplate,
            @Value("${asr-service.service-url:http://localhost:8000}") String asrServiceUrl) {
        this.transcriptionQueueService = transcriptionQueueService;
        this.transcriptionJobRepository = transcriptionJobRepository;
        this.restTemplate = restTemplate;
        this.asrServiceUrl = asrServiceUrl;
    }

    @Scheduled(fixedDelayString = "${asr-service.health-check-interval-s:30}000")
    public void pollHealth() {
        boolean healthy = checkHealth();
        boolean wasDown = !asrAvailable.getAndSet(healthy);
        if (healthy && wasDown) {
            log.info("ASR service is back online — recovering pending jobs");
            recoverPendingJobs();
        }
    }

    /**
     * Enqueue a PENDING job if ASR is reachable; otherwise leave it PENDING for recovery.
     */
    public void submitJob(TranscriptionJob job) {
        if (job.getStatus() != TranscriptionJobStatus.PENDING) {
            log.warn("submitJob called for job {} in status {}", job.getId(), job.getStatus());
            return;
        }
        if (!checkHealth()) {
            log.warn("ASR service unavailable — job {} stays PENDING", job.getId());
            return;
        }
        transcriptionQueueService.pushJob(job);
    }

    void recoverPendingJobs() {
        List<TranscriptionJob> pending =
                transcriptionJobRepository.findByStatus(TranscriptionJobStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Re-queuing {} PENDING transcription job(s)", pending.size());
        transcriptionQueueService.requeuePendingJobs(pending);
    }

    private boolean checkHealth() {
        try {
            String url = asrServiceUrl.replaceAll("/$", "") + "/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.debug("ASR health check failed: {}", e.getMessage());
            return false;
        }
    }
}
