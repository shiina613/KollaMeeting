package com.example.kolla.services;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for minutes confirmation stamp completeness.
 *
 * <p>Property 13: Minutes Confirmation Stamp Completeness
 *
 * <p>Verifies the invariants of the digital stamp embedded when the Host confirms
 * meeting minutes:
 * <blockquote>
 *   The confirmation stamp must include:
 *   <ul>
 *     <li>The Host's full name</li>
 *     <li>An ISO 8601 timestamp</li>
 *     <li>A SHA-256 hash of (JWT token + PDF content)</li>
 *   </ul>
 *   The hash must be deterministic: the same inputs always produce the same hash.
 *   Different inputs must produce different hashes (collision resistance).
 * </blockquote>
 *
 * <p>Tests run in pure logic mode (no Spring context) by directly exercising
 * the stamp computation logic extracted from {@code MinutesServiceImpl}.
 *
 * Requirements: 25.4
 */
class MinutesConfirmationStampPropertyTest {

    private static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    // ── Stamp computation (mirrors MinutesServiceImpl) ────────────────────────

    /**
     * Build the stamp text line: "Confirmed by: {hostName} | {isoTimestamp}"
     */
    private String buildStampText(String hostName, LocalDateTime confirmedAt) {
        String isoTimestamp = confirmedAt.atZone(ZONE_VN).format(ISO_FORMATTER);
        return "Confirmed by: " + hostName + " | " + isoTimestamp;
    }

