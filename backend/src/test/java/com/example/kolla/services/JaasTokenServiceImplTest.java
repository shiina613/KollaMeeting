package com.example.kolla.services;

import com.example.kolla.config.JaasProperties;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.exceptions.ServiceUnavailableException;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.User;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.MemberRepository;
import com.example.kolla.responses.JaasTokenResponse;
import com.example.kolla.services.impl.JaasTokenServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JaasTokenServiceImpl} (example-based).
 *
 * Covers:
 * - Successful token generation for a member
 * - moderator=true for host
 * - moderator=true for secretary
 * - moderator=false for regular member
 * - moderator=false when host is null
 * - 403 when user is not a member
 * - 404 when meeting does not exist
 * - 503 when JAAS_APP_ID is blank
 *
 * Requirements: 1.2, 1.3, 1.15, 1.16, 1.19
 */
@ExtendWith(MockitoExtension.class)
class JaasTokenServiceImplTest {

    // ── Static RSA key pair (generated once for all tests) ────────────────────

    private static String TEST_PRIVATE_KEY_PEM;
    private static final String APP_ID = "vpaas-magic-cookie-testapp";
    private static final String API_KEY = "vpaas-magic-cookie-testapp/testKeyId123";

    @BeforeAll
    static void generateRsaKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        String base64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        TEST_PRIVATE_KEY_PEM = "-----BEGIN PRIVATE KEY-----\n"
                + base64
                + "\n-----END PRIVATE KEY-----";
    }

    // ── Mocks ─────────────────────────────────────────────────────────────────

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MemberRepository memberRepository;

    // ── Service under test ────────────────────────────────────────────────────

    private JaasTokenServiceImpl jaasTokenService;
    private JaasProperties jaasProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jaasProperties = new JaasProperties();
        jaasProperties.setAppId(APP_ID);
        jaasProperties.setApiKey(API_KEY);
        jaasProperties.setPrivateKey(TEST_PRIVATE_KEY_PEM);

        jaasTokenService = new JaasTokenServiceImpl(jaasProperties, meetingRepository, memberRepository);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User buildUser(Long id, String username, Role role) {
        return User.builder()
                .id(id)
                .username(username)
                .passwordHash("$2a$12$hashed")
                .fullName(username + " Full Name")
                .email(username + "@example.com")
                .role(role)
                .isActive(true)
                .build();
    }

    private Meeting buildMeeting(Long id, String code, User host, User secretary) {
        Meeting meeting = new Meeting();
        meeting.setId(id);
        meeting.setCode(code);
        meeting.setHost(host);
        meeting.setSecretary(secretary);
        return meeting;
    }

    /**
     * Decodes the JWT payload (middle part) and returns it as a JsonNode.
     */
    private JsonNode decodeJwtPayload(String token) throws Exception {
        String[] parts = token.split("\\.");
        assertThat(parts).as("JWT must have 3 parts").hasSize(3);
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        return objectMapper.readTree(payloadJson);
    }

    // ── generateToken — success scenarios ────────────────────────────────────

    @Nested
    @DisplayName("generateToken() — successful token generation")
    class SuccessfulGeneration {

        @Test
        @DisplayName("Returns token and roomName for a valid member")
        void generateToken_successForMember() throws Exception {
            // Arrange
            User member = buildUser(10L, "member1", Role.USER);
            Meeting meeting = buildMeeting(1L, "ABCDEF1234567890ABCD", null, null);

            when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
            when(memberRepository.existsByMeetingIdAndUserId(1L, 10L)).thenReturn(true);

            // Act
            JaasTokenResponse response = jaasTokenService.generateToken(1L, member);

            // Assert response fields
            assertThat(response.getToken()).isNotBlank();
            assertThat(response.getRoomName()).isEqualTo(APP_ID + "/ABCDEF1234567890ABCD");

            // Assert JWT payload
            JsonNode payload = decodeJwtPayload(response.getToken());
            assertThat(payload.get("iss").asText()).isEqualTo("chat");
            assertThat(payload.get("aud").asText()).isEqualTo("jitsi");
            assertThat(payload.get("sub").asText()).isEqualTo(APP_ID);
            assertThat(payload.get("room").asText()).isEqualTo("ABCDEF1234567890ABCD");
            assertThat(payload.get("context").get("user").get("id").asText()).isEqualTo("10");
            assertThat(payload.get("context").get("user").get("email").asText()).isEqualTo("member1@example.com");
        }

        @Test
        @DisplayName("Sets moderator=true when user is the host")
        void generateToken_moderatorTrueForHost() throws Exception {
            // Arrange
            User host = buildUser(20L, "host_user", Role.USER);
            Meeting meeting = buildMeeting(2L, "HOSTMEETING12345ABCD", host, null);

            when(meetingRepository.findById(2L)).thenReturn(Optional.of(meeting));
            when(memberRepository.existsByMeetingIdAndUserId(2L, 20L)).thenReturn(true);

            // Act
            JaasTokenResponse response = jaasTokenService.generateToken(2L, host);

            // Assert
            JsonNode contextUser = decodeJwtPayload(response.getToken()).get("context").get("user");
            assertThat(contextUser.get("moderator").asBoolean())
                    .as("Host must have moderator=true")
                    .isTrue();
        }

        @Test
        @DisplayName("Sets moderator=true when user is the secretary")
        void generateToken_moderatorTrueForSecretary() throws Exception {
            // Arrange
            User secretary = buildUser(30L, "secretary_user", Role.USER);
            Meeting meeting = buildMeeting(3L, "SECRETARYMEETING1234", null, secretary);

            when(meetingRepository.findById(3L)).thenReturn(Optional.of(meeting));
            when(memberRepository.existsByMeetingIdAndUserId(3L, 30L)).thenReturn(true);

            // Act
            JaasTokenResponse response = jaasTokenService.generateToken(3L, secretary);

            // Assert
            JsonNode contextUser = decodeJwtPayload(response.getToken()).get("context").get("user");
            assertThat(contextUser.get("moderator").asBoolean())
                    .as("Secretary must have moderator=true")
                    .isTrue();
        }

        @Test
        @DisplayName("Sets moderator=false for a regular member (not host or secretary)")
        void generateToken_moderatorFalseForRegularMember() throws Exception {
            // Arrange
            User host = buildUser(20L, "host_user", Role.USER);
            User secretary = buildUser(30L, "secretary_user", Role.USER);
            User regularMember = buildUser(40L, "regular_member", Role.USER);
            Meeting meeting = buildMeeting(4L, "REGULARMEMBER1234567", host, secretary);

            when(meetingRepository.findById(4L)).thenReturn(Optional.of(meeting));
            when(memberRepository.existsByMeetingIdAndUserId(4L, 40L)).thenReturn(true);

            // Act
            JaasTokenResponse response = jaasTokenService.generateToken(4L, regularMember);

            // Assert
            JsonNode contextUser = decodeJwtPayload(response.getToken()).get("context").get("user");
            assertThat(contextUser.get("moderator").asBoolean())
                    .as("Regular member must have moderator=false")
                    .isFalse();
        }

        @Test
        @DisplayName("Sets moderator=false when host is null (meeting has no assigned host)")
        void generateToken_moderatorFalseWhenHostIsNull() throws Exception {
            // Arrange — meeting has no host and no secretary
            User member = buildUser(50L, "member_no_host", Role.USER);
            Meeting meeting = buildMeeting(5L, "NOHOSTMEETING1234567", null, null);

            when(meetingRepository.findById(5L)).thenReturn(Optional.of(meeting));
            when(memberRepository.existsByMeetingIdAndUserId(5L, 50L)).thenReturn(true);

            // Act
            JaasTokenResponse response = jaasTokenService.generateToken(5L, member);

            // Assert
            JsonNode contextUser = decodeJwtPayload(response.getToken()).get("context").get("user");
            assertThat(contextUser.get("moderator").asBoolean())
                    .as("Member must have moderator=false when meeting has no host")
                    .isFalse();
        }
    }

    // ── generateToken — error scenarios ──────────────────────────────────────

    @Nested
    @DisplayName("generateToken() — error scenarios")
    class ErrorScenarios {

        @Test
        @DisplayName("Throws ForbiddenException (403) when user is not a member of the meeting")
        void generateToken_throwsForbiddenWhenNotMember() {
            // Arrange
            User nonMember = buildUser(99L, "non_member", Role.USER);
            Meeting meeting = buildMeeting(1L, "ABCDEF1234567890ABCD", null, null);

            when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
            when(memberRepository.existsByMeetingIdAndUserId(1L, 99L)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> jaasTokenService.generateToken(1L, nonMember))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("not a member");
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException (404) when meeting does not exist")
        void generateToken_throwsNotFoundWhenMeetingMissing() {
            // Arrange
            User user = buildUser(10L, "some_user", Role.USER);

            when(meetingRepository.findById(999L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> jaasTokenService.generateToken(999L, user))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("Throws ServiceUnavailableException (503) when JAAS_APP_ID is blank")
        void generateToken_throwsServiceUnavailableWhenAppIdBlank() {
            // Arrange — override jaasProperties with blank appId
            JaasProperties disabledProperties = new JaasProperties();
            disabledProperties.setAppId("");
            disabledProperties.setApiKey(API_KEY);
            disabledProperties.setPrivateKey(TEST_PRIVATE_KEY_PEM);

            JaasTokenServiceImpl disabledService = new JaasTokenServiceImpl(
                    disabledProperties, meetingRepository, memberRepository);

            User user = buildUser(10L, "some_user", Role.USER);

            // Act & Assert
            assertThatThrownBy(() -> disabledService.generateToken(1L, user))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessageContaining("not configured");
        }

        @Test
        @DisplayName("Throws ServiceUnavailableException (503) when JAAS_APP_ID is null")
        void generateToken_throwsServiceUnavailableWhenAppIdNull() {
            // Arrange — override jaasProperties with null appId
            JaasProperties disabledProperties = new JaasProperties();
            disabledProperties.setAppId(null);
            disabledProperties.setApiKey(API_KEY);
            disabledProperties.setPrivateKey(TEST_PRIVATE_KEY_PEM);

            JaasTokenServiceImpl disabledService = new JaasTokenServiceImpl(
                    disabledProperties, meetingRepository, memberRepository);

            User user = buildUser(10L, "some_user", Role.USER);

            // Act & Assert
            assertThatThrownBy(() -> disabledService.generateToken(1L, user))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessageContaining("not configured");
        }
    }

    // ── generateToken — JWT structure ─────────────────────────────────────────

    @Nested
    @DisplayName("generateToken() — JWT structure")
    class JwtStructure {

        @Test
        @DisplayName("JWT header contains alg=RS256 and correct kid format")
        void generateToken_jwtHeaderHasCorrectAlgAndKid() throws Exception {
            // Arrange
            User member = buildUser(10L, "member1", Role.USER);
            Meeting meeting = buildMeeting(1L, "ABCDEF1234567890ABCD", null, null);

            when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
            when(memberRepository.existsByMeetingIdAndUserId(anyLong(), anyLong())).thenReturn(true);

            // Act
            JaasTokenResponse response = jaasTokenService.generateToken(1L, member);
            String token = response.getToken();

            // Decode header (first part)
            String[] parts = token.split("\\.");
            assertThat(parts).hasSize(3);
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode header = objectMapper.readTree(headerJson);

            // Assert
            assertThat(header.get("alg").asText()).isEqualTo("RS256");
            assertThat(header.get("kid").asText())
                    .startsWith("vpaas-magic-cookie-" + APP_ID + "/");
        }

        @Test
        @DisplayName("JWT payload contains context.features with all required flags set to false")
        void generateToken_jwtPayloadHasCorrectFeatures() throws Exception {
            // Arrange
            User member = buildUser(10L, "member1", Role.USER);
            Meeting meeting = buildMeeting(1L, "ABCDEF1234567890ABCD", null, null);

            when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
            when(memberRepository.existsByMeetingIdAndUserId(anyLong(), anyLong())).thenReturn(true);

            // Act
            JaasTokenResponse response = jaasTokenService.generateToken(1L, member);
            JsonNode payload = decodeJwtPayload(response.getToken());
            JsonNode features = payload.get("context").get("features");

            // Assert
            assertThat(features.get("livestreaming").asBoolean()).isFalse();
            assertThat(features.get("outbound-call").asBoolean()).isFalse();
            assertThat(features.get("sip-outbound-call").asBoolean()).isFalse();
            assertThat(features.get("transcription").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("roomName in response equals appId/meetingCode")
        void generateToken_roomNameFormatIsCorrect() {
            // Arrange
            User member = buildUser(10L, "member1", Role.USER);
            Meeting meeting = buildMeeting(1L, "TESTCODE1234567890AB", null, null);

            when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
            when(memberRepository.existsByMeetingIdAndUserId(anyLong(), anyLong())).thenReturn(true);

            // Act
            JaasTokenResponse response = jaasTokenService.generateToken(1L, member);

            // Assert
            assertThat(response.getRoomName()).isEqualTo(APP_ID + "/TESTCODE1234567890AB");
        }
    }
}
