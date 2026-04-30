package com.example.kolla.security;

import com.example.kolla.config.SecurityConfig;
import com.example.kolla.filter.JwtAuthenticationFilter;
import com.example.kolla.services.impl.UserDetailsServiceImpl;
import com.example.kolla.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-based access control tests using MockMvc + Spring Security.
 *
 * Uses @WebMvcTest with a minimal security configuration to avoid JPA/Redis
 * infrastructure dependencies while still testing the real JWT filter and
 * @PreAuthorize annotations.
 *
 * Tests that:
 * - Public endpoints are accessible without authentication
 * - Protected endpoints return 401 without JWT
 * - Protected endpoints return 403 when accessed with insufficient role
 * - ADMIN role can access admin-only endpoints
 * - SECRETARY role can access secretary-allowed endpoints
 *
 * Requirements: 20.5
 */
@WebMvcTest(controllers = TestSecurityController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
                org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
                org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class
        })
@Import({RoleBasedAccessControlTest.TestSecurityConfig.class, JwtAuthenticationFilter.class})
@ActiveProfiles("rbac-test")
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-unit-tests-only-32chars",
        "jwt.expiration-ms=3600000",
        "jwt.refresh-expiration-ms=604800000",
        "CORS_ALLOWED_ORIGINS=http://localhost:3000",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"
})
class RoleBasedAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    // ── Minimal security config for tests ────────────────────────────────────

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
                            .requestMatchers(org.springframework.http.HttpMethod.POST, "/auth/login").permitAll()
                            .anyRequest().authenticated()
                    )
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((request, response, authException) -> {
                                response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json");
                                response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\"}");
                            })
                            .accessDeniedHandler((request, response, accessDeniedException) -> {
                                response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
                                response.setContentType("application/json");
                                response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\"}");
                            })
                    )
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

            return http.build();
        }
    }

    // ── Helper: stub a valid token for a given userId/role/username ──────────

    private void stubToken(String token, Long userId, String role, String username) {
        when(jwtUtils.validateToken(token)).thenReturn(true);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(jwtUtils.getUserIdFromToken(token)).thenReturn(userId);
        when(jwtUtils.getRoleFromToken(token)).thenReturn(role);
        when(jwtUtils.getUsernameFromToken(token)).thenReturn(username);
    }

    // ── Public endpoint tests ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/login is accessible without authentication (200 or 400, not 401/403)")
    void loginEndpoint_isPublic() throws Exception {
        // The endpoint is public so it should NOT return 401 or 403.
        // It may return 400 (validation) or 200 (success) depending on the service mock.
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret\"}"))
                .andExpect(status().is(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.anyOf(
                                org.hamcrest.Matchers.is(401),
                                org.hamcrest.Matchers.is(403)))));
    }

    // ── Protected endpoint — no JWT ──────────────────────────────────────────

    @Test
    @DisplayName("GET /users/me returns 401 when no JWT is provided")
    void protectedEndpoint_noJwt_returns401() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /admin/users returns 401 when no JWT is provided")
    void adminEndpoint_noJwt_returns401() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    // ── Protected endpoint — USER role ───────────────────────────────────────

    @Test
    @DisplayName("GET /users/me returns 200 when USER-role JWT is provided")
    void protectedEndpoint_userRole_returns200() throws Exception {
        stubToken("user-token", 1L, "USER", "alice");

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /admin/users returns 403 when USER-role JWT is used")
    void adminEndpoint_userRole_returns403() throws Exception {
        stubToken("user-token", 1L, "USER", "alice");

        mockMvc.perform(get("/admin/users")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /admin/users returns 403 when USER-role JWT is used")
    void adminPostEndpoint_userRole_returns403() throws Exception {
        stubToken("user-token", 1L, "USER", "alice");

        mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ── ADMIN role ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /admin/users returns 200 when ADMIN-role JWT is provided")
    void adminEndpoint_adminRole_returns200() throws Exception {
        stubToken("admin-token", 2L, "ADMIN", "admin_user");

        mockMvc.perform(get("/admin/users")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /users/me returns 200 when ADMIN-role JWT is provided")
    void protectedEndpoint_adminRole_returns200() throws Exception {
        stubToken("admin-token", 2L, "ADMIN", "admin_user");

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    // ── SECRETARY role ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /secretary/meetings returns 200 when SECRETARY-role JWT is provided")
    void secretaryEndpoint_secretaryRole_returns200() throws Exception {
        stubToken("secretary-token", 3L, "SECRETARY", "secretary_user");

        mockMvc.perform(get("/secretary/meetings")
                        .header("Authorization", "Bearer secretary-token"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /admin/users returns 403 when SECRETARY-role JWT is used")
    void adminEndpoint_secretaryRole_returns403() throws Exception {
        stubToken("secretary-token", 3L, "SECRETARY", "secretary_user");

        mockMvc.perform(get("/admin/users")
                        .header("Authorization", "Bearer secretary-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /users/me returns 200 when SECRETARY-role JWT is provided")
    void protectedEndpoint_secretaryRole_returns200() throws Exception {
        stubToken("secretary-token", 3L, "SECRETARY", "secretary_user");

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer secretary-token"))
                .andExpect(status().isOk());
    }

    // ── Invalid token ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /users/me returns 401 when an invalid JWT is provided")
    void protectedEndpoint_invalidJwt_returns401() throws Exception {
        when(jwtUtils.validateToken("bad-token")).thenReturn(false);

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /users/me returns 401 when an expired JWT is provided")
    void protectedEndpoint_expiredJwt_returns401() throws Exception {
        when(jwtUtils.validateToken("expired-token")).thenReturn(false);

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
    }
}
