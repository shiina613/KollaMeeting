package com.example.kolla.services;

import com.example.kolla.enums.FileType;
import com.example.kolla.enums.RecordingStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Recording;
import com.example.kolla.models.TranscriptionJob;
import com.example.kolla.models.User;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.RecordingRepository;
import com.example.kolla.repositories.TranscriptionJobRepository;
import com.example.kolla.responses.RecordingResponse;
import com.example.kolla.services.impl.RecordingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingAggregateTest {

    @Mock private RecordingRepository recordingRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingService meetingService;
    @Mock private FileStorageService fileStorageService;
    @Mock private TranscriptionJobRepository transcriptionJobRepository;

    private RecordingServiceImpl service;
    private User host;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        service = new RecordingServiceImpl(
                recordingRepository,
                meetingRepository,
                meetingService,
                fileStorageService,
                transcriptionJobRepository,
                Clock.fixed(Instant.parse("2026-06-05T08:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        host = User.builder()
                .id(10L)
                .username("HOST001")
                .employeeCode("HOST001")
                .fullName("Chủ tọa")
                .role(Role.USER)
                .isActive(true)
                .build();
        meeting = Meeting.builder()
                .id(50L)
                .title("Họp nghiệm thu")
                .host(host)
                .creator(host)
                .activatedAt(LocalDateTime.of(2026, 6, 5, 9, 0))
                .endedAt(LocalDateTime.of(2026, 6, 5, 10, 0))
                .build();
    }

    @Test
    void createAggregateRecordingForMeeting_concatenatesCompletedWavChunks() throws Exception {
        Path chunk1 = Files.createTempFile("chunk-1", ".wav");
        Path chunk2 = Files.createTempFile("chunk-2", ".wav");
        Files.write(chunk1, wavBytes(new byte[] {1, 2, 3, 4}));
        Files.write(chunk2, wavBytes(new byte[] {5, 6}));

        when(transcriptionJobRepository.findByMeetingIdOrdered(50L))
                .thenReturn(List.of(job("job-1", chunk1), job("job-2", chunk2)));
        when(fileStorageService.storeBytes(
                any(byte[].class),
                eq(FileType.RECORDING),
                eq(50L),
                eq("meeting-50.wav")))
                .thenReturn(Path.of("recordings/50/meeting-50.wav"));
        when(recordingRepository.save(any(Recording.class))).thenAnswer(invocation -> {
            Recording recording = invocation.getArgument(0);
            recording.setId(99L);
            return recording;
        });

        RecordingResponse response = service.createAggregateRecordingForMeeting(meeting);

        assertThat(response.getFileName()).isEqualTo("meeting-50.wav");
        assertThat(response.getStatus()).isEqualTo(RecordingStatus.COMPLETED);
        assertThat(response.getFilePath()).isEqualTo(Path.of("recordings/50/meeting-50.wav").toString());

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).storeBytes(
                bytesCaptor.capture(),
                eq(FileType.RECORDING),
                eq(50L),
                eq("meeting-50.wav"));
        assertThat(bytesCaptor.getValue()).hasSize(44 + 6);
    }

    @Test
    void createAggregateRecordingForMeeting_skipsWhenNoAudioChunksExist() throws Exception {
        when(transcriptionJobRepository.findByMeetingIdOrdered(50L)).thenReturn(List.of());

        RecordingResponse response = service.createAggregateRecordingForMeeting(meeting);

        assertThat(response).isNull();
        verify(fileStorageService, never()).storeBytes(any(), any(), any(), any());
        verify(recordingRepository, never()).save(any());
    }

    private TranscriptionJob job(String id, Path audioPath) {
        return TranscriptionJob.builder()
                .id(id)
                .meeting(meeting)
                .speakerId(host.getId())
                .speakerName(host.getFullName())
                .speakerTurnId("turn-1")
                .sequenceNumber(id.endsWith("1") ? 1 : 2)
                .status(TranscriptionJobStatus.COMPLETED)
                .audioPath(audioPath.toString())
                .createdAt(LocalDateTime.of(2026, 6, 5, 9, 0))
                .completedAt(LocalDateTime.of(2026, 6, 5, 9, 1))
                .build();
    }

    private byte[] wavBytes(byte[] pcm) {
        byte[] wav = new byte[44 + pcm.length];
        wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
        int riffSize = 36 + pcm.length;
        writeLe(wav, 4, riffSize);
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
        writeLe(wav, 40, pcm.length);
        System.arraycopy(pcm, 0, wav, 44, pcm.length);
        return wav;
    }

    private void writeLe(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xff);
        target[offset + 1] = (byte) ((value >> 8) & 0xff);
        target[offset + 2] = (byte) ((value >> 16) & 0xff);
        target[offset + 3] = (byte) ((value >> 24) & 0xff);
    }
}
