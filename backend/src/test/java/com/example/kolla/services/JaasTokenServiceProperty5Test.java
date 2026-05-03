package com.example.kolla.services;

import com.example.kolla.config.JaasProperties;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.ForbiddenException;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test for JaaS access control — non-members are rejected.
 *
 * Property 5: Access control — non-members are rejected
 *
 * For any user who is not a member of a meeting, a call to {@code generateToken()}
 * for that meeting SHALL throw {@link ForbiddenException} (resulting in HTTP 403).
 *
 * Validates: Requirements 1.2, 1.3, 6.4
 */
class JaasTokenServiceProperty5Test {

    private static final String APP_ID = "vpaas-magic-cookie-test";
    private static final String API_KEY = "vpaas-magic-cookie-test/testKeyId";

    // Mocks created manually (jqwik does not process @ExtendWith(MockitoExtension.class))
    private final MeetingRepository meetingRepository = mock(MeetingRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);

    private final JaasTokenServiceImpl jaasTokenService;

    JaasTokenServiceProperty5Test() {
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
            throw new RuntimeException("Failed to set up JaasTokenServiceProperty5Test", e);
        }
    }

    /**
     * Property 5: Access control — non-members are rejected
     *
     * For any user who is not a member of a meeting, calling {@code generateToken()}
     * SHALL throw {@link ForbiddenException}.
     *
     * Validates: Requirements 1.2, 1.3, 6.4
     */
    @Property(tries = 100)
    @Label("P5: generateToken throws ForbiddenException for any non-member user")
    void nonMemberIsRejectedWithForbiddenException(
            @ForAll("meetingIds") Long meetingId,
            @ForAll("userIds") Long userId,
            @ForAll("userNames") String userName,
            @ForAll("userEmails") String userEmail) {

        // Arrange: build a valid meeting stub
        Meeting meeting = new Meeting();
        meeting.setId(meetingId);
        meeting.setCode("TESTMEETINGCODE12345");
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

        // Set up mocks: meeting exists, but user is NOT a member
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(memberRepository.existsByMeetingIdAndUserId(anyLong(), anyLong())).thenReturn(false);

        // Act & Assert: ForbiddenException must be thrown
        assertThatThrownBy(() -> jaasTokenService.generateToken(meetingId, user))
                .isInstanceOf(ForbiddenException.class);
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
