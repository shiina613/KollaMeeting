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
 * Requirements: 14.8
 */
@Configuration
public class AppConfig {

    private static final String TIMEZONE = "Asia/Ho_Chi_Minh";

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone(TIMEZONE));
    }

    /**
     * Clock bean for testability — inject Clock instead of calling LocalDateTime.now() directly.
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(TIMEZONE));
    }

    /**
     * RestTemplate configured for Gipformer HTTP calls.
     * Connect timeout: 5 s, read timeout: 60 s (Requirements: 8.7, 8.11).
     */
    @Bean("gipformerRestTemplate")
    public RestTemplate gipformerRestTemplate(
            RestTemplateBuilder builder,
            @Value("${gipformer.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${gipformer.read-timeout-ms:60000}") int readTimeoutMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}
