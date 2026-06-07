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
public class SpeakingPermission {
    private Long id;
    private Meeting meeting;
    private User user;
    private LocalDateTime grantedAt;
    private LocalDateTime revokedAt;
    private String speakerTurnId;
}
