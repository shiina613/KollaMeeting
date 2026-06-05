package com.example.kolla.config;

import com.example.kolla.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * WebSocket configuration: STOMP over SockJS.
 *
 * <p>Endpoint: {@code /ws} (SockJS fallback enabled)
 *
 * <p>Subscribe topics:
 * <ul>
 *   <li>{@code /topic/meeting/{meetingId}} — broadcast meeting events to all participants</li>
 *   <li>{@code /user/queue/notifications} — personal notifications per user</li>
 *   <li>{@code /user/queue/errors} — personal error messages per user</li>
 * </ul>
 *
 * <p>Send destinations (client → server):
 * <ul>
 *   <li>{@code /app/meeting/{meetingId}/raise-hand}</li>
 *   <li>{@code /app/meeting/{meetingId}/lower-hand}</li>
 *   <li>{@code /app/meeting/{meetingId}/heartbeat}</li>
 * </ul>
 *
 * <p>JWT authentication is enforced on CONNECT frames via an inbound channel interceptor.
 * Requirements: 10.1
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;

    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3000}")
    private String corsAllowedOrigins;

    // ── Endpoint registration ───────────────────────────────────────────────

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        List<String> origins = Arrays.asList(corsAllowedOrigins.split(","));
        String[] originPatterns = origins.contains("*")
                ? new String[]{"*"}
                : origins.toArray(new String[0]);

        // Native WebSocket endpoint (no SockJS) — used by frontend in LAN/tunnel mode
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(originPatterns);

        // SockJS fallback endpoint — for browsers that don't support native WebSocket
        registry.addEndpoint("/ws-sockjs")
                .setAllowedOriginPatterns(originPatterns)
                .withSockJS();
    }

    // ── Message broker ──────────────────────────────────────────────────────

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory broker for topic and user-specific queues
        registry.enableSimpleBroker("/topic", "/queue");

        // Prefix for messages routed to @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");

        // Prefix for user-specific destinations (e.g. /user/queue/notifications)
        registry.setUserDestinationPrefix("/user");
    }

    // ── JWT authentication on CONNECT ───────────────────────────────────────

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = extractToken(accessor);
                    if (token == null || !jwtUtils.validateToken(token)) {
                        log.warn("WebSocket CONNECT rejected: missing or invalid JWT token");
                        throw new MessageDeliveryException(
                                "Authentication required: invalid or missing JWT token");
                    }

                    // Check token blacklist (revoked tokens) — fail-closed on Redis errors
                    if (isTokenBlacklisted(token)) {
                        log.warn("WebSocket CONNECT rejected: token is blacklisted (revoked)");
                        throw new MessageDeliveryException(
                                "Authentication required: token has been revoked");
                    }

                    try {
                        Long userId = jwtUtils.getUserIdFromToken(token);

                        // Check per-user token invalidation (e.g. after password reset)
                        if (isUserTokenInvalidated(userId, token)) {
                            log.warn("WebSocket CONNECT rejected: user token invalidated, userId={}", userId);
                            throw new MessageDeliveryException(
                                    "Authentication required: token has been invalidated");
                        }

                        String role = jwtUtils.getRoleFromToken(token);

                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                        userId.toString(),
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));

                        accessor.setUser(auth);
                        log.debug("WebSocket CONNECT authenticated: userId={}", userId);
                    } catch (MessageDeliveryException e) {
                        throw e; // re-throw our own exceptions
                    } catch (Exception e) {
                        log.warn("WebSocket CONNECT JWT extraction failed: {}", e.getMessage());
                        throw new MessageDeliveryException("Authentication failed");
                    }
                }

                return message;
            }
        });
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Extract Bearer token from STOMP CONNECT headers.
     * Clients should send: {@code Authorization: Bearer <token>}
     */
    private String extractToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        // Fallback: token passed as a native header named "token"
        return accessor.getFirstNativeHeader("token");
    }

    /**
     * Checks if a token is blacklisted in Redis.
     * Fail-closed: if Redis is unavailable, treats the token as blacklisted
     * to prevent revoked tokens from being accepted.
     */
    private boolean isTokenBlacklisted(String token) {
        try {
            Boolean exists = redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + token);
            return Boolean.TRUE.equals(exists);
        } catch (RedisConnectionFailureException e) {
            log.error("Redis unavailable during WebSocket blacklist check, rejecting token: {}", e.getMessage());
            return true; // Fail-closed
        } catch (Exception e) {
            log.error("Redis blacklist check failed during WebSocket CONNECT: {}", e.getMessage());
            return true; // Fail-closed
        }
    }

    /**
     * Checks if a user's tokens have been invalidated (e.g. after role change or password reset).
     * Fail-closed: if Redis is unavailable, assumes token is invalidated.
     */
    private boolean isUserTokenInvalidated(Long userId, String token) {
        try {
            String userBlacklistKey = "jwt:blacklist:user:" + userId;
            String invalidatedAt = redisTemplate.opsForValue().get(userBlacklistKey);
            if (invalidatedAt != null) {
                long issuedAt = jwtUtils.getIssuedAtFromToken(token);
                return issuedAt <= Long.parseLong(invalidatedAt);
            }
            return false;
        } catch (Exception e) {
            log.error("Redis user-invalidation check failed for userId={} during WebSocket CONNECT: {}",
                    userId, e.getMessage());
            return true; // Fail-closed
        }
    }
}
