package com.example.kolla.services;

import com.example.kolla.models.User;
import com.example.kolla.responses.DocumentResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Document management service interface.
 * Requirements: 9.1–9.7
 */
public interface DocumentService {

    /**
     * Upload a document to a meeting.
     * Any authenticated member may upload.
     * Requirements: 9.1, 9.2, 9.3
     *
     * @param meetingId   the target meeting
     * @param file        the uploaded file
     * @param currentUser the authenticated user
     * @return the saved document response
     * @throws IOException if the file cannot be stored
     */
    DocumentResponse uploadDocument(Long meetingId, MultipartFile file, User currentUser)
            throws IOException;

    /**
     * List all documents for a meeting.
     * Meeting members only.
     * Requirements: 9.4
     *
     * @param meetingId   the target meeting
     * @param currentUser the authenticated user
     * @return list of document responses ordered by upload time descending
     */
    List<DocumentResponse> listDocuments(Long meetingId, User currentUser);

    /**
     * Get a document by its ID.
     * Meeting members only.
     * Requirements: 9.4
     *
     * @param documentId  the document ID
     * @param currentUser the authenticated user
     * @return the document response
     */
    DocumentResponse getDocumentById(Long documentId, User currentUser);

    /**
     * Download a document file.
     * Meeting members only.
     * Requirements: 9.5
     *
     * @param documentId  the document ID
     * @param currentUser the authenticated user
     * @return the file as a Spring Resource
     * @throws IOException if the file cannot be read
     */
    Resource downloadDocument(Long documentId, User currentUser) throws IOException;

    /**
     * Delete a document.
     * ADMIN or SECRETARY only.
     * Requirements: 9.6
     *
     * @param documentId  the document ID
     * @param currentUser the authenticated user
     */
    void deleteDocument(Long documentId, User currentUser);
}
