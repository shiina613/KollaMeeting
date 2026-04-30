package com.example.kolla.websocket;

import com.example.kolla.config.FileStorageProperties;
import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.TranscriptionJob;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.TranscriptionJobRepository;
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

    /** Maximum accumulated PCM bytes before auto-flush (30 s hard cap). */
    private static final int MAX_BUFFER_BYTES = SAMPLE_RATE * FRAME_SIZE * 30; // 960 000 bytes

    private static final String FINALIZE_COMMAND = "FINALIZE";

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final FileStorageProperties storageProperties;
    private final MeetingRepository meetingRepository;
    private final TranscriptionJobRepository transcriptionJobRepository;
    private final GipformerClient gipformerClient;
    private final JwtUtils jwtUtils;
    private final Clock clock;

    // ── Per-session state ─────────────────────────────────────────────────────

    /** Accumulated raw PCM bytes per WebSocket session. */
    private final Map<String, ByteBuffer> sessionBuffers = new ConcurrentHashMap<>();

    /** Metadata extracted from query params on connection. */
    private final Map<String, SessionMeta> sessionMeta = new ConcurrentHashMap<>();

    /** Sequence counter per speakerTurnId. */
    private final Map<String, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();

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

        sessionBuffers.put(sessionId, ByteBuffer.allocate(MAX_BUFFER_BYTES));
        sessionMeta.put(sessionId, new SessionMeta(meetingId, userId, speakerName, speakerTurnId));
        sequenceCounters.computeIfAbsent(speakerTurnId, k -> new AtomicInteger(0));

        log.info("AudioStream [{}]: connected — meetingId={}, userId={}, speakerTurnId={}",
                sessionId, meetingId, userId, speakerTurnId);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String sessionId = session.getId();
        ByteBuffer buffer = sessionBuffers.get(sessionId);
        if (buffer == null) {
            log.warn("AudioStream [{}]: received binary but no buffer; ignoring", sessionId);
            return;
        }

        byte[] payload = message.getPayload().array();
        int remaining = buffer.remaining();

        if (payload.length > remaining) {
            // Buffer full — auto-flush before accepting more data (30 s hard cap)
            log.info("AudioStream [{}]: buffer full ({} bytes), auto-flushing", sessionId, buffer.position());
            flushBuffer(sessionId, session);
            // Reset buffer
            buffer = ByteBuffer.allocate(MAX_BUFFER_BYTES);
            sessionBuffers.put(sessionId, buffer);
        }

        buffer.put(payload);
        log.trace("AudioStream [{}]: buffered {} bytes (total={})", sessionId, payload.length, buffer.position());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();
        String text = message.getPayload().trim();

        if (FINALIZE_COMMAND.equalsIgnoreCase(text)) {
            log.info("AudioStream [{}]: FINALIZE received", sessionId);
            flushBuffer(sessionId, session);
            // Reset buffer for potential next chunk in same session
            sessionBuffers.put(sessionId, ByteBuffer.allocate(MAX_BUFFER_BYTES));
        } else {
            log.debug("AudioStream [{}]: unknown text message '{}'; ignoring", sessionId, text);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        ByteBuffer buffer = sessionBuffers.get(sessionId);

        // Auto-flush any remaining data on disconnect
        if (buffer != null && buffer.position() > 0) {
            log.info("AudioStream [{}]: connection closed with {} buffered bytes; flushing",
                    sessionId, buffer.position());
            flushBuffer(sessionId, session);
        }

        sessionBuffers.remove(sessionId);
        sessionMeta.remove(sessionId);
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

        // Filename: chunk_{seqNum}_{jobId}.wav
        String fileName = String.format("chunk_%d_%s.wav", seqNum, jobId);
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
            String speakerName,
            String speakerTurnId) {
    }
}
