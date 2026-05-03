package com.example.kolla.filter;

import com.example.kolla.models.User;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter — runs once per request.
 * Extracts Bearer token, validates it, checks Redis blacklist,
 * and sets the SecurityContext if valid.
 * Requirements: 2.6, 19.7
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            try {
                if (jwtUtils.validateToken(token) && !isBlacklisted(token)) {
                    Long userId = jwtUtils.getUserIdFromToken(token);

                    // Check per-user token invalidation (e.g. after role change or password reset)
                    String userBlacklistKey = "jwt:blacklist:user:" + userId;
                    String invalidatedAt = redisTemplate.opsForValue().get(userBlacklistKey);
                    if (invalidatedAt != null) {
                        long issuedAt = jwtUtils.getIssuedAtFromToken(token);
                        if (issuedAt <= Long.parseLong(invalidatedAt)) {
                            log.debug("Token for userId={} was issued before invalidation, rejecting", userId);
                            filterChain.doFilter(request, response);
                            return;
                        }
                    }

                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null && user.isActive()) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        user, null, user.getAuthorities());
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("Authenticated user id={} username={} role={}",
                                userId, user.getUsername(), user.getRole());
                    }
                }
            } catch (Exception e) {
                log.debug("Could not authenticate from JWT token: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private boolean isBlacklisted(String token) {
        try {
            Boolean exists = redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + token);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Redis blacklist check failed, allowing token: {}", e.getMessage());
            return false;
        }
    }
}
