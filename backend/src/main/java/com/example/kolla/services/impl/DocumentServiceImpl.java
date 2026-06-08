package com.example.kolla.services.impl;

import com.example.kolla.enums.FileType;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.Document;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DocumentRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.responses.DocumentResponse;
import com.example.kolla.services.DocumentService;
import com.example.kolla.services.FileStorageService;
import com.example.kolla.services.MeetingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DocumentService implementation.
 * Requirements: 9.1â€“9.7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingService meetingService;
    private final FileStorageService fileStorageService;
    private final Clock clock;

    // â”€â”€ Upload Document â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    @Transactional
    public DocumentResponse uploadDocument(Long meetingId, MultipartFile file, User currentUser)
            throws IOException {

        Meeting meeting = findMeetingOrThrow(meetingId);

        // Submitted Word flow assigns document upload to the meeting secretary.
        checkAssignedSecretary(meeting, currentUser);

        // Validate file type and size (Requirement 9.2, 9.3)
        fileStorageService.validateFile(file, FileType.DOCUMENT);

        // Store file and get relative path (Requirement 9.7 â€” FileLock handles concurrency)
        Path storedPath = fileStorageService.storeFile(file, FileType.DOCUMENT, meetingId, "doc");

        Document document = Document.builder()
                .meeting(meeting)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .fileType(file.getContentType())
                .filePath(storedPath.toString())
                .uploadedBy(currentUser)
                .uploadedAt(LocalDateTime.now(clock))
                .build();

        Document saved = documentRepository.save(document);
        log.info("Uploaded document id={} '{}' to meeting id={} by user '{}'",
                saved.getId(), saved.getFileName(), meetingId, currentUser.getUsername());

        return DocumentResponse.from(saved);
    }

    // â”€â”€ List Documents â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(Long meetingId, User currentUser) {
        findMeetingOrThrow(meetingId); // ensure meeting exists

        // Members only (Requirement 9.4)
        checkMembership(meetingId, currentUser);

        return documentRepository.findByMeetingIdOrderByIdDesc(meetingId)
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }

    // â”€â”€ Get Document by ID â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocumentById(Long documentId, User currentUser) {
        Document document = findDocumentOrThrow(documentId);

        // Members only (Requirement 9.4)
        checkMembership(document.getMeeting().getId(), currentUser);

        return DocumentResponse.from(document);
    }

    // â”€â”€ Download Document â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    @Transactional(readOnly = true)
    public Resource downloadDocument(Long documentId, User currentUser) throws IOException {
        Document document = findDocumentOrThrow(documentId);

        // Members only (Requirement 9.5)
        checkMembership(document.getMeeting().getId(), currentUser);

        return fileStorageService.loadFileAsResource(document.getFilePath());
    }

    // â”€â”€ Delete Document â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    @Transactional
    public void deleteDocument(Long documentId, User currentUser) {
        Document document = findDocumentOrThrow(documentId);
        checkAssignedSecretaryForDelete(document.getMeeting(), currentUser);

        fileStorageService.deleteFile(document.getFilePath());

        documentRepository.delete(document);
        log.info("Deleted document id={} '{}' by user '{}'",
                documentId, document.getFileName(), currentUser.getUsername());
    }

    // â”€â”€ Private helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private Meeting findMeetingOrThrow(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Meeting not found with id: " + meetingId));
    }

    private Document findDocumentOrThrow(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document not found with id: " + documentId));
    }

    /**
     * Checks that the current user is a member of the meeting.
     * Throws ForbiddenException if not a member.
     */
    private void checkMembership(Long meetingId, User currentUser) {
        if (!meetingService.isMember(meetingId, currentUser.getId())) {
            throw new ForbiddenException(
                    "You are not a member of meeting id: " + meetingId);
        }
    }

    private void checkAssignedSecretary(Meeting meeting, User currentUser) {
        if (currentUser.getRole() != Role.SECRETARY) {
            throw new ForbiddenException("Only SECRETARY may upload documents");
        }
        User secretary = meeting.getSecretary();
        if (secretary == null || !secretary.getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Only the assigned meeting SECRETARY may upload documents");
        }
    }

    private void checkAssignedSecretaryForDelete(Meeting meeting, User currentUser) {
        if (currentUser.getRole() != Role.SECRETARY) {
            throw new ForbiddenException("Only SECRETARY may delete documents");
        }
        User secretary = meeting.getSecretary();
        if (secretary == null || !secretary.getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Only the assigned meeting SECRETARY may delete documents");
        }
    }
}
