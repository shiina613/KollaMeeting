package com.example.kolla.services;

import com.example.kolla.enums.FileType;
import com.example.kolla.enums.MeetingRole;
import com.example.kolla.enums.MinutesStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.dto.EditMinutesRequest;
import com.example.kolla.dto.MinutesContentEntryRequest;
import com.example.kolla.exceptions.ForbiddenException;
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
import com.example.kolla.responses.MinutesConfirmationResponse;
import com.example.kolla.services.impl.MinutesServiceImpl;
import com.example.kolla.websocket.MeetingEventPublisher;
import org.springframework.core.io.ByteArrayResource;
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
import java.util.Base64;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
                .contains("BIÊN BẢN CUỘC HỌP - BẢN NHÁP")
                .contains("Cuộc họp: Họp nghiệm thu")
                .contains("Bắt đầu: 05/06/2026 09:00")
                .contains("Kết thúc: 05/06/2026 10:00")
                .contains("Chủ tọa: Chủ tọa")
                .contains("09:01")
                .contains("Chủ tọa")
                .contains("Chủ tọa cuộc họp")
                .contains("Nội dung thứ nhất");
    }

    @Test
    void compileDraftMinutes_doesNotRepeatHeaderForConsecutiveSegmentsFromSameSpeaker() throws Exception {
        User host = User.builder()
                .id(10L)
                .username("host")
                .fullName("Host User")
                .email("host@example.com")
                .role(Role.SECRETARY)
                .isActive(true)
                .build();
        User speaker = User.builder()
                .id(20L)
                .username("speaker")
                .fullName("Nguyen Quang Tung")
                .email("speaker@example.com")
                .role(Role.USER)
                .isActive(true)
                .build();
        Meeting meeting = Meeting.builder()
                .id(124L)
                .title("Hop tong ket")
                .host(host)
                .creator(host)
                .activatedAt(LocalDateTime.of(2026, 6, 5, 9, 0))
                .endedAt(LocalDateTime.of(2026, 6, 5, 10, 0))
                .build();

        when(minutesRepository.existsByMeetingId(124L)).thenReturn(false);
        when(transcriptionSegmentRepository.findByMeetingIdOrderedForMinutes(124L))
                .thenReturn(List.of(
                        segment(1L, "turn-1", 1, 20L, "Nguyen Quang Tung", "Noi dung thu nhat"),
                        segment(2L, "turn-1", 2, 20L, "Nguyen Quang Tung", "Noi dung thu hai")));
        when(memberRepository.findByMeetingId(124L))
                .thenReturn(List.of(Member.builder()
                        .id(502L)
                        .meeting(meeting)
                        .user(speaker)
                        .meetingRole(MeetingRole.HOST)
                        .build()));
        when(minutesRepository.save(any(Minutes.class))).thenAnswer(invocation -> {
            Minutes minutes = invocation.getArgument(0);
            if (minutes.getId() == null) {
                minutes.setId(78L);
            }
            return minutes;
        });
        when(fileStorageService.storeBytes(any(byte[].class), eq(FileType.MINUTES), eq(124L), eq("draft_78.pdf")))
                .thenReturn(Path.of("minutes/124/draft_78.pdf"));
        when(fileStorageService.storeBytes(any(byte[].class), eq(FileType.MINUTES), eq(124L), eq("draft_78.docx")))
                .thenReturn(Path.of("minutes/124/draft_78.docx"));

        service.compileDraftMinutes(meeting);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<byte[]> docxBytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).storeBytes(
                docxBytesCaptor.capture(),
                eq(FileType.MINUTES),
                eq(124L),
                eq("draft_78.docx"));
        String docxXml = unzipTextEntries(docxBytesCaptor.getValue()).get("word/document.xml");

        assertThat(docxXml)
                .contains("Noi dung thu nhat")
                .contains("Noi dung thu hai");
        assertThat(countOccurrences(docxXml, "Nguyen Quang Tung")).isEqualTo(1);
    }

    @Test
    void confirmMinutes_returnsSignedPdfPayloadAndSha256() throws Exception {
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
                .build();
        Minutes minutes = Minutes.builder()
                .id(77L)
                .meeting(meeting)
                .status(com.example.kolla.enums.MinutesStatus.DRAFT)
                .draftPdfPath("minutes/123/draft_77.pdf")
                .build();
        byte[] draftBytes = "draft-pdf".getBytes(StandardCharsets.UTF_8);
        byte[] signedBytes = "%PDF-signed".getBytes(StandardCharsets.UTF_8);

        when(meetingRepository.findById(123L)).thenReturn(Optional.of(meeting));
        when(minutesRepository.findByMeetingId(123L)).thenReturn(Optional.of(minutes));
        when(fileStorageService.loadFileAsResource("minutes/123/draft_77.pdf"))
                .thenReturn(new ByteArrayResource(draftBytes));
        when(pdfDigitalSignatureService.signPdf(draftBytes, "Chủ tọa"))
                .thenReturn(signedBytes);
        when(pdfDigitalSignatureService.signerCertificateSubject()).thenReturn("CN=Test Signer");
        when(fileStorageService.storeBytes(
                signedBytes,
                FileType.MINUTES,
                123L,
                "confirmed_77.pdf"))
                .thenReturn(Path.of("minutes/123/confirmed_77.pdf"));
        when(minutesRepository.save(any(Minutes.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MinutesConfirmationResponse response = service.confirmMinutes(123L, host, "jwt-token");

        assertThat(response.getMinutes().getStatus())
                .isEqualTo(com.example.kolla.enums.MinutesStatus.HOST_CONFIRMED);
        assertThat(response.getSignedPdfFileName()).isEqualTo("bien-ban-xac-nhan-123.pdf");
        assertThat(response.getSignedPdfContentType()).isEqualTo("application/pdf");
        assertThat(response.getSignedPdfBase64())
                .isEqualTo(Base64.getEncoder().encodeToString(signedBytes));
        assertThat(response.getSignedPdfSha256()).isEqualTo(sha256(signedBytes));
        verify(eventPublisher).publishMinutesConfirmed(123L, 77L);
    }

    @Test
    void confirmMinutes_rejectsAdminWhoIsNotMeetingHost() {
        User host = User.builder()
                .id(10L)
                .username("host")
                .fullName("Host")
                .role(Role.SECRETARY)
                .isActive(true)
                .build();
        User admin = User.builder()
                .id(11L)
                .username("admin")
                .fullName("Admin")
                .role(Role.ADMIN)
                .isActive(true)
                .build();
        Meeting meeting = Meeting.builder()
                .id(123L)
                .title("Hop")
                .host(host)
                .creator(host)
                .build();

        when(meetingRepository.findById(123L)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> service.confirmMinutes(123L, admin, "jwt-token"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only the meeting Host may confirm the minutes");
    }

    @Test
    void confirmMinutes_allowsPersistedHostMemberWhenMeetingHostIsTransientMissing() throws Exception {
        User host = User.builder()
                .id(10L)
                .username("host")
                .fullName("Host")
                .role(Role.USER)
                .isActive(true)
                .build();
        Meeting meeting = Meeting.builder()
                .id(123L)
                .title("Hop")
                .creator(host)
                .build();
        Minutes minutes = Minutes.builder()
                .id(77L)
                .meeting(meeting)
                .status(com.example.kolla.enums.MinutesStatus.DRAFT)
                .draftPdfPath("minutes/123/draft_77.pdf")
                .build();
        byte[] draftBytes = "draft-pdf".getBytes(StandardCharsets.UTF_8);
        byte[] signedBytes = "%PDF-signed".getBytes(StandardCharsets.UTF_8);

        when(meetingRepository.findById(123L)).thenReturn(Optional.of(meeting));
        when(memberRepository.findByMeetingIdAndUserId(123L, 10L))
                .thenReturn(Optional.of(Member.builder()
                        .id(501L)
                        .meeting(meeting)
                        .user(host)
                        .meetingRole(MeetingRole.HOST)
                        .build()));
        when(minutesRepository.findByMeetingId(123L)).thenReturn(Optional.of(minutes));
        when(fileStorageService.loadFileAsResource("minutes/123/draft_77.pdf"))
                .thenReturn(new ByteArrayResource(draftBytes));
        when(pdfDigitalSignatureService.signPdf(draftBytes, "Host"))
                .thenReturn(signedBytes);
        when(pdfDigitalSignatureService.signerCertificateSubject()).thenReturn("CN=Test Signer");
        when(fileStorageService.storeBytes(
                signedBytes,
                FileType.MINUTES,
                123L,
                "confirmed_77.pdf"))
                .thenReturn(Path.of("minutes/123/confirmed_77.pdf"));
        when(minutesRepository.save(any(Minutes.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MinutesConfirmationResponse response = service.confirmMinutes(123L, host, "jwt-token");

        assertThat(response.getMinutes().getStatus())
                .isEqualTo(com.example.kolla.enums.MinutesStatus.HOST_CONFIRMED);
    }

    @Test
    void editMinutes_rendersEditedDocxFromStructuredContentOnly() throws Exception {
        User secretary = User.builder()
                .id(12L).username("secretary").fullName("Secretary")
                .role(Role.SECRETARY).isActive(true).build();
        Meeting meeting = Meeting.builder()
                .id(123L).title("Hop nghiem thu").secretary(secretary).creator(secretary)
                .activatedAt(LocalDateTime.of(2026, 6, 5, 9, 0))
                .endedAt(LocalDateTime.of(2026, 6, 5, 10, 0)).build();
        Minutes minutes = Minutes.builder()
                .id(77L).meeting(meeting).status(MinutesStatus.HOST_CONFIRMED)
                .draftPdfPath("minutes/123/draft_77.pdf")
                .draftDocxPath("minutes/123/draft_77.docx")
                .confirmedPdfPath("minutes/123/confirmed_77.pdf")
                .hostConfirmationHash("hash-raw").build();
        EditMinutesRequest request = new EditMinutesRequest(
                List.of(new MinutesContentEntryRequest("Nguyen Van A", "Chu tri", "09:01", "Edited speech")),
                "Edited conclusion");

        when(meetingRepository.findById(123L)).thenReturn(Optional.of(meeting));
        when(minutesRepository.findByMeetingId(123L)).thenReturn(Optional.of(minutes));
        when(fileStorageService.storeBytes(any(byte[].class), eq(FileType.MINUTES), eq(123L), eq("edited_77.docx")))
                .thenReturn(Path.of("minutes/123/edited_77.docx"));
        when(minutesRepository.save(any(Minutes.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.editMinutes(123L, request, secretary);

        ArgumentCaptor<Minutes> minutesCaptor = ArgumentCaptor.forClass(Minutes.class);
        verify(minutesRepository).save(minutesCaptor.capture());
        Minutes saved = minutesCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(MinutesStatus.SECRETARY_CONFIRMED);
        assertThat(saved.getSecretaryDocxPath()).isEqualTo(Path.of("minutes/123/edited_77.docx").toString());
        assertThat(saved.getSecretaryPdfPath()).isNull();
        assertThat(saved.getConfirmedPdfPath()).isEqualTo("minutes/123/confirmed_77.pdf");
        assertThat(saved.getHostConfirmationHash()).isEqualTo("hash-raw");
        assertThat(saved.getContentEntriesJson()).contains("Edited speech");
        assertThat(response.isSecretaryDocxAvailable()).isTrue();
        assertThat(response.isSecretaryAvailable()).isFalse();
        assertThat(response.getContentEntries().get(0).getText()).isEqualTo("Edited speech");
    }

    private TranscriptionSegment segment(
            Long id,
            String turnId,
            int sequence,
            String speakerName,
            String text) {
        return segment(id, turnId, sequence, 10L, speakerName, text);
    }

    private TranscriptionSegment segment(
            Long id,
            String turnId,
            int sequence,
            Long speakerId,
            String speakerName,
            String text) {
        TranscriptionSegment segment = new TranscriptionSegment();
        segment.setId(id);
        segment.setJobId("job-" + id);
        segment.setSpeakerTurnId(turnId);
        segment.setSequenceNumber(sequence);
        segment.setSpeakerId(speakerId);
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

    private String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(data));
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int fromIndex = 0;
        while ((fromIndex = text.indexOf(needle, fromIndex)) >= 0) {
            count++;
            fromIndex += needle.length();
        }
        return count;
    }
}
