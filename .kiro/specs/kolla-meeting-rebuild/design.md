# Kolla Meeting Rebuild - Design Document

## Overview

Kolla Meeting Rebuild là dự án tái xây dựng toàn bộ hệ thống họp trực tuyến Kolla với tech stack hiện đại. Hệ thống hỗ trợ 2–30 người tham gia mỗi cuộc họp, xử lý 5–7 cuộc họp đồng thời, và cung cấp khả năng ghi âm, phiên âm tự động bằng AI cho tiếng Việt.

### Mục tiêu thiết kế

- **Thay thế Angular 16 → React 18 + Vite + Tailwind CSS**: Cải thiện DX, build time, và bundle size.
- **Thay thế Google Drive → Local Filesystem**: Loại bỏ phụ thuộc external service, giảm latency I/O.
- **Thay thế Mediasoup → Jitsi Meet self-hosted**: Đơn giản hóa video conferencing, tận dụng Jitsi IFrame API.
- **Tích hợp Gipformer (Python FastAPI)**: Vietnamese ASR với ONNX inference, RTF < 1.
- **Priority-based transcription queue (Redis Sorted Set)**: Đảm bảo cuộc họp HIGH_PRIORITY nhận phiên âm gần thời gian thực.
- **Giữ nguyên Spring Boot 3.2 + MySQL**: Tận dụng codebase backend hiện có.

### Các tính năng đặc biệt

1. **Two meeting modes**: Free_Mode (tất cả mic đồng thời) và Meeting_Mode (raise-hand, 1 người nói tại một thời điểm).
2. **Raise Hand + Speaking_Permission**: Cơ chế xin phát biểu có thứ tự, Host cấp quyền.
3. **Adaptive VAD chunking**: Cắt audio tại điểm im lặng tự nhiên, hard cap 30s.
4. **Audio pipeline**: `getUserMedia()` → raw PCM → WebSocket → Spring Boot → Gipformer → WAV 16kHz → ONNX inference.
5. **Meeting lifecycle**: SCHEDULED → ACTIVE → ENDED với Host/Secretary fallback tự động.
6. **Minutes workflow**: Auto PDF → Host confirm (digital stamp) → Secretary edit → publish.

## Architecture

### System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          CLIENT LAYER                                    │
│                                                                          │
│   ┌──────────────────────────────────────────────────────────────────┐  │
│   │          React 18 + Vite + Tailwind CSS (Frontend App)           │  │
│   │                                                                  │  │
│   │  ┌─────────────┐  ┌──────────────┐  ┌────────────────────────┐  │  │
│   │  │ Jitsi IFrame │  │ Web Audio API│  │  WebSocket Client      │  │  │
│   │  │ (video conf) │  │ getUserMedia │  │  (STOMP over SockJS)   │  │  │
│   │  └─────────────┘  └──────────────┘  └────────────────────────┘  │  │
│   └──────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
         │ HTTPS REST          │ WSS (STOMP)         │ Jitsi IFrame API
         ▼                     ▼                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         SERVER LAYER                                     │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │              Spring Boot 3.2 (Backend API)                        │   │
│  │                                                                  │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │   │
│  │  │ REST Controllers│  │ WebSocket    │  │  Audio Stream        │   │   │
│  │  │ (Spring MVC) │  │ (STOMP/SockJS)│  │  Handler             │   │   │
│  │  └──────────────┘  └──────────────┘  └──────────────────────┘   │   │
│  │                                                                  │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │   │
│  │  │ Spring Security│  │ JPA/Hibernate│  │  Redis Client        │   │   │
│  │  │ (JWT Auth)   │  │ (MySQL)      │  │  (Sorted Set Queue)  │   │   │
│  │  └──────────────┘  └──────────────┘  └──────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐  │
│  │   MySQL Database  │  │  Local Filesystem │  │  Redis (Queue)       │  │
│  │   (port 3306)    │  │  (file storage)  │  │  (port 6379)         │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────────┘  │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │              Gipformer Service (Python FastAPI)                   │   │
│  │                                                                  │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │   │
│  │  │ /transcribe  │  │ /health      │  │  /jobs               │   │   │
│  │  │ POST endpoint│  │ GET endpoint │  │  POST endpoint       │   │   │
│  │  └──────────────┘  └──────────────┘  └──────────────────────┘   │   │
│  │                                                                  │   │
│  │  ┌──────────────────────────────────────────────────────────┐   │   │
│  │  │  sherpa-onnx (gipformer-65M-rnnt, ONNX int8)             │   │   │
│  │  │  VAD Engine + Audio Chunker + Redis Queue Poller         │   │   │
│  │  └──────────────────────────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │              Jitsi Meet Server (self-hosted)                      │   │
│  │  Prosody XMPP + Jicofo + JVB (Jitsi Videobridge)                │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

### Technology Decisions

| Component | Old | New | Rationale |
|-----------|-----|-----|-----------|
| Frontend framework | Angular 16 | React 18 + Vite | Smaller bundle, faster HMR, larger ecosystem, simpler mental model |
| CSS framework | Angular Material | Tailwind CSS | Utility-first, no runtime overhead, consistent với design system |
| Build tool | Angular CLI (webpack) | Vite | 10–100x faster dev server, native ESM |
| Video conferencing | Mediasoup (SFU) | Jitsi Meet self-hosted | Không cần quản lý SFU infrastructure, Jitsi IFrame API đơn giản hơn |
| File storage | Google Drive API | Local Filesystem | Loại bỏ external dependency, giảm latency, không giới hạn quota |
| ASR service | N/A | Gipformer (FastAPI + sherpa-onnx) | State-of-the-art Vietnamese ASR, RTF < 1, ONNX CPU-compatible |
| Job queue | N/A | Redis Sorted Set | Priority-based ordering, atomic operations, low latency |
| State management | RxJS/NgRx | Zustand | Lightweight, minimal boilerplate, React-native |
| WebSocket | Socket.IO | Spring WebSocket (STOMP/SockJS) | Tích hợp native với Spring Security, không cần thêm server |

### Deployment Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Docker Compose                        │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  frontend    │  │  backend     │  │  gipformer   │  │
│  │  :3000       │  │  :8080       │  │  :8000       │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  mysql       │  │  redis       │  │  jitsi       │  │
│  │  :3306       │  │  :6379       │  │  :8443       │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  shared volume: /app/storage                     │   │
│  │  (recordings, documents, audio_chunks, minutes)  │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### Frontend Components

```
src/
├── main.tsx
├── App.tsx
├── router/
│   └── AppRouter.tsx              # React Router v6 routes
├── store/
│   ├── authStore.ts               # Zustand: JWT, user info
│   ├── meetingStore.ts            # Zustand: active meeting state, mode, participants
│   └── notificationStore.ts      # Zustand: notification list
├── hooks/
│   ├── useWebSocket.ts            # STOMP/SockJS connection management
│   ├── useAudioCapture.ts         # getUserMedia + PCM streaming
│   ├── useJitsiApi.ts             # Jitsi IFrame API wrapper
│   └── useTranscription.ts       # Real-time transcription buffer + display
├── services/
│   ├── api.ts                     # Axios instance with JWT interceptor
│   ├── meetingService.ts
│   ├── userService.ts
│   ├── recordingService.ts
│   └── transcriptionService.ts
├── components/
│   ├── layout/
│   │   ├── Sidebar.tsx
│   │   ├── Header.tsx
│   │   └── NotificationPanel.tsx
│   ├── meeting/
│   │   ├── MeetingRoom.tsx        # Main meeting page container
│   │   ├── JitsiFrame.tsx         # Jitsi IFrame embed
│   │   ├── RaiseHandPanel.tsx     # Raise hand queue (Host view)
│   │   ├── TranscriptionPanel.tsx # Live transcription display
│   │   ├── MeetingModeToggle.tsx  # Free/Meeting mode switch
│   │   ├── ParticipantList.tsx    # Real-time participant list
│   │   └── SpeakingPermissionBadge.tsx
│   ├── minutes/
│   │   ├── MinutesEditor.tsx      # Rich text editor (Secretary)
│   │   ├── MinutesViewer.tsx      # PDF viewer
│   │   └── MinutesConfirmDialog.tsx
│   ├── admin/
│   │   ├── UserManagement.tsx
│   │   ├── StorageDashboard.tsx
│   │   └── BulkDeleteDialog.tsx
│   └── common/
│       ├── ConfirmDialog.tsx
│       ├── Pagination.tsx
│       └── StatusBadge.tsx
└── pages/
    ├── LoginPage.tsx
    ├── DashboardPage.tsx
    ├── MeetingListPage.tsx
    ├── MeetingDetailPage.tsx
    ├── MeetingRoomPage.tsx
    ├── RecordingListPage.tsx
    └── AdminPage.tsx
```

