package com.example.kolla.repositories;

import com.example.kolla.models.AttendanceLog;
import com.example.kolla.runtime.RuntimeMeetingStateStore;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendanceLogRepository {
    private final RuntimeMeetingStateStore store;

    public AttendanceLog save(AttendanceLog attendanceLog) {
        return store.saveAttendanceLog(attendanceLog);
    }

    public List<AttendanceLog> saveAll(List<AttendanceLog> attendanceLogs) {
        return store.saveAttendanceLogs(attendanceLogs);
    }

    public List<AttendanceLog> findByMeetingId(Long meetingId) {
        return store.findAttendanceByMeetingId(meetingId);
    }

    public List<AttendanceLog> findByMeetingIdAndUserId(Long meetingId, Long userId) {
        return store.findAttendanceByMeetingId(meetingId).stream()
                .filter(log -> log.getUser() != null && userId.equals(log.getUser().getId()))
                .toList();
    }

    public Optional<AttendanceLog> findOpenLog(Long meetingId, Long userId) {
        return store.findOpenAttendanceLog(meetingId, userId);
    }

    public List<AttendanceLog> findActiveParticipants(Long meetingId) {
        return store.findActiveAttendance(meetingId);
    }
}
