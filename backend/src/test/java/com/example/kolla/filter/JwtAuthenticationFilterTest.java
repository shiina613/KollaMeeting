package com.example.kolla.filter;

import com.example.kolla.enums.Role;
import com.example.kolla.models.User;
import com.example.kolla.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter.
 * Requirements: 20.5
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @Mock
    private FilterChain filterChain;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String INVALID_TOKEN = "invalid.jwt.token";
    private static final String EXPIRED_TOKEN = "expired.jwt.token";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private void stubValidToken(String token, Long userId, String role, String username) {
        when(jwtUtils.validateToken(token)).thenReturn(true);
        when(redisTemplate.hasKey("jwt:blacklist:" + token)).thenReturn(false);
        when(jwtUtils.getUserIdFromToken(token)).thenReturn(userId);
        when(jwtUtils.getRoleFromToken(token)).thenReturn(role);
        when(jwtUtils.getUsernameFromToken(token)).thenReturn(username);
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid JWT → SecurityContextHolder is populated with correct authentication")
    void validJwt_populatesSecurityContext() throws Exception {
        stubValidToken(VALID_TOKEN, 42L, "USER", "alice");

        MockHttpServletRequest request = requestWithBearer(VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Valid JWT → SecurityContextHolder contains the correct userId as principal")
    void validJwt_principalIsUserId() throws Exception {
        stubValidToken(VALID_TOKEN, 42L, "USER", "alice");

        MockHttpServletRequest request = requestWithBearer(VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getPrincipal()).isEqualTo("42");
    }

    @Test
    @DisplayName("Valid JWT → SecurityContextHolder contains the correct ROLE_ authority")
    void validJwt_containsCorrectAuthority() throws Exception {
        stubValidToken(VALID_TOKEN, 42L, "ADMIN", "admin_user");

        MockHttpServletRequest request = requestWithBearer(VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("No Authorization header → filter passes through without setting authentication")
    void noAuthorizationHeader_doesNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtUtils);
    }

    @Test
    @DisplayName("Authorization header without Bearer prefix → filter passes through without authentication")
    void authHeaderWithoutBearer_doesNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtUtils);
    }

    @Test
    @DisplayName("Bearer <invalid_token> → filter passes through without setting authentication")
    void invalidToken_doesNotSetAuthentication() throws Exception {
        when(jwtUtils.validateToken(INVALID_TOKEN)).thenReturn(false);

        MockHttpServletRequest request = requestWithBearer(INVALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer <invalid_token> → no exception propagated to caller")
    void invalidToken_noExceptionPropagated() {
        when(jwtUtils.validateToken(INVALID_TOKEN)).thenReturn(false);

        MockHttpServletRequest request = requestWithBearer(INVALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatNoException().isThrownBy(
                () -> filter.doFilterInternal(request, response, filterChain));
    }

    @Test
    @DisplayName("Bearer <expired_token> → filter passes through without setting authentication")
    void expiredToken_doesNotSetAuthentication() throws Exception {
        when(jwtUtils.validateToken(EXPIRED_TOKEN)).thenReturn(false);

        MockHttpServletRequest request = requestWithBearer(EXPIRED_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Blacklisted token → filter passes through without setting authentication")
    void blacklistedToken_doesNotSetAuthentication() throws Exception {
        when(jwtUtils.validateToken(VALID_TOKEN)).thenReturn(true);
        when(redisTemplate.hasKey("jwt:blacklist:" + VALID_TOKEN)).thenReturn(true);

        MockHttpServletRequest request = requestWithBearer(VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Exception during token processing → filter passes through without setting authentication")
    void exceptionDuringProcessing_doesNotSetAuthentication() throws Exception {
        when(jwtUtils.validateToken(VALID_TOKEN)).thenReturn(true);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(jwtUtils.getUserIdFromToken(VALID_TOKEN)).thenThrow(new RuntimeException("Unexpected error"));

        MockHttpServletRequest request = requestWithBearer(VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Should not propagate the exception
        assertThatNoException().isThrownBy(
                () -> filter.doFilterInternal(request, response, filterChain));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Redis failure during blacklist check → token is allowed (fail-open)")
    void redisFailure_allowsToken() throws Exception {
        when(jwtUtils.validateToken(VALID_TOKEN)).thenReturn(true);
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis down"));
        when(jwtUtils.getUserIdFromToken(VALID_TOKEN)).thenReturn(1L);
        when(jwtUtils.getRoleFromToken(VALID_TOKEN)).thenReturn("USER");
        when(jwtUtils.getUsernameFromToken(VALID_TOKEN)).thenReturn("alice");

        MockHttpServletRequest request = requestWithBearer(VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        // Fail-open: authentication should be set even when Redis is down
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Filter always calls filterChain.doFilter() regardless of token validity")
    void alwaysCallsFilterChain() throws Exception {
        when(jwtUtils.validateToken(INVALID_TOKEN)).thenReturn(false);

        MockHttpServletRequest request = requestWithBearer(INVALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }
}
