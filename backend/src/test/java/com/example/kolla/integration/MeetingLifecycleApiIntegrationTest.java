package com.example.kolla.integration;

import com.example.kolla.dto.CreateMeetingRequest;
import com.example.kolla.dto.LoginRequest;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.models.Department;
import com.example.kolla.models.Room;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DepartmentRepository;
import com.example.kolla.repositories.RoomRepository;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.AuthResponse;
import com.example.kolla.responses.MeetingResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers integration tests for the Meeting Lifecycle HTTP API.
 *
 * Uses real MySQL and Redis containers to test the full HTTP stack via TestRestTemplate.
 * Covers:
 * - POST /api/v1/meetings → 201 Created, meeting code generated
 * - POST /api/v1/meetings/{id}/activate → 200, status=ACTIVE
 * - POST /api/v1/meetings/{id}/end → 200, status=ENDED
 * - Invalid transitions → 400 Bad Request
 * - Non-member join → 403 Forbidden
 *
 * Requirements: 20.2
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("tc-test")
@Testcontainers
class MeetingLifecycleApiIntegrationTest {

    // ── Containers ────────────────────────────────────────────────────────────

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("kolla_test")
            .withUsername("kolla")
            .withPassword("kolla_pass");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // ── Injected beans ────────────────────────────────────────────────────────

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    // ── Test state ────────────────────────────────────────────────────────────

    private User admin;
    private User secretary;
    private User outsider;
    private Room room;
    private String adminToken;
    private String secretaryToken;
    private String outsiderToken;

    private static final String RAW_PASSWORD = "Test@1234";

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        long ts = System.nanoTime();
        String encodedPassword = passwordEncoder.encode(RAW_PASSWORD);

        admin = userRepository.save(User.builder()
                .username("tc_admin_" + ts)
                .email("tc_admin_" + ts + "@test.com")
                .passwordHash(encodedPassword)
                .fullName("TC Admin")
                .role(Role.ADMIN)
                .isActive(true)
                .build());

        secretary = userRepository.save(User.builder()
                .username("tc_sec_" + ts)
                .email("tc_sec_" + ts + "@test.com")
                .passwordHash(encodedPassword)
                .fullName("TC Secretary")
                .role(Role.SECRETARY)
                .isActive(true)
                .build());

        outsider = userRepository.save(User.builder()
                .username("tc_outsider_" + ts)
                .email("tc_outsider_" + ts + "@test.com")
                .passwordHash(encodedPassword)
                .fullName("TC Outsider")
                .role(Role.USER)
                .isActive(true)
                .build());

        Department dept = departmentRepository.save(Department.builder()
                .name("TC Dept " + ts)
                .description("Testcontainers dept")
                .build());

        room = roomRepository.save(Room.builder()
                .name("TC Room " + ts)
                .capacity(10)
                .department(dept)
                .build());

