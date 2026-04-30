package com.example.kolla.services;

import com.example.kolla.dto.CreateRoomRequest;
import com.example.kolla.dto.UpdateRoomRequest;
import com.example.kolla.responses.RoomAvailabilityResponse;
import com.example.kolla.responses.RoomResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Room management service interface.
 * Requirements: 12.1–12.8
 */
public interface RoomService {

    List<RoomResponse> listRooms();

    List<RoomResponse> listRoomsByDepartment(Long departmentId);

    RoomResponse getRoomById(Long id);

    RoomResponse createRoom(CreateRoomRequest request);

    RoomResponse updateRoom(Long id, UpdateRoomRequest request);

    void deleteRoom(Long id);

    /**
     * Check room availability for a given time range.
     * Returns booked slots that overlap with the requested period.
     * Requirements: 12.8
     */
    RoomAvailabilityResponse checkAvailability(Long roomId, LocalDateTime startTime, LocalDateTime endTime);
}
