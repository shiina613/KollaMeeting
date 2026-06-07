package com.example.kolla.config;

import com.example.kolla.filter.JwtAuthenticationFilter;
import com.example.kolla.services.impl.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
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

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
@EnableScheduling
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final ObjectMapper objectMapper;

    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3000}")
    private String corsAllowedOrigins;

    @Value("${app.rate-limit.trust-proxy:false}")
    private boolean trustProxy;

    // ── Rate limiter state ──────────────────────────────────────────────────

    /** Thread-safe rate limit entry using AtomicInteger for atomic count increments. */
    private static class RateLimitEntry {
        final AtomicInteger count;
        volatile long windowStartMs;

        RateLimitEntry(long windowStartMs) {
            this.count = new AtomicInteger(1);
            this.windowStartMs = windowStartMs;
        }
    }

    private static final int RATE_LIMIT_MAX_REQUESTS = 10;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000L; // 1 minute
    private static final int RATE_LIMIT_MAX_ENTRIES = 10_000; // prevent memory exhaustion

    // Upload rate limiting: 10 uploads per 5 minutes per IP
    private static final int UPLOAD_RATE_LIMIT_MAX_REQUESTS = 10;
    private static final long UPLOAD_RATE_LIMIT_WINDOW_MS = 300_000L; // 5 minutes

    private final ConcurrentHashMap<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateLimitEntry> uploadRateLimitMap = new ConcurrentHashMap<>();

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
                    .requestMatchers(HttpMethod.POST, "/auth/refresh").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                    // Internal callback — secured by X-Internal-Api-Key header, not JWT
                    .requestMatchers(HttpMethod.POST, "/transcription/callback").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api-docs/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api-docs").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v3/api-docs/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/swagger-ui/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/swagger-ui.html").permitAll()
                    // WebSocket endpoints (JWT auth handled in STOMP CONNECT)
                    .requestMatchers("/ws").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    .requestMatchers("/ws-sockjs/**").permitAll()
                    .requestMatchers("/api/v1/ws").permitAll()
                    .requestMatchers("/api/v1/ws/**").permitAll()
                    // All other endpoints require authentication
                    .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((request, response, authException) -> {
                        writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                                "Unauthorized", "Authentication required", request.getRequestURI());
                    })
                    .accessDeniedHandler((request, response, accessDeniedException) -> {
                        writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                                "Forbidden", "Access denied", request.getRequestURI());
                    })
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // Rate limiting filter for login and refresh endpoints
            .addFilterBefore(
                    (request, response, chain) -> {
                        HttpServletRequest httpRequest = (HttpServletRequest) request;
                        HttpServletResponse httpResponse = (HttpServletResponse) response;

                        String path = httpRequest.getServletPath();
                        if ("POST".equalsIgnoreCase(httpRequest.getMethod())
                                && (path.endsWith("/auth/login") || path.endsWith("/auth/refresh"))) {
                            String ip = getClientIp(httpRequest);
                            if (!isAllowed(ip)) {
                                log.warn("Rate limit exceeded for IP: {}", ip);
                                writeErrorResponse(httpResponse, 429,
                                        "Too Many Requests",
                                        "Rate limit exceeded. Please try again later.",
                                        httpRequest.getRequestURI());
                                return;
                            }
                        }

                        // Rate limiting for file upload endpoints
                        if ("POST".equalsIgnoreCase(httpRequest.getMethod())
                                && (path.contains("/documents") || path.contains("/recordings")
                                    || path.contains("/upload"))) {
                            String contentType = httpRequest.getContentType();
                            if (contentType != null && contentType.contains("multipart")) {
                                String ip = getClientIp(httpRequest);
                                if (!isUploadAllowed(ip)) {
                                    log.warn("Upload rate limit exceeded for IP: {}", ip);
                                    writeErrorResponse(httpResponse, 429,
                                            "Too Many Requests",
                                            "Upload rate limit exceeded. Please try again later.",
                                            httpRequest.getRequestURI());
                                    return;
                                }
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

        // Configure CORS origins.
        // When wildcard "*" is used, credentials MUST be disabled (CORS spec requirement).
        List<String> origins = Arrays.asList(corsAllowedOrigins.split(","));
        if (origins.contains("*")) {
            config.setAllowedOriginPatterns(List.of("*"));
            config.setAllowCredentials(false);
            log.warn("CORS: wildcard origin '*' detected — credentials disabled per CORS spec. "
                    + "Set explicit origins in CORS_ALLOWED_ORIGINS for credential support.");
        } else {
            config.setAllowedOriginPatterns(origins);
            config.setAllowCredentials(true);
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // ── Rate limiter helpers ────────────────────────────────────────────────

    /**
     * Returns true if the IP is within the rate limit window.
     * Uses AtomicInteger for thread-safe count increments.
     * Cleans up stale entries on each check.
     * Enforces a maximum map size to prevent memory exhaustion from distributed attacks.
     */
    private boolean isAllowed(String ip) {
        return checkRateLimit(ip, rateLimitMap, RATE_LIMIT_MAX_REQUESTS, RATE_LIMIT_WINDOW_MS);
    }

    /**
     * Returns true if the IP is within the upload rate limit window.
     */
    private boolean isUploadAllowed(String ip) {
        return checkRateLimit(ip, uploadRateLimitMap, UPLOAD_RATE_LIMIT_MAX_REQUESTS, UPLOAD_RATE_LIMIT_WINDOW_MS);
    }

    private boolean checkRateLimit(String ip, ConcurrentHashMap<String, RateLimitEntry> map,
                                    int maxRequests, long windowMs) {
        long now = Instant.now().toEpochMilli();

        // Clean up stale entries
        map.entrySet().removeIf(e -> now - e.getValue().windowStartMs > windowMs);

        // Prevent memory exhaustion: if map is too large after cleanup, reject new IPs
        if (map.size() >= RATE_LIMIT_MAX_ENTRIES && !map.containsKey(ip)) {
            log.warn("Rate limit map at capacity ({}), rejecting new IP: {}", RATE_LIMIT_MAX_ENTRIES, ip);
            return false;
        }

        RateLimitEntry entry = map.compute(ip, (key, existing) -> {
            if (existing == null || now - existing.windowStartMs > windowMs) {
                return new RateLimitEntry(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        return entry.count.get() <= maxRequests;
    }

    /**
     * Periodic cleanup of stale rate limit entries to prevent memory leaks
     * when traffic stops. Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300_000)
    public void cleanupRateLimitMap() {
        long now = Instant.now().toEpochMilli();

        int sizeBefore = rateLimitMap.size();
        rateLimitMap.entrySet().removeIf(e ->
                now - e.getValue().windowStartMs > RATE_LIMIT_WINDOW_MS);
        int removed = sizeBefore - rateLimitMap.size();

        int uploadSizeBefore = uploadRateLimitMap.size();
        uploadRateLimitMap.entrySet().removeIf(e ->
                now - e.getValue().windowStartMs > UPLOAD_RATE_LIMIT_WINDOW_MS);
        int uploadRemoved = uploadSizeBefore - uploadRateLimitMap.size();

        if (removed > 0 || uploadRemoved > 0) {
            log.debug("Rate limit cleanup: removed {} login + {} upload stale entries",
                    removed, uploadRemoved);
        }
    }

    /**
     * Extracts client IP address.
     * Only trusts X-Forwarded-For / X-Real-IP headers when app.rate-limit.trust-proxy=true
     * (i.e., when behind a trusted reverse proxy). Otherwise uses remoteAddr directly
     * to prevent IP spoofing attacks that bypass rate limiting.
     */
    private String getClientIp(HttpServletRequest request) {
        if (trustProxy) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Writes a structured JSON error response consistent with ErrorResponse format.
     */
    private void writeErrorResponse(HttpServletResponse response, int status,
                                     String error, String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("path", path);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
