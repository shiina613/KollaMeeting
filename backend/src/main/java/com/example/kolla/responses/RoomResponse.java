package com.example.kolla.responses;

import com.example.kolla.models.Room;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for Room data.
 * Requirements: 12.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomResponse {

    private Long id;
    private String name;
    private Integer capacity;
    private Long departmentId;
    private String departmentName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RoomResponse from(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .capacity(room.getCapacity())
                .departmentId(room.getDepartment() != null ? room.getDepartment().getId() : null)
                .departmentName(room.getDepartment() != null ? room.getDepartment().getName() : null)
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .build();
    }
}
