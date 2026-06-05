package com.example.kolla.dto;

import jakarta.validation.constraints.Min;
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

    @Size(max = 100, message = "Room code must not exceed 100 characters")
    private String roomCode;

    @Size(max = 255, message = "Room name must not exceed 255 characters")
    private String name;

    @Size(max = 255, message = "Room name must not exceed 255 characters")
    private String roomName;

    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    @NotNull(message = "Department ID is required")
    private Long departmentId;
}
