package com.example.kolla.websocket;

import com.example.kolla.config.FileStorageProperties;
import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.models.Department;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.TranscriptionJob;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DepartmentRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.TranscriptionJobRepository;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.services.GipformerClient;
import com.example.kolla.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Binary WebSocket handler that receives raw PCM audio frames from the frontend,
 * accumulates them into a per-session buffer, converts to WAV 16 kHz mono, saves
 * to {@code /app/storage/audio_chunks/{meetingId}/{speakerTurnId}/}, and creates
 * a {@link TranscriptionJob} record.
 *
 * <h3>Protocol</h3>
 * <ol>
 *   <li>Client connects to {@code /ws/audio?meetingId=X&speakerTurnId=Y&token=JWT}</li>
 *   <li>Client sends raw PCM Int16 LE frames at 16 kHz mono as binary messages.</li>
 *   <li>Client sends a text message {@code "FINALIZE"} to flush the buffer and
 *       create a transcription job.</li>
 *   <li>Server saves the WAV file and creates a {@link TranscriptionJob}.</li>
 * </ol>
 *
 * <h3>WAV format</h3>
 * <ul>
 *   <li>Sample rate: 16 000 Hz</li>
 *   <li>Channels: 1 (mono)</li>
 *   <li>Sample size: 16 bits (PCM signed, little-endian)</li>
 * </ul>
 *
 * Requirements: 8.14, 8.15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudioStreamHandler extends AbstractWebSocketHandler {

    // ── Constants ────────────────────────────────────────────────────────────

    private static final int SAMPLE_RATE = 16_000;
    private static final int CHANNELS = 1;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int FRAME_SIZE = CHANNELS * (SAMPLE_SIZE_BITS / 8); // 2 bytes
    private static final boolean BIG_ENDIAN = false; // PCM LE

    /** Hard cap Normal meeting: 10 phút voiced audio. */
    private static final int NORMAL_MAX_VOICED_BYTES  = SAMPLE_RATE * FRAME_SIZE * 600;

    /** Hard cap Priority meeting: 3 phút voiced audio. */
    private static final int PRIORITY_MAX_VOICED_BYTES = SAMPLE_RATE * FRAME_SIZE * 180;

    /** Ngưỡng RMS (PCM Int16, scale 0–32767) để phân biệt voice/silence. */
    private static final double VAD_ENERGY_THRESHOLD  = 300.0;

    /** Silence threshold (ms) cho Normal meeting — cố định. */
    private static final long NORMAL_SILENCE_MS       = 2_500;

    /** Silence threshold (ms) — Priority meeting, voiced ≤ 15s. */
    private static final long PRIORITY_SILENCE_P1_MS  = 1_500;

    /** Silence threshold (ms) — Priority meeting, voiced 15–30s. */
    private static final long PRIORITY_SILENCE_P2_MS  =   800;

    /** Silence threshold (ms) — Priority meeting, voiced > 30s (flush ngay). */
    private static final long PRIORITY_SILENCE_P3_MS  =   300;

    /** Voiced bytes tương đương 15s (boundary phase 1→2). */
    private static final int PRIORITY_PHASE1_BYTES    = SAMPLE_RATE * FRAME_SIZE * 15;

    /** Voiced bytes tương đương 30s (boundary phase 2→3). */
    private static final int PRIORITY_PHASE2_BYTES    = SAMPLE_RATE * FRAME_SIZE * 30;

    /** Tối thiểu voiced bytes trước khi cho phép auto-flush (1.5s). */
    private static final int MIN_CHUNK_VOICED_BYTES   = SAMPLE_RATE * FRAME_SIZE * 3 / 2;

    private static final String FINALIZE_COMMAND = "FINALIZE";

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final FileStorageProperties storageProperties;
    private final MeetingRepository meetingRepository;
    private final TranscriptionJobRepository transcriptionJobRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final GipformerClient gipformerClient;
    private final JwtUtils jwtUtils;
    private final Clock clock;

    // ── Per-session state ─────────────────────────────────────────────────────

    /** Accumulated voiced PCM bytes per WebSocket session. */
    private final Map<String, ByteBuffer> sessionBuffers = new ConcurrentHashMap<>();

    /** Metadata extracted from query params on connection. */
    private final Map<String, SessionMeta> sessionMeta = new ConcurrentHashMap<>();

    /** Sequence counter per speakerTurnId. */
    private final Map<String, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();

    /** Total voiced bytes accumulated in current chunk (after VAD filtering), per session. */
    private final Map<String, AtomicInteger> voicedBytesCounters = new ConcurrentHashMap<>();

    /** Timestamp (ms) when current silence period started; -1L if voice is active. */
    private final Map<String, Long> silenceStartTimes = new ConcurrentHashMap<>();

    // ── WebSocket lifecycle ───────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        Map<String, String> params = parseQueryParams(session);

        String token = params.get("token");
        String meetingIdStr = params.get("meetingId");
        String speakerTurnId = params.get("speakerTurnId");

        if (token == null || meetingIdStr == null || speakerTurnId == null) {
            log.warn("AudioStream [{}]: missing required query params; closing", sessionId);
            closeSession(session, CloseStatus.BAD_DATA);
            return;
        }

        if (!jwtUtils.validateToken(token)) {
            log.warn("AudioStream [{}]: invalid JWT; closing", sessionId);
            closeSession(session, CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        Long userId;
        String speakerName;
        try {
            userId = jwtUtils.getUserIdFromToken(token);
            speakerName = jwtUtils.getSubjectFromToken(token);
        } catch (Exception e) {
            log.warn("AudioStream [{}]: JWT extraction failed: {}", sessionId, e.getMessage());
            closeSession(session, CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        Long meetingId;
        try {
            meetingId = Long.parseLong(meetingIdStr);
        } catch (NumberFormatException e) {
            log.warn("AudioStream [{}]: invalid meetingId '{}'", sessionId, meetingIdStr);
            closeSession(session, CloseStatus.BAD_DATA);
            return;
        }

        // Lookup full name, username and department name
        String username = speakerName; // fallback: JWT subject
        String departmentName = "unknown";
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                username = user.getUsername();
                // Use fullName for display; fall back to username if null
                speakerName = (user.getFullName() != null && !user.getFullName().isBlank())
                        ? user.getFullName()
                        : user.getUsername();
                if (user.getDepartmentId() != null) {
                    departmentName = departmentRepository.findById(user.getDepartmentId())
                            .map(Department::getName)
                            .orElse("unknown");
                }
            }
        } catch (Exception e) {
            log.warn("AudioStream [{}]: failed to lookup user/department for userId={}: {}",
                    sessionId, userId, e.getMessage());
        }

        // Allocate at max Normal cap; hard cap check in handleBinaryMessage will limit Priority
        sessionBuffers.put(sessionId, ByteBuffer.allocate(NORMAL_MAX_VOICED_BYTES));
        sessionMeta.put(sessionId, new SessionMeta(meetingId, userId, username, speakerName, departmentName, speakerTurnId));
        sequenceCounters.computeIfAbsent(speakerTurnId, k -> new AtomicInteger(0));
        voicedBytesCounters.put(sessionId, new AtomicInteger(0));
        silenceStartTimes.put(sessionId, -1L);

        log.info("AudioStream [{}]: connected — meetingId={}, userId={}, dept={}, speakerTurnId={}",
                sessionId, meetingId, userId, departmentName, speakerTurnId);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String sessionId = session.getId();
        ByteBuffer buffer = sessionBuffers.get(sessionId);
        if (buffer == null) {
            log.warn("AudioStream [{}]: received binary but no buffer; ignoring", sessionId);
            return;
        }

        SessionMeta meta = sessionMeta.get(sessionId);
        if (meta == null) return;

        // Safety check: only accept audio in MEETING_MODE (TASK-003)
        Meeting meeting = meetingRepository.findById(meta.meetingId()).orElse(null);
        if (meeting == null || meeting.getMode() != MeetingMode.MEETING_MODE) {
            log.debug("AudioStream [{}]: meeting not in MEETING_MODE, dropping audio frame", sessionId);
            return;
        }

        boolean isPriority = meeting.getTranscriptionPriority() != TranscriptionPriority.NORMAL_PRIORITY;
        byte[] payload = message.getPayload().array();
        boolean isVoice = computeRms(payload) >= VAD_ENERGY_THRESHOLD;

        if (isVoice) {
            // Voice frame: check hard cap then buffer
            int voicedBytes = voicedBytesCounters.get(sessionId).get();
            int maxBytes = isPriority ? PRIORITY_MAX_VOICED_BYTES : NORMAL_MAX_VOICED_BYTES;
            if (voicedBytes + payload.length > maxBytes) {
                log.info("AudioStream [{}]: hard cap ({} s) reached, flushing",
                        sessionId, isPriority ? 180 : 600);
                flushBuffer(sessionId, session);
                resetSessionBuffer(sessionId);
                buffer = sessionBuffers.get(sessionId);
            }
            buffer.put(payload);
            voicedBytesCounters.get(sessionId).addAndGet(payload.length);
            silenceStartTimes.put(sessionId, -1L); // reset silence timer
            log.trace("AudioStream [{}]: voice frame buffered (voiced={} bytes)",
                    sessionId, voicedBytesCounters.get(sessionId).get());
        } else {
            // Silence frame: don't buffer, track silence duration for auto-flush
            long now = System.currentTimeMillis();
            Long silenceStart = silenceStartTimes.get(sessionId);
            if (silenceStart == null || silenceStart == -1L) {
                silenceStartTimes.put(sessionId, now); // silence just started
            } else {
                long silenceDurationMs = now - silenceStart;
                int voicedBytes = voicedBytesCounters.get(sessionId).get();
                long threshold = isPriority
                        ? getSilenceThreshold(voicedBytes)
                        : NORMAL_SILENCE_MS;
                if (silenceDurationMs >= threshold && voicedBytes >= MIN_CHUNK_VOICED_BYTES) {
                    log.info("AudioStream [{}]: silence {}ms \u2265 threshold {}ms \u2192 auto-flush (voiced={} bytes)",
                            sessionId, silenceDurationMs, threshold, voicedBytes);
                    flushBuffer(sessionId, session);
                    resetSessionBuffer(sessionId);
                }
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();
        String text = message.getPayload().trim();

        if (FINALIZE_COMMAND.equalsIgnoreCase(text)) {
            log.info("AudioStream [{}]: FINALIZE received", sessionId);
            flushBuffer(sessionId, session);
            resetSessionBuffer(sessionId);
        } else {
            log.debug("AudioStream [{}]: unknown text message '{}'; ignoring", sessionId, text);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        ByteBuffer buffer = sessionBuffers.get(sessionId);

        // Auto-flush any remaining voiced data on disconnect
        if (buffer != null && buffer.position() > 0) {
            log.info("AudioStream [{}]: connection closed with {} buffered bytes; flushing",
                    sessionId, buffer.position());
            flushBuffer(sessionId, session);
        }

        sessionBuffers.remove(sessionId);
        sessionMeta.remove(sessionId);
        voicedBytesCounters.remove(sessionId);
        silenceStartTimes.remove(sessionId);
        log.info("AudioStream [{}]: connection closed — status={}", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("AudioStream [{}]: transport error: {}", session.getId(), exception.getMessage());
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }

    // ── Core logic ────────────────────────────────────────────────────────────

    /**
     * Flush the accumulated PCM buffer: convert to WAV, save to disk, create a
     * {@link TranscriptionJob}, and push it to the Redis queue.
     */
    private void flushBuffer(String sessionId, WebSocketSession session) {
        ByteBuffer buffer = sessionBuffers.get(sessionId);
        SessionMeta meta = sessionMeta.get(sessionId);

        if (buffer == null || meta == null) {
            log.warn("AudioStream [{}]: flushBuffer called but state missing", sessionId);
            return;
        }

        int pcmBytes = buffer.position();
        if (pcmBytes == 0) {
            log.debug("AudioStream [{}]: buffer empty, nothing to flush", sessionId);
            return;
        }

        // Extract PCM bytes
        byte[] pcmData = new byte[pcmBytes];
        buffer.flip();
        buffer.get(pcmData);

        // Determine sequence number for this chunk
        int seqNum = sequenceCounters
                .computeIfAbsent(meta.speakerTurnId, k -> new AtomicInteger(0))
                .incrementAndGet();

        String jobId = UUID.randomUUID().toString();

        try {
            // 1. Convert PCM → WAV and save to disk
            String audioPath = saveWavFile(pcmData, meta, seqNum, jobId);

            // 2. Load meeting to get priority
            Meeting meeting = meetingRepository.findById(meta.meetingId).orElse(null);
            if (meeting == null) {
                log.error("AudioStream [{}]: meeting {} not found; dropping chunk", sessionId, meta.meetingId);
                return;
            }

            TranscriptionPriority priority = meeting.getTranscriptionPriority();

            // 3. Persist TranscriptionJob
            TranscriptionJob job = TranscriptionJob.builder()
                    .id(jobId)
                    .meeting(meeting)
                    .speakerId(meta.userId)
                    .speakerName(meta.speakerName)
                    .speakerDept(meta.departmentName)
                    .speakerTurnId(meta.speakerTurnId)
                    .sequenceNumber(seqNum)
                    .priority(priority)
                    .status(TranscriptionJobStatus.PENDING)
                    .audioPath(audioPath)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now(clock))
                    .build();

            transcriptionJobRepository.save(job);
            log.info("AudioStream [{}]: saved job {} (seq={}, priority={}, path={})",
                    sessionId, jobId, seqNum, priority, audioPath);

            // 4. Submit to Gipformer (handles availability check, retry, and queue push)
            gipformerClient.submitJob(job);

            // 5. Notify frontend about successful flush
            int voicedBytes = voicedBytesCounters.getOrDefault(sessionId, new AtomicInteger(0)).get();
            int voicedMs = (int) ((long) voicedBytes * 1000 / (SAMPLE_RATE * FRAME_SIZE));
            sendFlushNotification(session, seqNum, voicedMs);

        } catch (Exception e) {
            log.error("AudioStream [{}]: failed to flush buffer for job {}: {}",
                    sessionId, jobId, e.getMessage(), e);
        }
    }

    /**
     * Convert raw PCM Int16 LE bytes to a WAV file at 16 kHz mono and save it.
     *
     * @param pcmData raw PCM bytes (Int16 LE, 16 kHz, mono)
     * @param meta    session metadata
     * @param seqNum  sequence number within the speaker turn
     * @param jobId   UUID for the job (used in filename)
     * @return absolute path to the saved WAV file
     */
    private String saveWavFile(byte[] pcmData, SessionMeta meta, int seqNum, String jobId)
            throws IOException {

        // Build directory: /app/storage/audio_chunks/{meetingId}/{speakerTurnId}/
        Path dir = Paths.get(storageProperties.getBasePath())
                .resolve(storageProperties.getAudioChunksDir())
                .resolve(String.valueOf(meta.meetingId))
                .resolve(meta.speakerTurnId);
        Files.createDirectories(dir);

        // Filename: username-department-userId-seqNum.wav
        String safeName = meta.username().replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeDept = meta.departmentName().replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileName = String.format("%s-%s-%d-%d.wav", safeName, safeDept, meta.userId(), seqNum);
        Path wavPath = dir.resolve(fileName);

        // AudioFormat: 16 kHz, 16-bit, mono, signed, little-endian
        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE,
                SAMPLE_SIZE_BITS,
                CHANNELS,
                FRAME_SIZE,
                SAMPLE_RATE,
                BIG_ENDIAN);

        // Wrap PCM bytes in AudioInputStream and write as WAV
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
             AudioInputStream audioInputStream = new AudioInputStream(bais, format,
                     pcmData.length / FRAME_SIZE)) {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, wavPath.toFile());
        }

        log.debug("Saved WAV: {} ({} PCM bytes → {} WAV bytes)",
                wavPath, pcmData.length, Files.size(wavPath));

        return wavPath.toAbsolutePath().toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Compute RMS energy of a PCM Int16 LE frame.
     * Samples are interleaved as 2-byte little-endian signed integers.
     *
     * @param pcmFrame raw PCM bytes
     * @return RMS value in range [0, 32767]; 0.0 for empty or too-short frames
     */
    private static double computeRms(byte[] pcmFrame) {
        if (pcmFrame == null || pcmFrame.length < 2) return 0.0;
        long sumSquares = 0;
        int samples = pcmFrame.length / 2;
        for (int i = 0; i < pcmFrame.length - 1; i += 2) {
            // Little-endian Int16
            int sample = (pcmFrame[i] & 0xFF) | (pcmFrame[i + 1] << 8);
            sumSquares += (long) sample * sample;
        }
        return Math.sqrt((double) sumSquares / samples);
    }

    /**
     * Return adaptive silence threshold (ms) for Priority meetings
     * based on how much voiced audio has accumulated.
     *
     * @param voicedBytes bytes of voiced audio buffered so far
     * @return silence duration (ms) that triggers a flush
     */
    private static long getSilenceThreshold(int voicedBytes) {
        if (voicedBytes <= PRIORITY_PHASE1_BYTES) return PRIORITY_SILENCE_P1_MS;
        if (voicedBytes <= PRIORITY_PHASE2_BYTES) return PRIORITY_SILENCE_P2_MS;
        return PRIORITY_SILENCE_P3_MS;
    }

    /**
     * Reset per-session buffer and VAD state after a flush.
     * Allocates a fresh ByteBuffer and zeroes voiced counter and silence timer.
     */
    private void resetSessionBuffer(String sessionId) {
        sessionBuffers.put(sessionId, ByteBuffer.allocate(NORMAL_MAX_VOICED_BYTES));
        AtomicInteger vc = voicedBytesCounters.get(sessionId);
        if (vc != null) vc.set(0);
        silenceStartTimes.put(sessionId, -1L);
    }

    /**
     * Send a {@code CHUNK_FLUSHED} notification back to the frontend
     * so it can log the flush event in the browser console.
     *
     * @param session  WebSocket session to reply to
     * @param seqNum   sequence number of the flushed chunk
     * @param voicedMs duration of voiced audio (ms) in the flushed chunk
     */
    private void sendFlushNotification(WebSocketSession session, int seqNum, int voicedMs) {
        if (session == null || !session.isOpen()) return;
        try {
            String json = String.format(
                    "{\"type\":\"CHUNK_FLUSHED\",\"seqNum\":%d,\"voicedMs\":%d}",
                    seqNum, voicedMs);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.warn("AudioStream [{}]: failed to send flush notification: {}",
                    session.getId(), e.getMessage());
        }
    }

    /**
     * Parse query parameters from the WebSocket session URI.
     * e.g. {@code /ws/audio?meetingId=1&speakerTurnId=abc&token=xyz}
     */
    private Map<String, String> parseQueryParams(WebSocketSession session) {
        Map<String, String> params = new ConcurrentHashMap<>();
        if (session.getUri() == null) {
            return params;
        }
        String query = session.getUri().getQuery();
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }

    private void closeSession(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.warn("AudioStream: failed to close session {}: {}", session.getId(), e.getMessage());
        }
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /**
     * Immutable metadata extracted from the WebSocket connection query params.
     */
    private record SessionMeta(
            Long meetingId,
            Long userId,
            String username,
            String speakerName,
            String departmentName,
            String speakerTurnId) {
    }
}