### Backend Package Structure

```
com.example.kolla/
├── config/
│   ├── SecurityConfig.java        # Spring Security + JWT filter chain
│   ├── WebSocketConfig.java       # STOMP + SockJS endpoint config
│   ├── RedisConfig.java           # Redis connection + Sorted Set ops
│   └── FileStorageConfig.java     # Local filesystem paths
├── controllers/
│   ├── AuthController.java
│   ├── MeetingController.java
│   ├── MeetingModeController.java # Mode switch, raise hand, speaking permission
│   ├── TranscriptionController.java # Gipformer callback receiver
│   ├── RecordingController.java
│   ├── DocumentController.java
│   ├── MinutesController.java
│   ├── UserController.java
│   ├── RoomController.java
│   ├── DepartmentController.java
│   ├── NotificationController.java
│   └── StorageController.java
├── websocket/
│   ├── AudioStreamHandler.java    # Binary WebSocket for PCM audio
│   ├── MeetingEventPublisher.java # STOMP broadcast helper
│   └── HeartbeatMonitor.java      # Detect disconnections
├── services/
│   ├── MeetingService.java
│   ├── MeetingModeService.java
│   ├── SpeakingPermissionService.java
│   ├── TranscriptionQueueService.java # Redis Sorted Set operations
│   ├── GipformerClient.java       # HTTP client to Gipformer FastAPI
│   ├── MinutesService.java        # PDF generation + workflow
│   ├── FileStorageService.java
│   ├── RecordingService.java
│   ├── NotificationService.java
│   └── MeetingLifecycleService.java
├── models/
│   ├── (existing entities + new ones below)
│   ├── SpeakingPermission.java
│   ├── RaiseHandRequest.java
│   ├── TranscriptionJob.java
│   ├── TranscriptionSegment.java
│   └── Minutes.java
├── enums/
│   ├── MeetingStatus.java         # SCHEDULED, ACTIVE, ENDED
│   ├── MeetingMode.java           # FREE_MODE, MEETING_MODE
│   ├── TranscriptionPriority.java # HIGH_PRIORITY, NORMAL_PRIORITY
│   ├── TranscriptionJobStatus.java # PENDING, PROCESSING, COMPLETED, FAILED
│   └── MinutesStatus.java         # DRAFT, HOST_CONFIRMED, SECRETARY_CONFIRMED
└── dto/ (request/response DTOs)
```

### Gipformer Service Structure

```
gipformer/
├── main.py                        # FastAPI app entry point
├── api/
│   ├── routes.py                  # /transcribe, /health, /jobs endpoints
│   └── schemas.py                 # Pydantic request/response models
├── core/
│   ├── recognizer.py              # sherpa-onnx recognizer singleton
│   ├── vad_chunker.py             # Adaptive VAD + hard cap logic
│   └── audio_converter.py        # PCM → WAV 16kHz mono conversion
├── queue/
│   ├── redis_queue.py             # Redis Sorted Set poll + push
│   └── worker.py                  # Background worker thread
├── callback/
│   └── backend_notifier.py       # HTTP POST result to Spring Boot
└── config.py                      # Environment variable config
```

### Key Interfaces

#### Frontend ↔ Backend REST
- Base URL: `http://localhost:8080/api/v1`
- Auth: `Authorization: Bearer <JWT>`
- Content-Type: `application/json`

#### Frontend ↔ Backend WebSocket
- Endpoint: `ws://localhost:8080/ws` (SockJS fallback)
- Protocol: STOMP
- Subscribe topics: `/topic/meeting/{meetingId}`, `/user/queue/notifications`
- Send destinations: `/app/meeting/{meetingId}/raise-hand`, `/app/meeting/{meetingId}/audio`

#### Frontend ↔ Jitsi
- Jitsi IFrame API loaded from `https://jitsi.kolla.local/external_api.js`
- Room name = `meetingCode`
- JWT token passed for authenticated rooms

#### Backend ↔ Gipformer
- Gipformer base URL: `http://gipformer:8000`
- Spring Boot → Gipformer: `POST /jobs` (submit job)
- Gipformer → Spring Boot: `POST /api/v1/transcription/callback` (result delivery)
- Health check: `GET /health`

#### Backend ↔ Redis
- Sorted Set key: `transcription:queue`
- Score: `priority_score` (HIGH = 1000 + timestamp_offset, NORMAL = 0 + timestamp_offset)
- Member: `job_id` (UUID)
- Job details stored in Redis Hash: `transcription:job:{job_id}`

## Data Models

### Database Schema (MySQL)

#### Existing Tables (Modified)

```sql
-- meeting: thêm các cột mới cho lifecycle và mode
ALTER TABLE meeting ADD COLUMN status ENUM('SCHEDULED','ACTIVE','ENDED') NOT NULL DEFAULT 'SCHEDULED';
ALTER TABLE meeting ADD COLUMN mode ENUM('FREE_MODE','MEETING_MODE') NOT NULL DEFAULT 'FREE_MODE';
ALTER TABLE meeting ADD COLUMN transcription_priority ENUM('HIGH_PRIORITY','NORMAL_PRIORITY') NOT NULL DEFAULT 'NORMAL_PRIORITY';
ALTER TABLE meeting ADD COLUMN host_user_id BIGINT NULL;  -- FK to user.id
ALTER TABLE meeting ADD COLUMN secretary_user_id BIGINT NULL;  -- FK to user.id
ALTER TABLE meeting ADD COLUMN activated_at DATETIME(6) NULL;
ALTER TABLE meeting ADD COLUMN ended_at DATETIME(6) NULL;
ALTER TABLE meeting ADD COLUMN waiting_timeout_at DATETIME(6) NULL;

-- recording: thêm cột status, loại bỏ file_content (LONGBLOB) → dùng file path
ALTER TABLE recording ADD COLUMN status ENUM('RECORDING','COMPLETED','FAILED') NOT NULL DEFAULT 'RECORDING';
ALTER TABLE recording ADD COLUMN file_path VARCHAR(500) NULL;
-- Giữ nguyên: url, start_time, end_time, created_by, meeting_id, file_name, file_size
-- Xóa: file_content (LONGBLOB) - không lưu binary trong DB nữa
```

#### New Tables

