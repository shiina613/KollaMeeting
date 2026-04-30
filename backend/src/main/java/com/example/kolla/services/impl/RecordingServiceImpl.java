package com.example.kolla.services.impl;

import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.RecordingStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Recording;
import com.example.kolla.models.User;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.RecordingRepository;
import com.example.kolla.responses.RecordingResponse;
import com.example.kolla.services.FileStorageService;
import com.example.kolla.services.MeetingService;
import com.example.kolla.services.RecordingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
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
     * Checks that the current user is the meeting host, secretary, or an ADMIN.
     * Throws ForbiddenException if not.
     */
    private void checkHostOrSecretaryOrAdmin(Meeting meeting, User currentUser, String action) {
        if (currentUser.getRole() == Role.ADMIN) {
            return; // ADMIN always allowed
        }
        boolean isHost = meeting.getHost() != null
                && meeting.getHost().getId().equals(currentUser.getId());
        boolean isSecretary = meeting.getSecretary() != null
                && meeting.getSecretary().getId().equals(currentUser.getId());

        if (!isHost && !isSecretary) {
            throw new ForbiddenException(
                    "Only the meeting Host, Secretary, or an ADMIN may " + action);
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
}
