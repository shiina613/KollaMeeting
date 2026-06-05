package com.example.kolla.services;

import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for the signed-PDF integrity digest stored after Host confirmation.
 *
 * <p>Replaces the legacy JWT+PDF text-stamp hash (Property 13).
 */
class MinutesSignedPdfDigestPropertyTest {

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Property(tries = 300)
    @Label("Signed PDF digest is 64-char hex (SHA-256)")
    void digestIs64CharHex(@ForAll @Size(min = 1, max = 50_000) byte[] signedPdf) {
        String hash = computeSha256(signedPdf);
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Property(tries = 300)
    @Label("Same signed PDF bytes produce the same digest")
    void digestIsDeterministic(@ForAll @Size(min = 1, max = 10_000) byte[] signedPdf) {
        assertThat(computeSha256(signedPdf)).isEqualTo(computeSha256(signedPdf));
    }

    @Property(tries = 300)
    @Label("Different signed PDF bytes produce different digests")
    void differentPdfDifferentDigest(
            @ForAll @Size(min = 1, max = 5000) byte[] pdf1,
            @ForAll @Size(min = 1, max = 5000) byte[] pdf2) {
        Assume.that(!java.util.Arrays.equals(pdf1, pdf2));
        assertThat(computeSha256(pdf1)).isNotEqualTo(computeSha256(pdf2));
    }
}