```sql
-- speaking_permission: ai đang giữ quyền phát biểu
CREATE TABLE speaking_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    granted_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    speaker_turn_id VARCHAR(36) NOT NULL,  -- UUID, mỗi lần grant = 1 turn mới
    CONSTRAINT fk_sp_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id),
    CONSTRAINT fk_sp_user FOREIGN KEY (user_id) REFERENCES user(id),
    INDEX idx_sp_meeting_active (meeting_id, revoked_at)
);

-- raise_hand_request: hàng đợi xin phát biểu
CREATE TABLE raise_hand_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    status ENUM('PENDING','GRANTED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'PENDING',
    resolved_at DATETIME(6) NULL,
    CONSTRAINT fk_rhr_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id),
    CONSTRAINT fk_rhr_user FOREIGN KEY (user_id) REFERENCES user(id),
    INDEX idx_rhr_meeting_pending (meeting_id, status, requested_at)
);

-- transcription_job: tracking các job phiên âm
CREATE TABLE transcription_job (
    id VARCHAR(36) PRIMARY KEY,  -- UUID (job_id)
    meeting_id BIGINT NOT NULL,
    speaker_id BIGINT NOT NULL,
    speaker_name VARCHAR(255) NOT NULL,
    speaker_turn_id VARCHAR(36) NOT NULL,
    sequence_number INT NOT NULL,
    priority ENUM('HIGH_PRIORITY','NORMAL_PRIORITY') NOT NULL,
    status ENUM('PENDING','QUEUED','PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
    audio_path VARCHAR(500) NULL,  -- path to WAV file in File_Storage
    retry_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    queued_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    error_message TEXT NULL,
    CONSTRAINT fk_tj_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id),
    INDEX idx_tj_meeting_status (meeting_id, status),
    INDEX idx_tj_speaker_turn (speaker_turn_id, sequence_number)
);

-- transcription_segment: kết quả phiên âm từng chunk
CREATE TABLE transcription_segment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    meeting_id BIGINT NOT NULL,
    speaker_id BIGINT NOT NULL,
    speaker_name VARCHAR(255) NOT NULL,
    speaker_turn_id VARCHAR(36) NOT NULL,
    sequence_number INT NOT NULL,
    text TEXT NOT NULL,
    confidence FLOAT NULL,
    processing_time_ms INT NULL,
    segment_start_time DATETIME(6) NOT NULL,  -- thời điểm bắt đầu chunk trong cuộc họp
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_ts_job FOREIGN KEY (job_id) REFERENCES transcription_job(id),
    CONSTRAINT fk_ts_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id),
    INDEX idx_ts_meeting_order (meeting_id, speaker_turn_id, sequence_number),
    UNIQUE KEY uk_ts_job (job_id)  -- idempotency: 1 job → 1 segment
);

-- minutes: biên bản cuộc họp
CREATE TABLE minutes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id BIGINT NOT NULL UNIQUE,
    status ENUM('DRAFT','HOST_CONFIRMED','SECRETARY_CONFIRMED') NOT NULL DEFAULT 'DRAFT',
    draft_pdf_path VARCHAR(500) NULL,
    confirmed_pdf_path VARCHAR(500) NULL,  -- Host-confirmed với digital stamp
    secretary_pdf_path VARCHAR(500) NULL,  -- Secretary-edited version
    content_html TEXT NULL,                -- Secretary's rich text content
    host_confirmed_at DATETIME(6) NULL,
    host_confirmation_hash VARCHAR(255) NULL,  -- hash(JWT + content)
    secretary_confirmed_at DATETIME(6) NULL,
    reminder_sent_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_min_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id)
);

-- participant_session: tracking ai đang online trong meeting (in-memory + DB)
CREATE TABLE participant_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(255) NOT NULL,  -- WebSocket session ID
    joined_at DATETIME(6) NOT NULL,
    last_heartbeat_at DATETIME(6) NOT NULL,
    is_connected TINYINT(1) NOT NULL DEFAULT 1,
    CONSTRAINT fk_ps_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id),
    CONSTRAINT fk_ps_user FOREIGN KEY (user_id) REFERENCES user(id),
    INDEX idx_ps_meeting_connected (meeting_id, is_connected)
);

-- storage_log: audit log cho bulk deletion
CREATE TABLE storage_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_user_id BIGINT NOT NULL,
    operation ENUM('BULK_DELETE','SINGLE_DELETE') NOT NULL,
    file_count INT NOT NULL,
    total_size_bytes BIGINT NOT NULL,
    description TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_sl_user FOREIGN KEY (admin_user_id) REFERENCES user(id)
);
```

### Entity Relationship Diagram

```
User ──────────────────────────────────────────────────────────────────────┐
 │ 1                                                                        │
 │ N                                                                        │
Member ──── Meeting ──── Room ──── Department                              │
              │                                                             │
              │ 1                                                           │
              ├── N ── AttendanceLog ──── User                             │
              ├── N ── Recording                                            │
              ├── N ── Document                                             │
              ├── N ── Notification ──── User (sender/receiver)            │
              ├── N ── TranscriptionJob ──── TranscriptionSegment          │
              ├── 1 ── Minutes                                              │
              ├── N ── SpeakingPermission ──── User                        │
              ├── N ── RaiseHandRequest ──── User                          │
              └── N ── ParticipantSession ──── User                        │
                                                                            │
Meeting.host_user_id ──────────────────────────────────────────────────────┘
Meeting.secretary_user_id ─────────────────────────────────────────────────┘
```

### Redis Data Structures

```
# Transcription Queue (Sorted Set)
Key: transcription:queue
Score: priority_score (HIGH = 1_000_000 - unix_ms, NORMAL = 0 - unix_ms)
  → Cao hơn = ưu tiên hơn; trong cùng priority, job cũ hơn có score cao hơn
Member: job_id (UUID string)

# Job Details (Hash)
Key: transcription:job:{job_id}
Fields:
  meeting_id, speaker_id, speaker_name, speaker_turn_id,
  sequence_number, priority, audio_path, callback_url,
  created_at, status

# Active High Priority Meeting (String)
Key: meeting:high_priority
Value: meeting_id
TTL: none (managed by application logic)

# Meeting Participant Count (String)
Key: meeting:{meeting_id}:participant_count
Value: integer
TTL: auto-expire after meeting ends

# Waiting Timeout (String with TTL)
Key: meeting:{meeting_id}:waiting_timeout
Value: "1"
TTL: 600 seconds (10 minutes)
```

### File Storage Structure

```
/app/storage/
├── recordings/
│   └── {meeting_id}/
│       └── {recording_id}_{filename}.mp4
├── documents/
│   └── {meeting_id}/
│       └── {document_id}_{filename}.pdf
├── audio_chunks/
│   └── {meeting_id}/
│       └── {speaker_turn_id}/
│           └── chunk_{sequence_number}_{job_id}.wav
└── minutes/
    └── {meeting_id}/
        ├── draft_{meeting_id}.pdf
        ├── confirmed_{meeting_id}.pdf
        └── secretary_{meeting_id}.pdf
```

## API Design

### REST Endpoints

#### Authentication

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/auth/login` | Login, trả về JWT | Public |
| POST | `/api/v1/auth/logout` | Invalidate session | JWT |
| POST | `/api/v1/auth/refresh` | Refresh JWT token | JWT |

#### Meeting Management

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/meetings` | List meetings (paginated, filterable) | JWT |
| POST | `/api/v1/meetings` | Create meeting | ADMIN/SECRETARY |
| GET | `/api/v1/meetings/{id}` | Get meeting detail | JWT |
| PUT | `/api/v1/meetings/{id}` | Update meeting | ADMIN/SECRETARY |
| DELETE | `/api/v1/meetings/{id}` | Delete meeting | ADMIN |
| POST | `/api/v1/meetings/{id}/activate` | SCHEDULED → ACTIVE | HOST |
| POST | `/api/v1/meetings/{id}/end` | Transition to ENDED | HOST/SECRETARY |
| GET | `/api/v1/meetings/{id}/members` | List members | JWT |
| POST | `/api/v1/meetings/{id}/members` | Add member | ADMIN/SECRETARY |
| DELETE | `/api/v1/meetings/{id}/members/{userId}` | Remove member | ADMIN/SECRETARY |
| POST | `/api/v1/meetings/{id}/join` | Join meeting (create attendance log) | JWT (member only) |
| POST | `/api/v1/meetings/{id}/leave` | Leave meeting | JWT |

