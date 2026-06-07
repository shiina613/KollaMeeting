package com.example.kolla.dto;

import com.example.kolla.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for creating a new user.
 * Requirements: 11.1, 11.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters")
    private String username;

    @Size(min = 3, max = 100, message = "Employee code must be between 3 and 100 characters")
    private String employeeCode;

    @jakarta.validation.constraints.NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @jakarta.validation.constraints.NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    @Email(message = "Email must be a valid email address")
    private String email;

    @NotNull(message = "Role is required")
    private Role role;

    @NotNull(message = "Department ID is required")
    private Long departmentId;

    private LocalDate dob;

    @Size(max = 30, message = "Phone number must not exceed 30 characters")
    private String phoneNumber;

    @Size(max = 255, message = "Degree must not exceed 255 characters")
    private String degree;

    @Size(max = 100, message = "Identification must not exceed 100 characters")
    private String identification;

    @Size(max = 1000, message = "Address must not exceed 1000 characters")
    private String address;

    @Size(max = 255, message = "Bank name must not exceed 255 characters")
    private String bankName;

    @Size(max = 100, message = "Bank number must not exceed 100 characters")
    private String bankNumber;

    @Size(max = 1000, message = "Image path must not exceed 1000 characters")
    private String img;
}
