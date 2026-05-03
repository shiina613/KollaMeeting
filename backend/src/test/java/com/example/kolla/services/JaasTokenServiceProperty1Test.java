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
 * Property-based test for JaaS JWT claims integrity.
 *
 * Property 1: JWT claims integrity
 *
 * For any authenticated meeting member, the generated JaaS JWT token SHALL contain
 * the correct `iss` ("chat"), `aud` ("jitsi"), `sub` ({AppID}), `room` ({meetingCode}
 * without AppID prefix), `context.user.id` (user ID as string), `context.user.name`,
 * `context.user.email`, and `context.user.moderator` values matching the input user
 * and meeting data.
 *
 * Validates: Requirements 1.7, 1.8, 1.9, 1.10, 1.13, 1.15, 1.16
 */
class JaasTokenServiceProperty1Test {

    private static final String APP_ID = "vpaas-magic-cookie-test";
    private static final String API_KEY = "vpaas-magic-cookie-test/testKeyId";

    // Mocks created manually (jqwik does not process @ExtendWith(MockitoExtension.class))
    private final MeetingRepository meetingRepository = mock(MeetingRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);

    private final JaasTokenServiceImpl jaasTokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    JaasTokenServiceProperty1Test() {
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
            throw new RuntimeException("Failed to set up JaasTokenServiceProperty1Test", e);
        }
    }

    /**
     * Property 1: JWT claims integrity
     *
     * For any combination of meetingCode, userId, userName, and userEmail,
     * the generated JWT token SHALL contain the correct claim values.
     *
     * Validates: Requirements 1.7, 1.8, 1.9, 1.10, 1.13, 1.15, 1.16
     */
    @Property(tries = 100)
    @Label("P1: JWT claims match input user and meeting data for any valid member")
    void jwtClaimsMatchInput(
            @ForAll("meetingCodes") String meetingCode,
            @ForAll("userIds") Long userId,
            @ForAll("userNames") String userName,
            @ForAll("userEmails") String userEmail) throws Exception {

        // Arrange: build a meeting stub with the generated code
        Meeting meeting = new Meeting();
        meeting.setId(1L);
        meeting.setCode(meetingCode);
        // No host or secretary — user is a regular member (moderator: false)
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

        // Act: generate the token
        JaasTokenResponse response = jaasTokenService.generateToken(1L, user);
        String token = response.getToken();

        // Decode JWT body (split by '.', Base64-decode the middle part, parse as JSON)
        String[] parts = token.split("\\.");
        assertThat(parts).as("JWT must have 3 parts (header.payload.signature)").hasSize(3);

        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Assert standard claims
        assertThat(payload.get("iss").asText())
                .as("iss claim must be 'chat'")
                .isEqualTo("chat");

        assertThat(payload.get("aud").asText())
                .as("aud claim must be 'jitsi'")
                .isEqualTo("jitsi");

        assertThat(payload.get("sub").asText())
                .as("sub claim must equal the AppID")
                .isEqualTo(APP_ID);

        assertThat(payload.get("room").asText())
                .as("room claim must equal the meetingCode (without AppID prefix)")
                .isEqualTo(meetingCode);

        // Assert context.user claims
        JsonNode contextUser = payload.get("context").get("user");

        assertThat(contextUser.get("id").asText())
                .as("context.user.id must be the userId as a string")
                .isEqualTo(userId.toString());

        assertThat(contextUser.get("name").asText())
                .as("context.user.name must match the user's full name")
                .isEqualTo(userName);

        assertThat(contextUser.get("email").asText())
                .as("context.user.email must match the user's email")
                .isEqualTo(userEmail);

        // Regular member (no host/secretary) → moderator must be false
        assertThat(contextUser.get("moderator").asBoolean())
                .as("context.user.moderator must be false for a regular member")
                .isFalse();
    }

    /**
     * Property 1 (moderator variant): When the user IS the host,
     * context.user.moderator SHALL be true.
     *
     * Validates: Requirements 1.15, 1.16
     */
    @Property(tries = 100)
    @Label("P1b: JWT moderator=true when user is the host")
    void jwtModeratorTrueForHost(
            @ForAll("meetingCodes") String meetingCode,
            @ForAll("userIds") Long userId,
            @ForAll("userNames") String userName,
            @ForAll("userEmails") String userEmail) throws Exception {

        // Build user
        User user = User.builder()
                .id(userId)
                .username("host_" + userId)
                .passwordHash("$2a$12$hashed")
                .fullName(userName)
                .email(userEmail)
                .role(Role.USER)
                .isActive(true)
                .build();

        // Build meeting where this user is the host
        Meeting meeting = new Meeting();
        meeting.setId(1L);
        meeting.setCode(meetingCode);
        meeting.setHost(user);
        meeting.setSecretary(null);

        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(memberRepository.existsByMeetingIdAndUserId(anyLong(), anyLong())).thenReturn(true);

        JaasTokenResponse response = jaasTokenService.generateToken(1L, user);
        String token = response.getToken();

        String[] parts = token.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payload = objectMapper.readTree(payloadJson);

        JsonNode contextUser = payload.get("context").get("user");

        assertThat(contextUser.get("moderator").asBoolean())
                .as("context.user.moderator must be true when user is the host")
                .isTrue();
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