#### Meeting Mode & Speaking Permission

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/meetings/{id}/mode` | Switch FREE_MODE ↔ MEETING_MODE | HOST |
| GET | `/api/v1/meetings/{id}/raise-hand` | List pending raise hand requests | HOST |
| POST | `/api/v1/meetings/{id}/raise-hand` | Submit raise hand request | PARTICIPANT |
| DELETE | `/api/v1/meetings/{id}/raise-hand` | Lower hand (cancel request) | PARTICIPANT |
| POST | `/api/v1/meetings/{id}/speaking-permission/{userId}` | Grant speaking permission | HOST |
| DELETE | `/api/v1/meetings/{id}/speaking-permission` | Revoke speaking permission | HOST |
| GET | `/api/v1/meetings/{id}/speaking-permission` | Get current speaker | JWT |

#### Transcription Priority

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| PUT | `/api/v1/meetings/{id}/priority` | Set HIGH/NORMAL priority | ADMIN/SECRETARY |
| GET | `/api/v1/meetings/{id}/priority` | Get current priority | JWT |

#### Transcription Callback (Internal)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/transcription/callback` | Receive result from Gipformer | Internal (API key) |
| GET | `/api/v1/meetings/{id}/transcription` | Get transcription segments | JWT |

#### Recordings

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/meetings/{id}/recordings` | List recordings | JWT |
| POST | `/api/v1/meetings/{id}/recordings/start` | Start recording | HOST/SECRETARY |
| POST | `/api/v1/meetings/{id}/recordings/{recId}/stop` | Stop recording | HOST/SECRETARY |
| GET | `/api/v1/recordings/{id}/download` | Download recording file | JWT |
| DELETE | `/api/v1/recordings/{id}` | Delete recording | ADMIN |

#### Documents

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/meetings/{id}/documents` | List documents | JWT |
| POST | `/api/v1/meetings/{id}/documents` | Upload document (multipart) | JWT (member) |
| GET | `/api/v1/documents/{id}/download` | Download document | JWT |
| DELETE | `/api/v1/documents/{id}` | Delete document | ADMIN/SECRETARY |

#### Minutes

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/meetings/{id}/minutes` | Get minutes | JWT (member) |
| POST | `/api/v1/meetings/{id}/minutes/confirm` | Host confirms minutes | HOST |
| PUT | `/api/v1/meetings/{id}/minutes/edit` | Secretary edits minutes | SECRETARY |
| GET | `/api/v1/meetings/{id}/minutes/download?version=draft\|confirmed\|secretary` | Download PDF | JWT (member) |

#### Users & Admin

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/users` | List users (paginated) | ADMIN |
| POST | `/api/v1/users` | Create user | ADMIN |
| GET | `/api/v1/users/{id}` | Get user | ADMIN/self |
| PUT | `/api/v1/users/{id}` | Update user | ADMIN/self |
| DELETE | `/api/v1/users/{id}` | Delete user | ADMIN |
| POST | `/api/v1/users/{id}/reset-password` | Reset password | ADMIN |
| GET | `/api/v1/users/me` | Get current user profile | JWT |

#### Rooms & Departments

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/rooms` | List rooms | JWT |
| POST | `/api/v1/rooms` | Create room | ADMIN |
| PUT | `/api/v1/rooms/{id}` | Update room | ADMIN |
| DELETE | `/api/v1/rooms/{id}` | Delete room | ADMIN |
| GET | `/api/v1/departments` | List departments | JWT |
| POST | `/api/v1/departments` | Create department | ADMIN |
| GET | `/api/v1/rooms/{id}/availability` | Get room availability slots | JWT |

#### Storage Management

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/storage/stats` | Get storage usage stats | ADMIN |
| POST | `/api/v1/storage/bulk-delete` | Bulk delete old recordings | ADMIN |

#### Notifications

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/notifications` | List notifications (paginated) | JWT |
| PUT | `/api/v1/notifications/{id}/read` | Mark as read | JWT |
| PUT | `/api/v1/notifications/read-all` | Mark all as read | JWT |

#### Search

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/search/meetings` | Search meetings | JWT |
| GET | `/api/v1/search/transcriptions` | Full-text search in transcriptions | JWT |

### WebSocket Events (STOMP)

#### Client → Server (send destinations)

```
/app/meeting/{meetingId}/raise-hand          # Submit raise hand
/app/meeting/{meetingId}/lower-hand          # Cancel raise hand
/app/meeting/{meetingId}/heartbeat           # Keep-alive ping
```

#### Server → Client (subscribe topics)

```
/topic/meeting/{meetingId}                   # All meeting events (broadcast)
/user/queue/notifications                    # Personal notifications
/user/queue/errors                           # Personal error messages
```

#### Meeting Event Payload Format

```json
{
  "type": "MEETING_EVENT_TYPE",
  "meetingId": 123,
  "timestamp": "2025-01-01T10:00:00+07:00",
  "payload": { ... }
}
```

#### Meeting Event Types

| Event Type | Trigger | Payload |
|------------|---------|---------|
| `MEETING_STARTED` | Host activates meeting | `{ meetingId, hostName }` |
| `MEETING_ENDED` | Meeting transitions to ENDED | `{ meetingId, reason }` |
| `MODE_CHANGED` | Host switches mode | `{ mode: "FREE_MODE"\|"MEETING_MODE" }` |
| `RAISE_HAND` | Participant raises hand | `{ userId, userName, requestedAt }` |
| `HAND_LOWERED` | Participant lowers hand | `{ userId }` |
| `SPEAKING_PERMISSION_GRANTED` | Host grants permission | `{ userId, userName, speakerTurnId }` |
| `SPEAKING_PERMISSION_REVOKED` | Permission revoked | `{ userId, reason }` |
| `PARTICIPANT_JOINED` | User joins meeting | `{ userId, userName }` |
| `PARTICIPANT_LEFT` | User leaves meeting | `{ userId }` |
| `HOST_TRANSFERRED` | Host authority transferred | `{ fromUserId, toUserId, toUserName }` |
| `HOST_RESTORED` | Host reconnects | `{ userId, userName }` |
| `WAITING_TIMEOUT_STARTED` | No host/secretary present | `{ expiresAt }` |
| `WAITING_TIMEOUT_CANCELLED` | Host/secretary reconnected | `{}` |
| `TRANSCRIPTION_SEGMENT` | New transcription result (HIGH_PRIORITY only) | `{ speakerId, speakerName, speakerTurnId, sequenceNumber, text, segmentStartTime }` |
| `PRIORITY_CHANGED` | Meeting priority changed | `{ priority: "HIGH_PRIORITY"\|"NORMAL_PRIORITY" }` |
| `TRANSCRIPTION_UNAVAILABLE` | Gipformer service down | `{ message }` |
| `TRANSCRIPTION_RECOVERED` | Gipformer service back | `{ pendingJobCount }` |
| `DOCUMENT_UPLOADED` | New document added | `{ documentId, fileName, uploadedBy }` |
| `MINUTES_READY` | Draft minutes created | `{ minutesId }` |
| `MINUTES_CONFIRMED` | Host confirmed minutes | `{ minutesId }` |
| `MINUTES_PUBLISHED` | Secretary confirmed minutes | `{ minutesId }` |

### Gipformer REST API

#### POST /jobs

```json
// Request
{
  "job_id": "uuid-v4",
  "meeting_id": 123,
  "speaker_id": 456,
  "speaker_name": "Nguyễn Văn A",
  "speaker_turn_id": "uuid-v4",
  "sequence_number": 3,
  "priority": "HIGH_PRIORITY",
  "audio_path": "/app/storage/audio_chunks/123/turn-uuid/chunk_3_job-uuid.wav",
  "callback_url": "http://backend:8080/api/v1/transcription/callback"
}

// Response 202 Accepted
{
  "job_id": "uuid-v4",
  "status": "QUEUED",
  "queue_position": 2
}
```

