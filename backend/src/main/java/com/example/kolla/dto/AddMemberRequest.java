package com.example.kolla.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for adding a member to a meeting.
 * Requirements: 3.9
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberRequest {

    @NotNull(message = "User ID is required")
    private Long userId;
}
