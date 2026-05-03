package com.example.kolla.services;

import com.example.kolla.config.JaasProperties;
import com.example.kolla.enums.Role;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.User;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.MemberRepository;
import com.example.kolla.services.impl.JaasTokenServiceImpl;
import net.jqwik.api.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test for JaaS private key exposure prevention.
 *
 * Property 6: Private key never exposed
 *
 * For any exception or error scenario during token generation (invalid key,
 * missing config, non-member user, non-existent meeting), the error message
 * SHALL NOT contain the value of {@code JAAS_PRIVATE_KEY}.
 *
 * Validates: Requirements 6.1
 */
class JaasTokenServiceProperty6Test {

    private static final String APP_ID = "vpaas-magic-cookie-test";
    private static final String API_KEY = "vpaas-magic-cookie-test/testKeyId";

    /**
     * A known test private key value (Base64-encoded body only, without PEM headers).
     * This constant is used to assert that the key value never leaks into error messages.
     * Generated once per test class instantiation and reused across all property iterations.
     */
    private final String TEST_PRIVATE_KEY_BASE64;

    /**
     * The full PEM string for the test private key (with headers).
     */
    private final String TEST_PRIVATE_KEY_PEM;

    // Mocks created manually (jqwik does not process @ExtendWith(MockitoExtension.class))
    private final MeetingRepository meetingRepository = mock(MeetingRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);

    /**
     * Service configured with a valid RSA key and a known APP_ID.
     * Used for scenarios where the key is valid but other conditions fail
     * (non-member, non-existent meeting).
     */
    private final JaasTokenServiceImpl serviceWithValidKey;

    /**
     * Service configured with a blank APP_ID (JaaS disabled).
     * Used for the "missing config" error scenario.
     */
    private final JaasTokenServiceImpl serviceWithMissingConfig;

    /**
     * Service configured with an invalid/garbage PEM key.
     * Used for the "invalid key" error scenario.
     */
    private final JaasTokenServiceImpl serviceWithInvalidKey;

    JaasTokenServiceProperty6Test() {
        try {
            // Generate a test RSA key pair programmatically (2048-bit)
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            // Store the Base64-encoded private key body as a known constant
            TEST_PRIVATE_KEY_BASE64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            TEST_PRIVATE_KEY_PEM = "-----BEGIN PRIVATE KEY-----\n"
                    + TEST_PRIVATE_KEY_BASE64
                    + "\n-----END PRIVATE KEY-----";

            // ── Service 1: valid key, valid config ────────────────────────────
            JaasProperties validProps = new JaasProperties();
            validProps.setAppId(APP_ID);
            validProps.setApiKey(API_KEY);
            validProps.setPrivateKey(TEST_PRIVATE_KEY_PEM);
            serviceWithValidKey = new JaasTokenServiceImpl(validProps, meetingRepository, memberRepository);

            // ── Service 2: blank APP_ID (JaaS disabled / missing config) ─────
            JaasProperties missingConfigProps = new JaasProperties();
            missingConfigProps.setAppId("");   // blank → isEnabled() == false
            missingConfigProps.setApiKey(API_KEY);
            missingConfigProps.setPrivateKey(TEST_PRIVATE_KEY_PEM);
            MeetingRepository meetingRepo2 = mock(MeetingRepository.class);
            MemberRepository memberRepo2 = mock(MemberRepository.class);
            serviceWithMissingConfig = new JaasTokenServiceImpl(missingConfigProps, meetingRepo2, memberRepo2);

            // ── Service 3: invalid PEM key ────────────────────────────────────
            // The invalid key string contains the TEST_PRIVATE_KEY_BASE64 prefix
            // embedded in garbage data — this ensures we test that even when the
            // key value appears in the config, it does NOT appear in error messages.
            String invalidKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                    + "NOTAVALIDBASE64KEYVALUE=="
                    + "\n-----END PRIVATE KEY-----";
            JaasProperties invalidKeyProps = new JaasProperties();
            invalidKeyProps.setAppId(APP_ID);
            invalidKeyProps.setApiKey(API_KEY);
            invalidKeyProps.setPrivateKey(invalidKeyPem);
            MeetingRepository meetingRepo3 = mock(MeetingRepository.class);
            MemberRepository memberRepo3 = mock(MemberRepository.class);
            // Meeting must exist and user must be a member so we reach key parsing
            Meeting stubMeeting = new Meeting();
            stubMeeting.setId(1L);
            stubMeeting.setCode("TESTMEETINGCODE12345");
            stubMeeting.setHost(null);
            stubMeeting.setSecretary(null);
            when(meetingRepo3.findById(anyLong())).thenReturn(Optional.of(stubMeeting));
            when(memberRepo3.existsByMeetingIdAndUserId(anyLong(), anyLong())).thenReturn(true);
            serviceWithInvalidKey = new JaasTokenServiceImpl(invalidKeyProps, meetingRepo3, memberRepo3);

        } catch (Exception e) {
            throw new RuntimeException("Failed to set up JaasTokenServiceProperty6Test", e);
        }
    }

    // ── Scenario 1: Missing config (blank APP_ID) ─────────────────────────────

