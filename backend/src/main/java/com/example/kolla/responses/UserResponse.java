package com.example.kolla.responses;

import com.example.kolla.enums.Role;
import com.example.kolla.models.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for User data.
 * Never exposes passwordHash.
 * Requirements: 11.1, 11.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private Long id;
    private String username;
    private String fullName;
    private String email;
    private Role role;
    private Long departmentId;
    private String departmentName;
    @JsonProperty("isActive")
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Convenience factory from entity (no department name). */
    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .departmentId(user.getDepartmentId())
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /** Convenience factory from entity with resolved department name. */
    public static UserResponse from(User user, String departmentName) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .departmentId(user.getDepartmentId())
                .departmentName(departmentName)
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