#### GET /health

```json
// Response 200
{
  "status": "ready",
  "model_loaded": true,
  "queue_depth": 5,
  "uptime_seconds": 3600
}
```

#### POST /transcribe (direct, synchronous)

```json
// Request: multipart/form-data with audio file (WAV 16kHz)
// Response 200
{
  "text": "xin chào tôi muốn phát biểu về vấn đề này",
  "processing_time_ms": 450,
  "rtf": 0.15
}
```

#### Callback to Spring Boot: POST /api/v1/transcription/callback

```json
{
  "job_id": "uuid-v4",
  "meeting_id": 123,
  "speaker_id": 456,
  "speaker_name": "Nguyễn Văn A",
  "speaker_turn_id": "uuid-v4",
  "sequence_number": 3,
  "text": "xin chào tôi muốn phát biểu về vấn đề này",
  "confidence": 0.92,
  "processing_time_ms": 450,
  "segment_start_time": "2025-01-01T10:05:30+07:00"
}
```

## Key Flows

### Flow 1: Audio Pipeline (Real-Time Transcription)

```
Participant (Browser)          Spring Boot              Gipformer Service
      │                            │                          │
      │ getUserMedia()             │                          │
      │ → AudioContext             │                          │
      │ → ScriptProcessorNode      │                          │
      │   (PCM Float32, 16kHz)     │                          │
      │                            │                          │
      │──── WS Binary Frame ──────►│                          │
      │     (raw PCM chunks)       │                          │
      │                            │ accumulate PCM buffer    │
      │                            │ convert to WAV 16kHz     │
      │                            │ save to audio_chunks/    │
      │                            │                          │
      │                            │──── POST /jobs ─────────►│
      │                            │     (job_id, audio_path) │
      │                            │                          │ poll Redis queue
      │                            │                          │ load WAV file
      │                            │                          │ sherpa-onnx inference
      │                            │                          │ (RTF < 1)
      │                            │                          │
      │                            │◄─── POST /callback ──────│
      │                            │     (job_id, text)       │
      │                            │                          │
      │                            │ save TranscriptionSegment│
      │                            │ (idempotency check)      │
      │                            │                          │
      │◄─── WS TRANSCRIPTION_SEGMENT (if HIGH_PRIORITY)       │
      │     broadcast to all       │                          │
      │     participants           │                          │
```

**Audio Capture Implementation (Frontend)**:
```javascript
// useAudioCapture.ts
const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
const audioContext = new AudioContext({ sampleRate: 16000 });
const source = audioContext.createMediaStreamSource(stream);
const processor = audioContext.createScriptProcessor(4096, 1, 1);

processor.onaudioprocess = (e) => {
  const pcmData = e.inputBuffer.getChannelData(0); // Float32Array
  const int16 = float32ToInt16(pcmData);           // convert to Int16
  wsClient.sendBinary(int16.buffer);               // send via WebSocket
};
source.connect(processor);
processor.connect(audioContext.destination);
```

### Flow 2: Adaptive VAD Chunking

```
Gipformer VAD Chunker (vad_chunker.py)

Audio stream arrives as PCM frames
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│  Accumulate audio buffer                                 │
│  Track: buffer_duration, last_speech_end_time           │
│                                                          │
│  VAD detection (WebRTC VAD or silero-vad)               │
│                                                          │
│  IF buffer_duration < 15s:                              │
│    silence_threshold = 2.0–3.0 seconds                  │
│  ELSE:                                                   │
│    silence_threshold = 0.5–1.0 seconds                  │
│                                                          │
│  IF silence_detected AND silence_duration >= threshold: │
│    → CUT CHUNK at silence point                         │
│    → push to Redis Queue                                 │
│    → reset buffer                                        │
│                                                          │
│  IF buffer_duration >= 30s (hard cap):                  │
│    → FORCE CUT immediately                              │
│    → push to Redis Queue                                 │
│    → reset buffer                                        │
│                                                          │
│  IF speaking_permission_revoked signal received:        │
│    → CUT at next VAD silence OR immediately             │
│    → push final chunk to Redis Queue                    │
└─────────────────────────────────────────────────────────┘
```

**Sequence Number Tracking**:
- Mỗi lần Host grant Speaking_Permission → tạo `speaker_turn_id` mới (UUID)
- `sequence_number` bắt đầu từ 1, tăng dần cho mỗi chunk trong cùng turn
- Khi assemble Minutes: sort by `(speaker_turn_id, sequence_number)`

### Flow 3: Raise Hand Mechanism

```
Participant                  Spring Boot                    Host (Browser)
    │                            │                               │
    │ POST /raise-hand           │                               │
    │ ──────────────────────────►│                               │
    │                            │ INSERT raise_hand_request     │
    │                            │ (status=PENDING)              │
    │                            │                               │
    │                            │──── WS RAISE_HAND ───────────►│
    │                            │     (userId, userName,        │
    │                            │      requestedAt)             │
    │                            │                               │
    │                            │     Host sees queue           │
    │                            │     (chronological order)     │
    │                            │                               │
    │                            │◄─── POST /speaking-permission │
    │                            │     /{userId}                 │
    │                            │                               │
    │                            │ SELECT FOR UPDATE             │
    │                            │ (prevent race condition)      │
    │                            │ revoke existing permission    │
    │                            │ INSERT speaking_permission    │
    │                            │ UPDATE raise_hand_request     │
    │                            │ (status=GRANTED)              │
    │                            │                               │
    │◄─── WS SPEAKING_PERMISSION_GRANTED (broadcast all) ───────│
    │     (userId, speakerTurnId)│                               │
    │                            │                               │
    │ Jitsi: unmute self         │                               │
    │ Others: Jitsi mute         │                               │
    │                            │                               │
    │ [speaking...]              │                               │
    │                            │                               │
    │ DELETE /raise-hand         │                               │
    │ (lower hand voluntarily)   │                               │
    │ ──────────────────────────►│                               │
    │                            │ finalize current chunk        │
    │                            │ push to Redis Queue           │
    │                            │ revoke speaking_permission    │
    │                            │                               │
    │◄─── WS SPEAKING_PERMISSION_REVOKED (broadcast all) ───────│
```

### Flow 4: Meeting Lifecycle

```
                    ┌─────────────┐
                    │  SCHEDULED  │
                    └──────┬──────┘
                           │ Host activates meeting room
                           │ POST /meetings/{id}/activate
                           ▼
                    ┌─────────────┐
                    │   ACTIVE    │◄──────────────────────────────┐
                    └──────┬──────┘                               │
                           │                                      │
              ┌────────────┼────────────────────────┐            │
              │            │                        │            │
              ▼            ▼                        ▼            │
    Host/Secretary    Both absent              Explicit end      │
    disconnects       (no host/sec)            by Host/Sec       │
              │            │                        │            │
              │            │ Start 10-min           │            │
              │            │ Waiting_Timeout         │            │
              │            │                        │            │
              │       ┌────┴────┐                   │            │
              │       │Reconnect│──────────────────────────────►─┘
              │       │ before  │  Cancel timeout,              
              │       │ timeout │  restore normal               
              │       └────┬────┘                               
              │            │ Timeout expires                    
              │            │ OR room empty 10min                
              │            ▼                        │            
              │     ┌─────────────┐                 │            
              └────►│    ENDED    │◄────────────────┘            
                    └─────────────┘
                           │
                           │ Auto-trigger Minutes workflow
                           ▼
                    Generate draft PDF
                    Notify Host to confirm
```

