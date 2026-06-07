package com.example.kolla.models;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantSession {
    private Long id;
    private Meeting meeting;
    private User user;
    private String sessionId;
    private LocalDateTime joinedAt;
    private LocalDateTime lastHeartbeatAt;

    @Builder.Default
    private boolean isConnected = true;
}
