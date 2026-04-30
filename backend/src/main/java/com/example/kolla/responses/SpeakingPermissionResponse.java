package com.example.kolla.responses;

import com.example.kolla.models.SpeakingPermission;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for SpeakingPermission data.
 * Requirements: 22.4, 22.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpeakingPermissionResponse {

    private Long id;
    private Long meetingId;
    private Long userId;
    private String userName;
    private String speakerTurnId;
    private LocalDateTime grantedAt;
    private LocalDateTime revokedAt;

    /** {@code true} if this permission is still active (not yet revoked). */
    private boolean active;

    public static SpeakingPermissionResponse from(SpeakingPermission sp) {
        return SpeakingPermissionResponse.builder()
                .id(sp.getId())
                .meetingId(sp.getMeeting().getId())
                .userId(sp.getUser().getId())
                .userName(sp.getUser().getFullName())
                .speakerTurnId(sp.getSpeakerTurnId())
                .grantedAt(sp.getGrantedAt())
                .revokedAt(sp.getRevokedAt())
                .active(sp.getRevokedAt() == null)
                .build();
    }
}
