package com.example.kolla.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a room.
 * Requirements: 12.1, 12.6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoomRequest {

    @Size(max = 255, message = "Room name must not exceed 255 characters")
    private String name;

    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    private Long departmentId;
}
