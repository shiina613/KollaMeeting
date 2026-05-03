package com.example.kolla.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binding class for jaas.* properties in application.yml.
 * Requirements: 2.1, 2.2, 2.3, 2.5
 */
@Data
@ConfigurationProperties(prefix = "jaas")
public class JaasProperties {

    /** JaaS application ID (tenant identifier). */
    private String appId;

    /** JaaS API key, typically in the form "vpaas-magic-cookie-xxx/keyId". */
    private String apiKey;

    /** PEM-encoded RSA private key used to sign JaaS JWTs. */
    private String privateKey;

    /**
     * Returns {@code true} when JaaS integration is active,
     * i.e. {@code appId} is configured and non-blank.
     */
    public boolean isEnabled() {
        return appId != null && !appId.isBlank();
    }

    /**
     * Extracts the key ID from the API key.
     * <p>
     * The API key is expected to follow the pattern
     * {@code "vpaas-magic-cookie-xxx/keyId"}. This method returns the
     * substring after the last {@code '/'}.
     * <ul>
     *   <li>If {@code apiKey} is {@code null}, returns {@code ""}.</li>
     *   <li>If no {@code '/'} is found, returns the full {@code apiKey}.</li>
     * </ul>
     *
     * @return the key ID portion of the API key
     */
    public String extractKeyId() {
        if (apiKey == null) {
            return "";
        }
        int lastSlash = apiKey.lastIndexOf('/');
        if (lastSlash < 0) {
            return apiKey;
        }
        return apiKey.substring(lastSlash + 1);
    }
}
