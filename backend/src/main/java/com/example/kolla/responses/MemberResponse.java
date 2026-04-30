package com.example.kolla.responses;

import com.example.kolla.enums.Role;
import com.example.kolla.models.Member;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for Member data.
 * Requirements: 3.9
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemberResponse {

    private Long id;
    private Long meetingId;
    private Long userId;
    private String username;
    private String fullName;
    private Role role;
    private LocalDateTime addedAt;

    /** Convenience factory from entity. */
    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .meetingId(member.getMeeting().getId())
                .userId(member.getUser().getId())
                .username(member.getUser().getUsername())
                .fullName(member.getUser().getFullName())
                .role(member.getUser().getRole())
                .addedAt(member.getAddedAt())
                .build();
    }
}
