package com.example.kolla.services.impl;

import com.example.kolla.enums.FileType;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.RecordingStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Recording;
import com.example.kolla.models.TranscriptionJob;
import com.example.kolla.models.User;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.RecordingRepository;
import com.example.kolla.repositories.TranscriptionJobRepository;
import com.example.kolla.responses.RecordingResponse;
import com.example.kolla.services.FileStorageService;
import com.example.kolla.services.MeetingService;
import com.example.kolla.services.RecordingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RecordingService implementation.
 * Requirements: 7.1–7.7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingServiceImpl implements RecordingService {

    private final RecordingRepository recordingRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingService meetingService;
    private final FileStorageService fileStorageService;
    private final TranscriptionJobRepository transcriptionJobRepository;
    private final Clock clock;

    // ── Start Recording ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public RecordingResponse startRecording(Long meetingId, User currentUser) {
        Meeting meeting = findMeetingOrThrow(meetingId);

        // Meeting must be ACTIVE to start recording (Requirement 7.1)
        if (meeting.getStatus() != MeetingStatus.ACTIVE) {
            throw new BadRequestException(
                    "Recording can only be started for an ACTIVE meeting, but meeting is "
                    + meeting.getStatus());
        }

        // Only HOST, SECRETARY, or ADMIN may start recording (Requirement 7.1)
        checkHostOrSecretaryOrAdmin(meeting, currentUser, "start recording");

        String fileName = "recording_" + meetingId + "_" + System.currentTimeMillis() + ".mp4";

        Recording recording = Recording.builder()
                .meeting(meeting)
                .fileName(fileName)
                .status(RecordingStatus.RECORDING)
                .startTime(LocalDateTime.now(clock))
                .createdBy(currentUser)
                .build();

        Recording saved = recordingRepository.save(recording);
        log.info("Started recording id={} for meeting id={} by user '{}'",
                saved.getId(), meetingId, currentUser.getUsername());

        return RecordingResponse.from(saved);
    }

    // ── Stop Recording ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public RecordingResponse stopRecording(Long recordingId, User currentUser) {
        Recording recording = findRecordingOrThrow(recordingId);

        // Only HOST, SECRETARY, or ADMIN may stop recording
        checkHostOrSecretaryOrAdmin(recording.getMeeting(), currentUser, "stop recording");

        if (recording.getStatus() != RecordingStatus.RECORDING) {
            throw new BadRequestException(
                    "Recording is not in RECORDING state; current status: " + recording.getStatus());
        }

        recording.setEndTime(LocalDateTime.now(clock));
        recording.setStatus(RecordingStatus.COMPLETED);

        Recording saved = recordingRepository.save(recording);
        log.info("Stopped recording id={} for meeting id={} by user '{}'",
                recordingId, recording.getMeeting().getId(), currentUser.getUsername());

        return RecordingResponse.from(saved);
    }

    // ── List Recordings ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<RecordingResponse> listRecordings(Long meetingId, User currentUser) {
        findMeetingOrThrow(meetingId); // ensure meeting exists

        // User must be a member of the meeting (Requirement 7.6)
        checkMembership(meetingId, currentUser);

        return recordingRepository.findByMeetingIdOrderByStartTimeDesc(meetingId)
                .stream()
                .map(RecordingResponse::from)
                .toList();
    }

    // ── Get Recording by ID ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public RecordingResponse getRecordingById(Long recordingId, User currentUser) {
        Recording recording = findRecordingOrThrow(recordingId);

        // User must be a member of the meeting
        checkMembership(recording.getMeeting().getId(), currentUser);

        return RecordingResponse.from(recording);
    }

    // ── Delete Recording ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteRecording(Long recordingId, User currentUser) {
        // Only ADMIN may delete recordings (Requirement 7.3)
        if (currentUser.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only ADMIN may delete recordings");
        }

        Recording recording = findRecordingOrThrow(recordingId);

        // Delete file from filesystem if path is set (Requirement 6.6)
        if (recording.getFilePath() != null && !recording.getFilePath().isBlank()) {
            fileStorageService.deleteFile(recording.getFilePath());
        }

        recordingRepository.delete(recording);
        log.info("Deleted recording id={} by admin '{}'", recordingId, currentUser.getUsername());
    }

    // ── Download Recording ────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Resource downloadRecording(Long recordingId, User currentUser) throws IOException {
        Recording recording = findRecordingOrThrow(recordingId);

        // User must be a member of the meeting (Requirement 7.7)
        checkMembership(recording.getMeeting().getId(), currentUser);

        if (recording.getFilePath() == null || recording.getFilePath().isBlank()) {
            throw new BadRequestException(
                    "Recording file is not yet available for download (no file path set)");
        }

        return fileStorageService.loadFileAsResource(recording.getFilePath());
    }

    @Override
    @Transactional
    public RecordingResponse createAggregateRecordingForMeeting(Meeting meeting) throws IOException {
        List<TranscriptionJob> jobs = transcriptionJobRepository.findByMeetingIdOrdered(meeting.getId())
                .stream()
                .filter(job -> job.getAudioPath() != null && !job.getAudioPath().isBlank())
                .toList();
        if (jobs.isEmpty()) {
            log.info("No audio chunks found for meeting id={}, skipping aggregate recording", meeting.getId());
            return null;
        }

        ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
        for (TranscriptionJob job : jobs) {
            byte[] wavBytes = readAudioBytes(job.getAudioPath());
            if (wavBytes.length == 0) {
                continue;
            }
            pcmOut.write(stripWavHeaderIfPresent(wavBytes));
        }

        byte[] pcmBytes = pcmOut.toByteArray();
        if (pcmBytes.length == 0) {
            log.info("Audio chunks for meeting id={} did not contain readable bytes", meeting.getId());
            return null;
        }

        byte[] wavBytes = buildWav16kMonoPcm(pcmBytes);
        String fileName = "meeting-" + meeting.getId() + ".wav";
        Path storedPath = fileStorageService.storeBytes(
                wavBytes, FileType.RECORDING, meeting.getId(), fileName);

        LocalDateTime now = LocalDateTime.now(clock);
        User createdBy = firstNonNull(meeting.getHost(), meeting.getSecretary(), meeting.getCreator());
        Recording recording = Recording.builder()
                .meeting(meeting)
                .fileName(fileName)
                .fileSize((long) wavBytes.length)
                .filePath(storedPath.toString())
                .status(RecordingStatus.COMPLETED)
                .startTime(meeting.getActivatedAt() != null ? meeting.getActivatedAt() : now)
                .endTime(meeting.getEndedAt() != null ? meeting.getEndedAt() : now)
                .createdBy(createdBy)
                .build();

        Recording saved = recordingRepository.save(recording);
        log.info("Created aggregate recording id={} for meeting id={} from {} audio chunks",
                saved.getId(), meeting.getId(), jobs.size());
        return RecordingResponse.from(saved);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Meeting findMeetingOrThrow(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Meeting not found with id: " + meetingId));
    }

    private Recording findRecordingOrThrow(Long recordingId) {
        return recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Recording not found with id: " + recordingId));
    }

    /**
     * Checks that the current user is the meeting host, the meeting's assigned secretary, or ADMIN.
     * Throws ForbiddenException if not.
     */
    private void checkHostOrSecretaryOrAdmin(Meeting meeting, User currentUser, String action) {
        if (currentUser.getRole() == Role.ADMIN) {
            return; // ADMIN may manage all recordings
        }
        boolean isHost = meeting.getHost() != null
                && meeting.getHost().getId().equals(currentUser.getId());
        boolean isMeetingSecretary = meeting.getSecretary() != null
                && meeting.getSecretary().getId().equals(currentUser.getId());
        if (!isHost && !isMeetingSecretary) {
            throw new ForbiddenException(
                    "Only the meeting Host, the meeting's Secretary, or an ADMIN may " + action);
        }
    }

    /**
     * Checks that the current user is a member of the meeting.
     * ADMIN users bypass the membership check.
     * Throws ForbiddenException if not a member.
     */
    private void checkMembership(Long meetingId, User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return; // ADMIN can access all recordings
        }
        if (!meetingService.isMember(meetingId, currentUser.getId())) {
            throw new ForbiddenException(
                    "You are not a member of meeting id: " + meetingId);
        }
    }

    private byte[] readAudioBytes(String audioPath) throws IOException {
        Path path = Path.of(audioPath);
        if (Files.exists(path)) {
            return Files.readAllBytes(path);
        }
        try {
            Resource resource = fileStorageService.loadFileAsResource(audioPath);
            try (InputStream input = resource.getInputStream()) {
                return input.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("Skipping unreadable audio chunk '{}': {}", audioPath, e.getMessage());
            return new byte[0];
        }
    }

    private byte[] stripWavHeaderIfPresent(byte[] wavBytes) {
        if (wavBytes.length > 44
                && wavBytes[0] == 'R'
                && wavBytes[1] == 'I'
                && wavBytes[2] == 'F'
                && wavBytes[3] == 'F'
                && wavBytes[8] == 'W'
                && wavBytes[9] == 'A'
                && wavBytes[10] == 'V'
                && wavBytes[11] == 'E') {
            return java.util.Arrays.copyOfRange(wavBytes, 44, wavBytes.length);
        }
        return wavBytes;
    }

    private byte[] buildWav16kMonoPcm(byte[] pcmBytes) {
        byte[] wav = new byte[44 + pcmBytes.length];
        wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
        writeLe(wav, 4, 36 + pcmBytes.length);
        wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
        wav[12] = 'f'; wav[13] = 'm'; wav[14] = 't'; wav[15] = ' ';
        writeLe(wav, 16, 16);
        wav[20] = 1;
        wav[22] = 1;
        writeLe(wav, 24, 16000);
        writeLe(wav, 28, 32000);
        wav[32] = 2;
        wav[34] = 16;
        wav[36] = 'd'; wav[37] = 'a'; wav[38] = 't'; wav[39] = 'a';
        writeLe(wav, 40, pcmBytes.length);
        System.arraycopy(pcmBytes, 0, wav, 44, pcmBytes.length);
        return wav;
    }

    private void writeLe(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xff);
        target[offset + 1] = (byte) ((value >> 8) & 0xff);
        target[offset + 2] = (byte) ((value >> 16) & 0xff);
        target[offset + 3] = (byte) ((value >> 24) & 0xff);
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
