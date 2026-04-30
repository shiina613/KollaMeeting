package com.example.kolla.services.impl;

import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.models.TranscriptionJob;
import com.example.kolla.repositories.TranscriptionJobRepository;
import com.example.kolla.services.TranscriptionQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Redis Sorted Set implementation of {@link TranscriptionQueueService}.
 *
 * <h3>Score formula</h3>
 * <pre>
 *   HIGH_PRIORITY:   score = 1_000_000_000 - (unix_ms % 1_000_000_000)
 *   NORMAL_PRIORITY: score = unix_ms % 1_000_000_000
 * </pre>
 *
 * <p>Because HIGH scores are in the range [0, 1B) and NORMAL scores are also in [0, 1B),
 * we add a base offset of 1B to HIGH scores so they always outrank NORMAL:
 * <pre>
 *   HIGH_PRIORITY:   score = 1_000_000_000 + (1_000_000_000 - unix_ms % 1_000_000_000)
 *   NORMAL_PRIORITY: score = unix_ms % 1_000_000_000
 * </pre>
 * This ensures every HIGH job has a score ≥ 1B and every NORMAL job has a score < 1B.
 * Within HIGH, older jobs have a higher score (processed first).
 * Within NORMAL, newer jobs have a higher score (FIFO-ish, but inverted — see design.md).
 *
 * Requirements: 8.10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionQueueServiceImpl implements TranscriptionQueueService {

    // ── Score constants ───────────────────────────────────────────────────────

    /** Modulus to keep scores within a manageable range. */
    private static final long SCORE_MOD = 1_000_000_000L;

    /** Base offset added to HIGH_PRIORITY scores so they always outrank NORMAL. */
    private static final long HIGH_BASE = 1_000_000_000L;

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final StringRedisTemplate redisTemplate;
    private final TranscriptionJobRepository transcriptionJobRepository;
    private final Clock clock;

    @Value("${transcription.queue.sorted-set-key:transcription:queue}")
    private String sortedSetKey;

    @Value("${transcription.queue.job-hash-prefix:transcription:job:}")
    private String jobHashPrefix;

    // ── TranscriptionQueueService ─────────────────────────────────────────────

    @Override
    public void pushJob(TranscriptionJob job) {
        double score = computeScore(job.getPriority(),
                job.getCreatedAt().atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                        .toInstant().toEpochMilli());

        // Store job details in Redis Hash for the worker
        storeJobHash(job);

        // Add to Sorted Set
        redisTemplate.opsForZSet().add(sortedSetKey, job.getId(), score);

        // Update DB status → QUEUED
        job.setStatus(TranscriptionJobStatus.QUEUED);
        job.setQueuedAt(LocalDateTime.now(clock));
        transcriptionJobRepository.save(job);

        log.info("Queued job {} (priority={}, score={:.0f})", job.getId(), job.getPriority(), score);
    }

    @Override
    public Optional<String> popHighestPriorityJobId() {
        // ZPOPMAX returns the member with the highest score
        Set<String> result = redisTemplate.opsForZSet().popMax(sortedSetKey, 1)
                .stream()
                .map(tv -> tv.getValue())
                .collect(java.util.stream.Collectors.toSet());

        if (result.isEmpty()) {
            return Optional.empty();
        }
        String jobId = result.iterator().next();
        log.debug("Popped job {} from queue", jobId);
        return Optional.of(jobId);
    }

    @Override
    public void rescoreJob(String jobId, TranscriptionPriority newPriority) {
        // Check if job is in the queue
        Double currentScore = redisTemplate.opsForZSet().score(sortedSetKey, jobId);
        if (currentScore == null) {
            log.debug("rescoreJob: job {} not in queue, skipping", jobId);
            return;
        }

        long nowMs = clock.instant().toEpochMilli();
        double newScore = computeScore(newPriority, nowMs);
        redisTemplate.opsForZSet().add(sortedSetKey, jobId, newScore);

        // Update hash priority field
        redisTemplate.opsForHash().put(jobHashPrefix + jobId, "priority", newPriority.name());

        log.info("Rescored job {} to priority={} score={:.0f}", jobId, newPriority, newScore);
    }

    @Override
    public void removeJob(String jobId) {
        redisTemplate.opsForZSet().remove(sortedSetKey, jobId);
        redisTemplate.delete(jobHashPrefix + jobId);
        log.debug("Removed job {} from queue", jobId);
    }

    @Override
    public void requeuePendingJobs(List<TranscriptionJob> pendingJobs) {
        if (pendingJobs.isEmpty()) {
            return;
        }
        log.info("Re-queuing {} PENDING jobs after Gipformer recovery", pendingJobs.size());
        for (TranscriptionJob job : pendingJobs) {
            try {
                pushJob(job);
            } catch (Exception e) {
                log.error("Failed to re-queue job {}: {}", job.getId(), e.getMessage());
            }
        }
    }

    @Override
    public long getQueueDepth() {
        Long size = redisTemplate.opsForZSet().size(sortedSetKey);
        return size != null ? size : 0L;
    }

    @Override
    public double computeScore(TranscriptionPriority priority, long unixMs) {
        long modMs = unixMs % SCORE_MOD;
        if (priority == TranscriptionPriority.HIGH_PRIORITY) {
            // HIGH: base offset + inverted modMs → older jobs have higher score
            return (double) (HIGH_BASE + (SCORE_MOD - modMs));
        } else {
            // NORMAL: raw modMs → newer jobs have slightly higher score (FIFO within NORMAL)
            return (double) modMs;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Store job metadata in a Redis Hash for the Gipformer worker to read.
     * Key: {@code transcription:job:{jobId}}
     */
    private void storeJobHash(TranscriptionJob job) {
        String hashKey = jobHashPrefix + job.getId();
        Map<String, String> fields = new HashMap<>();
        fields.put("jobId", job.getId());
        fields.put("meetingId", String.valueOf(job.getMeeting().getId()));
        fields.put("speakerId", String.valueOf(job.getSpeakerId()));
        fields.put("speakerName", job.getSpeakerName());
        fields.put("speakerTurnId", job.getSpeakerTurnId());
        fields.put("sequenceNumber", String.valueOf(job.getSequenceNumber()));
        fields.put("priority", job.getPriority().name());
        fields.put("audioPath", job.getAudioPath() != null ? job.getAudioPath() : "");
        fields.put("callbackUrl", "/api/v1/transcription/callback");
        fields.put("createdAt", job.getCreatedAt().toString());
        fields.put("status", job.getStatus().name());

        redisTemplate.opsForHash().putAll(hashKey, fields);
        log.debug("Stored job hash for {}", job.getId());
    }
}
