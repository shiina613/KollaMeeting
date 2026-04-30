package com.example.kolla.dto;

import com.example.kolla.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an existing user.
 * All fields are optional — only non-null fields are applied.
 * Requirements: 11.1, 11.4, 11.6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    @Email(message = "Email must be a valid email address")
    private String email;

    /** Only ADMIN may change roles (enforced in service layer). */
    private Role role;

    private Long departmentId;

    private Boolean isActive;
}
