package com.example.kolla.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binding class for file.storage.* properties in application.yml.
 * Requirements: 6.1–6.7
 */
@Data
@ConfigurationProperties(prefix = "file.storage")
public class FileStorageProperties {

    /** Root directory for all stored files. Default: /app/storage */
    private String basePath = "/app/storage";

    /** Sub-directory for meeting recordings. */
    private String recordingsDir = "recordings";

    /** Sub-directory for uploaded documents. */
    private String documentsDir = "documents";

    /** Sub-directory for audio chunks used in transcription. */
    private String audioChunksDir = "audio_chunks";

    /** Sub-directory for generated meeting minutes. */
    private String minutesDir = "minutes";

    /** Allowed MIME types for document uploads. */
    private List<String> allowedDocumentTypes = List.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "image/jpeg",
            "image/png"
    );

    /** Maximum allowed document size in megabytes. */
    private long maxDocumentSizeMb = 100;

    /** Maximum allowed recording size in megabytes. */
    private long maxRecordingSizeMb = 5120;
}
