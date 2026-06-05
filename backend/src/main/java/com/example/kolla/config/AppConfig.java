package com.example.kolla.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Application-level configuration.
 * Sets JVM default timezone to Asia/Ho_Chi_Minh (UTC+7).
 * Validates critical security properties at startup.
 * Requirements: 14.8
 */
@Configuration
public class AppConfig {

    private static final String TIMEZONE = "Asia/Ho_Chi_Minh";
    private static final String DEFAULT_JWT_SECRET =
            "Y2hhbmdlbWUtc3VwZXItc2VjcmV0LWtleS1hdC1sZWFzdC0zMi1jaGFycw==";
    private static final String DEFAULT_CALLBACK_KEY = "internal-callback-key-change-me";

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${asr-service.callback-api-key:}")
    private String callbackApiKey;

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone(TIMEZONE));
        validateSecurityProperties();
    }

    /**
     * Fail-fast if critical secrets are still set to their insecure defaults.
     * This prevents accidental deployment with forgeable JWT tokens.
     */
    private void validateSecurityProperties() {
        if (jwtSecret.isBlank() || DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "SECURITY ERROR: JWT_SECRET is not configured or is still the default value. "
                    + "Set a strong, unique JWT_SECRET environment variable before starting the application. "
                    + "Generate one with: openssl rand -base64 32");
        }
        if (callbackApiKey.isBlank() || DEFAULT_CALLBACK_KEY.equals(callbackApiKey)) {
            throw new IllegalStateException(
                    "SECURITY ERROR: ASR_CALLBACK_API_KEY is not configured or is still the default value. "
                    + "Set a strong, unique ASR_CALLBACK_API_KEY environment variable.");
        }
    }

    /**
     * Clock bean for testability — inject Clock instead of calling LocalDateTime.now() directly.
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(TIMEZONE));
    }

    /**
     * RestTemplate configured for ASR service HTTP calls.
     * Connect timeout: 5 s, read timeout: 60 s (Requirements: 8.7, 8.11).
     */
    @Bean("asrServiceRestTemplate")
    public RestTemplate asrServiceRestTemplate(
            RestTemplateBuilder builder,
            @Value("${asr-service.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${asr-service.read-timeout-ms:60000}") int readTimeoutMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}
