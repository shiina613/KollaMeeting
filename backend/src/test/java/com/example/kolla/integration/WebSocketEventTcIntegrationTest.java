package com.example.kolla.integration;

import com.example.kolla.dto.LoginRequest;
import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.models.Department;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Room;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DepartmentRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.RoomRepository;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.websocket.MeetingEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers integration tests for WebSocket events.
 *
 * Uses a real STOMP client connecting to the running server to verify:
 * - Connect to /ws endpoint with JWT
 * - Subscribe to /topic/meeting/{id}
 * - Trigger meeting activation → receive MEETING_STARTED event
 * - Trigger mode switch → receive MODE_CHANGED event
 *
 * Requirements: 20.2
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("tc-test")
@Testcontainers(disabledWithoutDocker = true)
class WebSocketEventTcIntegrationTest {

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
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingEventPublisher eventPublisher;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    // ── Test state ────────────────────────────────────────────────────────────

    private User admin;
    private Meeting activeMeeting;
    private String adminToken;

    private static final String RAW_PASSWORD = "Test@1234";
    private static final int WS_TIMEOUT_SECONDS = 10;

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        long ts = System.nanoTime();
        String encodedPassword = passwordEncoder.encode(RAW_PASSWORD);

        admin = userRepository.save(User.builder()
                .username("ws_tc_admin_" + ts)
                .email("ws_tc_admin_" + ts + "@test.com")
                .passwordHash(encodedPassword)
                .fullName("WS TC Admin")
                .role(Role.ADMIN)
                .isActive(true)
                .build());

        User secretary = userRepository.save(User.builder()
                .username("ws_tc_sec_" + ts)
                .email("ws_tc_sec_" + ts + "@test.com")
                .passwordHash(encodedPassword)
                .fullName("WS TC Secretary")
                .role(Role.SECRETARY)
                .isActive(true)
                .build());

        Department dept = departmentRepository.save(Department.builder()
                .name("WS TC Dept " + ts)
                .description("WebSocket TC test dept")
                .build());

        Room room = roomRepository.save(Room.builder()
                .name("WS TC Room " + ts)
                .capacity(10)
                .department(dept)
                .build());

        activeMeeting = meetingRepository.save(Meeting.builder()
                .code("WSTC" + ts)
                .title("WS TC Test Meeting")
                .description("WebSocket Testcontainers integration test")
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(2))
                .room(room)
                .creator(admin)
                .host(admin)
                .secretary(secretary)
                .status(MeetingStatus.ACTIVE)
                .mode(MeetingMode.FREE_MODE)
                .transcriptionPriority(TranscriptionPriority.NORMAL_PRIORITY)
                .build());

        adminToken = login(admin.getUsername());
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Subscribe to /topic/meeting/{id} and receive MEETING_STARTED event")
    void receiveMeetingStartedEvent() throws Exception {
        CompletableFuture<Map<String, Object>> eventFuture = new CompletableFuture<>();

        StompSession session = connectWithToken(adminToken);
        session.subscribe("/topic/meeting/" + activeMeeting.getId(),
                new MapFrameHandler(eventFuture));

        // Give subscription time to register
        Thread.sleep(300);

        // Trigger the event
        eventPublisher.publishMeetingStarted(activeMeeting.getId(), admin.getFullName());

        Map<String, Object> event = eventFuture.get(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(event).isNotNull();
        assertThat(event.get("type")).isEqualTo("MEETING_STARTED");
        assertThat(event.get("meetingId")).isNotNull();

        session.disconnect();
    }

    @Test
    @DisplayName("Subscribe to /topic/meeting/{id} and receive MODE_CHANGED event")
    void receiveModeChangedEvent() throws Exception {
        CompletableFuture<Map<String, Object>> eventFuture = new CompletableFuture<>();

        StompSession session = connectWithToken(adminToken);
        session.subscribe("/topic/meeting/" + activeMeeting.getId(),
                new MapFrameHandler(eventFuture));

        Thread.sleep(300);

        // Trigger mode change
        eventPublisher.publishModeChanged(activeMeeting.getId(), MeetingMode.MEETING_MODE);

        Map<String, Object> event = eventFuture.get(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(event).isNotNull();
        assertThat(event.get("type")).isEqualTo("MODE_CHANGED");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        assertThat(payload).containsEntry("mode", "MEETING_MODE");

        session.disconnect();
    }

    @Test
    @DisplayName("Events for different meetings go to different topics")
    void eventsRoutedToCorrectTopic() throws Exception {
        long otherMeetingId = activeMeeting.getId() + 9999;

        CompletableFuture<Map<String, Object>> correctTopicFuture = new CompletableFuture<>();
        CompletableFuture<Map<String, Object>> wrongTopicFuture = new CompletableFuture<>();

        StompSession session = connectWithToken(adminToken);
        session.subscribe("/topic/meeting/" + activeMeeting.getId(),
                new MapFrameHandler(correctTopicFuture));
        session.subscribe("/topic/meeting/" + otherMeetingId,
                new MapFrameHandler(wrongTopicFuture));

        Thread.sleep(300);

        // Publish only to activeMeeting
        eventPublisher.publishMeetingEnded(activeMeeting.getId(), "HOST_ENDED");

        // Correct topic should receive the event
        Map<String, Object> event = correctTopicFuture.get(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(event.get("type")).isEqualTo("MEETING_ENDED");

        // Wrong topic should NOT receive anything within a short timeout
        assertThat(wrongTopicFuture.isDone()).isFalse();

        session.disconnect();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private StompSession connectWithToken(String token) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        String wsUrl = "ws://localhost:" + port + "/ws";

        return stompClient.connectAsync(wsUrl, new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {})
                .get(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private String login(String username) {
        LoginRequest loginRequest = new LoginRequest(username, RAW_PASSWORD);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginRequest> entity = new HttpEntity<>(loginRequest, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/auth/login", entity, String.class);

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

    /**
     * STOMP frame handler that deserializes the payload into a Map and completes a future.
     */
    private static class MapFrameHandler implements StompFrameHandler {

        private final CompletableFuture<Map<String, Object>> future;

        MapFrameHandler(CompletableFuture<Map<String, Object>> future) {
            this.future = future;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handleFrame(StompHeaders headers, Object payload) {
            if (!future.isDone()) {
                future.complete((Map<String, Object>) payload);
            }
        }
    }
}
