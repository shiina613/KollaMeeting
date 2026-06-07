package com.example.kolla.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.kolla.enums.FileType;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.models.Document;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DocumentRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.responses.DocumentResponse;
import com.example.kolla.services.impl.DocumentServiceImpl;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MeetingService meetingService;

    @Mock
    private FileStorageService fileStorageService;

    private DocumentServiceImpl service;
    private Meeting meeting;
    private MockMultipartFile file;

    @BeforeEach
    void setUp() {
        service = new DocumentServiceImpl(
                documentRepository,
                meetingRepository,
                meetingService,
                fileStorageService,
                Clock.fixed(Instant.parse("2026-06-07T00:00:00Z"), ZoneOffset.UTC));
        meeting = Meeting.builder().id(10L).build();
        file = new MockMultipartFile("file", "agenda.pdf", "application/pdf", "data".getBytes());
    }

    @Test
    void uploadDocumentAllowsSecretary() throws Exception {
        User secretary = user(20L, Role.SECRETARY);
        meeting.setSecretary(secretary);
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));
        when(fileStorageService.storeFile(file, FileType.DOCUMENT, 10L, "doc"))
                .thenReturn(Path.of("documents/10/agenda.pdf"));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setId(99L);
            return document;
        });

        DocumentResponse response = service.uploadDocument(10L, file, secretary);

        assertThat(response.getId()).isEqualTo(99L);
        verify(fileStorageService).validateFile(file, FileType.DOCUMENT);
        verify(fileStorageService).storeFile(file, FileType.DOCUMENT, 10L, "doc");
    }

    @Test
    void uploadDocumentRejectsSecretaryNotAssignedToMeeting() {
        User assignedSecretary = user(20L, Role.SECRETARY);
        User otherSecretary = user(23L, Role.SECRETARY);
        meeting.setSecretary(assignedSecretary);
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> service.uploadDocument(10L, file, otherSecretary))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only the assigned meeting SECRETARY may upload documents");

        verify(fileStorageService, never()).validateFile(any(), eq(FileType.DOCUMENT));
        verify(documentRepository, never()).save(any());
    }

    @Test
    void uploadDocumentRejectsNormalMemberEvenWhenAssignedToMeeting() {
        User member = user(21L, Role.USER);
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> service.uploadDocument(10L, file, member))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only SECRETARY may upload documents");

        verify(fileStorageService, never()).validateFile(any(), eq(FileType.DOCUMENT));
        verify(documentRepository, never()).save(any());
    }

    @Test
    void uploadDocumentRejectsAdminBecauseWordFlowAssignsUploadToSecretary() {
        User admin = user(22L, Role.ADMIN);
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> service.uploadDocument(10L, file, admin))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only SECRETARY may upload documents");

        verify(fileStorageService, never()).validateFile(any(), eq(FileType.DOCUMENT));
        verify(documentRepository, never()).save(any());
    }

    private static User user(Long id, Role role) {
        return User.builder()
                .id(id)
                .username("user" + id)
                .fullName("User " + id)
                .role(role)
                .isActive(true)
                .build();
    }
}
