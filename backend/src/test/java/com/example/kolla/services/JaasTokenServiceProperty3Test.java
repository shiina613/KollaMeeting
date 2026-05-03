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
 * Property-based test for JaaS JWT moderator flag correctness.
 *
 * Property 3: Moderator flag correctness
 *
 * For any meeting with a host and/or secretary, generating a token for the host or
 * secretary SHALL produce {@code moderator: true}, and generating a token for any
 * other member (where host and secretary may be null) SHALL produce
 * {@code moderator: false}.
 *
 * Validates: Requirements 1.15, 1.16
 */
class JaasTokenServiceProperty3Test {

    private static final String APP_ID = "vpaas-magic-cookie-test";
    private static final String API_KEY = "vpaas-magic-cookie-test/testKeyId";

    // Mocks created manually (jqwik does not process @ExtendWith(MockitoExtension.class))
    private final MeetingRepository meetingRepository = mock(MeetingRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);

    private final JaasTokenServiceImpl jaasTokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    JaasTokenServiceProperty3Test() {
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
            throw new RuntimeException("Failed to set up JaasTokenServiceProperty3Test", e);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private User buildUser(Long id, String name, String email) {
        return User.builder()
                .id(id)
                .username("user_" + id)
                .passwordHash("$2a$12$hashed")
                .fullName(name)
                .email(email)
                .role(Role.USER)
                .isActive(true)
                .build();
    }

    private JsonNode decodePayload(String token) throws Exception {
        String[] parts = token.split("\\.");
        assertThat(parts).as("JWT must have 3 parts (header.payload.signature)").hasSize(3);
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        return objectMapper.readTree(payloadJson);
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /**
     * Property 3a: Host is moderator.
     *
     * For any user who is the host of a meeting, the generated JWT SHALL have
     * {@code context.user.moderator = true}.
     *
     * Validates: Requirement 1.15
     */
    @Property(tries = 100)
    @Label("P3a: moderator=true when user is the host")
    void hostIsModerator(
            @ForAll("meetingCodes") String meetingCode,
            @ForAll("userIds") Long userId,
            @ForAll("userNames") String userName,
            @ForAll("userEmails") String userEmail) throws Exception {

        User host = buildUser(userId, userName, userEmail);

        Meeting meeting = new Meeting();
        meeting.setId(1L);
        meeting.setCode(meetingCode);
        meeting.setHost(host);
        meeting.setSecretary(null);

        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(memberRepository.existsByMeetingIdAndUserId(anyLong(), anyLong())).thenReturn(true);

        JaasTokenResponse response = jaasTokenService.generateToken(1L, host);
        JsonNode contextUser = decodePayload(response.getToken()).get("context").get("user");

        assertThat(contextUser.get("moderator").asBoolean())
                .as("context.user.moderator must be true when user is the host")
                .isTrue();
    }

    /**
     * Property 3b: Secretary is moderator.
     *
     * For any user who is the secretary of a meeting, the generated JWT SHALL have
     * {@code context.user.moderator = true}.
     *
     * Validates: Requirement 1.15
     */
    @Property(tries = 100)
    @Label("P3b: moderator=true when user is the secretary")
    void secretaryIsModerator(
            @ForAll("meetingCodes") String meetingCode,
            @ForAll("userIds") Long userId,
            @ForAll("userNames") String userName,
            @ForAll("userEmails") String userEmail) throws Exception {

        User secretary = buildUser(userId, userName, userEmail);

        Meeting meeting = new Meeting();
        meeting.setId(1L);
        meeting.setCode(meetingCode);
        meeting.setHost(null);
        meeting.setSecretary(secretary);

        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(memberRepository.existsByMeetingIdAndUserId(anyLong(), anyLong())).thenReturn(true);

        JaasTokenResponse response = jaasTokenService.generateToken(1L, secretary);
        JsonNode contextUser = decodePayload(response.getToken()).get("context").get("user");

        assertThat(contextUser.get("moderator").asBoolean())
                .as("context.user.moderator must be true when user is the secretary")
                .isTrue();
    }

    /**
     * Property 3c: Regular member is not moderator.
     *
     * For any user who is neither the host nor the secretary of a meeting, the
     * generated JWT SHALL have {@code context.user.moderator = false}.
     * Uses two distinct user IDs to ensure the requesting user is not the host.
     *
     * Validates: Requirement 1.16
     */
    @Property(tries = 100)
    @Label("P3c: moderator=false when user is a regular member (not host or secretary)")
    void regularMemberIsNotModerator(
            @ForAll("meetingCodes") String meetingCode,
            @ForAll("distinctUserIdPairs") long[] idPair,
            @ForAll("userNames") String memberName,
            @ForAll("userEmails") String memberEmail,
            @ForAll("userNames") String hostName,
            @ForAll("userEmails") String hostEmail) throws Exception {

        long hostId = idPair[0];
        long memberId = idPair[1];

        User host = buildUser(hostId, hostName, hostEmail);
        User member = buildUser(memberId, memberName, memberEmail);

        Meeting meeting = new Meeting();
        meeting.setId(1L);
        meeting.setCode(meetingCode);
        meeting.setHost(host);
        meeting.setSecretary(null);

        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(memberRepository.existsByMeetingIdAndUserId(anyLong(), anyLong())).thenReturn(true);

        JaasTokenResponse response = jaasTokenService.generateToken(1L, member);
        JsonNode contextUser = decodePayload(response.getToken()).get("context").get("user");

        assertThat(contextUser.get("moderator").asBoolean())
                .as("context.user.moderator must be false for a regular member who is not host or secretary")
                .isFalse();
    }

    /**
     * Property 3d: Null host and null secretary → not moderator.
     *
     * When a meeting has no host and no secretary, any member's generated JWT SHALL
     * have {@code context.user.moderator = false}.
     *
     * Validates: Requirement 1.16
     */
    @Property(tries = 100)
    @Label("P3d: moderator=false when meeting has no host and no secretary")
    void nullHostAndSecretaryNotModerator(
            @ForAll("meetingCodes") String meetingCode,
            @ForAll("userIds") Long userId,
            @ForAll("userNames") String userName,
            @ForAll("userEmails") String userEmail) throws Exception {

        User member = buildUser(userId, userName, userEmail);

        Meeting meeting = new Meeting();
        meeting.setId(1L);
        meeting.setCode(meetingCode);
        meeting.setHost(null);
        meeting.setSecretary(null);

        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(memberRepository.existsByMeetingIdAndUserId(anyLong(), anyLong())).thenReturn(true);

        JaasTokenResponse response = jaasTokenService.generateToken(1L, member);
        JsonNode contextUser = decodePayload(response.getToken()).get("context").get("user");

        assertThat(contextUser.get("moderator").asBoolean())
                .as("context.user.moderator must be false when meeting has no host and no secretary")
                .isFalse();
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
     * Generates pairs of distinct positive long user IDs [hostId, memberId].
     * Both IDs are in [1, Long.MAX_VALUE] and are guaranteed to be different.
     */
    @Provide
    Arbitrary<long[]> distinctUserIdPairs() {
        return Arbitraries.longs().between(1L, Long.MAX_VALUE / 2)
                .flatMap(hostId ->
                        Arbitraries.longs().between(1L, Long.MAX_VALUE / 2)
                                .filter(memberId -> !memberId.equals(hostId))
                                .map(memberId -> new long[]{hostId, memberId})
                );
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