**Host/Secretary Fallback Logic**:
```
Host disconnects:
  → Transfer authority to Secretary
  → WS broadcast: HOST_TRANSFERRED

Host reconnects:
  → Restore authority from Secretary
  → WS broadcast: HOST_RESTORED

Both absent:
  → Start Redis TTL key: meeting:{id}:waiting_timeout (600s)
  → WS broadcast: WAITING_TIMEOUT_STARTED

Either reconnects:
  → Delete Redis TTL key
  → WS broadcast: WAITING_TIMEOUT_CANCELLED

TTL expires (Spring @Scheduled polls or Redis keyspace notification):
  → Transition meeting to ENDED
```

### Flow 5: Minutes Workflow

```
Meeting ENDED
    │
    ▼
Backend: compile all TranscriptionSegments
  → sort by (speaker_turn_id, sequence_number)
  → format: [timestamp] SpeakerName: text
  → generate PDF (Apache PDFBox)
  → save to /storage/minutes/{id}/draft_{id}.pdf
  → INSERT minutes (status=DRAFT)
  → WS notify Host: MINUTES_READY
    │
    ▼
Host reviews draft PDF
  → POST /meetings/{id}/minutes/confirm
    │
    ▼
Backend:
  → embed digital stamp in PDF:
      "Confirmed by: {hostName}"
      "Timestamp: {ISO8601}"
      "Hash: SHA256(JWT_token + PDF_content)"
  → save confirmed PDF
  → UPDATE minutes (status=HOST_CONFIRMED)
  → WS notify Secretary: MINUTES_CONFIRMED
    │
    ▼
Secretary reviews + edits
  → PUT /meetings/{id}/minutes/edit
    body: { contentHtml: "<p>...</p>" }
    │
    ▼
Backend:
  → render HTML → PDF (Apache PDFBox + jsoup)
  → save secretary PDF
  → UPDATE minutes (status=SECRETARY_CONFIRMED)
  → WS broadcast all members: MINUTES_PUBLISHED
    │
    ▼
All members can download:
  - Original draft PDF
  - Host-confirmed PDF (with digital stamp)
  - Secretary-edited PDF

If Host doesn't confirm within 24h:
  → @Scheduled job sends reminder notification
```

### Flow 6: Priority-Based Transcription Queue

```
Admin/Secretary sets HIGH_PRIORITY for Meeting A
    │
    ▼
Backend:
  1. Check Redis key: meeting:high_priority
  2. If exists (Meeting B is HIGH_PRIORITY):
     → Update Meeting B: priority = NORMAL_PRIORITY
     → WS notify Meeting B: PRIORITY_CHANGED (NORMAL)
     → Re-score Meeting B's pending jobs in Redis Sorted Set
       (score = 0 - unix_ms instead of 1_000_000 - unix_ms)
  3. Set Meeting A: priority = HIGH_PRIORITY
  4. Set Redis key: meeting:high_priority = meetingA_id
  5. Re-score Meeting A's pending jobs in Redis Sorted Set
  6. WS notify Meeting A: PRIORITY_CHANGED (HIGH)

Redis Sorted Set scoring:
  HIGH_PRIORITY job:   score = 1_000_000_000 - created_at_unix_ms
  NORMAL_PRIORITY job: score = created_at_unix_ms (inverted: older = higher)
  → ZREVRANGE returns highest score first
  → HIGH_PRIORITY jobs always processed before NORMAL_PRIORITY

Gipformer worker loop:
  while True:
    job_id = ZPOPMAX transcription:queue 1
    if job_id:
      job = HGETALL transcription:job:{job_id}
      process(job)
      POST callback to Spring Boot
    else:
      sleep(0.1)  # 100ms poll interval
```

### Flow 7: Gipformer Service Resilience

```
Spring Boot health check (startup):
  → Poll GET /health every 5s
  → Wait until status = "ready"
  → Only then start routing jobs

During operation:
  → Health check every 30s
  → If /health returns non-200 or timeout:
    → Set flag: gipformer_available = false
    → WS notify all active meeting Hosts: TRANSCRIPTION_UNAVAILABLE
    → New audio chunks: save to File_Storage + INSERT transcription_job (status=PENDING)
    → Do NOT push to Redis Queue

Recovery:
  → /health returns "ready" again
  → Set flag: gipformer_available = true
  → Query all PENDING transcription_jobs
  → Push to Redis Queue (respecting priority)
  → WS notify Hosts: TRANSCRIPTION_RECOVERED (pendingJobCount)

Retry logic:
  → If callback not received within 60s: retry push to Redis Queue
  → Max 3 retries
  → After 3 failures: UPDATE transcription_job (status=FAILED)
  → Log error
```

### Flow 8: Disconnection Handling

