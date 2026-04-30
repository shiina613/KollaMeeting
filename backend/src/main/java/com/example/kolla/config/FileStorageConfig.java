package com.example.kolla.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Ensures all required storage directories exist at application startup.
 * Requirements: 6.1
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfig {

    private final FileStorageProperties properties;

    @PostConstruct
    public void createStorageDirectories() {
        Path base = Paths.get(properties.getBasePath());

        String[] subDirs = {
                properties.getRecordingsDir(),
                properties.getDocumentsDir(),
                properties.getAudioChunksDir(),
                properties.getMinutesDir()
        };

        for (String dir : subDirs) {
            Path target = base.resolve(dir);
            try {
                Files.createDirectories(target);
                log.info("Storage directory ready: {}", target.toAbsolutePath());
            } catch (IOException e) {
                log.error("Failed to create storage directory: {}", target.toAbsolutePath(), e);
                throw new IllegalStateException(
                        "Cannot create storage directory: " + target.toAbsolutePath(), e);
            }
        }
    }
}
