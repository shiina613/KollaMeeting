package com.example.kolla.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * JWT utility: generate, validate, and extract claims from tokens.
 * Requirements: 2.1–2.4, 14.2
 */
@Slf4j
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    // ── Token generation ────────────────────────────────────────────────────

    /**
     * Generate an access token.
     * Subject = userId (as string), claims: "role", "username".
     */
    public String generateAccessToken(UserDetails userDetails, Long userId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("role", role)
                .claim("username", userDetails.getUsername())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate a refresh token.
     * Subject = username, longer TTL.
     */
    public String generateRefreshToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpirationMs);

        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim("type", "refresh")
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ── Validation ──────────────────────────────────────────────────────────

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.debug("JWT token unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.debug("JWT token malformed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.debug("JWT token illegal argument: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("JWT token validation failed: {}", e.getMessage());
        }
        return false;
    }

    // ── Claim extraction ────────────────────────────────────────────────────

    public Long getUserIdFromToken(String token) {
        String subject = parseClaims(token).getSubject();
        return Long.parseLong(subject);
    }

    public String getRoleFromToken(String token) {
        return (String) parseClaims(token).get("role");
    }

    /**
     * Returns the "username" claim from an access token.
     * For refresh tokens, use {@link #getSubjectFromToken(String)} instead.
     */
    public String getUsernameFromToken(String token) {
        return (String) parseClaims(token).get("username");
    }

    /**
     * Returns the JWT subject claim.
     * Access tokens: subject = userId (Long as string).
     * Refresh tokens: subject = username.
     */
    public String getSubjectFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Returns the remaining lifetime of the token in milliseconds.
     * Used to set Redis TTL when blacklisting on logout.
     */
    public long getRemainingTtlMs(String token) {
        Date expiration = parseClaims(token).getExpiration();
        long remaining = expiration.getTime() - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }

    /**
     * Returns the issuedAt timestamp in milliseconds.
     * Used to check if a token was issued before a per-user invalidation event.
     */
    public long getIssuedAtFromToken(String token) {
        return parseClaims(token).getIssuedAt().getTime();
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(jwtSecret.getBytes())
        );
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
