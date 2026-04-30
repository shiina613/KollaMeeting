package com.example.kolla.config;

import com.example.kolla.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
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

    private final JwtUtils jwtUtils;

    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3000}")
    private String corsAllowedOrigins;

    // ── Endpoint registration ───────────────────────────────────────────────

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = corsAllowedOrigins.split(",");
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins)
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
                    if (token != null && jwtUtils.validateToken(token)) {
                        try {
                            Long userId = jwtUtils.getUserIdFromToken(token);
                            String role = jwtUtils.getRoleFromToken(token);

                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(
                                            userId.toString(),
                                            null,
                                            List.of(new SimpleGrantedAuthority("ROLE_" + role)));

                            accessor.setUser(auth);
                            log.debug("WebSocket CONNECT authenticated: userId={}", userId);
                        } catch (Exception e) {
                            log.warn("WebSocket CONNECT JWT extraction failed: {}", e.getMessage());
                        }
                    } else {
                        log.warn("WebSocket CONNECT with missing or invalid JWT token");
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
}
