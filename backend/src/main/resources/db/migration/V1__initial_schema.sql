-- ============================================================
-- V1__initial_schema.sql
-- Kolla Meeting Rebuild — full initial schema
-- Tạo toàn bộ schema từ đầu cho fresh install.
-- Requirements: 3.11, 17.1–17.6
-- ============================================================

-- ─────────────────────────────────────────────
-- department
-- ─────────────────────────────────────────────
CREATE TABLE department (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- room
-- ─────────────────────────────────────────────
CREATE TABLE room (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    capacity      INT NULL,
    department_id BIGINT NOT NULL,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_room_department FOREIGN KEY (department_id) REFERENCES department(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- user
-- ─────────────────────────────────────────────
CREATE TABLE user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NULL UNIQUE,
    role          ENUM('ADMIN','SECRETARY','USER') NOT NULL DEFAULT 'USER',
    department_id BIGINT NULL,
    is_active     TINYINT(1) NOT NULL DEFAULT 1,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES department(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- meeting
-- Bao gồm đầy đủ các cột cho rebuild (status, mode, lifecycle, v.v.)
-- Requirements: 3.1–3.12, 21.1
-- ─────────────────────────────────────────────
CREATE TABLE meeting (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                    VARCHAR(50)  NOT NULL UNIQUE COMMENT 'Unique meeting code generated on creation',
    title                   VARCHAR(500) NOT NULL,
    description             TEXT NULL,
    start_time              DATETIME(6)  NOT NULL,
    end_time                DATETIME(6)  NOT NULL,
    room_id                 BIGINT NULL,
    creator_id              BIGINT NOT NULL,
    -- Lifecycle (Requirement 3.11)
    status                  ENUM('SCHEDULED','ACTIVE','ENDED') NOT NULL DEFAULT 'SCHEDULED',
    -- Meeting mode (Requirement 21.1)
    mode                    ENUM('FREE_MODE','MEETING_MODE') NOT NULL DEFAULT 'FREE_MODE',
    -- Transcription priority for Redis queue
    transcription_priority  ENUM('HIGH_PRIORITY','NORMAL_PRIORITY') NOT NULL DEFAULT 'NORMAL_PRIORITY',
    -- Host & Secretary assignment (Requirement 3.8)
    host_user_id            BIGINT NULL COMMENT 'SECRETARY or ADMIN role; required before saving',
    secretary_user_id       BIGINT NULL COMMENT 'SECRETARY role; required before saving',
    -- Lifecycle timestamps
    activated_at            DATETIME(6) NULL COMMENT 'SCHEDULED → ACTIVE transition time',
    ended_at                DATETIME(6) NULL COMMENT 'Transition to ENDED time',
    waiting_timeout_at      DATETIME(6) NULL COMMENT 'Auto-end deadline when no Host/Secretary present (10 min)',
    created_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_meeting_room      FOREIGN KEY (room_id)          REFERENCES room(id),
    CONSTRAINT fk_meeting_creator   FOREIGN KEY (creator_id)       REFERENCES user(id),
    CONSTRAINT fk_meeting_host      FOREIGN KEY (host_user_id)     REFERENCES user(id),
    CONSTRAINT fk_meeting_secretary FOREIGN KEY (secretary_user_id) REFERENCES user(id),
    -- Room conflict detection query (Requirement 3.12)
    INDEX idx_meeting_room_time (room_id, start_time, end_time, status),
    INDEX idx_meeting_status    (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- member  (meeting ↔ user many-to-many)
-- Requirement 3.9: only members may join
-- ─────────────────────────────────────────────
CREATE TABLE member (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    added_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_member_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
    CONSTRAINT fk_member_user    FOREIGN KEY (user_id)    REFERENCES user(id),
    UNIQUE KEY uk_member_meeting_user (meeting_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- attendance_log
-- Requirements: 5.1–5.8
-- ─────────────────────────────────────────────
CREATE TABLE attendance_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id  BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    join_time   DATETIME(6) NOT NULL,
    leave_time  DATETIME(6) NULL,
    duration_s  INT NULL,
    ip_address  VARCHAR(45) NULL,
    device_info VARCHAR(500) NULL,
    CONSTRAINT fk_al_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
    CONSTRAINT fk_al_user    FOREIGN KEY (user_id)    REFERENCES user(id),
    INDEX idx_al_meeting_user (meeting_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- recording
-- Requirements: 7.1–7.7
-- file_path thay thế file_content LONGBLOB của hệ thống cũ
-- ─────────────────────────────────────────────
CREATE TABLE recording (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id BIGINT NOT NULL,
    file_name  VARCHAR(500) NOT NULL,
    file_size  BIGINT NULL,
    file_path  VARCHAR(500) NULL COMMENT 'Path under /app/storage/recordings/{meeting_id}/',
    url        VARCHAR(1000) NULL,
    status     ENUM('RECORDING','COMPLETED','FAILED') NOT NULL DEFAULT 'RECORDING',
    start_time DATETIME(6) NOT NULL,
    end_time   DATETIME(6) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_rec_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
    CONSTRAINT fk_rec_user    FOREIGN KEY (created_by) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- document
-- Requirements: 9.1–9.7
-- ─────────────────────────────────────────────
CREATE TABLE document (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id  BIGINT NOT NULL,
    file_name   VARCHAR(500) NOT NULL,
    file_size   BIGINT NULL,
    file_type   VARCHAR(100) NULL,
    file_path   VARCHAR(1000) NOT NULL,
    uploaded_by BIGINT NOT NULL,
    uploaded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_doc_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
    CONSTRAINT fk_doc_user    FOREIGN KEY (uploaded_by) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- notification
-- Requirements: 10.5–10.7
-- ─────────────────────────────────────────────
CREATE TABLE notification (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    sender_id  BIGINT NULL,
    type       VARCHAR(100) NOT NULL,
    title      VARCHAR(500) NOT NULL,
    message    TEXT NULL,
    is_read    TINYINT(1) NOT NULL DEFAULT 0,
    meeting_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_notif_user    FOREIGN KEY (user_id)    REFERENCES user(id),
    CONSTRAINT fk_notif_sender  FOREIGN KEY (sender_id)  REFERENCES user(id),
    CONSTRAINT fk_notif_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE SET NULL,
    INDEX idx_notif_user_read (user_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- speaking_permission
-- Ai đang giữ quyền phát biểu trong Meeting_Mode.
-- Requirements: 22.4, 22.8
-- ─────────────────────────────────────────────
CREATE TABLE speaking_permission (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id      BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    granted_at      DATETIME(6) NOT NULL,
    revoked_at      DATETIME(6) NULL     COMMENT 'NULL = permission still active',
    speaker_turn_id VARCHAR(36) NOT NULL COMMENT 'UUID; new value on every grant',
    CONSTRAINT fk_sp_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
    CONSTRAINT fk_sp_user    FOREIGN KEY (user_id)    REFERENCES user(id),
    INDEX idx_sp_meeting_active (meeting_id, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='At most one active (revoked_at IS NULL) row per meeting at any time';

-- ─────────────────────────────────────────────
-- raise_hand_request
-- Hàng đợi xin phát biểu theo thứ tự chronological.
-- Requirements: 22.1–22.11
-- ─────────────────────────────────────────────
CREATE TABLE raise_hand_request (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id   BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    status       ENUM('PENDING','GRANTED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'PENDING',
    resolved_at  DATETIME(6) NULL,
    CONSTRAINT fk_rhr_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
    CONSTRAINT fk_rhr_user    FOREIGN KEY (user_id)    REFERENCES user(id),
    INDEX idx_rhr_meeting_pending (meeting_id, status, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- transcription_job
-- Mỗi audio chunk = 1 job; mirrors Redis state cho durability.
-- Requirements: 8.9–8.13
-- ─────────────────────────────────────────────
CREATE TABLE transcription_job (
    id              VARCHAR(36)  PRIMARY KEY COMMENT 'UUID; also used as Redis Sorted Set member',
    meeting_id      BIGINT       NOT NULL,
    speaker_id      BIGINT       NOT NULL,
    speaker_name    VARCHAR(255) NOT NULL,
    speaker_turn_id VARCHAR(36)  NOT NULL,
    sequence_number INT          NOT NULL COMMENT 'Monotonically increasing within a speaker_turn_id, starting at 1',
    priority        ENUM('HIGH_PRIORITY','NORMAL_PRIORITY') NOT NULL,
    status          ENUM('PENDING','QUEUED','PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
    audio_path      VARCHAR(500) NULL COMMENT '/app/storage/audio_chunks/{meeting_id}/{turn_id}/chunk_{seq}_{job_id}.wav',
    retry_count     INT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL,
    queued_at       DATETIME(6)  NULL,
    completed_at    DATETIME(6)  NULL,
    error_message   TEXT         NULL,
    CONSTRAINT fk_tj_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
    INDEX idx_tj_meeting_status  (meeting_id, status),
    INDEX idx_tj_speaker_turn    (speaker_turn_id, sequence_number),
    INDEX idx_tj_priority_status (priority, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- transcription_segment
-- Kết quả phiên âm đã persist; UNIQUE KEY đảm bảo idempotency.
-- Requirements: 8.12, 8.13, 25.1
-- ─────────────────────────────────────────────
CREATE TABLE transcription_segment (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id             VARCHAR(36)  NOT NULL,
    meeting_id         BIGINT       NOT NULL,
    speaker_id         BIGINT       NOT NULL,
    speaker_name       VARCHAR(255) NOT NULL,
    speaker_turn_id    VARCHAR(36)  NOT NULL,
    sequence_number    INT          NOT NULL,
    text               TEXT         NOT NULL,
    confidence         FLOAT        NULL,
    processing_time_ms INT          NULL,
    segment_start_time DATETIME(6)  NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    CONSTRAINT fk_ts_job     FOREIGN KEY (job_id)     REFERENCES transcription_job(id),
    CONSTRAINT fk_ts_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
    INDEX idx_ts_meeting_order (meeting_id, speaker_turn_id, sequence_number),
    -- 1 job → 1 segment (idempotency: callback gọi 2 lần vẫn không tạo duplicate)
    UNIQUE KEY uk_ts_job (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- minutes
-- Biên bản họp: DRAFT → HOST_CONFIRMED → SECRETARY_CONFIRMED.
-- Requirements: 25.1–25.7
-- ─────────────────────────────────────────────
CREATE TABLE minutes (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id             BIGINT       NOT NULL UNIQUE,
    status                 ENUM('DRAFT','HOST_CONFIRMED','SECRETARY_CONFIRMED') NOT NULL DEFAULT 'DRAFT',
    draft_pdf_path         VARCHAR(500) NULL,
    confirmed_pdf_path     VARCHAR(500) NULL COMMENT 'Host-confirmed PDF với digital stamp',
    secretary_pdf_path     VARCHAR(500) NULL COMMENT 'Secretary-edited final PDF',
    content_html           TEXT         NULL COMMENT 'Rich-text content từ Secretary editor',
    host_confirmed_at      DATETIME(6)  NULL,
    host_confirmation_hash VARCHAR(255) NULL COMMENT 'SHA-256(JWT + PDF content)',
    secretary_confirmed_at DATETIME(6)  NULL,
    reminder_sent_at       DATETIME(6)  NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    CONSTRAINT fk_min_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- participant_session
-- WebSocket presence tracking qua heartbeat.
-- Requirements: 5.3–5.5, 10.1
-- ─────────────────────────────────────────────
CREATE TABLE participant_session (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id        BIGINT       NOT NULL,
    user_id           BIGINT       NOT NULL,
    session_id        VARCHAR(255) NOT NULL COMMENT 'Spring WebSocket session ID',
    joined_at         DATETIME(6)  NOT NULL,
    last_heartbeat_at DATETIME(6)  NOT NULL,
    is_connected      TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT fk_ps_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
    CONSTRAINT fk_ps_user    FOREIGN KEY (user_id)    REFERENCES user(id),
    INDEX idx_ps_meeting_connected (meeting_id, is_connected),
    INDEX idx_ps_session_id        (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- storage_log
-- Audit trail cho bulk/single file deletion.
-- Requirement: 6.7
-- ─────────────────────────────────────────────
CREATE TABLE storage_log (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_user_id    BIGINT NOT NULL,
    operation        ENUM('BULK_DELETE','SINGLE_DELETE') NOT NULL,
    file_count       INT    NOT NULL,
    total_size_bytes BIGINT NOT NULL,
    description      TEXT   NULL,
    created_at       DATETIME(6) NOT NULL,
    CONSTRAINT fk_sl_user FOREIGN KEY (admin_user_id) REFERENCES user(id),
    INDEX idx_sl_admin_time (admin_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
