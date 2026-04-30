package com.example.kolla.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for admin-initiated password reset.
 * If newPassword is omitted, a random temporary password is generated.
 * Requirements: 11.8, 11.9, 11.10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    /**
     * Optional explicit new password.
     * If null or blank, the service generates a random temporary password.
     */
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;
}
