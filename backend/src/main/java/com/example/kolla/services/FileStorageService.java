package com.example.kolla.services;

import com.example.kolla.enums.FileType;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Service for storing, retrieving, and deleting files on the local filesystem.
 * Files are organised under: {basePath}/{type}/{meetingId}/
 * Requirements: 6.1–6.7
 */
public interface FileStorageService {

    /**
     * Store a {@link MultipartFile} under {basePath}/{type}/{meetingId}/.
     * Validates type and size before writing.
     * Uses {@code FileLock} for concurrent-write safety.
     *
     * @param file           the uploaded file
     * @param type           the file category
     * @param meetingId      the owning meeting's ID
     * @param fileNamePrefix prefix prepended to the generated filename
     * @return relative path from basePath (e.g. {@code recordings/123/prefix_uuid_file.mp4})
     * @throws IOException if the file cannot be written
     */
    Path storeFile(MultipartFile file, FileType type, Long meetingId, String fileNamePrefix)
            throws IOException;

    /**
     * Store raw bytes (e.g. a generated PDF) under {basePath}/{type}/{meetingId}/.
     * Uses {@code FileLock} for concurrent-write safety.
     *
     * @param content   the bytes to write
     * @param type      the file category
     * @param meetingId the owning meeting's ID
     * @param fileName  the exact filename to use
     * @return relative path from basePath
     * @throws IOException if the file cannot be written
     */
    Path storeBytes(byte[] content, FileType type, Long meetingId, String fileName)
            throws IOException;

    /**
     * Resolve a stored file and return it as a downloadable {@link Resource}.
     * Performs a path-traversal security check before resolving.
     *
     * @param filePath relative path returned by {@link #storeFile} or {@link #storeBytes}
     * @return a {@link org.springframework.core.io.UrlResource} pointing to the file
     * @throws IOException if the file cannot be read
     */
    Resource loadFileAsResource(String filePath) throws IOException;

    /**
     * Delete a file from the filesystem.
     * Silently ignores missing files; logs the deletion.
     *
     * @param filePath relative path returned by {@link #storeFile} or {@link #storeBytes}
     */
    void deleteFile(String filePath);

    /**
     * Calculate total stored bytes per {@link FileType}.
     *
     * @return map of FileType → total bytes in that directory
     */
    Map<FileType, Long> getStorageStats();

    /**
     * Validate a {@link MultipartFile} against the configured type/size rules.
     * <ul>
     *   <li>DOCUMENT: content-type must be in {@code allowedDocumentTypes}; size ≤ maxDocumentSizeMb</li>
     *   <li>RECORDING: size ≤ maxRecordingSizeMb</li>
     * </ul>
     *
     * @param file the file to validate
     * @param type the intended file category
     * @throws com.example.kolla.exceptions.BadRequestException on violation
     */
    void validateFile(MultipartFile file, FileType type);
}
