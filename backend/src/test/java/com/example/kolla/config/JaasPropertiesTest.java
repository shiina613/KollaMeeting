package com.example.kolla.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JaasProperties.
 * Requirements: 2.1, 2.2, 2.5
 */
class JaasPropertiesTest {

    private JaasProperties jaasProperties;

    @BeforeEach
    void setUp() {
        jaasProperties = new JaasProperties();
    }

    // ── isEnabled() ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("isEnabled() returns false when appId is null")
    void isEnabled_returnsFalse_whenAppIdIsNull() {
        jaasProperties.setAppId(null);

        assertThat(jaasProperties.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEnabled() returns false when appId is empty string")
    void isEnabled_returnsFalse_whenAppIdIsEmpty() {
        jaasProperties.setAppId("");

        assertThat(jaasProperties.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEnabled() returns false when appId is blank (whitespace only)")
    void isEnabled_returnsFalse_whenAppIdIsBlank() {
        jaasProperties.setAppId("   ");

        assertThat(jaasProperties.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEnabled() returns true when appId has a non-blank value")
    void isEnabled_returnsTrue_whenAppIdIsNonBlank() {
        jaasProperties.setAppId("my-app-id");

        assertThat(jaasProperties.isEnabled()).isTrue();
    }

    // ── extractKeyId() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("extractKeyId() returns the part after the last '/' in the standard JaaS format")
    void extractKeyId_returnsKeyIdPart_forStandardFormat() {
        jaasProperties.setApiKey("vpaas-magic-cookie-abc123/myKeyId");

        assertThat(jaasProperties.extractKeyId()).isEqualTo("myKeyId");
    }

    @Test
    @DisplayName("extractKeyId() returns the full string when there is no '/' in apiKey")
    void extractKeyId_returnsFullString_whenNoSlash() {
        jaasProperties.setApiKey("noSlashApiKey");

        assertThat(jaasProperties.extractKeyId()).isEqualTo("noSlashApiKey");
    }

    @Test
    @DisplayName("extractKeyId() returns empty string when apiKey is null")
    void extractKeyId_returnsEmptyString_whenApiKeyIsNull() {
        jaasProperties.setApiKey(null);

        assertThat(jaasProperties.extractKeyId()).isEqualTo("");
    }
}