```
WebSocket heartbeat: client sends /app/meeting/{id}/heartbeat every 5s
Spring Boot: if no heartbeat for 10s → mark participant as disconnected

On disconnect detected:
  1. UPDATE participant_session (is_connected=false)
  2. UPDATE attendance_log (leave_at=now)
  3. If participant held Speaking_Permission:
     → Finalize current audio chunk (signal Gipformer)
     → Push partial chunk to Redis Queue
     → DELETE speaking_permission
     → WS broadcast: SPEAKING_PERMISSION_REVOKED (reason=DISCONNECTED)
  4. If participant was Host:
     → Transfer authority to Secretary
     → WS broadcast: HOST_TRANSFERRED
  5. If both Host and Secretary absent:
     → Start Waiting_Timeout (Redis TTL key, 600s)
     → WS broadcast: WAITING_TIMEOUT_STARTED

On reconnect:
  1. Client re-establishes WebSocket
  2. Client sends JOIN event
  3. Backend: UPDATE participant_session (is_connected=true)
  4. If reconnected user was Host:
     → Restore Host authority
     → Cancel Waiting_Timeout (delete Redis key)
     → WS broadcast: HOST_RESTORED
  5. WS broadcast: PARTICIPANT_JOINED
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Speaking Permission Exclusivity

*For any* active meeting in Meeting_Mode, after any sequence of grant and revoke operations, at most one participant SHALL hold Speaking_Permission at any given time.

**Validates: Requirements 22.4, 22.8**

---

### Property 2: Concurrent Grant Safety

*For any* active meeting in Meeting_Mode, when N concurrent requests to grant Speaking_Permission to different participants are submitted simultaneously, exactly one participant SHALL hold Speaking_Permission after all requests complete.

**Validates: Requirements 22.12**

---

### Property 3: Queue Priority Ordering

*For any* set of Transcription_Jobs containing both HIGH_PRIORITY and NORMAL_PRIORITY jobs, the Gipformer worker SHALL always dequeue and process a HIGH_PRIORITY job before any NORMAL_PRIORITY job, regardless of the order in which jobs were enqueued.

**Validates: Requirements 23.1, 8.10**

---

### Property 4: At Most One High Priority Meeting

*For any* sequence of priority assignment operations across any number of concurrent meetings, the count of meetings with HIGH_PRIORITY status SHALL never exceed one at any point in time.

**Validates: Requirements 23.2**

---

### Property 5: Adaptive VAD Threshold Function

*For any* audio buffer being processed by the Gipformer VAD chunker: if the buffer duration is less than 15 seconds, the silence threshold applied SHALL be in the range [2.0, 3.0] seconds; if the buffer duration is 15 seconds or more, the silence threshold applied SHALL be in the range [0.5, 1.0] seconds.

**Validates: Requirements 23.7, 23.8**

---

### Property 6: Sequence Number Monotonicity

*For any* speaker turn (identified by speaker_turn_id), the sequence numbers of all Audio_Chunks produced during that turn SHALL form a contiguous sequence starting from 1 and incrementing by 1 for each subsequent chunk, with no gaps or duplicates.

**Validates: Requirements 23.13**

---

### Property 7: Minutes Assembly Ordering

*For any* set of TranscriptionSegments belonging to a meeting, regardless of the order in which they arrive or are stored, assembling them into Minutes SHALL produce output ordered by (speaker_turn_id, sequence_number) in ascending order.

**Validates: Requirements 23.14**

---

### Property 8: Transcription Callback Idempotency

*For any* transcription callback with a given job_id, calling the `/api/v1/transcription/callback` endpoint N times with the same job_id SHALL result in exactly one TranscriptionSegment record in the database, and all calls after the first SHALL return 200 OK without creating duplicate records.

**Validates: Requirements 30.10**

---

### Property 9: Meeting Code Uniqueness

*For any* number of meetings created in the system, all meeting codes SHALL be globally unique — no two meetings SHALL share the same meeting code.

**Validates: Requirements 3.1**

---

### Property 10: Room Scheduling Conflict Detection

*For any* room and any two time ranges that overlap, attempting to create or update a meeting to occupy the same room during the overlapping period SHALL be rejected with a 409 Conflict response, provided another ACTIVE or SCHEDULED meeting already occupies that room during that time.

**Validates: Requirements 3.12**

---

### Property 11: Transcription Broadcast vs Persist Based on Priority

*For any* transcription result received by the Backend_API: if the associated meeting holds HIGH_PRIORITY status, the result SHALL be both broadcast to all participants via WebSocket AND persisted to the database; if the meeting holds NORMAL_PRIORITY status, the result SHALL be persisted to the database only, with no WebSocket broadcast.

**Validates: Requirements 8.12, 8.13**

---

### Property 12: Mode Switch Completeness

*For any* active meeting switching from Meeting_Mode to Free_Mode: if any participant currently holds Speaking_Permission, that permission SHALL be revoked before the mode transition completes, and all participants SHALL be notified of both the revocation and the mode change via WebSocket.

**Validates: Requirements 21.3, 21.9, 21.10**

---

### Property 13: Minutes Confirmation Stamp Completeness

*For any* host user confirming any meeting's minutes, the generated confirmed PDF SHALL contain: the host's full name, the confirmation timestamp, and a non-empty hash string derived from the JWT token and document content.

**Validates: Requirements 25.4**

---

### Property 14: Pending Job Requeue on Recovery

*For any* set of TranscriptionJobs with status PENDING in the database, when the Gipformer service transitions from unavailable to available, all PENDING jobs SHALL be pushed to the Redis Queue for processing, with their original priority scores preserved.

**Validates: Requirements 28.3**

## Error Handling

### Backend Error Response Format

```json
{
  "timestamp": "2025-01-01T10:00:00+07:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Meeting code already exists",
  "path": "/api/v1/meetings",
  "details": { }
}
```

### HTTP Status Code Conventions

| Status | Usage |
|--------|-------|
| 200 OK | Successful GET, PUT, DELETE |
| 201 Created | Successful POST (resource created) |
| 202 Accepted | Job submitted (e.g., transcription job) |
| 400 Bad Request | Validation failure, malformed request |
| 401 Unauthorized | Missing or invalid JWT |
| 403 Forbidden | Insufficient role/permission |
| 404 Not Found | Resource does not exist |
| 409 Conflict | Scheduling conflict, duplicate resource |
| 422 Unprocessable Entity | Business rule violation |
| 500 Internal Server Error | Unexpected server error |
| 503 Service Unavailable | Gipformer service down |

### Global Exception Handler

Spring Boot `@ControllerAdvice` handles:
- `EntityNotFoundException` → 404
- `AccessDeniedException` → 403
- `SchedulingConflictException` → 409
- `MethodArgumentNotValidException` → 400 (with field-level errors)
- `MaxUploadSizeExceededException` → 400
- `Exception` (catch-all) → 500 with sanitized message

### Frontend Error Handling

- Axios interceptor: catch 401 → redirect to login, clear auth state
- Axios interceptor: catch 5xx → show toast notification "Lỗi máy chủ, vui lòng thử lại"
- WebSocket disconnect: show reconnecting indicator, auto-retry with exponential backoff (1s, 2s, 4s, max 30s)
- Jitsi IFrame error: show fallback message with meeting code for manual join

### Audio Pipeline Error Handling

- `getUserMedia()` denied: show permission request dialog, disable audio capture
- WebSocket binary send failure: buffer locally, retry on reconnect
- Gipformer unavailable: save chunk to File_Storage, show TRANSCRIPTION_UNAVAILABLE indicator
- Transcription job failed (3 retries): mark as FAILED, log error, continue meeting normally

### File Storage Error Handling

- Disk full: return 507 Insufficient Storage, notify admin via notification
- File not found on download: return 404, log inconsistency
- Concurrent write conflict: use file locking (Java `FileLock`)

### Meeting Lifecycle Error Handling

- Host disconnects mid-speech: auto-revoke Speaking_Permission, push partial chunk
- Jitsi server unreachable: show error, allow retry, meeting state preserved in Spring Boot
- Database connection lost: Spring Boot connection pool retry (HikariCP), queue WebSocket events

## Testing Strategy

### Dual Testing Approach

The testing strategy combines unit/integration tests for specific behaviors with property-based tests for universal correctness properties.

### Backend Testing (Spring Boot)

**Unit Tests (JUnit 5 + Mockito)**:
- Service layer: `MeetingModeService`, `SpeakingPermissionService`, `TranscriptionQueueService`, `MinutesService`
- Target: 80%+ line coverage on service layer
- Focus: business logic, state transitions, error conditions

**Integration Tests (Spring Boot Test + Testcontainers)**:
- API endpoint tests with real MySQL (Testcontainers)
- WebSocket event tests with real STOMP client
- Redis Sorted Set operations with embedded Redis (Testcontainers)
- File storage operations with temp directories

**Property-Based Tests (jqwik)**:
- Library: `net.jqwik:jqwik:1.8.x`
- Minimum 100 iterations per property test
- Each test tagged with: `// Feature: kolla-meeting-rebuild, Property N: <property_text>`

Property tests to implement:

