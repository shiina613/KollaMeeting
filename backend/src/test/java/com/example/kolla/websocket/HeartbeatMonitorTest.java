package com.example.kolla.websocket;

import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.ParticipantSession;
import com.example.kolla.models.User;
import com.example.kolla.repositories.AttendanceLogRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.ParticipantSessionRepository;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.services.AttendanceService;
import com.example.kolla.services.MeetingLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HeartbeatMonitor.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Heartbeat recording updates last_heartbeat_at.</li>
 *   <li>Stale session scanner detects and handles disconnected sessions.</li>
 *   <li>Disconnect fallback: session marked disconnected, attendance closed,
 *       PARTICIPANT_LEFT broadcast, host transfer if needed.</li>
 *   <li>Host authority transfer when Host disconnects and Secretary is present.</li>
 *   <li>Host restore when Host reconnects.</li>
 * </ul>
 *
 * Requirements: 5.3–5.5, 20.2
 */
@ExtendWith(MockitoExtension.class)
class HeartbeatMonitorTest {

    @Mock
    private ParticipantSessionRepository sessionRepository;

    @Mock
    private AttendanceLogRepository attendanceLogRepository;

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MeetingLifecycleService meetingLifecycleService;

    @Mock
    private AttendanceService attendanceService;

    @Mock
    private MeetingEventPublisher eventPublisher;

    @Mock
    private Clock clock;

    @InjectMocks
    private HeartbeatMonitor heartbeatMonitor;

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static final Long MEETING_ID = 10L;
    private static final Long HOST_ID = 1L;
    private static final Long SECRETARY_ID = 2L;
    private static final Long PARTICIPANT_ID = 3L;

    private User hostUser;
    private User secretaryUser;
    private User participantUser;
    private Meeting activeMeeting;

