package com.example.kolla.services;

import com.example.kolla.config.JaasProperties;
import com.example.kolla.enums.Role;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.User;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.MemberRepository;
import com.example.kolla.responses.JaasTokenResponse;
import com.example.kolla.services.impl.JaasTokenServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Property-based test for JaaS JWT expiry window.
 *
 * Property 2: JWT expiry window
 *
 * For any generated JaaS JWT token, the {@code exp} claim SHALL be exactly 3600 seconds
 * after the {@code iat} claim, and the {@code nbf} claim SHALL be exactly 10 seconds
 * before the {@code iat} claim.
 *
 * Validates: Requirements 1.11, 1.12
 */
class JaasTokenServiceProperty2Test {

    private static final String APP_ID = "vpaas-magic-cookie-test";
    private static final String API_KEY = "vpaas-magic-cookie-test/testKeyId";

    // Mocks created manually (jqwik does not process @ExtendWith(MockitoExtension.class))
    private final MeetingRepository meetingRepository = mock(MeetingRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);

    private final JaasTokenServiceImpl jaasTokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    JaasTokenServiceProperty2Test() {
        try {
            // Generate a test RSA key pair programmatically (2048-bit)
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            // Convert private key to PEM format
            String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            String privateKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                    + privateKeyBase64
                    + "\n-----END PRIVATE KEY-----";

            // Set up JaasProperties with test values
            JaasProperties jaasProperties = new JaasProperties();
            jaasProperties.setAppId(APP_ID);
            jaasProperties.setApiKey(API_KEY);
            jaasProperties.setPrivateKey(privateKeyPem);

            // Instantiate the service with mocked repositories
            jaasTokenService = new JaasTokenServiceImpl(jaasProperties, meetingRepository, memberRepository);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up JaasTokenServiceProperty2Test", e);
        }
    }

    /**
     * Property 2: JWT expiry window
     *
     * For any combination of meetingCode and user data, the generated JWT SHALL satisfy:
     * <ul>
     *   <li>{@code exp == iat + 3600} (exactly 1 hour after issuance)</li>
     *   <li>{@code nbf == iat - 10} (exactly 10 seconds before issuance, to tolerate clock skew)</li>
     *   <li>{@code iat} is within the time window {@code [beforeCall, afterCall]} (real current timestamp)</li>
     * </ul>
     *
     * Validates: Requirements 1.11, 1.12
     */
    @Property(tries = 100)
    @Label("P2: exp == iat + 3600 and nbf == iat - 10 for any generated JaaS JWT")
    void jwtExpiryWindowIsCorrect(
            @ForAll("meetingCodes") String meetingCode,
            @ForAll("userIds") Long userId,
            @ForAll("userNames") String userName,
            @ForAll("userEmails") String userEmail) throws Exception {

        // Arrange: build a meeting stub with the generated code
        Meeting meeting = new Meeting();
        meeting.setId(1L);
        meeting.setCode(meetingCode);
        meeting.setHost(null);
        meeting.setSecretary(null);

        // Build a user stub with the generated values
        User user = User.builder()
                .id(userId)
                .username("user_" + userId)
                .passwordHash("$2a$12$hashed")
                .fullName(userName)
                .email(userEmail)
                .role(Role.USER)
                .isActive(true)
                .build();

        // Set up mocks
        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(memberRepository.existsByMeetingIdAndUserId(anyLong(), anyLong())).thenReturn(true);

        // Record time window around the call (in seconds, Unix epoch)
        long beforeCall = System.currentTimeMillis() / 1000L;

        // Act: generate the token
        JaasTokenResponse response = jaasTokenService.generateToken(1L, user);

        long afterCall = System.currentTimeMillis() / 1000L;

        String token = response.getToken();

        // Decode JWT body (split by '.', Base64-URL-decode the middle part, parse as JSON)
        String[] parts = token.split("\\.");
        assertThat(parts).as("JWT must have 3 parts (header.payload.signature)").hasSize(3);

        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payload = objectMapper.readTree(payloadJson);

        long iat = payload.get("iat").asLong();
        long nbf = payload.get("nbf").asLong();
        long exp = payload.get("exp").asLong();

        // Assert: iat is a real current timestamp within the measured window
        assertThat(iat)
                .as("iat must be within the time window [beforeCall=%d, afterCall=%d]", beforeCall, afterCall)
                .isGreaterThanOrEqualTo(beforeCall)
                .isLessThanOrEqualTo(afterCall);

        // Assert: exp == iat + 3600 (exactly 1 hour after issuance) — Requirement 1.11
        assertThat(exp)
                .as("exp must be exactly iat + 3600 (iat=%d, exp=%d)", iat, exp)
                .isEqualTo(iat + 3600L);

        // Assert: nbf == iat - 10 (exactly 10 seconds before issuance) — Requirement 1.12
        assertThat(nbf)
                .as("nbf must be exactly iat - 10 (iat=%d, nbf=%d)", iat, nbf)
                .isEqualTo(iat - 10L);
    }

    // ── Arbitraries ───────────────────────────────────────────────────────────

    /**
     * Generates alphanumeric meeting codes of length 20 (matching the real format).
     */
    @Provide
    Arbitrary<String> meetingCodes() {
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .ofLength(20);
    }

    /**
     * Generates positive Long user IDs.
     */
    @Provide
    Arbitrary<Long> userIds() {
        return Arbitraries.longs().between(1L, Long.MAX_VALUE);
    }

    /**
     * Generates non-blank user names (1–100 characters, printable ASCII excluding control chars).
     */
    @Provide
    Arbitrary<String> userNames() {
        return Arbitraries.strings()
                .withCharRange(' ', '~')
                .ofMinLength(1)
                .ofMaxLength(100)
                .filter(s -> !s.isBlank());
    }

    /**
     * Generates email-like strings in the format localPart@domain.tld.
     */
    @Provide
    Arbitrary<String> userEmails() {
        Arbitrary<String> localPart = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20);
        Arbitrary<String> domain = Arbitraries.strings()
                .alpha()
                .ofMinLength(2)
                .ofMaxLength(15);
        Arbitrary<String> tld = Arbitraries.strings()
                .alpha()
                .ofMinLength(2)
                .ofMaxLength(5);
        return Combinators.combine(localPart, domain, tld)
                .as((l, d, t) -> l + "@" + d + "." + t);
    }
}
