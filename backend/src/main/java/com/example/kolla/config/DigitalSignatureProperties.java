package com.example.kolla.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PKCS#12 / JKS keystore settings for Host PDF digital signatures.
 *
 * <p>Matches thesis §4.3.2 (SHA-256 + embedded PAdES/CAdES signature in PDF).
 * USB Token signing is out of scope here — use a .p12/.pfx exported from the token.
 */
@Data
@ConfigurationProperties(prefix = "digital-signature")
public class DigitalSignatureProperties {

    /**
     * When false, {@code POST /minutes/confirm} fails with a clear configuration error.
     */
    private boolean enabled = false;

    /**
     * Absolute path to PKCS#12 (.p12/.pfx) or JKS keystore inside the container/host.
     */
    private String keystorePath = "";

    /** Keystore password (use env var in production). */
    private String keystorePassword = "";

    /** {@code PKCS12} or {@code JKS}. */
    private String keystoreType = "PKCS12";

    /** Private-key entry alias; empty = first key entry in the keystore. */
    private String keyAlias = "";

    /** Visible in PDF signature panel. */
    private String reason = "Xác nhận biên bản cuộc họp";

    /** Visible in PDF signature panel. */
    private String location = "KollaMeeting";
}
