package com.example.kolla.runtime;

import com.example.kolla.config.FileStorageProperties;
import com.example.kolla.enums.MinutesStatus;
import com.example.kolla.enums.RecordingStatus;
import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.models.AttendanceLog;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Minutes;
import com.example.kolla.models.Notification;
import com.example.kolla.models.ParticipantSession;
import com.example.kolla.models.Recording;
import com.example.kolla.models.SpeakingPermission;
import com.example.kolla.models.StorageLog;
import com.example.kolla.models.TranscriptionJob;
import com.example.kolla.models.TranscriptionSegment;
import com.example.kolla.models.User;
import com.example.kolla.repositories.MeetingRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeMeetingStateStore {

    private final FileStorageProperties storageProperties;
    private final MeetingRepository meetingRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private final AtomicLong minutesIds = new AtomicLong(1);
    private final AtomicLong recordingIds = new AtomicLong(1);
    private final AtomicLong segmentIds = new AtomicLong(1);
    private final AtomicLong attendanceIds = new AtomicLong(1);
    private final AtomicLong permissionIds = new AtomicLong(1);
    private final AtomicLong notificationIds = new AtomicLong(1);
    private final AtomicLong storageLogIds = new AtomicLong(1);

    private final ConcurrentMap<Long, Minutes> minutesByMeetingId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TranscriptionJob> jobsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TranscriptionSegment> segmentsByJobId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Recording> recordingsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, AttendanceLog> attendanceById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ParticipantSession> sessionsBySessionId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, SpeakingPermission> permissionsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Notification> notificationsById = new ConcurrentHashMap<>();

    public boolean minutesExists(Long meetingId) {
        return findMinutesByMeetingId(meetingId).isPresent();
    }

    public synchronized Minutes saveMinutes(Minutes minutes) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (minutes.getId() == null) {
            minutes.setId(minutesIds.getAndIncrement());
            minutes.setCreatedAt(now);
        }
        minutes.setUpdatedAt(now);
        minutesByMeetingId.put(minutes.getMeeting().getId(), minutes);
        writeMinutesState(minutes);
        return minutes;
    }

    public Optional<Minutes> findMinutesByMeetingId(Long meetingId) {
        Minutes cached = minutesByMeetingId.get(meetingId);
        if (cached != null) {
            return Optional.of(cached);
        }
        return loadMinutesState(meetingId);
    }

    public List<Minutes> findDraftMinutesNeedingReminder(LocalDateTime cutoff) {
        loadAllMinutesStates();
        return minutesByMeetingId.values().stream()
                .filter(minutes -> minutes.getStatus() == MinutesStatus.DRAFT)
                .filter(minutes -> minutes.getReminderSentAt() == null)
                .filter(minutes -> minutes.getCreatedAt() != null
                        && minutes.getCreatedAt().isBefore(cutoff))
                .sorted(Comparator.comparing(Minutes::getCreatedAt))
                .toList();
    }

    public synchronized TranscriptionJob saveTranscriptionJob(TranscriptionJob job) {
        if (job.getCreatedAt() == null) {
            job.setCreatedAt(LocalDateTime.now(clock));
        }
        jobsById.put(job.getId(), job);
        writeJobState(job);
        return job;
    }

    public Optional<TranscriptionJob> findTranscriptionJob(String jobId) {
        TranscriptionJob cached = jobsById.get(jobId);
        if (cached != null) {
            return Optional.of(cached);
        }
        return loadJobState(jobId);
    }

    public List<TranscriptionJob> findTranscriptionJobsByMeetingId(Long meetingId) {
        loadJobsForMeeting(meetingId);
        return jobsById.values().stream()
                .filter(job -> job.getMeeting() != null && meetingId.equals(job.getMeeting().getId()))
                .sorted(Comparator
                        .comparing(TranscriptionJob::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TranscriptionJob::getSpeakerTurnId, Comparator.nullsLast(String::compareTo))
                        .thenComparingInt(TranscriptionJob::getSequenceNumber))
                .toList();
    }

    public List<TranscriptionJob> findTranscriptionJobsByStatus(TranscriptionJobStatus status) {
        loadAllJobs();
        return jobsById.values().stream()
                .filter(job -> job.getStatus() == status)
                .sorted(Comparator.comparing(TranscriptionJob::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public synchronized TranscriptionSegment saveTranscriptionSegment(TranscriptionSegment segment) {
        TranscriptionSegment existing = segmentsByJobId.get(segment.getJobId());
        if (existing != null) {
            return existing;
        }
        if (segment.getId() == null) {
            segment.setId(segmentIds.getAndIncrement());
        }
        if (segment.getCreatedAt() == null) {
            segment.setCreatedAt(LocalDateTime.now(clock));
        }
        segmentsByJobId.put(segment.getJobId(), segment);
        appendSegment(segment);
        return segment;
    }

    public Optional<TranscriptionSegment> findSegmentByJobId(String jobId) {
        TranscriptionSegment cached = segmentsByJobId.get(jobId);
        if (cached != null) {
            return Optional.of(cached);
        }
        loadAllSegments();
        return Optional.ofNullable(segmentsByJobId.get(jobId));
    }

    public List<TranscriptionSegment> findSegmentsByMeetingId(Long meetingId) {
        loadSegmentsForMeeting(meetingId);
        return sortSegmentsForMinutes(segmentsByJobId.values().stream()
                .filter(segment -> segment.getMeeting() != null
                        && meetingId.equals(segment.getMeeting().getId())));
    }

    public Page<TranscriptionSegment> searchSegments(String keyword, Long meetingId, Pageable pageable) {
        loadAllSegments();
        String normalized = keyword == null ? "" : keyword.toLowerCase();
        List<TranscriptionSegment> matches = sortSegmentsForMinutes(segmentsByJobId.values().stream()
                .filter(segment -> meetingId == null
                        || (segment.getMeeting() != null && meetingId.equals(segment.getMeeting().getId())))
                .filter(segment -> segment.getText() != null
                        && segment.getText().toLowerCase().contains(normalized)));
        if (pageable.isUnpaged()) {
            return new PageImpl<>(matches);
        }
        int start = Math.min((int) pageable.getOffset(), matches.size());
        int end = Math.min(start + pageable.getPageSize(), matches.size());
        return new PageImpl<>(matches.subList(start, end), pageable, matches.size());
    }

    public synchronized Recording saveRecording(Recording recording) {
        if (recording.getId() == null) {
            recording.setId(recordingIds.getAndIncrement());
        }
        if (recording.getCreatedAt() == null) {
            recording.setCreatedAt(LocalDateTime.now(clock));
        }
        recordingsById.put(recording.getId(), recording);
        writeRecordingState(recording);
        return recording;
    }

    public Optional<Recording> findRecordingById(Long recordingId) {
        Recording cached = recordingsById.get(recordingId);
        if (cached != null) {
            return Optional.of(cached);
        }
        loadAllRecordings();
        return Optional.ofNullable(recordingsById.get(recordingId));
    }

    public List<Recording> findRecordingsByMeetingId(Long meetingId) {
        loadRecordingsForMeeting(meetingId);
        return recordingsById.values().stream()
                .filter(recording -> recording.getMeeting() != null
                        && meetingId.equals(recording.getMeeting().getId()))
                .sorted(Comparator.comparing(Recording::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public void deleteRecording(Recording recording) {
        recordingsById.remove(recording.getId());
        if (recording.getMeeting() != null) {
            try {
                Files.deleteIfExists(recordingStatePath(recording.getMeeting().getId(), recording.getId()));
            } catch (IOException e) {
                log.warn("Could not delete recording metadata {}: {}", recording.getId(), e.getMessage());
            }
        }
    }

    public synchronized AttendanceLog saveAttendanceLog(AttendanceLog attendanceLog) {
        if (attendanceLog.getId() == null) {
            attendanceLog.setId(attendanceIds.getAndIncrement());
        }
        attendanceById.put(attendanceLog.getId(), attendanceLog);
        writeAttendanceState(attendanceLog);
        return attendanceLog;
    }

    public List<AttendanceLog> saveAttendanceLogs(List<AttendanceLog> logs) {
        return logs.stream().map(this::saveAttendanceLog).toList();
    }

    public Optional<AttendanceLog> findOpenAttendanceLog(Long meetingId, Long userId) {
        loadAttendanceForMeeting(meetingId);
        return attendanceById.values().stream()
                .filter(log -> log.getMeeting() != null && meetingId.equals(log.getMeeting().getId()))
                .filter(log -> log.getUser() != null && userId.equals(log.getUser().getId()))
                .filter(log -> log.getLeaveTime() == null)
                .findFirst();
    }

    public List<AttendanceLog> findAttendanceByMeetingId(Long meetingId) {
        loadAttendanceForMeeting(meetingId);
        return attendanceById.values().stream()
                .filter(log -> log.getMeeting() != null && meetingId.equals(log.getMeeting().getId()))
                .sorted(Comparator.comparing(AttendanceLog::getJoinTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public List<AttendanceLog> findActiveAttendance(Long meetingId) {
        return findAttendanceByMeetingId(meetingId).stream()
                .filter(log -> log.getLeaveTime() == null)
                .toList();
    }

    public synchronized ParticipantSession saveSession(ParticipantSession session) {
        sessionsBySessionId.put(session.getSessionId(), session);
        return session;
    }

    public Optional<ParticipantSession> findActiveSession(Long meetingId, Long userId) {
        return sessionsBySessionId.values().stream()
                .filter(ParticipantSession::isConnected)
                .filter(session -> session.getMeeting() != null && meetingId.equals(session.getMeeting().getId()))
                .filter(session -> session.getUser() != null && userId.equals(session.getUser().getId()))
                .findFirst();
    }

    public Optional<ParticipantSession> findSessionBySessionId(String sessionId) {
        return Optional.ofNullable(sessionsBySessionId.get(sessionId));
    }

    public List<ParticipantSession> findStaleConnectedSessions(LocalDateTime threshold) {
        return sessionsBySessionId.values().stream()
                .filter(ParticipantSession::isConnected)
                .filter(session -> session.getLastHeartbeatAt() != null
                        && session.getLastHeartbeatAt().isBefore(threshold))
                .toList();
    }

    public boolean isUserConnected(Long meetingId, Long userId) {
        return findActiveSession(meetingId, userId).isPresent();
    }

    public synchronized SpeakingPermission saveSpeakingPermission(SpeakingPermission permission) {
        if (permission.getId() == null) {
            permission.setId(permissionIds.getAndIncrement());
        }
        permissionsById.put(permission.getId(), permission);
        return permission;
    }

    public Optional<SpeakingPermission> findActiveSpeakingPermission(Long meetingId) {
        return permissionsById.values().stream()
                .filter(permission -> permission.getMeeting() != null
                        && meetingId.equals(permission.getMeeting().getId()))
                .filter(permission -> permission.getRevokedAt() == null)
                .findFirst();
    }

    public boolean hasActiveSpeakingPermission(Long meetingId, Long userId) {
        return findActiveSpeakingPermission(meetingId)
                .filter(permission -> permission.getUser() != null
                        && userId.equals(permission.getUser().getId()))
                .isPresent();
    }

    public synchronized int revokeAllSpeakingPermissions(Long meetingId, LocalDateTime revokedAt) {
        int count = 0;
        for (SpeakingPermission permission : permissionsById.values()) {
            if (permission.getMeeting() != null
                    && meetingId.equals(permission.getMeeting().getId())
                    && permission.getRevokedAt() == null) {
                permission.setRevokedAt(revokedAt);
                count++;
            }
        }
        return count;
    }

    public synchronized Notification saveNotification(Notification notification) {
        if (notification.getId() == null) {
            notification.setId(notificationIds.getAndIncrement());
        }
        if (notification.getCreatedAt() == null) {
            notification.setCreatedAt(LocalDateTime.now(clock));
        }
        notificationsById.put(notification.getId(), notification);
        return notification;
    }

    public Optional<Notification> findNotificationById(Long notificationId) {
        return Optional.ofNullable(notificationsById.get(notificationId));
    }

    public Page<Notification> findNotificationsByUserId(Long userId, Pageable pageable) {
        List<Notification> matches = notificationsById.values().stream()
                .filter(notification -> notification.getUser() != null
                        && userId.equals(notification.getUser().getId()))
                .sorted(Comparator.comparing(Notification::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int start = Math.min((int) pageable.getOffset(), matches.size());
        int end = Math.min(start + pageable.getPageSize(), matches.size());
        return new PageImpl<>(matches.subList(start, end), pageable, matches.size());
    }

    public long countUnreadNotifications(Long userId) {
        return notificationsById.values().stream()
                .filter(notification -> notification.getUser() != null
                        && userId.equals(notification.getUser().getId()))
                .filter(notification -> !notification.isRead())
                .count();
    }

    public int markAllNotificationsRead(Long userId) {
        int count = 0;
        for (Notification notification : notificationsById.values()) {
            if (notification.getUser() != null
                    && userId.equals(notification.getUser().getId())
                    && !notification.isRead()) {
                notification.setRead(true);
                count++;
            }
        }
        return count;
    }

    public synchronized StorageLog saveStorageLog(StorageLog storageLog) {
        if (storageLog.getId() == null) {
            storageLog.setId(storageLogIds.getAndIncrement());
        }
        if (storageLog.getCreatedAt() == null) {
            storageLog.setCreatedAt(LocalDateTime.now(clock));
        }
        writeStorageLog(storageLog);
        return storageLog;
    }

    private List<TranscriptionSegment> sortSegmentsForMinutes(Stream<TranscriptionSegment> segmentStream) {
        List<TranscriptionSegment> segments = segmentStream.toList();
        Map<String, LocalDateTime> turnStartTimes = new java.util.HashMap<>();
        for (TranscriptionSegment segment : segments) {
            turnStartTimes.merge(
                    speakerTurnKey(segment),
                    segmentOrderTime(segment),
                    (left, right) -> left.isBefore(right) ? left : right);
        }

        return segments.stream()
                .sorted((left, right) -> compareSegmentsForMinutes(left, right, turnStartTimes))
                .toList();
    }

    private int compareSegmentsForMinutes(
            TranscriptionSegment left,
            TranscriptionSegment right,
            Map<String, LocalDateTime> turnStartTimes) {
        String leftTurn = speakerTurnKey(left);
        String rightTurn = speakerTurnKey(right);

        if (!leftTurn.equals(rightTurn)) {
            int turnStartComparison = turnStartTimes.get(leftTurn).compareTo(turnStartTimes.get(rightTurn));
            if (turnStartComparison != 0) {
                return turnStartComparison;
            }
            int turnKeyComparison = leftTurn.compareTo(rightTurn);
            if (turnKeyComparison != 0) {
                return turnKeyComparison;
            }
        }

        int sequenceComparison = Integer.compare(left.getSequenceNumber(), right.getSequenceNumber());
        if (sequenceComparison != 0) {
            return sequenceComparison;
        }

        int timeComparison = segmentOrderTime(left).compareTo(segmentOrderTime(right));
        if (timeComparison != 0) {
            return timeComparison;
        }

        return String.valueOf(left.getJobId()).compareTo(String.valueOf(right.getJobId()));
    }

    private String speakerTurnKey(TranscriptionSegment segment) {
        if (segment.getSpeakerTurnId() != null && !segment.getSpeakerTurnId().isBlank()) {
            return segment.getSpeakerTurnId();
        }
        return "job:" + String.valueOf(segment.getJobId());
    }

    private LocalDateTime segmentOrderTime(TranscriptionSegment segment) {
        if (segment.getSegmentStartTime() != null) {
            return segment.getSegmentStartTime();
        }
        if (segment.getCreatedAt() != null) {
            return segment.getCreatedAt();
        }
        return LocalDateTime.MAX;
    }

    private Path basePath() {
        return Path.of(storageProperties.getBasePath());
    }

    private Path meetingPath(Long meetingId) {
        return basePath().resolve("meetings").resolve(String.valueOf(meetingId));
    }

    private Path minutesPath(Long meetingId) {
        return meetingPath(meetingId).resolve("minutes");
    }

    private Path recordingsPath(Long meetingId) {
        return meetingPath(meetingId).resolve("recordings");
    }

    private Path transcriptPath(Long meetingId) {
        return meetingPath(meetingId).resolve("transcript");
    }

    private Path auditPath(Long meetingId) {
        return meetingPath(meetingId).resolve("audit");
    }

    private Path attendancePath(Long meetingId) {
        return meetingPath(meetingId).resolve("attendance");
    }

    private Optional<Meeting> meeting(Long meetingId) {
        return meetingRepository.findById(meetingId);
    }

    private void writeMinutesState(Minutes minutes) {
        try {
            Path dir = minutesPath(minutes.getMeeting().getId());
            Files.createDirectories(dir);
            Properties p = new Properties();
            set(p, "id", minutes.getId());
            set(p, "status", minutes.getStatus());
            set(p, "draftPdfPath", minutes.getDraftPdfPath());
            set(p, "draftDocxPath", minutes.getDraftDocxPath());
            set(p, "confirmedPdfPath", minutes.getConfirmedPdfPath());
            set(p, "secretaryPdfPath", minutes.getSecretaryPdfPath());
            set(p, "secretaryDocxPath", minutes.getSecretaryDocxPath());
            set(p, "contentHtml", minutes.getContentHtml());
            set(p, "hostConfirmedAt", minutes.getHostConfirmedAt());
            set(p, "hostConfirmationHash", minutes.getHostConfirmationHash());
            set(p, "secretaryConfirmedAt", minutes.getSecretaryConfirmedAt());
            set(p, "reminderSentAt", minutes.getReminderSentAt());
            set(p, "createdAt", minutes.getCreatedAt());
            set(p, "updatedAt", minutes.getUpdatedAt());
            try (Writer writer = Files.newBufferedWriter(dir.resolve("state.properties"), StandardCharsets.UTF_8)) {
                p.store(writer, "KollaMeeting minutes runtime state");
            }
        } catch (IOException e) {
            log.warn("Could not persist minutes state for meeting {}: {}",
                    minutes.getMeeting().getId(), e.getMessage());
        }
    }

    private Optional<Minutes> loadMinutesState(Long meetingId) {
        Path path = minutesPath(meetingId).resolve("state.properties");
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        return meeting(meetingId).flatMap(meeting -> {
            try {
                Properties p = loadProperties(path);
                Minutes minutes = Minutes.builder()
                        .id(longProp(p, "id"))
                        .meeting(meeting)
                        .status(enumProp(p, "status", MinutesStatus.class, MinutesStatus.DRAFT))
                        .draftPdfPath(p.getProperty("draftPdfPath"))
                        .draftDocxPath(p.getProperty("draftDocxPath"))
                        .confirmedPdfPath(p.getProperty("confirmedPdfPath"))
                        .secretaryPdfPath(p.getProperty("secretaryPdfPath"))
                        .secretaryDocxPath(p.getProperty("secretaryDocxPath"))
                        .contentHtml(p.getProperty("contentHtml"))
                        .hostConfirmedAt(dateTimeProp(p, "hostConfirmedAt"))
                        .hostConfirmationHash(p.getProperty("hostConfirmationHash"))
                        .secretaryConfirmedAt(dateTimeProp(p, "secretaryConfirmedAt"))
                        .reminderSentAt(dateTimeProp(p, "reminderSentAt"))
                        .createdAt(dateTimeProp(p, "createdAt"))
                        .updatedAt(dateTimeProp(p, "updatedAt"))
                        .build();
                minutesByMeetingId.put(meetingId, minutes);
                return Optional.of(minutes);
            } catch (IOException e) {
                log.warn("Could not load minutes state {}: {}", path, e.getMessage());
                return Optional.empty();
            }
        });
    }

    private void loadAllMinutesStates() {
        try (Stream<Path> paths = Files.exists(basePath().resolve("meetings"))
                ? Files.list(basePath().resolve("meetings"))
                : Stream.empty()) {
            paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .map(this::parseLong)
                    .flatMap(Optional::stream)
                    .forEach(this::loadMinutesState);
        } catch (IOException e) {
            log.warn("Could not scan minutes states: {}", e.getMessage());
        }
    }

    private void writeJobState(TranscriptionJob job) {
        try {
            Long meetingId = job.getMeeting().getId();
            Path dir = transcriptPath(meetingId).resolve("jobs");
            Files.createDirectories(dir);
            Properties p = new Properties();
            set(p, "id", job.getId());
            set(p, "meetingId", meetingId);
            set(p, "speakerId", job.getSpeakerId());
            set(p, "speakerName", job.getSpeakerName());
            set(p, "speakerDept", job.getSpeakerDept());
            set(p, "speakerTurnId", job.getSpeakerTurnId());
            set(p, "sequenceNumber", job.getSequenceNumber());
            set(p, "priority", job.getPriority());
            set(p, "status", job.getStatus());
            set(p, "audioPath", job.getAudioPath());
            set(p, "retryCount", job.getRetryCount());
            set(p, "createdAt", job.getCreatedAt());
            set(p, "queuedAt", job.getQueuedAt());
            set(p, "completedAt", job.getCompletedAt());
            set(p, "errorMessage", job.getErrorMessage());
            try (Writer writer = Files.newBufferedWriter(dir.resolve(job.getId() + ".properties"), StandardCharsets.UTF_8)) {
                p.store(writer, "KollaMeeting transcription job runtime state");
            }
        } catch (IOException e) {
            log.warn("Could not persist transcription job {}: {}", job.getId(), e.getMessage());
        }
    }

    private Optional<TranscriptionJob> loadJobState(String jobId) {
        loadAllJobs();
        return Optional.ofNullable(jobsById.get(jobId));
    }

    private void loadJobsForMeeting(Long meetingId) {
        Path dir = transcriptPath(meetingId).resolve("jobs");
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .forEach(this::loadJobPath);
        } catch (IOException e) {
            log.warn("Could not scan transcription jobs for meeting {}: {}", meetingId, e.getMessage());
        }
    }

    private void loadAllJobs() {
        try (Stream<Path> meetingDirs = Files.exists(basePath().resolve("meetings"))
                ? Files.list(basePath().resolve("meetings"))
                : Stream.empty()) {
            meetingDirs.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .map(this::parseLong)
                    .flatMap(Optional::stream)
                    .forEach(this::loadJobsForMeeting);
        } catch (IOException e) {
            log.warn("Could not scan transcription job states: {}", e.getMessage());
        }
    }

    private void loadJobPath(Path path) {
        try {
            Properties p = loadProperties(path);
            Long meetingId = longProp(p, "meetingId");
            if (meetingId == null) {
                return;
            }
            meeting(meetingId).ifPresent(meeting -> {
                TranscriptionJob job = TranscriptionJob.builder()
                        .id(p.getProperty("id"))
                        .meeting(meeting)
                        .speakerId(longProp(p, "speakerId"))
                        .speakerName(p.getProperty("speakerName"))
                        .speakerDept(p.getProperty("speakerDept"))
                        .speakerTurnId(p.getProperty("speakerTurnId"))
                        .sequenceNumber(intProp(p, "sequenceNumber", 0))
                        .priority(enumProp(p, "priority", TranscriptionPriority.class, TranscriptionPriority.NORMAL_PRIORITY))
                        .status(enumProp(p, "status", TranscriptionJobStatus.class, TranscriptionJobStatus.PENDING))
                        .audioPath(p.getProperty("audioPath"))
                        .retryCount(intProp(p, "retryCount", 0))
                        .createdAt(dateTimeProp(p, "createdAt"))
                        .queuedAt(dateTimeProp(p, "queuedAt"))
                        .completedAt(dateTimeProp(p, "completedAt"))
                        .errorMessage(p.getProperty("errorMessage"))
                        .build();
                jobsById.putIfAbsent(job.getId(), job);
            });
        } catch (IOException e) {
            log.warn("Could not load transcription job {}: {}", path, e.getMessage());
        }
    }

    private void appendSegment(TranscriptionSegment segment) {
        try {
            Long meetingId = segment.getMeeting().getId();
            Path dir = transcriptPath(meetingId);
            Files.createDirectories(dir);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", segment.getId());
            row.put("jobId", segment.getJobId());
            row.put("meetingId", meetingId);
            row.put("speakerId", segment.getSpeakerId());
            row.put("speakerName", segment.getSpeakerName());
            row.put("speakerTurnId", segment.getSpeakerTurnId());
            row.put("sequenceNumber", segment.getSequenceNumber());
            row.put("text", segment.getText());
            row.put("confidence", segment.getConfidence());
            row.put("processingTimeMs", segment.getProcessingTimeMs());
            row.put("segmentStartTime", stringValue(segment.getSegmentStartTime()));
            row.put("createdAt", stringValue(segment.getCreatedAt()));
            Files.writeString(
                    dir.resolve("segments.jsonl"),
                    objectMapper.writeValueAsString(row) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    Files.exists(dir.resolve("segments.jsonl"))
                            ? java.nio.file.StandardOpenOption.APPEND
                            : java.nio.file.StandardOpenOption.CREATE);
        } catch (IOException e) {
            log.warn("Could not append transcription segment {}: {}", segment.getJobId(), e.getMessage());
        }
    }

    private void loadSegmentsForMeeting(Long meetingId) {
        Path path = transcriptPath(meetingId).resolve("segments.jsonl");
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            lines.filter(line -> !line.isBlank()).forEach(this::loadSegmentLine);
        } catch (IOException e) {
            log.warn("Could not load transcription segments for meeting {}: {}", meetingId, e.getMessage());
        }
    }

    private void loadAllSegments() {
        try (Stream<Path> meetingDirs = Files.exists(basePath().resolve("meetings"))
                ? Files.list(basePath().resolve("meetings"))
                : Stream.empty()) {
            meetingDirs.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .map(this::parseLong)
                    .flatMap(Optional::stream)
                    .forEach(this::loadSegmentsForMeeting);
        } catch (IOException e) {
            log.warn("Could not scan transcription segment states: {}", e.getMessage());
        }
    }

    private void loadSegmentLine(String line) {
        try {
            Map<String, Object> row = objectMapper.readValue(line, new TypeReference<>() {});
            Long meetingId = toLong(row.get("meetingId"));
            String jobId = (String) row.get("jobId");
            if (meetingId == null || jobId == null || segmentsByJobId.containsKey(jobId)) {
                return;
            }
            meeting(meetingId).ifPresent(meeting -> segmentsByJobId.put(jobId, TranscriptionSegment.builder()
                    .id(toLong(row.get("id")))
                    .jobId(jobId)
                    .meeting(meeting)
                    .speakerId(toLong(row.get("speakerId")))
                    .speakerName((String) row.get("speakerName"))
                    .speakerTurnId((String) row.get("speakerTurnId"))
                    .sequenceNumber(toInt(row.get("sequenceNumber"), 0))
                    .text((String) row.get("text"))
                    .confidence(toFloat(row.get("confidence")))
                    .processingTimeMs(toInteger(row.get("processingTimeMs")))
                    .segmentStartTime(parseDateTime((String) row.get("segmentStartTime")))
                    .createdAt(parseDateTime((String) row.get("createdAt")))
                    .build()));
        } catch (IOException e) {
            log.warn("Could not parse transcription segment line: {}", e.getMessage());
        }
    }

    private void writeRecordingState(Recording recording) {
        try {
            Long meetingId = recording.getMeeting().getId();
            Path path = recordingStatePath(meetingId, recording.getId());
            Files.createDirectories(path.getParent());
            Properties p = new Properties();
            set(p, "id", recording.getId());
            set(p, "meetingId", meetingId);
            set(p, "fileName", recording.getFileName());
            set(p, "fileSize", recording.getFileSize());
            set(p, "filePath", recording.getFilePath());
            set(p, "url", recording.getUrl());
            set(p, "status", recording.getStatus());
            set(p, "startTime", recording.getStartTime());
            set(p, "endTime", recording.getEndTime());
            set(p, "createdBy", recording.getCreatedBy() != null ? recording.getCreatedBy().getId() : null);
            set(p, "createdAt", recording.getCreatedAt());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                p.store(writer, "KollaMeeting recording runtime state");
            }
        } catch (IOException e) {
            log.warn("Could not persist recording {}: {}", recording.getId(), e.getMessage());
        }
    }

    private Path recordingStatePath(Long meetingId, Long recordingId) {
        return recordingsPath(meetingId).resolve("recording-" + recordingId + ".properties");
    }

    private void loadRecordingsForMeeting(Long meetingId) {
        Path dir = recordingsPath(meetingId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(path -> path.getFileName().toString().startsWith("recording-"))
                    .forEach(this::loadRecordingPath);
        } catch (IOException e) {
            log.warn("Could not scan recordings for meeting {}: {}", meetingId, e.getMessage());
        }
    }

    private void loadAllRecordings() {
        try (Stream<Path> meetingDirs = Files.exists(basePath().resolve("meetings"))
                ? Files.list(basePath().resolve("meetings"))
                : Stream.empty()) {
            meetingDirs.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .map(this::parseLong)
                    .flatMap(Optional::stream)
                    .forEach(this::loadRecordingsForMeeting);
        } catch (IOException e) {
            log.warn("Could not scan recordings: {}", e.getMessage());
        }
    }

    private void writeAttendanceState(AttendanceLog attendanceLog) {
        if (attendanceLog.getMeeting() == null || attendanceLog.getMeeting().getId() == null) {
            return;
        }
        try {
            Long meetingId = attendanceLog.getMeeting().getId();
            Path path = attendanceStatePath(meetingId, attendanceLog.getId());
            Files.createDirectories(path.getParent());
            Properties p = new Properties();
            set(p, "id", attendanceLog.getId());
            set(p, "meetingId", meetingId);
            set(p, "userId", attendanceLog.getUser() != null ? attendanceLog.getUser().getId() : null);
            set(p, "username", attendanceLog.getUser() != null ? attendanceLog.getUser().getUsername() : null);
            set(p, "fullName", attendanceLog.getUser() != null ? attendanceLog.getUser().getFullName() : null);
            set(p, "joinTime", attendanceLog.getJoinTime());
            set(p, "leaveTime", attendanceLog.getLeaveTime());
            set(p, "durationSeconds", attendanceLog.getDurationSeconds());
            set(p, "ipAddress", attendanceLog.getIpAddress());
            set(p, "deviceInfo", attendanceLog.getDeviceInfo());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                p.store(writer, "KollaMeeting attendance runtime state");
            }
        } catch (IOException e) {
            log.warn("Could not persist attendance log {}: {}", attendanceLog.getId(), e.getMessage());
        }
    }

    private Path attendanceStatePath(Long meetingId, Long attendanceId) {
        return attendancePath(meetingId).resolve("attendance-" + attendanceId + ".properties");
    }

    private void loadAttendanceForMeeting(Long meetingId) {
        Path dir = attendancePath(meetingId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(path -> path.getFileName().toString().startsWith("attendance-"))
                    .forEach(this::loadAttendancePath);
        } catch (IOException e) {
            log.warn("Could not scan attendance logs for meeting {}: {}", meetingId, e.getMessage());
        }
    }

    private void loadAttendancePath(Path path) {
        try {
            Properties p = loadProperties(path);
            Long meetingId = longProp(p, "meetingId");
            Long id = longProp(p, "id");
            if (meetingId == null || id == null || attendanceById.containsKey(id)) {
                return;
            }
            meeting(meetingId).ifPresent(meeting -> {
                User user = User.builder()
                        .id(longProp(p, "userId"))
                        .username(p.getProperty("username"))
                        .fullName(p.getProperty("fullName"))
                        .build();
                AttendanceLog attendanceLog = AttendanceLog.builder()
                        .id(id)
                        .meeting(meeting)
                        .user(user)
                        .joinTime(dateTimeProp(p, "joinTime"))
                        .leaveTime(dateTimeProp(p, "leaveTime"))
                        .durationSeconds(longProp(p, "durationSeconds"))
                        .ipAddress(p.getProperty("ipAddress"))
                        .deviceInfo(p.getProperty("deviceInfo"))
                        .build();
                attendanceById.put(id, attendanceLog);
                attendanceIds.accumulateAndGet(id + 1, Math::max);
            });
        } catch (IOException e) {
            log.warn("Could not load attendance log {}: {}", path, e.getMessage());
        }
    }

    private void loadRecordingPath(Path path) {
        try {
            Properties p = loadProperties(path);
            Long meetingId = longProp(p, "meetingId");
            if (meetingId == null) {
                return;
            }
            meeting(meetingId).ifPresent(meeting -> recordingsById.putIfAbsent(longProp(p, "id"), Recording.builder()
                    .id(longProp(p, "id"))
                    .meeting(meeting)
                    .fileName(p.getProperty("fileName"))
                    .fileSize(longProp(p, "fileSize"))
                    .filePath(p.getProperty("filePath"))
                    .url(p.getProperty("url"))
                    .status(enumProp(p, "status", RecordingStatus.class, RecordingStatus.COMPLETED))
                    .startTime(dateTimeProp(p, "startTime"))
                    .endTime(dateTimeProp(p, "endTime"))
                    .createdAt(dateTimeProp(p, "createdAt"))
                    .build()));
        } catch (IOException e) {
            log.warn("Could not load recording {}: {}", path, e.getMessage());
        }
    }

    private void writeStorageLog(StorageLog storageLog) {
        List<Long> meetingIds = storageLog.getMeetingIds() == null || storageLog.getMeetingIds().isEmpty()
                ? List.of(0L)
                : storageLog.getMeetingIds();
        for (Long meetingId : meetingIds) {
            writeStorageLogForMeeting(storageLog, meetingId);
        }
    }

    private void writeStorageLogForMeeting(StorageLog storageLog, Long meetingId) {
        try {
            Path dir = auditPath(meetingId);
            Files.createDirectories(dir);
            Properties p = new Properties();
            set(p, "id", storageLog.getId());
            set(p, "adminUserId", storageLog.getAdminUser() != null ? storageLog.getAdminUser().getId() : null);
            set(p, "operation", storageLog.getOperation());
            set(p, "meetingIds", storageLog.getMeetingIds() == null ? null : storageLog.getMeetingIds());
            set(p, "fileCount", storageLog.getFileCount());
            set(p, "totalSizeBytes", storageLog.getTotalSizeBytes());
            set(p, "description", storageLog.getDescription());
            set(p, "createdAt", storageLog.getCreatedAt());
            try (Writer writer = Files.newBufferedWriter(
                    dir.resolve("storage-delete-" + storageLog.getId() + ".properties"),
                    StandardCharsets.UTF_8)) {
                p.store(writer, "KollaMeeting storage audit runtime state");
            }
        } catch (IOException e) {
            log.warn("Could not persist storage audit log {}: {}", storageLog.getId(), e.getMessage());
        }
    }

    private Properties loadProperties(Path path) throws IOException {
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            p.load(reader);
        }
        return p;
    }

    private void set(Properties properties, String key, Object value) {
        if (value != null) {
            properties.setProperty(key, stringValue(value));
        }
    }

    private String stringValue(Object value) {
        return value instanceof LocalDateTime dt ? dt.toString() : String.valueOf(value);
    }

    private Long longProp(Properties p, String key) {
        return parseLong(p.getProperty(key)).orElse(null);
    }

    private int intProp(Properties p, String key, int defaultValue) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private LocalDateTime dateTimeProp(Properties p, String key) {
        return parseDateTime(p.getProperty(key));
    }

    private <E extends Enum<E>> E enumProp(Properties p, String key, Class<E> type, E defaultValue) {
        String value = p.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : Enum.valueOf(type, value);
    }

    private Optional<Long> parseLong(String value) {
        try {
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private LocalDateTime parseDateTime(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.parseLong(String.valueOf(value));
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
    }

    private Integer toInteger(Object value) {
        return value == null ? null : toInt(value, 0);
    }

    private Float toFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return value == null ? null : Float.parseFloat(String.valueOf(value));
    }
}
