package com.example.kolla.config;

import com.example.kolla.filter.JwtAuthenticationFilter;
import com.example.kolla.services.impl.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring Security configuration.
 * - Stateless JWT filter chain
 * - CORS from env CORS_ALLOWED_ORIGINS
 * - BCrypt password encoder (strength 12)
 * - In-memory rate limiting on /auth/login (10 req/min per IP)
 * - Public endpoints: POST /auth/login, GET /actuator/health, Swagger/OpenAPI
 * Requirements: 2.6, 2.7, 19.1–19.7
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3000}")
    private String corsAllowedOrigins;

    // ── Rate limiter state ──────────────────────────────────────────────────

    /** Simple in-memory rate limit entry. */
    private static class RateLimitEntry {
        volatile int count;
        volatile long windowStartMs;

        RateLimitEntry(long windowStartMs) {
            this.count = 1;
            this.windowStartMs = windowStartMs;
        }
    }

    private static final int RATE_LIMIT_MAX_REQUESTS = 10;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000L; // 1 minute

    private final ConcurrentHashMap<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    // ── Security filter chain ───────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // Public endpoints
                    .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api-docs/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api-docs").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v3/api-docs/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/swagger-ui/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/swagger-ui.html").permitAll()
                    // WebSocket SockJS handshake endpoints (JWT auth handled in STOMP CONNECT)
                    .requestMatchers("/ws/**").permitAll()
                    // All other endpoints require authentication
                    .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((request, response, authException) -> {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write(
                                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                    })
                    .accessDeniedHandler((request, response, accessDeniedException) -> {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write(
                                "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                    })
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // Rate limiting filter for login endpoint
            .addFilterBefore(
                    (request, response, chain) -> {
                        HttpServletRequest httpRequest = (HttpServletRequest) request;
                        HttpServletResponse httpResponse = (HttpServletResponse) response;

                        String path = httpRequest.getServletPath();
                        if ("POST".equalsIgnoreCase(httpRequest.getMethod())
                                && path.endsWith("/auth/login")) {
                            String ip = getClientIp(httpRequest);
                            if (!isAllowed(ip)) {
                                log.warn("Rate limit exceeded for IP: {}", ip);
                                httpResponse.setStatus(429);
                                httpResponse.setContentType("application/json");
                                httpResponse.getWriter().write(
                                        "{\"status\":429,\"error\":\"Too Many Requests\","
                                        + "\"message\":\"Rate limit exceeded. Max 10 login attempts per minute.\"}");
                                return;
                            }
                        }
                        chain.doFilter(request, response);
                    },
                    UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    // ── Beans ───────────────────────────────────────────────────────────────

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.asList(corsAllowedOrigins.split(","));
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // ── Rate limiter helpers ────────────────────────────────────────────────

    /**
     * Returns true if the IP is within the rate limit window.
     * Cleans up stale entries on each check.
     */
    private boolean isAllowed(String ip) {
        long now = Instant.now().toEpochMilli();

        // Clean up stale entries
        rateLimitMap.entrySet().removeIf(e ->
                now - e.getValue().windowStartMs > RATE_LIMIT_WINDOW_MS);

        RateLimitEntry entry = rateLimitMap.compute(ip, (key, existing) -> {
            if (existing == null || now - existing.windowStartMs > RATE_LIMIT_WINDOW_MS) {
                return new RateLimitEntry(now);
            }
            existing.count++;
            return existing;
        });

        return entry.count <= RATE_LIMIT_MAX_REQUESTS;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
