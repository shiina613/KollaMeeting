package com.example.kolla.config;

import com.example.kolla.websocket.AudioStreamHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the raw binary WebSocket endpoint for PCM audio streaming.
 *
 * <p>Endpoint: {@code /ws/audio?meetingId=X&speakerTurnId=Y&token=JWT}
 *
 * <p>This is a separate configuration from {@link WebSocketConfig} because
 * {@code WebSocketMessageBrokerConfigurer} does not expose
 * {@code registerWebSocketHandlers} — that interface is for STOMP only.
 *
 * Requirements: 8.14, 8.15
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class AudioWebSocketConfig implements WebSocketConfigurer {

    private final AudioStreamHandler audioStreamHandler;

    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3000}")
    private String corsAllowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = corsAllowedOrigins.split(",");
        registry.addHandler(audioStreamHandler, "/ws/audio")
                .setAllowedOrigins(origins);
    }
}