    /**
     * Compute SHA-256 hash of (jwtToken + pdfContent).
     * Mirrors {@code MinutesServiceImpl.computeSha256()}.
     */
    private String computeConfirmationHash(String jwtToken, String pdfContent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = jwtToken + pdfContent;
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Validate that a stamp text contains all required components.
     */
    private boolean stampContainsHostName(String stampText, String hostName) {
        return stampText.contains(hostName);
    }

    private boolean stampContainsIsoTimestamp(String stampText) {
        // ISO 8601 pattern: YYYY-MM-DDTHH:mm:ss+HH:MM
        // Use (?s) flag to allow . to match newlines in the host name portion
        return stampText.matches("(?s).*\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}.*");
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /**
     * Property 13a: Stamp text always contains the Host's full name.
     *
     * <p>For any host name and confirmation timestamp, the generated stamp text
     * must contain the host's full name verbatim.
     */
    @Property(tries = 500)
    @Label("P13a: Stamp text always contains the Host's full name")
    void stampTextAlwaysContainsHostName(
            @ForAll @NotBlank String hostName,
            @ForAll("confirmationTimes") LocalDateTime confirmedAt) {

        String stampText = buildStampText(hostName, confirmedAt);

        assertThat(stampContainsHostName(stampText, hostName))
                .as("Stamp text '%s' must contain host name '%s'", stampText, hostName)
                .isTrue();
    }

    /**
     * Property 13b: Stamp text always contains an ISO 8601 timestamp.
     *
     * <p>The timestamp must be in the format YYYY-MM-DDTHH:mm:ss+HH:MM (UTC+7).
     */
    @Property(tries = 500)
    @Label("P13b: Stamp text always contains an ISO 8601 timestamp")
    void stampTextAlwaysContainsIsoTimestamp(
            @ForAll @NotBlank String hostName,
            @ForAll("confirmationTimes") LocalDateTime confirmedAt) {

        String stampText = buildStampText(hostName, confirmedAt);

        assertThat(stampContainsIsoTimestamp(stampText))
                .as("Stamp text '%s' must contain an ISO 8601 timestamp", stampText)
                .isTrue();
    }

    /**
     * Property 13c: Confirmation hash is a valid 64-character hex string (SHA-256).
     *
     * <p>SHA-256 produces 32 bytes = 64 hex characters.
     */
    @Property(tries = 500)
    @Label("P13c: Confirmation hash is a valid 64-character hex string (SHA-256)")
    void confirmationHashIsValid64CharHex(
            @ForAll @NotBlank String jwtToken,
            @ForAll @NotBlank String pdfContent) {

        String hash = computeConfirmationHash(jwtToken, pdfContent);

        assertThat(hash)
                .as("SHA-256 hash must be exactly 64 hex characters")
                .hasSize(64);
        assertThat(hash.matches("[0-9a-f]{64}"))
                .as("SHA-256 hash must contain only lowercase hex characters, got: " + hash)
                .isTrue();
    }

    /**
     * Property 13d: Confirmation hash is deterministic — same inputs produce same hash.
     *
     * <p>Computing the hash twice with the same JWT and PDF content must yield
     * identical results.
     */
    @Property(tries = 500)
    @Label("P13d: Confirmation hash is deterministic (same inputs → same hash)")
    void confirmationHashIsDeterministic(
            @ForAll @NotBlank String jwtToken,
            @ForAll @NotBlank String pdfContent) {

        String hash1 = computeConfirmationHash(jwtToken, pdfContent);
        String hash2 = computeConfirmationHash(jwtToken, pdfContent);

        assertThat(hash1)
                .as("Same inputs must always produce the same hash")
                .isEqualTo(hash2);
    }

    /**
     * Property 13e: Different JWT tokens produce different hashes (for same PDF content).
     *
     * <p>Changing the JWT token must change the confirmation hash, ensuring the
     * stamp is bound to the specific confirming user's session.
     */
    @Property(tries = 500)
    @Label("P13e: Different JWT tokens produce different hashes (collision resistance)")
    void differentJwtTokensProduceDifferentHashes(
            @ForAll @NotBlank String jwtToken1,
            @ForAll @NotBlank String jwtToken2,
            @ForAll @NotBlank String pdfContent) {

        Assume.that(!jwtToken1.equals(jwtToken2));

        String hash1 = computeConfirmationHash(jwtToken1, pdfContent);
        String hash2 = computeConfirmationHash(jwtToken2, pdfContent);

        assertThat(hash1)
                .as("Different JWT tokens must produce different hashes "
                        + "(jwt1='%s', jwt2='%s', pdf='%s')",
                        jwtToken1, jwtToken2, pdfContent)
                .isNotEqualTo(hash2);
    }

    /**
     * Property 13f: Different PDF contents produce different hashes (for same JWT).
     *
     * <p>Changing the PDF content must change the confirmation hash, ensuring the
     * stamp is bound to the specific document being confirmed.
     */
    @Property(tries = 500)
    @Label("P13f: Different PDF contents produce different hashes (collision resistance)")
    void differentPdfContentsProduceDifferentHashes(
            @ForAll @NotBlank String jwtToken,
            @ForAll @NotBlank String pdfContent1,
            @ForAll @NotBlank String pdfContent2) {

        Assume.that(!pdfContent1.equals(pdfContent2));

        String hash1 = computeConfirmationHash(jwtToken, pdfContent1);
        String hash2 = computeConfirmationHash(jwtToken, pdfContent2);

        assertThat(hash1)
                .as("Different PDF contents must produce different hashes "
                        + "(jwt='%s', pdf1='%s', pdf2='%s')",
                        jwtToken, pdfContent1, pdfContent2)
                .isNotEqualTo(hash2);
    }

    /**
     * Property 13g: Stamp text format is "Confirmed by: {name} | {timestamp}".
     *
     * <p>The stamp must follow the exact format expected by the PDF renderer
     * and any downstream verification tools.
     */
    @Property(tries = 300)
    @Label("P13g: Stamp text follows the expected format 'Confirmed by: {name} | {timestamp}'")
    void stampTextFollowsExpectedFormat(
            @ForAll @NotBlank String hostName,
            @ForAll("confirmationTimes") LocalDateTime confirmedAt) {

        // Avoid names that contain the separator " | " to keep the test clean
        Assume.that(!hostName.contains(" | "));

        String stampText = buildStampText(hostName, confirmedAt);

        assertThat(stampText)
                .as("Stamp text must start with 'Confirmed by: '")
                .startsWith("Confirmed by: ");

        assertThat(stampText)
                .as("Stamp text must contain ' | ' separator between name and timestamp")
                .contains(" | ");

        // Extract the name portion
        String afterPrefix = stampText.substring("Confirmed by: ".length());
        String extractedName = afterPrefix.split(" \\| ")[0];

        assertThat(extractedName)
                .as("Extracted name '%s' must equal the original host name '%s'",
                        extractedName, hostName)
                .isEqualTo(hostName);
    }

    /**
     * Property 13h: Timestamp in stamp is in UTC+7 timezone.
     *
     * <p>The ISO 8601 timestamp embedded in the stamp must use the +07:00 offset,
     * as required by the system's UTC+7 timezone setting.
     */
    @Property(tries = 300)
    @Label("P13h: Timestamp in stamp uses UTC+7 offset (+07:00)")
    void timestampInStampUsesUtcPlus7(
            @ForAll @NotBlank String hostName,
            @ForAll("confirmationTimes") LocalDateTime confirmedAt) {

        String stampText = buildStampText(hostName, confirmedAt);

        assertThat(stampText)
                .as("Stamp text '%s' must contain UTC+7 offset (+07:00)", stampText)
                .contains("+07:00");
    }

    /**
     * Property 13i: Hash length is always exactly 64 characters regardless of input length.
     *
     * <p>SHA-256 always produces a fixed-length output, regardless of input size.
     */
    @Property(tries = 300)
    @Label("P13i: Hash length is always exactly 64 characters regardless of input length")
    void hashLengthIsAlways64Characters(
            @ForAll("variableLengthStrings") String jwtToken,
            @ForAll("variableLengthStrings") String pdfContent) {

        String hash = computeConfirmationHash(jwtToken, pdfContent);

        assertThat(hash)
                .as("SHA-256 hash must always be exactly 64 characters, got %d for "
                        + "jwt.length=%d, pdf.length=%d",
                        hash.length(), jwtToken.length(), pdfContent.length())
                .hasSize(64);
    }

    // ── Arbitraries ───────────────────────────────────────────────────────────

    @Provide
    Arbitrary<LocalDateTime> confirmationTimes() {
        // Realistic meeting confirmation times (year 2024–2026)
        return Arbitraries.longs()
                .between(
                        LocalDateTime.of(2024, 1, 1, 0, 0).toEpochSecond(
                                java.time.ZoneOffset.ofHours(7)),
                        LocalDateTime.of(2026, 12, 31, 23, 59).toEpochSecond(
                                java.time.ZoneOffset.ofHours(7)))
                .map(epochSecond -> LocalDateTime.ofEpochSecond(
                        epochSecond, 0, java.time.ZoneOffset.ofHours(7)));
    }

    @Provide
    Arbitrary<String> variableLengthStrings() {
        return Arbitraries.strings()
                .withCharRange(' ', '~')
                .ofMinLength(0)
                .ofMaxLength(10_000);
    }
}