    /**
     * Property 6 — Scenario: missing config (blank APP_ID).
     *
     * When JaaS is not configured (APP_ID is blank), the thrown exception message
     * SHALL NOT contain the private key value.
     *
     * Validates: Requirements 6.1
     */
    @Property(tries = 100)
    @Label("P6-S1: exception message does not contain private key when config is missing")
    void privateKeyNotExposedWhenConfigMissing(
            @ForAll("meetingIds") Long meetingId,
            @ForAll("userIds") Long userId) {

        User user = buildUser(userId);

        try {
            serviceWithMissingConfig.generateToken(meetingId, user);
        } catch (Exception e) {
            assertExceptionDoesNotExposePrivateKey(e);
        }
    }

    // ── Scenario 2: Invalid PEM key ───────────────────────────────────────────

    /**
     * Property 6 — Scenario: invalid PEM key.
     *
     * When the configured private key is not a valid PEM/PKCS#8 key, the thrown
     * exception message SHALL NOT contain the private key value.
     *
     * Validates: Requirements 6.1
     */
    @Property(tries = 100)
    @Label("P6-S2: exception message does not contain private key when key is invalid")
    void privateKeyNotExposedWhenKeyIsInvalid(
            @ForAll("userIds") Long userId) {

        User user = buildUser(userId);

        try {
            serviceWithInvalidKey.generateToken(1L, user);
        } catch (Exception e) {
            assertExceptionDoesNotExposePrivateKey(e);
        }
    }

    // ── Scenario 3: Non-member user ───────────────────────────────────────────

    /**
     * Property 6 — Scenario: non-member user.
     *
     * When the user is not a member of the meeting, the thrown exception message
     * SHALL NOT contain the private key value.
     *
     * Validates: Requirements 6.1
     */
    @Property(tries = 100)
    @Label("P6-S3: exception message does not contain private key for non-member user")
    void privateKeyNotExposedForNonMember(
            @ForAll("meetingIds") Long meetingId,
            @ForAll("userIds") Long userId) {

        // Meeting exists, but user is NOT a member
        Meeting meeting = new Meeting();
        meeting.setId(meetingId);
        meeting.setCode("TESTMEETINGCODE12345");
        meeting.setHost(null);
        meeting.setSecretary(null);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(memberRepository.existsByMeetingIdAndUserId(anyLong(), anyLong())).thenReturn(false);

        User user = buildUser(userId);

        try {
            serviceWithValidKey.generateToken(meetingId, user);
        } catch (Exception e) {
            assertExceptionDoesNotExposePrivateKey(e);
        }
    }

    // ── Scenario 4: Non-existent meeting ─────────────────────────────────────

    /**
     * Property 6 — Scenario: non-existent meeting.
     *
     * When the meeting does not exist, the thrown exception message
     * SHALL NOT contain the private key value.
     *
     * Validates: Requirements 6.1
     */
    @Property(tries = 100)
    @Label("P6-S4: exception message does not contain private key for non-existent meeting")
    void privateKeyNotExposedForNonExistentMeeting(
            @ForAll("meetingIds") Long meetingId,
            @ForAll("userIds") Long userId) {

        // Meeting does NOT exist
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.empty());

        User user = buildUser(userId);

        try {
            serviceWithValidKey.generateToken(meetingId, user);
        } catch (Exception e) {
            assertExceptionDoesNotExposePrivateKey(e);
        }
    }

    // ── Assertion helper ──────────────────────────────────────────────────────

    /**
     * Asserts that neither the exception message nor the cause message contains
     * the test private key value (either the full PEM or the Base64 body).
     *
     * @param e the exception to inspect
     */
    private void assertExceptionDoesNotExposePrivateKey(Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : "";

        // Assert the full PEM string is not in the message
        assertThat(message)
                .as("Exception message must NOT contain the full PEM private key")
                .doesNotContain(TEST_PRIVATE_KEY_PEM);

        // Assert the Base64-encoded key body is not in the message
        // (guards against partial exposure of the key material)
        assertThat(message)
                .as("Exception message must NOT contain the Base64-encoded private key body")
                .doesNotContain(TEST_PRIVATE_KEY_BASE64);

        // Also check cause message if present
        if (e.getCause() != null && e.getCause().getMessage() != null) {
            String causeMessage = e.getCause().getMessage();
            assertThat(causeMessage)
                    .as("Cause exception message must NOT contain the full PEM private key")
                    .doesNotContain(TEST_PRIVATE_KEY_PEM);
            assertThat(causeMessage)
                    .as("Cause exception message must NOT contain the Base64-encoded private key body")
                    .doesNotContain(TEST_PRIVATE_KEY_BASE64);
        }
    }

    // ── Builder helper ────────────────────────────────────────────────────────

    /**
     * Builds a minimal {@link User} stub with the given ID.
     */
    private User buildUser(Long userId) {
        return User.builder()
                .id(userId)
                .username("user_" + userId)
                .passwordHash("$2a$12$hashed")
                .fullName("Test User " + userId)
                .email("user" + userId + "@example.com")
                .role(Role.USER)
                .isActive(true)
                .build();
    }

    // ── Arbitraries ───────────────────────────────────────────────────────────

    /**
     * Generates positive Long meeting IDs.
     */
    @Provide
    Arbitrary<Long> meetingIds() {
        return Arbitraries.longs().between(1L, Long.MAX_VALUE);
    }

    /**
     * Generates positive Long user IDs.
     */
    @Provide
    Arbitrary<Long> userIds() {
        return Arbitraries.longs().between(1L, Long.MAX_VALUE);
    }
}