```java
// Property 1: Speaking Permission Exclusivity
// Feature: kolla-meeting-rebuild, Property 1: Speaking Permission Exclusivity
@Property(tries = 200)
void speakingPermissionExclusivity(@ForAll("activeMeetings") Meeting meeting,
                                    @ForAll("participants") List<User> participants) {
    // Grant permission to random participants in sequence
    // Assert: at most 1 holds permission at any time
}

// Property 3: Queue Priority Ordering
// Feature: kolla-meeting-rebuild, Property 3: Queue Priority Ordering
@Property(tries = 500)
void queuePriorityOrdering(@ForAll("mixedPriorityJobs") List<TranscriptionJob> jobs) {
    // Push all jobs to Redis Sorted Set
    // Pop all jobs
    // Assert: all HIGH_PRIORITY jobs appear before NORMAL_PRIORITY jobs
}

// Property 4: At Most One High Priority Meeting
// Feature: kolla-meeting-rebuild, Property 4: At Most One High Priority Meeting
@Property(tries = 200)
void atMostOneHighPriorityMeeting(@ForAll("meetingIds") List<Long> meetingIds) {
    // Assign HIGH_PRIORITY to each meeting in sequence
    // After each assignment, count HIGH_PRIORITY meetings
    // Assert: count <= 1 always
}

// Property 5: Adaptive VAD Threshold Function
// Feature: kolla-meeting-rebuild, Property 5: Adaptive VAD Threshold Function
@Property(tries = 1000)
void adaptiveVadThreshold(@ForAll @DoubleRange(min = 0, max = 60) double bufferDurationSeconds) {
    double threshold = VadChunker.getThreshold(bufferDurationSeconds);
    if (bufferDurationSeconds < 15.0) {
        assertThat(threshold).isBetween(2.0, 3.0);
    } else {
        assertThat(threshold).isBetween(0.5, 1.0);
    }
}

// Property 6: Sequence Number Monotonicity
// Feature: kolla-meeting-rebuild, Property 6: Sequence Number Monotonicity
@Property(tries = 200)
void sequenceNumberMonotonicity(@ForAll("speakerTurns") SpeakerTurn turn) {
    List<Integer> seqNums = turn.getChunks().stream()
        .map(AudioChunk::getSequenceNumber).collect(toList());
    assertThat(seqNums).containsExactly(IntStream.rangeClosed(1, seqNums.size()).boxed().toArray());
}

// Property 7: Minutes Assembly Ordering
// Feature: kolla-meeting-rebuild, Property 7: Minutes Assembly Ordering
@Property(tries = 300)
void minutesAssemblyOrdering(@ForAll("transcriptionSegments") List<TranscriptionSegment> segments) {
    Collections.shuffle(segments); // randomize arrival order
    List<TranscriptionSegment> assembled = minutesService.assembleSegments(segments);
    // Assert: sorted by (speakerTurnId, sequenceNumber)
    assertThat(assembled).isSortedAccordingTo(
        Comparator.comparing(TranscriptionSegment::getSpeakerTurnId)
                  .thenComparingInt(TranscriptionSegment::getSequenceNumber));
}

// Property 8: Transcription Callback Idempotency
// Feature: kolla-meeting-rebuild, Property 8: Transcription Callback Idempotency
@Property(tries = 100)
void callbackIdempotency(@ForAll("transcriptionCallbacks") TranscriptionCallback callback,
                          @ForAll @IntRange(min = 1, max = 10) int callCount) {
    for (int i = 0; i < callCount; i++) {
        transcriptionController.handleCallback(callback);
    }
    long segmentCount = segmentRepository.countByJobId(callback.getJobId());
    assertThat(segmentCount).isEqualTo(1);
}

// Property 9: Meeting Code Uniqueness
// Feature: kolla-meeting-rebuild, Property 9: Meeting Code Uniqueness
@Property(tries = 100)
void meetingCodeUniqueness(@ForAll @IntRange(min = 2, max = 50) int meetingCount) {
    List<String> codes = IntStream.range(0, meetingCount)
        .mapToObj(i -> meetingService.generateMeetingCode())
        .collect(toList());
    assertThat(codes).doesNotHaveDuplicates();
}

// Property 10: Room Scheduling Conflict Detection
// Feature: kolla-meeting-rebuild, Property 10: Room Scheduling Conflict Detection
@Property(tries = 200)
void roomSchedulingConflictDetection(@ForAll("rooms") Room room,
                                      @ForAll("overlappingTimeRanges") TimeRangePair ranges) {
    meetingService.createMeeting(room, ranges.first()); // succeeds
    assertThatThrownBy(() -> meetingService.createMeeting(room, ranges.second()))
        .isInstanceOf(SchedulingConflictException.class);
}
```

### Frontend Testing (Vitest + React Testing Library)

**Unit Tests**:
- Utility functions: audio conversion (float32ToInt16), PCM chunking, time formatting
- Custom hooks: `useAudioCapture`, `useTranscription` (with mocked WebSocket)
- Store logic: Zustand store actions and selectors

**Component Tests (React Testing Library)**:
- `RaiseHandPanel`: renders pending requests in chronological order
- `TranscriptionPanel`: appends segments in sequence_number order
- `MeetingModeToggle`: shows correct state, calls correct API
- `MinutesEditor`: rich text editor renders and submits correctly

**Property-Based Tests (fast-check)**:
- Library: `fast-check@^3.x`
- Minimum 100 iterations per property test

```typescript
// Property 5: VAD threshold (frontend mirror)
// Feature: kolla-meeting-rebuild, Property 5: Adaptive VAD Threshold Function
test('adaptive VAD threshold', () => {
  fc.assert(fc.property(
    fc.float({ min: 0, max: 60 }),
    (duration) => {
      const threshold = getVadThreshold(duration);
      if (duration < 15) {
        return threshold >= 2.0 && threshold <= 3.0;
      } else {
        return threshold >= 0.5 && threshold <= 1.0;
      }
    }
  ), { numRuns: 1000 });
});

// Property 7: Minutes segment ordering (frontend display)
// Feature: kolla-meeting-rebuild, Property 7: Minutes Assembly Ordering
test('transcription segments display in order', () => {
  fc.assert(fc.property(
    fc.array(arbitrarySegment(), { minLength: 1, maxLength: 50 }),
    (segments) => {
      const shuffled = [...segments].sort(() => Math.random() - 0.5);
      const ordered = sortSegments(shuffled);
      for (let i = 1; i < ordered.length; i++) {
        const prev = ordered[i - 1];
        const curr = ordered[i];
        if (prev.speakerTurnId === curr.speakerTurnId) {
          expect(curr.sequenceNumber).toBeGreaterThan(prev.sequenceNumber);
        }
      }
    }
  ), { numRuns: 500 });
});
```

### Gipformer Service Testing (pytest + hypothesis)

**Unit Tests (pytest)**:
- `vad_chunker.py`: threshold logic, hard cap behavior
- `audio_converter.py`: PCM → WAV conversion correctness
- `redis_queue.py`: push/pop ordering

**Property-Based Tests (hypothesis)**:
- Library: `hypothesis>=6.x`
- Minimum 100 examples per property test

```python
# Property 3: Queue Priority Ordering
# Feature: kolla-meeting-rebuild, Property 3: Queue Priority Ordering
@given(st.lists(
    st.builds(TranscriptionJob,
              priority=st.sampled_from(['HIGH_PRIORITY', 'NORMAL_PRIORITY'])),
    min_size=2, max_size=50
))
@settings(max_examples=500)
def test_queue_priority_ordering(jobs):
    queue = RedisQueue(redis_client)
    for job in jobs:
        queue.push(job)
    
    dequeued = []
    while queue.size() > 0:
        dequeued.append(queue.pop())
    
    # All HIGH_PRIORITY jobs should come before NORMAL_PRIORITY jobs
    seen_normal = False
    for job in dequeued:
        if job.priority == 'NORMAL_PRIORITY':
            seen_normal = True
        if seen_normal:
            assert job.priority == 'NORMAL_PRIORITY', \
                "HIGH_PRIORITY job found after NORMAL_PRIORITY job"

# Property 5: Adaptive VAD Threshold
# Feature: kolla-meeting-rebuild, Property 5: Adaptive VAD Threshold Function
@given(st.floats(min_value=0.0, max_value=60.0))
@settings(max_examples=1000)
def test_adaptive_vad_threshold(duration_seconds):
    threshold = get_vad_threshold(duration_seconds)
    if duration_seconds < 15.0:
        assert 2.0 <= threshold <= 3.0
    else:
        assert 0.5 <= threshold <= 1.0

# Property 6: Sequence Number Monotonicity
# Feature: kolla-meeting-rebuild, Property 6: Sequence Number Monotonicity
@given(st.integers(min_value=1, max_value=100))
@settings(max_examples=200)
def test_sequence_number_monotonicity(chunk_count):
    chunker = VadChunker()
    chunks = chunker.simulate_chunks(chunk_count)
    seq_nums = [c.sequence_number for c in chunks]
    assert seq_nums == list(range(1, chunk_count + 1))
```

### End-to-End Tests (Playwright)

Critical user workflows:
1. Login → Create meeting → Add members → Activate meeting
2. Join meeting → Switch to Meeting_Mode → Raise hand → Grant permission → Transcription appears
3. End meeting → Confirm minutes (Host) → Edit minutes (Secretary) → Download PDF
4. Admin assigns HIGH_PRIORITY → Verify real-time transcription panel appears
5. Gipformer unavailable → Meeting continues → Recovery → Pending jobs processed

### Test Configuration

```yaml
# Backend: application-test.yml
spring:
  datasource:
    url: jdbc:tc:mysql:8.0:///kolla_test  # Testcontainers
  redis:
    host: localhost
    port: 6370  # Testcontainers Redis

# Frontend: vitest.config.ts
export default defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    coverage: { provider: 'v8', threshold: { lines: 80 } }
  }
})
```
