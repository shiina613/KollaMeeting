package com.example.kolla.controllers;

import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.exceptions.ServiceUnavailableException;
import com.example.kolla.filter.JwtAuthenticationFilter;
import com.example.kolla.models.User;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.responses.JaasTokenResponse;
import com.example.kolla.services.JaasTokenService;
import com.example.kolla.services.impl.UserDetailsServiceImpl;
import com.example.kolla.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link JaasTokenController} using MockMvc.
 *
 * Uses {@code @WebMvcTest} with a minimal security configuration that mirrors
 * the real {@code SecurityConfig} but without JPA/Redis infrastructure.
 * The real {@link JwtAuthenticationFilter} is imported so that 401 behaviour
 * is exercised through the actual filter chain.
 *
 * Test scenarios:
 * - 200 OK with valid Kolla JWT and user is a member
 * - 401 when no Kolla JWT is provided
 * - 403 when user is not a member (service throws ForbiddenException)
 * - 404 when meeting does not exist (service throws ResourceNotFoundException)
 * - 503 when JaaS is not configured (service throws ServiceUnavailableException)
 *
 * Requirements: 1.1, 1.2, 1.3, 1.4, 1.19
 */
@WebMvcTest(
        controllers = JaasTokenController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
                org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
                org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class
        }
)
@Import({JaasTokenControllerTest.TestSecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-unit-tests-only-32chars",
        "jwt.expiration-ms=3600000",
        "jwt.refresh-expiration-ms=604800000",
        "CORS_ALLOWED_ORIGINS=http://localhost:3000",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"
})
class JaasTokenControllerTest {

    // ── Minimal security config ───────────────────────────────────────────────

    /**
     * Minimal security configuration that mirrors the real SecurityConfig
     * but without JPA/Redis infrastructure dependencies.
     */
    @org.springframework.boot.test.context.TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;

        TestSecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
            this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        }

        @org.springframework.context.annotation.Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session ->
                            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().authenticated()
                    )
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((request, response, authException) -> {
                                response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                response.getWriter().write(
                                        "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                            })
                    )
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

            return http.build();
        }
    }

    // ── Injected beans ────────────────────────────────────────────────────────

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JaasTokenService jaasTokenService;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private UserRepository userRepository;

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private static final String VALID_TOKEN = "valid-kolla-jwt";
    private static final Long USER_ID = 42L;
    private static final Long MEETING_ID = 1L;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(USER_ID)
                .username("testuser")
                .passwordHash("$2a$12$hashed")
                .fullName("Test User")
                .email("testuser@example.com")
                .role(Role.USER)
                .isActive(true)
                .build();
    }

    // ── Helper: stub a valid Kolla JWT ────────────────────────────────────────

    /**
     * Stubs the JWT filter chain so that requests with {@code VALID_TOKEN}
     * are authenticated as {@code testUser}.
     *
     * The filter performs two Redis checks:
     * 1. {@code redisTemplate.hasKey("jwt:blacklist:<token>")} — token blacklist
     * 2. {@code redisTemplate.opsForValue().get("jwt:blacklist:user:<userId>")} — per-user invalidation
     */
    @SuppressWarnings("unchecked")
    private void stubValidToken() {
        when(jwtUtils.validateToken(VALID_TOKEN)).thenReturn(true);
        // Token blacklist check
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        // Per-user invalidation check — opsForValue() must not return null
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null); // null means not invalidated
        when(jwtUtils.getUserIdFromToken(VALID_TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /meetings/{id}/jaas-token — 200 OK")
    class SuccessScenario {

        @Test
        @DisplayName("Returns 200 with token and roomName when user is a valid member")
        void getJaasToken_validMemberWithJwt_returns200() throws Exception {
            // Arrange
            stubValidToken();
            JaasTokenResponse serviceResponse = new JaasTokenResponse(
                    "eyJhbGciOiJSUzI1NiJ9.payload.signature",
                    "vpaas-magic-cookie-testapp/ABCDEF1234567890ABCD"
            );
            when(jaasTokenService.generateToken(anyLong(), any(User.class)))
                    .thenReturn(serviceResponse);

            // Act & Assert
            mockMvc.perform(get("/meetings/{id}/jaas-token", MEETING_ID)
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.token",
                            is("eyJhbGciOiJSUzI1NiJ9.payload.signature")))
                    .andExpect(jsonPath("$.data.roomName",
                            is("vpaas-magic-cookie-testapp/ABCDEF1234567890ABCD")));
        }
    }

    @Nested
    @DisplayName("GET /meetings/{id}/jaas-token — 401 Unauthorized")
    class UnauthorizedScenario {

        @Test
        @DisplayName("Returns 401 when no Authorization header is provided")
        void getJaasToken_noJwt_returns401() throws Exception {
            // Act & Assert — no Authorization header
            mockMvc.perform(get("/meetings/{id}/jaas-token", MEETING_ID)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Returns 401 when an invalid JWT is provided")
        void getJaasToken_invalidJwt_returns401() throws Exception {
            // Arrange — token fails validation
            when(jwtUtils.validateToken("bad-token")).thenReturn(false);

            // Act & Assert
            mockMvc.perform(get("/meetings/{id}/jaas-token", MEETING_ID)
                            .header("Authorization", "Bearer bad-token")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /meetings/{id}/jaas-token — 403 Forbidden")
    class ForbiddenScenario {

        @Test
        @DisplayName("Returns 403 when user is not a member of the meeting")
        void getJaasToken_notMember_returns403() throws Exception {
            // Arrange
            stubValidToken();
            when(jaasTokenService.generateToken(anyLong(), any(User.class)))
                    .thenThrow(new ForbiddenException("User is not a member of this meeting"));

            // Act & Assert
            mockMvc.perform(get("/meetings/{id}/jaas-token", MEETING_ID)
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /meetings/{id}/jaas-token — 404 Not Found")
    class NotFoundScenario {

        @Test
        @DisplayName("Returns 404 when the meeting does not exist")
        void getJaasToken_meetingNotFound_returns404() throws Exception {
            // Arrange
            stubValidToken();
            when(jaasTokenService.generateToken(anyLong(), any(User.class)))
                    .thenThrow(new ResourceNotFoundException("Meeting not found: " + MEETING_ID));

            // Act & Assert
            mockMvc.perform(get("/meetings/{id}/jaas-token", MEETING_ID)
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /meetings/{id}/jaas-token — 503 Service Unavailable")
    class ServiceUnavailableScenario {

        @Test
        @DisplayName("Returns 503 when JaaS is not configured (JAAS_APP_ID or JAAS_PRIVATE_KEY missing)")
        void getJaasToken_jaasNotConfigured_returns503() throws Exception {
            // Arrange
            stubValidToken();
            when(jaasTokenService.generateToken(anyLong(), any(User.class)))
                    .thenThrow(new ServiceUnavailableException("JaaS is not configured"));

            // Act & Assert
            mockMvc.perform(get("/meetings/{id}/jaas-token", MEETING_ID)
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isServiceUnavailable());
        }
    }
}
