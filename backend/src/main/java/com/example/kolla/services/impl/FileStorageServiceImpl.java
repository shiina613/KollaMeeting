package com.example.kolla.services.impl;

import com.example.kolla.config.FileStorageProperties;
import com.example.kolla.enums.FileType;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.services.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Local-filesystem implementation of {@link FileStorageService}.
 * Files are stored under: {basePath}/{type}/{meetingId}/
 * Concurrent writes are protected with {@link FileLock}.
 * Requirements: 6.1–6.7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private final FileStorageProperties properties;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Path storeFile(MultipartFile file, FileType type, Long meetingId, String fileNamePrefix)
            throws IOException {

        validateFile(file, type);

        Path dir = resolveTypeDir(type).resolve(String.valueOf(meetingId));
        Files.createDirectories(dir);

        String sanitizedOriginal = sanitizeFilename(file.getOriginalFilename());
        String uniqueName = fileNamePrefix + "_" + UUID.randomUUID() + "_" + sanitizedOriginal;
        Path target = dir.resolve(uniqueName);

        try (InputStream in = file.getInputStream()) {
            writeBytesWithLock(target, in.readAllBytes());
        }

        Path relative = basePath().relativize(target);
        log.info("Stored file [{}] for meeting {} -> {}", type, meetingId, relative);
        return relative;
    }

    @Override
    public Path storeBytes(byte[] content, FileType type, Long meetingId, String fileName)
            throws IOException {

        Path dir = resolveTypeDir(type).resolve(String.valueOf(meetingId));
        Files.createDirectories(dir);

        String sanitizedName = sanitizeFilename(fileName);
        Path target = dir.resolve(sanitizedName);

        writeBytesWithLock(target, content);

        Path relative = basePath().relativize(target);
        log.info("Stored bytes [{}] for meeting {} -> {}", type, meetingId, relative);
        return relative;
    }

    @Override
    public Resource loadFileAsResource(String filePath) throws IOException {
        Path base = basePath().toAbsolutePath().normalize();
        Path resolved = base.resolve(filePath).normalize();

        // Security: prevent path traversal
        if (!resolved.startsWith(base)) {
            log.warn("Path traversal attempt detected for path: {}", filePath);
            throw new BadRequestException("Invalid file path");
        }

        if (!Files.exists(resolved)) {
            throw new NoSuchFileException("File not found: " + filePath);
        }

        Resource resource = new UrlResource(resolved.toUri());
        if (!resource.isReadable()) {
            throw new IOException("File is not readable: " + filePath);
        }
        return resource;
    }

    @Override
    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            Path base = basePath().toAbsolutePath().normalize();
            Path resolved = base.resolve(filePath).normalize();

            // Security: prevent path traversal on delete
            if (!resolved.startsWith(base)) {
                log.warn("Path traversal attempt on delete for path: {}", filePath);
                return;
            }

            boolean deleted = Files.deleteIfExists(resolved);
            if (deleted) {
                log.info("Deleted file: {}", resolved);
            } else {
                log.debug("File not found for deletion (already removed?): {}", resolved);
            }
        } catch (IOException e) {
            log.error("Failed to delete file: {}", filePath, e);
        }
    }

    @Override
    public Map<FileType, Long> getStorageStats() {
        Map<FileType, Long> stats = new EnumMap<>(FileType.class);
        for (FileType type : FileType.values()) {
            Path dir = resolveTypeDir(type);
            stats.put(type, sumDirectorySize(dir));
        }
        return stats;
    }

    @Override
    public void validateFile(MultipartFile file, FileType type) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File must not be empty");
        }

        long sizeBytes = file.getSize();

        switch (type) {
            case DOCUMENT -> {
                String contentType = file.getContentType();
                if (contentType == null
                        || !properties.getAllowedDocumentTypes().contains(contentType)) {
                    throw new BadRequestException(
                            "Unsupported document type: " + contentType
                            + ". Allowed types: " + properties.getAllowedDocumentTypes());
                }
                long maxBytes = properties.getMaxDocumentSizeMb() * 1024L * 1024L;
                if (sizeBytes > maxBytes) {
                    throw new BadRequestException(
                            "Document size " + toMb(sizeBytes) + " MB exceeds the maximum of "
                            + properties.getMaxDocumentSizeMb() + " MB");
                }
            }
            case RECORDING -> {
                long maxBytes = properties.getMaxRecordingSizeMb() * 1024L * 1024L;
                if (sizeBytes > maxBytes) {
                    throw new BadRequestException(
                            "Recording size " + toMb(sizeBytes) + " MB exceeds the maximum of "
                            + properties.getMaxRecordingSizeMb() + " MB");
                }
            }
            // AUDIO_CHUNK and MINUTES have no size restriction by default
            default -> { /* no-op */ }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Write bytes to {@code target} using a {@link FileChannel} + {@link FileLock}
     * to prevent data corruption from concurrent writes.
     */
    private void writeBytesWithLock(Path target, byte[] data) throws IOException {
        try (FileChannel channel = FileChannel.open(target,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
             FileLock lock = channel.lock()) {

            ByteBuffer buffer = ByteBuffer.wrap(data);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true); // flush to disk
            log.debug("Wrote {} bytes to {} (lock acquired)", data.length, target);
        }
    }

    /** Resolve the base storage path as a {@link Path}. */
    private Path basePath() {
        return Paths.get(properties.getBasePath());
    }

    /** Resolve the top-level directory for a given {@link FileType}. */
    private Path resolveTypeDir(FileType type) {
        return basePath().resolve(type.getDirName());
    }

    /**
     * Sanitize a filename by replacing whitespace and characters that are
     * unsafe in filesystem paths with underscores, while preserving the extension.
     */
    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "file";
        }
        // Normalize path separators first (prevent directory traversal in filename)
        String name = Paths.get(original).getFileName().toString();
        // Replace any character that is not alphanumeric, dot, hyphen, or underscore
        return name.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
    }

    /**
     * Walk a directory tree and sum all regular file sizes.
     * Returns 0 if the directory does not exist.
     */
    private long sumDirectorySize(Path dir) {
        if (!Files.exists(dir)) {
            return 0L;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            log.warn("Cannot read size of file: {}", p, e);
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            log.error("Failed to calculate storage stats for directory: {}", dir, e);
            return 0L;
        }
    }

    /** Convert bytes to megabytes (rounded to 2 decimal places) for error messages. */
    private static String toMb(long bytes) {
        return String.format("%.2f", bytes / (1024.0 * 1024.0));
    }
}