        adminToken = login(admin.getUsername());
        secretaryToken = login(secretary.getUsername());
        outsiderToken = login(outsider.getUsername());
    }

    // ── Create meeting ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/meetings — Create meeting")
    class CreateMeeting {

        @Test
        @DisplayName("Returns 201 and meeting code is generated")
        void createMeetingReturns201WithCode() {
            ResponseEntity<String> response = postMeeting("TC Meeting Alpha", adminToken);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            MeetingResponse meeting = extractMeetingData(response.getBody());
            assertThat(meeting.getId()).isNotNull();
            assertThat(meeting.getCode()).isNotBlank();
            assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.SCHEDULED);
        }

        @Test
        @DisplayName("Two meetings have different codes")
        void twoMeetingsHaveDifferentCodes() {
            ResponseEntity<String> r1 = postMeeting("TC Meeting Beta", adminToken);
            ResponseEntity<String> r2 = postMeeting("TC Meeting Gamma", adminToken);

            MeetingResponse m1 = extractMeetingData(r1.getBody());
            MeetingResponse m2 = extractMeetingData(r2.getBody());

            assertThat(m1.getCode()).isNotEqualTo(m2.getCode());
        }

        @Test
        @DisplayName("Returns 403 when PARTICIPANT tries to create a meeting")
        void participantCannotCreateMeeting() {
            ResponseEntity<String> response = postMeeting("TC Forbidden Meeting", outsiderToken);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── Activate meeting ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/meetings/{id}/activate — Activate meeting")
    class ActivateMeeting {

        @Test
        @DisplayName("Returns 200 and status=ACTIVE after activation")
        void activateMeetingReturnsActive() {
            MeetingResponse created = extractMeetingData(
                    postMeeting("TC Activate Test", adminToken).getBody());

            ResponseEntity<String> activateResponse = postWithToken(
                    "/api/v1/meetings/" + created.getId() + "/activate", adminToken);

            assertThat(activateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            MeetingResponse activated = extractMeetingData(activateResponse.getBody());
            assertThat(activated.getStatus()).isEqualTo(MeetingStatus.ACTIVE);
            assertThat(activated.getActivatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Returns 400 when activating an already ACTIVE meeting")
        void activatingActiveMeetingReturns400() {
            MeetingResponse created = extractMeetingData(
                    postMeeting("TC Double Activate", adminToken).getBody());

            // First activation
            postWithToken("/api/v1/meetings/" + created.getId() + "/activate", adminToken);

            // Second activation should fail
            ResponseEntity<String> response = postWithToken(
                    "/api/v1/meetings/" + created.getId() + "/activate", adminToken);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Returns 400 when activating an ENDED meeting")
        void activatingEndedMeetingReturns400() {
            MeetingResponse created = extractMeetingData(
                    postMeeting("TC Ended Activate", adminToken).getBody());
            long id = created.getId();

            postWithToken("/api/v1/meetings/" + id + "/activate", adminToken);
            postWithToken("/api/v1/meetings/" + id + "/end", adminToken);

            ResponseEntity<String> response = postWithToken(
                    "/api/v1/meetings/" + id + "/activate", adminToken);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ── End meeting ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/meetings/{id}/end — End meeting")
    class EndMeeting {

        @Test
        @DisplayName("Returns 200 and status=ENDED after ending an ACTIVE meeting")
        void endMeetingReturnsEnded() {
            MeetingResponse created = extractMeetingData(
                    postMeeting("TC End Test", adminToken).getBody());
            long id = created.getId();

            postWithToken("/api/v1/meetings/" + id + "/activate", adminToken);

            ResponseEntity<String> endResponse = postWithToken(
                    "/api/v1/meetings/" + id + "/end", adminToken);

            assertThat(endResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            MeetingResponse ended = extractMeetingData(endResponse.getBody());
            assertThat(ended.getStatus()).isEqualTo(MeetingStatus.ENDED);
            assertThat(ended.getEndedAt()).isNotNull();
        }

        @Test
        @DisplayName("Returns 400 when ending a SCHEDULED meeting")
        void endingScheduledMeetingReturns400() {
            MeetingResponse created = extractMeetingData(
                    postMeeting("TC End Scheduled", adminToken).getBody());

            ResponseEntity<String> response = postWithToken(
                    "/api/v1/meetings/" + created.getId() + "/end", adminToken);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Returns 400 when ending an already ENDED meeting")
        void endingEndedMeetingReturns400() {
            MeetingResponse created = extractMeetingData(
                    postMeeting("TC Double End", adminToken).getBody());
            long id = created.getId();

            postWithToken("/api/v1/meetings/" + id + "/activate", adminToken);
            postWithToken("/api/v1/meetings/" + id + "/end", adminToken);

            ResponseEntity<String> response = postWithToken(
                    "/api/v1/meetings/" + id + "/end", adminToken);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ── Non-member join ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/meetings/{id}/join — Non-member access")
    class NonMemberJoin {

        @Test
        @DisplayName("Returns 403 when non-member tries to join an active meeting")
        void nonMemberJoinReturns403() {
            MeetingResponse created = extractMeetingData(
                    postMeeting("TC Non-Member Join", adminToken).getBody());
            long id = created.getId();

            postWithToken("/api/v1/meetings/" + id + "/activate", adminToken);

            // outsider is not a member of this meeting
            ResponseEntity<String> joinResponse = postWithToken(
                    "/api/v1/meetings/" + id + "/join", outsiderToken);

            assertThat(joinResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── Full lifecycle ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Full lifecycle: SCHEDULED → ACTIVE → ENDED")
    class FullLifecycle {

        @Test
        @DisplayName("Meeting passes through all three states via HTTP API")
        void fullLifecycleViaApi() {
            // Create
            ResponseEntity<String> createResp = postMeeting("TC Full Lifecycle", adminToken);
            assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            MeetingResponse created = extractMeetingData(createResp.getBody());
            assertThat(created.getStatus()).isEqualTo(MeetingStatus.SCHEDULED);

            // Activate
            ResponseEntity<String> activateResp = postWithToken(
                    "/api/v1/meetings/" + created.getId() + "/activate", adminToken);
            assertThat(activateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(extractMeetingData(activateResp.getBody()).getStatus())
                    .isEqualTo(MeetingStatus.ACTIVE);

            // End
            ResponseEntity<String> endResp = postWithToken(
                    "/api/v1/meetings/" + created.getId() + "/end", adminToken);
            assertThat(endResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(extractMeetingData(endResp.getBody()).getStatus())
                    .isEqualTo(MeetingStatus.ENDED);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String login(String username) {
        LoginRequest loginRequest = new LoginRequest(username, RAW_PASSWORD);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginRequest> entity = new HttpEntity<>(loginRequest, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/v1/auth/login", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        try {
            Map<String, Object> body = objectMapper.readValue(response.getBody(),
                    new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            return (String) data.get("accessToken");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse login response", e);
        }
    }

    private ResponseEntity<String> postMeeting(String title, String token) {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .title(title)
                .description("Testcontainers integration test")
                .startTime(start)
                .endTime(start.plusHours(1))
                .roomId(room.getId())
                .hostUserId(admin.getId())
                .secretaryUserId(secretary.getId())
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        HttpEntity<CreateMeetingRequest> entity = new HttpEntity<>(request, headers);

        return restTemplate.postForEntity(
                baseUrl() + "/api/v1/meetings", entity, String.class);
    }

    private ResponseEntity<String> postWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(
                baseUrl() + path, HttpMethod.POST, entity, String.class);
    }

    private MeetingResponse extractMeetingData(String json) {
        try {
            Map<String, Object> body = objectMapper.readValue(json, new TypeReference<>() {});
            Object data = body.get("data");
            return objectMapper.convertValue(data, MeetingResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse meeting response: " + json, e);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
