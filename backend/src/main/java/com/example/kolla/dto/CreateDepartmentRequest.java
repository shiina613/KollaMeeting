package com.example.kolla.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a department.
 * Requirements: 12.1, 12.6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDepartmentRequest {

    @NotBlank(message = "Department name is required")
    @Size(max = 255, message = "Department name must not exceed 255 characters")
    private String name;

    private String description;
}
