package com.example.kolla.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.kolla.config.FileStorageProperties;
import com.example.kolla.models.AttendanceLog;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.TranscriptionSegment;
import com.example.kolla.models.User;
import com.example.kolla.repositories.MeetingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

@ExtendWith(MockitoExtension.class)
class RuntimeMeetingStateStoreTest {

    @TempDir
    private Path tempDir;

    @Mock
    private MeetingRepository meetingRepository;

    @Test
    void attendanceLogsPersistAcrossStoreReload() {
        Meeting meeting = Meeting.builder().id(42L).title("Hop").build();
        User user = User.builder().id(7L).username("u7").fullName("User 7").build();
        when(meetingRepository.findById(42L)).thenReturn(Optional.of(meeting));

        RuntimeMeetingStateStore firstStore = store();
        AttendanceLog saved = firstStore.saveAttendanceLog(AttendanceLog.builder()
                .meeting(meeting)
                .user(user)
                .joinTime(LocalDateTime.of(2026, 6, 7, 9, 0))
                .leaveTime(LocalDateTime.of(2026, 6, 7, 9, 30))
                .durationSeconds(1800L)
                .ipAddress("127.0.0.1")
                .deviceInfo("browser")
                .build());

        RuntimeMeetingStateStore reloadedStore = store();
        List<AttendanceLog> logs = reloadedStore.findAttendanceByMeetingId(42L);

        assertThat(logs).hasSize(1);
        AttendanceLog reloaded = logs.get(0);
        assertThat(reloaded.getId()).isEqualTo(saved.getId());
        assertThat(reloaded.getMeeting().getId()).isEqualTo(42L);
        assertThat(reloaded.getUser().getId()).isEqualTo(7L);
        assertThat(reloaded.getJoinTime()).isEqualTo(LocalDateTime.of(2026, 6, 7, 9, 0));
        assertThat(reloaded.getLeaveTime()).isEqualTo(LocalDateTime.of(2026, 6, 7, 9, 30));
        assertThat(reloaded.getDurationSeconds()).isEqualTo(1800L);
        assertThat(reloaded.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(reloaded.getDeviceInfo()).isEqualTo("browser");
    }

    @Test
    void transcriptionSegmentsOrderTurnsByFirstStartTimeThenSequence() {
        Meeting meeting = Meeting.builder().id(42L).title("Hop").build();
        RuntimeMeetingStateStore store = store();

        store.saveTranscriptionSegment(segment(
                meeting,
                "job-later-1",
                "a-later-turn",
                1,
                "later first",
                LocalDateTime.of(2026, 6, 7, 9, 53)));
        store.saveTranscriptionSegment(segment(
                meeting,
                "job-earlier-1",
                "z-earlier-turn",
                1,
                "earlier first",
                LocalDateTime.of(2026, 6, 7, 9, 52)));
        store.saveTranscriptionSegment(segment(
                meeting,
                "job-later-2",
                "a-later-turn",
                2,
                "later second",
                LocalDateTime.of(2026, 6, 7, 9, 54)));

        List<TranscriptionSegment> segments = store.findSegmentsByMeetingId(42L);

        assertThat(segments)
                .extracting(TranscriptionSegment::getText)
                .containsExactly("earlier first", "later first", "later second");
    }

    private TranscriptionSegment segment(
            Meeting meeting,
            String jobId,
            String speakerTurnId,
            int sequenceNumber,
            String text,
            LocalDateTime startTime) {
        return TranscriptionSegment.builder()
                .jobId(jobId)
                .meeting(meeting)
                .speakerId(7L)
                .speakerName("Speaker")
                .speakerTurnId(speakerTurnId)
                .sequenceNumber(sequenceNumber)
                .text(text)
                .segmentStartTime(startTime)
                .createdAt(startTime.plusSeconds(sequenceNumber))
                .build();
    }

    private RuntimeMeetingStateStore store() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBasePath(tempDir.toString());
        return new RuntimeMeetingStateStore(
                properties,
                meetingRepository,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-06-07T00:00:00Z"), ZoneOffset.UTC));
    }
}
