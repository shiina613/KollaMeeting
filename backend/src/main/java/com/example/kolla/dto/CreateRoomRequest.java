package com.example.kolla.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a room.
 * Requirements: 12.1, 12.2, 12.6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {

    @NotBlank(message = "Room name is required")
    @Size(max = 255, message = "Room name must not exceed 255 characters")
    private String name;

    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    @NotNull(message = "Department ID is required")
    private Long departmentId;
}
