package com.example.kolla.dto;

import com.example.kolla.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for updating an existing user.
 * All fields are optional Ã¢â‚¬â€ only non-null fields are applied.
 * Requirements: 11.1, 11.4, 11.6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    @Size(min = 3, max = 100, message = "Employee code must be between 3 and 100 characters")
    private String employeeCode;

    @Email(message = "Email must be a valid email address")
    private String email;

    /** Only ADMIN may change roles (enforced in service layer). */
    private Role role;

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
