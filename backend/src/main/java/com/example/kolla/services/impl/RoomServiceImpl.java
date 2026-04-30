package com.example.kolla.services.impl;

import com.example.kolla.dto.CreateRoomRequest;
import com.example.kolla.dto.UpdateRoomRequest;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.Department;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Room;
import com.example.kolla.repositories.DepartmentRepository;
import com.example.kolla.repositories.RoomRepository;
import com.example.kolla.responses.RoomAvailabilityResponse;
import com.example.kolla.responses.RoomResponse;
import com.example.kolla.services.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RoomService implementation.
 * Requirements: 12.1–12.8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;

    private static final List<MeetingStatus> ACTIVE_STATUSES =
            List.of(MeetingStatus.SCHEDULED, MeetingStatus.ACTIVE);

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> listRooms() {
        return roomRepository.findAll().stream()
                .map(RoomResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> listRoomsByDepartment(Long departmentId) {
        return roomRepository.findByDepartmentId(departmentId).stream()
                .map(RoomResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoomResponse getRoomById(Long id) {
        return RoomResponse.from(findRoomOrThrow(id));
    }

    @Override
    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        Department department = findDepartmentOrThrow(request.getDepartmentId());

        Room room = Room.builder()
                .name(request.getName())
                .capacity(request.getCapacity())
                .department(department)
                .build();

        Room saved = roomRepository.save(room);
        log.info("Created room '{}' (id={}) in department '{}'",
                saved.getName(), saved.getId(), department.getName());
        return RoomResponse.from(saved);
    }

    @Override
    @Transactional
    public RoomResponse updateRoom(Long id, UpdateRoomRequest request) {
        Room room = findRoomOrThrow(id);

        if (request.getName() != null && !request.getName().isBlank()) {
            room.setName(request.getName());
        }
        if (request.getCapacity() != null) {
            room.setCapacity(request.getCapacity());
        }
        if (request.getDepartmentId() != null) {
            Department department = findDepartmentOrThrow(request.getDepartmentId());
            room.setDepartment(department);
        }

        Room saved = roomRepository.save(room);
        log.info("Updated room '{}' (id={})", saved.getName(), saved.getId());
        return RoomResponse.from(saved);
    }

    @Override
    @Transactional
    public void deleteRoom(Long id) {
        Room room = findRoomOrThrow(id);

        // Prevent deletion of rooms with scheduled or active meetings (Requirement 12.7)
        if (roomRepository.hasScheduledOrActiveMeetings(id, ACTIVE_STATUSES)) {
            throw new BadRequestException(
                    "Cannot delete room '" + room.getName()
                    + "': it has scheduled or active meetings");
        }

        roomRepository.delete(room);
        log.info("Deleted room '{}' (id={})", room.getName(), id);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomAvailabilityResponse checkAvailability(Long roomId,
                                                       LocalDateTime startTime,
                                                       LocalDateTime endTime) {
        Room room = findRoomOrThrow(roomId);

        if (startTime != null && endTime != null && !startTime.isBefore(endTime)) {
            throw new BadRequestException("startTime must be before endTime");
        }

        List<Meeting> overlapping = (startTime != null && endTime != null)
                ? roomRepository.findOverlappingMeetings(roomId, startTime, endTime, ACTIVE_STATUSES)
                : List.of();

        List<RoomAvailabilityResponse.BookedSlot> slots = overlapping.stream()
                .map(m -> RoomAvailabilityResponse.BookedSlot.builder()
                        .meetingId(m.getId())
                        .meetingTitle(m.getTitle())
                        .meetingCode(m.getCode())
                        .startTime(m.getStartTime())
                        .endTime(m.getEndTime())
                        .status(m.getStatus().name())
                        .build())
                .toList();

        return RoomAvailabilityResponse.builder()
                .roomId(roomId)
                .roomName(room.getName())
                .available(slots.isEmpty())
                .bookedSlots(slots)
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Room findRoomOrThrow(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + id));
    }

    private Department findDepartmentOrThrow(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));
    }
}