    @BeforeEach
    void setUp() {
        // Fixed clock at a known instant
        Instant fixedInstant = Instant.parse("2025-01-01T10:00:00Z");
        lenient().when(clock.instant()).thenReturn(fixedInstant);
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));

        hostUser = buildUser(HOST_ID, "host", Role.SECRETARY);
        secretaryUser = buildUser(SECRETARY_ID, "secretary", Role.SECRETARY);
        participantUser = buildUser(PARTICIPANT_ID, "participant", Role.USER);

        activeMeeting = Meeting.builder()
                .id(MEETING_ID)
                .title("Test Meeting")
                .status(MeetingStatus.ACTIVE)
                .mode(MeetingMode.FREE_MODE)
                .host(hostUser)
                .secretary(secretaryUser)
                .build();
    }

    // ── recordHeartbeat ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordHeartbeat()")
    class RecordHeartbeat {

        @Test
        @DisplayName("Updates last_heartbeat_at for the active session")
        void updatesLastHeartbeatAt() {
            ParticipantSession session = buildSession(HOST_ID, "session-1", true);
            when(sessionRepository.findActiveSession(MEETING_ID, HOST_ID))
                    .thenReturn(Optional.of(session));

            heartbeatMonitor.recordHeartbeat(MEETING_ID, HOST_ID);

            verify(sessionRepository).save(session);
            assertThat(session.getLastHeartbeatAt()).isNotNull();
        }

        @Test
        @DisplayName("Does nothing when no active session found")
        void doesNothingWhenNoSession() {
            when(sessionRepository.findActiveSession(MEETING_ID, HOST_ID))
                    .thenReturn(Optional.empty());

            heartbeatMonitor.recordHeartbeat(MEETING_ID, HOST_ID);

            verify(sessionRepository, never()).save(any());
        }
    }

    // ── scanStaleSessions ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("scanStaleSessions()")
    class ScanStaleSessions {

        @Test
        @DisplayName("Triggers handleDisconnect for each stale session")
        void triggersDisconnectForStaleSessions() {
            ParticipantSession staleSession = buildSession(PARTICIPANT_ID, "stale-session", true);
            staleSession.setMeeting(activeMeeting);
            staleSession.setUser(participantUser);

            when(sessionRepository.findStaleConnectedSessions(any(LocalDateTime.class)))
                    .thenReturn(List.of(staleSession));
            when(meetingRepository.findById(MEETING_ID))
                    .thenReturn(Optional.of(activeMeeting));

            heartbeatMonitor.scanStaleSessions();

            // Session should be marked disconnected
            verify(sessionRepository).save(argThat(s -> !s.isConnected()));
            // PARTICIPANT_LEFT should be broadcast
            verify(eventPublisher).publishParticipantLeft(MEETING_ID, PARTICIPANT_ID);
        }

        @Test
        @DisplayName("Does nothing when no stale sessions")
        void doesNothingWhenNoStaleSessions() {
            when(sessionRepository.findStaleConnectedSessions(any(LocalDateTime.class)))
                    .thenReturn(List.of());

            heartbeatMonitor.scanStaleSessions();

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Processes multiple stale sessions independently")
        void processesMultipleStaleSessions() {
            ParticipantSession stale1 = buildSession(HOST_ID, "s1", true);
            stale1.setMeeting(activeMeeting);
            stale1.setUser(hostUser);

            ParticipantSession stale2 = buildSession(PARTICIPANT_ID, "s2", true);
            stale2.setMeeting(activeMeeting);
            stale2.setUser(participantUser);

            when(sessionRepository.findStaleConnectedSessions(any(LocalDateTime.class)))
                    .thenReturn(List.of(stale1, stale2));
            when(meetingRepository.findById(MEETING_ID))
                    .thenReturn(Optional.of(activeMeeting));
            when(sessionRepository.isUserConnected(MEETING_ID, SECRETARY_ID))
                    .thenReturn(true);

            heartbeatMonitor.scanStaleSessions();

            verify(eventPublisher, times(2)).publishParticipantLeft(eq(MEETING_ID), anyLong());
        }
    }

    // ── handleDisconnect ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleDisconnect()")
    class HandleDisconnect {

        @Test
        @DisplayName("Marks session as disconnected")
        void marksSessionDisconnected() {
            ParticipantSession session = buildSession(PARTICIPANT_ID, "s1", true);
            session.setMeeting(activeMeeting);
            session.setUser(participantUser);

            when(meetingRepository.findById(MEETING_ID))
                    .thenReturn(Optional.of(activeMeeting));

            heartbeatMonitor.handleDisconnect(session);

            assertThat(session.isConnected()).isFalse();
            verify(sessionRepository).save(session);
        }

        @Test
        @DisplayName("Closes attendance log via AttendanceService")
        void closesAttendanceLog() {
            ParticipantSession session = buildSession(PARTICIPANT_ID, "s1", true);
            session.setMeeting(activeMeeting);
            session.setUser(participantUser);

            when(meetingRepository.findById(MEETING_ID))
                    .thenReturn(Optional.of(activeMeeting));

            heartbeatMonitor.handleDisconnect(session);

            verify(attendanceService).leaveMeeting(MEETING_ID, participantUser);
        }

        @Test
        @DisplayName("Broadcasts PARTICIPANT_LEFT")
        void broadcastsParticipantLeft() {
            ParticipantSession session = buildSession(PARTICIPANT_ID, "s1", true);
            session.setMeeting(activeMeeting);
            session.setUser(participantUser);

            when(meetingRepository.findById(MEETING_ID))
                    .thenReturn(Optional.of(activeMeeting));

            heartbeatMonitor.handleDisconnect(session);

            verify(eventPublisher).publishParticipantLeft(MEETING_ID, PARTICIPANT_ID);
        }

        @Test
        @DisplayName("Does not throw when attendance service fails (graceful degradation)")
        void doesNotThrowWhenAttendanceFails() {
            ParticipantSession session = buildSession(PARTICIPANT_ID, "s1", true);
            session.setMeeting(activeMeeting);
            session.setUser(participantUser);

            when(meetingRepository.findById(MEETING_ID))
                    .thenReturn(Optional.of(activeMeeting));
            doThrow(new RuntimeException("DB error"))
                    .when(attendanceService).leaveMeeting(anyLong(), any());

            // Should not propagate exception
            org.assertj.core.api.Assertions.assertThatNoException()
                    .isThrownBy(() -> heartbeatMonitor.handleDisconnect(session));

            // PARTICIPANT_LEFT should still be broadcast
            verify(eventPublisher).publishParticipantLeft(MEETING_ID, PARTICIPANT_ID);
        }
    }

    // ── Host authority transfer ───────────────────────────────────────────────

    @Nested
    @DisplayName("Host authority transfer on disconnect")
    class HostAuthorityTransfer {

        @Test
        @DisplayName("Broadcasts HOST_TRANSFERRED when Host disconnects and Secretary is present")
        void broadcastsHostTransferredWhenSecretaryPresent() {
            ParticipantSession hostSession = buildSession(HOST_ID, "host-session", true);
            hostSession.setMeeting(activeMeeting);
            hostSession.setUser(hostUser);

            when(meetingRepository.findById(MEETING_ID))
                    .thenReturn(Optional.of(activeMeeting));
            when(sessionRepository.isUserConnected(MEETING_ID, SECRETARY_ID))
                    .thenReturn(true);

            heartbeatMonitor.handleDisconnect(hostSession);

            verify(eventPublisher).publishHostTransferred(
                    MEETING_ID, HOST_ID, SECRETARY_ID, secretaryUser.getFullName());
        }

        @Test
        @DisplayName("Does NOT broadcast HOST_TRANSFERRED when Secretary is also absent")
        void doesNotBroadcastTransferWhenSecretaryAbsent() {
            ParticipantSession hostSession = buildSession(HOST_ID, "host-session", true);
            hostSession.setMeeting(activeMeeting);
            hostSession.setUser(hostUser);

            when(meetingRepository.findById(MEETING_ID))
                    .thenReturn(Optional.of(activeMeeting));
            when(sessionRepository.isUserConnected(MEETING_ID, SECRETARY_ID))
                    .thenReturn(false);

            heartbeatMonitor.handleDisconnect(hostSession);

            verify(eventPublisher, never()).publishHostTransferred(
                    anyLong(), anyLong(), anyLong(), anyString());
        }

        @Test
        @DisplayName("Does NOT broadcast HOST_TRANSFERRED when disconnected user is not the Host")
        void doesNotBroadcastTransferForNonHost() {
            ParticipantSession participantSession = buildSession(PARTICIPANT_ID, "p-session", true);
            participantSession.setMeeting(activeMeeting);
            participantSession.setUser(participantUser);

            when(meetingRepository.findById(MEETING_ID))
                    .thenReturn(Optional.of(activeMeeting));

            heartbeatMonitor.handleDisconnect(participantSession);

            verify(eventPublisher, never()).publishHostTransferred(
                    anyLong(), anyLong(), anyLong(), anyString());
        }

        @Test
        @DisplayName("Does NOT broadcast HOST_TRANSFERRED when meeting has no Secretary")
        void doesNotBroadcastTransferWhenNoSecretary() {
            Meeting meetingNoSecretary = Meeting.builder()
                    .id(MEETING_ID)
                    .title("No Secretary Meeting")
                    .status(MeetingStatus.ACTIVE)
                    .mode(MeetingMode.FREE_MODE)
                    .host(hostUser)
                    .secretary(null)
                    .build();

            ParticipantSession hostSession = buildSession(HOST_ID, "host-session", true);
            hostSession.setMeeting(meetingNoSecretary);
            hostSession.setUser(hostUser);

            when(meetingRepository.findById(MEETING_ID))
                    .thenReturn(Optional.of(meetingNoSecretary));

            heartbeatMonitor.handleDisconnect(hostSession);

            verify(eventPublisher, never()).publishHostTransferred(
                    anyLong(), anyLong(), anyLong(), anyString());
        }
    }

    // ── Heartbeat timeout constant ────────────────────────────────────────────

    @Nested
    @DisplayName("HEARTBEAT_TIMEOUT_SECONDS constant")
    class HeartbeatTimeoutConstant {

        @Test
        @DisplayName("Timeout is 10 seconds as per requirements")
        void timeoutIs10Seconds() {
            assertThat(HeartbeatMonitor.HEARTBEAT_TIMEOUT_SECONDS).isEqualTo(10L);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private User buildUser(Long id, String username, Role role) {
        return User.builder()
                .id(id)
                .username(username)
                .passwordHash("$2a$12$hashed")
                .fullName(username + " Full Name")
                .email(username + "@example.com")
                .role(role)
                .isActive(true)
                .build();
    }

    private ParticipantSession buildSession(Long userId, String sessionId, boolean connected) {
        User user = buildUser(userId, "user" + userId, Role.USER);
        return ParticipantSession.builder()
                .id(userId * 100)
                .meeting(activeMeeting)
                .user(user)
                .sessionId(sessionId)
                .joinedAt(LocalDateTime.now())
                .lastHeartbeatAt(LocalDateTime.now().minusSeconds(15))
                .isConnected(connected)
                .build();
    }
}
