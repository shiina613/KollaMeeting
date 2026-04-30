package com.example.kolla.services.impl;

import com.example.kolla.dto.BulkDeleteRequest;
import com.example.kolla.enums.FileType;
import com.example.kolla.enums.StorageOperationType;
import com.example.kolla.models.Document;
import com.example.kolla.models.Recording;
import com.example.kolla.models.StorageLog;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DocumentRepository;
import com.example.kolla.repositories.RecordingRepository;
import com.example.kolla.repositories.StorageLogRepository;
import com.example.kolla.responses.BulkDeleteResponse;
import com.example.kolla.responses.StorageStatsResponse;
import com.example.kolla.services.FileStorageService;
import com.example.kolla.services.StorageManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of {@link StorageManagementService}.
 * Handles storage stats retrieval and bulk delete with audit logging.
 * Requirements: 6.7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageManagementServiceImpl implements StorageManagementService {

    private final FileStorageService fileStorageService;
    private final RecordingRepository recordingRepository;
    private final DocumentRepository documentRepository;
    private final StorageLogRepository storageLogRepository;
    private final Clock clock;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public StorageStatsResponse getStorageStats() {
        Map<FileType, Long> stats = fileStorageService.getStorageStats();

        long recordingsBytes   = stats.getOrDefault(FileType.RECORDING, 0L);
        long documentsBytes    = stats.getOrDefault(FileType.DOCUMENT, 0L);
        long audioChunksBytes  = stats.getOrDefault(FileType.AUDIO_CHUNK, 0L);
        long minutesBytes      = stats.getOrDefault(FileType.MINUTES, 0L);
        long totalBytes        = recordingsBytes + documentsBytes + audioChunksBytes + minutesBytes;

        return StorageStatsResponse.builder()
                .recordingsTotalBytes(recordingsBytes)
                .documentsTotalBytes(documentsBytes)
                .audioChunksTotalBytes(audioChunksBytes)
                .minutesTotalBytes(minutesBytes)
                .totalBytes(totalBytes)
                .recordingsTotalMb(toMbString(recordingsBytes))
                .documentsTotalMb(toMbString(documentsBytes))
                .audioChunksTotalMb(toMbString(audioChunksBytes))
                .minutesTotalMb(toMbString(minutesBytes))
                .totalMb(toMbString(totalBytes))
                .build();
    }

    @Override
    @Transactional
    public BulkDeleteResponse bulkDelete(BulkDeleteRequest request, User adminUser) {
        List<Long> recordingIds = request.getRecordingIds() != null
                ? request.getRecordingIds() : List.of();
        List<Long> documentIds = request.getDocumentIds() != null
                ? request.getDocumentIds() : List.of();

        int deletedRecordings = 0;
        long totalSizeBytes = 0L;

        // Process recordings
        for (Long id : recordingIds) {
            Optional<Recording> opt = recordingRepository.findById(id);
            if (opt.isEmpty()) {
                log.warn("Recording ID {} not found, skipping", id);
                continue;
            }
            Recording recording = opt.get();
            long size = recording.getFileSize() != null ? recording.getFileSize() : 0L;

            log.debug("Deleting recording ID={} path={} size={} bytes", id, recording.getFilePath(), size);
            fileStorageService.deleteFile(recording.getFilePath());
            recordingRepository.delete(recording);

            deletedRecordings++;
            totalSizeBytes += size;
        }

        int deletedDocuments = 0;

        // Process documents
        for (Long id : documentIds) {
            Optional<Document> opt = documentRepository.findById(id);
            if (opt.isEmpty()) {
                log.warn("Document ID {} not found, skipping", id);
                continue;
            }
            Document document = opt.get();
            long size = document.getFileSize() != null ? document.getFileSize() : 0L;

            log.debug("Deleting document ID={} path={} size={} bytes", id, document.getFilePath(), size);
            fileStorageService.deleteFile(document.getFilePath());
            documentRepository.delete(document);

            deletedDocuments++;
            totalSizeBytes += size;
        }

        int totalDeleted = deletedRecordings + deletedDocuments;

        // Persist audit log
        StorageLog storageLog = StorageLog.builder()
                .adminUser(adminUser)
                .operation(StorageOperationType.BULK_DELETE)
                .fileCount(totalDeleted)
                .totalSizeBytes(totalSizeBytes)
                .description(request.getDescription())
                .createdAt(LocalDateTime.now(clock))
                .build();
        storageLogRepository.save(storageLog);

        log.info("Bulk delete completed by user={}: {} recordings, {} documents deleted, total {} bytes freed",
                adminUser.getId(), deletedRecordings, deletedDocuments, totalSizeBytes);

        return BulkDeleteResponse.builder()
                .deletedRecordings(deletedRecordings)
                .deletedDocuments(deletedDocuments)
                .totalDeleted(totalDeleted)
                .totalSizeDeletedBytes(totalSizeBytes)
                .totalSizeDeletedMb(toMbString(totalSizeBytes))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String toMbString(long bytes) {
        return String.format("%.2f", bytes / (1024.0 * 1024.0));
    }
}
