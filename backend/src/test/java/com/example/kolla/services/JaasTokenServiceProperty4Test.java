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
 * Property-based test for JaaS room name format consistency.
 *
 * Property 4: Room name format consistency
 *
 * For any meeting code and AppID, the {@code roomName} returned by the token endpoint
 * SHALL equal {@code {AppID}/{meetingCode}}, and the {@code room} claim in the JWT
 * SHALL equal {@code {meetingCode}} (without AppID prefix).
 *
 * Validates: Requirements 1.10, 1.18
 */
class JaasTokenServiceProperty4Test {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates a fresh {@link JaasTokenServiceImpl} instance for the given appId,
     * using a newly generated RSA key pair and fresh mock repositories.
     *
     * @param appId the JaaS application ID to configure
     * @return a configured service instance with fresh mocks
     */
    private ServiceWithMocks createService(String appId) {
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

            // Set up JaasProperties with the given appId
            JaasProperties jaasProperties = new JaasProperties();
            jaasProperties.setAppId(appId);
            jaasProperties.setApiKey(appId + "/testKeyId");
            jaasProperties.setPrivateKey(privateKeyPem);

            // Create fresh mocks for each invocation
            MeetingRepository meetingRepository = mock(MeetingRepository.class);
            MemberRepository memberRepository = mock(MemberRepository.class);

            JaasTokenServiceImpl service = new JaasTokenServiceImpl(
                    jaasProperties, meetingRepository, memberRepository);

            return new ServiceWithMocks(service, meetingRepository, memberRepository);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JaasTokenServiceImpl for appId=" + appId, e);
        }
    }

    /**
     * Property 4: Room name format consistency
     *
     * For any combination of meetingCode and appId:
     * <ul>
     *   <li>{@code response.getRoomName()} SHALL equal {@code appId + "/" + meetingCode}</li>
     *   <li>The {@code room} claim in the JWT SHALL equal {@code meetingCode} (NOT {@code appId + "/" + meetingCode})</li>
     * </ul>
     *
     * Validates: Requirements 1.10, 1.18
     */
    @Property(tries = 100)
    @Label("P4: roomName == appId/meetingCode and JWT room claim == meetingCode (no prefix)")
    void roomNameFormatConsistency(
            @ForAll("meetingCodes") String meetingCode,
            @ForAll("appIds") String appId) throws Exception {

        // Arrange: create a fresh service instance with the generated appId
        ServiceWithMocks ctx = createService(appId);

        // Build a meeting stub with the generated meetingCode
        Meeting meeting = new Meeting();
        meeting.setId(1L);
        meeting.setCode(meetingCode);
        meeting.setHost(null);
        meeting.setSecretary(null);

        // Build a minimal user stub
        User user = User.builder()
                .id(1L)
                .username("testuser")
                .passwordHash("$2a$12$hashed")
                .fullName("Test User")
                .email("test@example.com")
                .role(Role.USER)
                .isActive(true)
                .build();

        // Set up mocks
        when(ctx.meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(ctx.memberRepository.existsByMeetingIdAndUserId(anyLong(), anyLong())).thenReturn(true);

        // Act: generate the token
        JaasTokenResponse response = ctx.service.generateToken(1L, user);

        // Assert 1: roomName in response == appId + "/" + meetingCode (Requirement 1.18)
        assertThat(response.getRoomName())
                .as("roomName must equal appId + '/' + meetingCode")
                .isEqualTo(appId + "/" + meetingCode);

        // Assert 2: JWT 'room' claim == meetingCode (without AppID prefix) (Requirement 1.10)
        String token = response.getToken();
        String[] parts = token.split("\\.");
        assertThat(parts).as("JWT must have 3 parts (header.payload.signature)").hasSize(3);

        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payload = objectMapper.readTree(payloadJson);

        assertThat(payload.get("room").asText())
                .as("JWT room claim must equal meetingCode (NOT appId/meetingCode)")
                .isEqualTo(meetingCode);

        // Extra guard: room claim must NOT contain the appId prefix
        assertThat(payload.get("room").asText())
                .as("JWT room claim must NOT contain the appId prefix")
                .doesNotContain(appId + "/");
    }

    // ── Helper record ─────────────────────────────────────────────────────────

    /**
     * Holds a service instance together with its mock repositories,
     * so the property method can configure mock behaviour after creation.
     */
    private record ServiceWithMocks(
            JaasTokenServiceImpl service,
            MeetingRepository meetingRepository,
            MemberRepository memberRepository) {
    }

    // ── Arbitraries ───────────────────────────────────────────────────────────

    /**
     * Generates alphanumeric meeting codes of length 20 (uppercase A-Z).
     */
    @Provide
    Arbitrary<String> meetingCodes() {
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .ofLength(20);
    }

    /**
     * Generates JaaS AppIDs in the format {@code "vpaas-magic-cookie-" + alphanumeric(8)}.
     */
    @Provide
    Arbitrary<String> appIds() {
        return Arbitraries.strings()
                .alpha()
                .ofLength(8)
                .map(s -> "vpaas-magic-cookie-" + s);
    }
}
