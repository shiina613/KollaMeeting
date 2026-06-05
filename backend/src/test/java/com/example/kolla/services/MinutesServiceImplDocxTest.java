package com.example.kolla.services;

import com.example.kolla.enums.FileType;
import com.example.kolla.enums.MeetingRole;
import com.example.kolla.enums.Role;
import com.example.kolla.models.Document;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Member;
import com.example.kolla.models.Minutes;
import com.example.kolla.models.TranscriptionSegment;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DocumentRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.MinutesRepository;
import com.example.kolla.repositories.MemberRepository;
import com.example.kolla.repositories.TranscriptionSegmentRepository;
import com.example.kolla.services.impl.MinutesServiceImpl;
import com.example.kolla.websocket.MeetingEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinutesServiceImplDocxTest {

    @Mock private MinutesRepository minutesRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private TranscriptionSegmentRepository transcriptionSegmentRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private NotificationService notificationService;
    @Mock private MeetingService meetingService;
    @Mock private MeetingEventPublisher eventPublisher;
    @Mock private PdfDigitalSignatureService pdfDigitalSignatureService;
    @Mock private DocumentRepository documentRepository;
    @Mock private MemberRepository memberRepository;

    private MinutesServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-05T08:00:00Z"),
                ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new MinutesServiceImpl(
                minutesRepository,
                meetingRepository,
                transcriptionSegmentRepository,
                fileStorageService,
                notificationService,
                meetingService,
                eventPublisher,
                pdfDigitalSignatureService,
                documentRepository,
                memberRepository,
                clock);
    }

    @Test
    void compileDraftMinutes_storesPdfAndDocxAndCreatesMeetingDocuments() throws Exception {
        User host = User.builder()
                .id(10L)
                .username("host")
                .fullName("Chủ tọa")
                .email("host@example.com")
                .role(Role.SECRETARY)
                .isActive(true)
                .build();
        Meeting meeting = Meeting.builder()
                .id(123L)
                .title("Họp nghiệm thu")
                .host(host)
                .creator(host)
                .activatedAt(LocalDateTime.of(2026, 6, 5, 9, 0))
                .endedAt(LocalDateTime.of(2026, 6, 5, 10, 0))
                .build();

        when(minutesRepository.existsByMeetingId(123L)).thenReturn(false);
        when(transcriptionSegmentRepository.findByMeetingIdOrderedForMinutes(123L))
                .thenReturn(List.of(segment(1L, "turn-1", 1, "Chủ tọa", "Nội dung thứ nhất")));
        when(memberRepository.findByMeetingId(123L))
                .thenReturn(List.of(Member.builder()
                        .id(501L)
                        .meeting(meeting)
                        .user(host)
                        .meetingRole(MeetingRole.HOST)
                        .build()));
        when(minutesRepository.save(any(Minutes.class))).thenAnswer(invocation -> {
            Minutes minutes = invocation.getArgument(0);
            if (minutes.getId() == null) {
                minutes.setId(77L);
            }
            return minutes;
        });
        when(fileStorageService.storeBytes(any(byte[].class), eq(FileType.MINUTES), eq(123L), eq("draft_77.pdf")))
                .thenReturn(Path.of("minutes/123/draft_77.pdf"));
        when(fileStorageService.storeBytes(any(byte[].class), eq(FileType.MINUTES), eq(123L), eq("draft_77.docx")))
                .thenReturn(Path.of("minutes/123/draft_77.docx"));

        service.compileDraftMinutes(meeting);

        ArgumentCaptor<Minutes> minutesCaptor = ArgumentCaptor.forClass(Minutes.class);
        verify(minutesRepository, times(2)).save(minutesCaptor.capture());
        Minutes saved = minutesCaptor.getAllValues().get(1);
        assertThat(saved.getDraftPdfPath()).isEqualTo(Path.of("minutes/123/draft_77.pdf").toString());
        assertThat(saved.getDraftDocxPath()).isEqualTo(Path.of("minutes/123/draft_77.docx").toString());

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository, times(2)).save(documentCaptor.capture());
        assertThat(documentCaptor.getAllValues())
                .extracting(Document::getFileName)
                .containsExactlyInAnyOrder(
                        "bien-ban-nhap-123.pdf",
                        "bien-ban-nhap-123.docx");
        assertThat(documentCaptor.getAllValues())
                .extracting(Document::getFileType)
                .containsExactlyInAnyOrder(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(documentCaptor.getAllValues())
                .allSatisfy(document -> assertThat(document.getUploadedBy()).isEqualTo(host));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<byte[]> docxBytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).storeBytes(
                docxBytesCaptor.capture(),
                eq(FileType.MINUTES),
                eq(123L),
                eq("draft_77.docx"));
        String docxXml = unzipTextEntries(docxBytesCaptor.getValue()).get("word/document.xml");
        assertThat(docxXml)
                .contains("09:01")
                .contains("Chủ tọa")
                .contains("HOST")
                .contains("Nội dung thứ nhất");
    }

    private TranscriptionSegment segment(
            Long id,
            String turnId,
            int sequence,
            String speakerName,
            String text) {
        TranscriptionSegment segment = new TranscriptionSegment();
        segment.setId(id);
        segment.setJobId("job-" + id);
        segment.setSpeakerTurnId(turnId);
        segment.setSequenceNumber(sequence);
        segment.setSpeakerId(10L);
        segment.setSpeakerName(speakerName);
        segment.setText(text);
        segment.setSegmentStartTime(LocalDateTime.of(2026, 6, 5, 9, sequence));
        segment.setCreatedAt(LocalDateTime.of(2026, 6, 5, 9, sequence));
        return segment;
    }

    private Map<String, String> unzipTextEntries(byte[] docx) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()
                        && (entry.getName().endsWith(".xml") || entry.getName().endsWith(".rels"))) {
                    entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return entries;
    }
}
