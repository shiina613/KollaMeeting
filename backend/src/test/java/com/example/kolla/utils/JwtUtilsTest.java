package com.example.kolla.utils;

import com.example.kolla.enums.Role;
import com.example.kolla.models.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JwtUtils.
 * Requirements: 20.5
 */
@ExtendWith(MockitoExtension.class)
class JwtUtilsTest {

    private JwtUtils jwtUtils;

    // A 32-char secret that is valid for HS256
    private static final String TEST_SECRET = "test-secret-key-for-unit-tests-only-32chars";
    private static final long EXPIRATION_MS = 3_600_000L; // 1 hour

    private User testUser;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", EXPIRATION_MS);
        ReflectionTestUtils.setField(jwtUtils, "refreshExpirationMs", 604_800_000L);

        testUser = User.builder()
                .id(42L)
                .username("alice")
                .passwordHash("$2a$12$hashed")
                .fullName("Alice Nguyen")
                .email("alice@example.com")
                .role(Role.USER)
                .isActive(true)
                .build();
    }

    // ── generateAccessToken ─────────────────────────────────────────────────

    @Test
    @DisplayName("generateAccessToken() returns a non-null, non-empty token")
    void generateAccessToken_returnsNonNullNonEmpty() {
        String token = jwtUtils.generateAccessToken(testUser, testUser.getId(), testUser.getRole().name());

        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("generateAccessToken() produces a token with three JWT parts")
    void generateAccessToken_hasThreeParts() {
        String token = jwtUtils.generateAccessToken(testUser, testUser.getId(), testUser.getRole().name());

        assertThat(token.split("\\.")).hasSize(3);
    }

    // ── extractUsername ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getUsernameFromToken() returns the correct username from a valid token")
    void getUsernameFromToken_returnsCorrectUsername() {
        String token = jwtUtils.generateAccessToken(testUser, testUser.getId(), testUser.getRole().name());

        String username = jwtUtils.getUsernameFromToken(token);

        assertThat(username).isEqualTo("alice");
    }

    @Test
    @DisplayName("getUserIdFromToken() returns the correct userId from a valid token")
    void getUserIdFromToken_returnsCorrectUserId() {
        String token = jwtUtils.generateAccessToken(testUser, testUser.getId(), testUser.getRole().name());

        Long userId = jwtUtils.getUserIdFromToken(token);

        assertThat(userId).isEqualTo(42L);
    }

    // ── role claim ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Token contains the correct role claim")
    void token_containsCorrectRoleClaim() {
        String token = jwtUtils.generateAccessToken(testUser, testUser.getId(), Role.ADMIN.name());

        String role = jwtUtils.getRoleFromToken(token);

        assertThat(role).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Token role claim matches the role passed to generateAccessToken()")
    void token_roleClaimMatchesPassedRole() {
        for (Role role : Role.values()) {
            String token = jwtUtils.generateAccessToken(testUser, testUser.getId(), role.name());
            assertThat(jwtUtils.getRoleFromToken(token)).isEqualTo(role.name());
        }
    }

    // ── validateToken ───────────────────────────────────────────────────────

    @Test
    @DisplayName("validateToken() returns true for a valid, non-expired token")
    void validateToken_returnsTrueForValidToken() {
        String token = jwtUtils.generateAccessToken(testUser, testUser.getId(), testUser.getRole().name());

        assertThat(jwtUtils.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken() returns false for an expired token")
    void validateToken_returnsFalseForExpiredToken() throws InterruptedException {
        // Set a very short expiration (1 ms)
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 1L);
        String token = jwtUtils.generateAccessToken(testUser, testUser.getId(), testUser.getRole().name());

        // Wait for the token to expire
        Thread.sleep(50);

        assertThat(jwtUtils.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("validateToken() returns false for a token signed with a different secret")
    void validateToken_returnsFalseForWrongSecret() {
        // Create a JwtUtils with a different secret
        JwtUtils otherJwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(otherJwtUtils, "jwtSecret", "different-secret-key-for-testing-32chars");
        ReflectionTestUtils.setField(otherJwtUtils, "jwtExpirationMs", EXPIRATION_MS);
        ReflectionTestUtils.setField(otherJwtUtils, "refreshExpirationMs", 604_800_000L);

        // Token signed with the other secret
        String tokenFromOther = otherJwtUtils.generateAccessToken(testUser, testUser.getId(), testUser.getRole().name());

        // Validate with the original jwtUtils (different secret) → should fail
        assertThat(jwtUtils.validateToken(tokenFromOther)).isFalse();
    }

    @Test
    @DisplayName("validateToken() returns false for a malformed token string")
    void validateToken_returnsFalseForMalformedToken() {
        assertThat(jwtUtils.validateToken("not.a.valid.jwt.token")).isFalse();
    }

    @Test
    @DisplayName("validateToken() returns false for an empty string")
    void validateToken_returnsFalseForEmptyString() {
        assertThat(jwtUtils.validateToken("")).isFalse();
    }

    // ── getUsernameFromToken on malformed token ─────────────────────────────

    @Test
    @DisplayName("getUsernameFromToken() on a malformed token throws an exception gracefully")
    void getUsernameFromToken_malformedToken_throwsException() {
        // The method calls parseClaims which throws on malformed input.
        // The test verifies it throws rather than returning garbage.
        assertThatThrownBy(() -> jwtUtils.getUsernameFromToken("malformed.token.here"))
                .isInstanceOf(Exception.class);
    }

    // ── refresh token ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generateRefreshToken() returns a non-null, non-empty token")
    void generateRefreshToken_returnsNonNullNonEmpty() {
        String token = jwtUtils.generateRefreshToken(testUser);

        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("getSubjectFromToken() on refresh token returns the username")
    void getSubjectFromToken_refreshToken_returnsUsername() {
        String token = jwtUtils.generateRefreshToken(testUser);

        assertThat(jwtUtils.getSubjectFromToken(token)).isEqualTo("alice");
    }

    @Test
    @DisplayName("validateToken() returns true for a valid refresh token")
    void validateToken_returnsTrueForValidRefreshToken() {
        String token = jwtUtils.generateRefreshToken(testUser);

        assertThat(jwtUtils.validateToken(token)).isTrue();
    }

    // ── getRemainingTtlMs ───────────────────────────────────────────────────

    @Test
    @DisplayName("getRemainingTtlMs() returns a positive value for a fresh token")
    void getRemainingTtlMs_returnsPositiveForFreshToken() {
        String token = jwtUtils.generateAccessToken(testUser, testUser.getId(), testUser.getRole().name());

        long ttl = jwtUtils.getRemainingTtlMs(token);

        assertThat(ttl).isPositive().isLessThanOrEqualTo(EXPIRATION_MS);
    }
}
