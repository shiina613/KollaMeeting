-- Fresh schema aligned with the submitted Word document.
-- Business tables only: department, room, user, meeting, member, document,
-- meeting_message. Runtime data such as minutes, recordings, transcripts,
-- notifications and audit logs lives in local storage or Redis/in-memory state.

CREATE TABLE department (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    DepartmentCode  VARCHAR(100) NULL UNIQUE,
    Name            VARCHAR(255) NOT NULL,
    description     TEXT NULL,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE room (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    RoomCode       VARCHAR(100) NULL UNIQUE,
    RoomName       VARCHAR(255) NOT NULL,
    capacity       INT NULL,
    Department_id  BIGINT NOT NULL,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_room_department FOREIGN KEY (Department_id) REFERENCES department(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user` (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    Department_id   BIGINT NULL,
    EmployeeCode    VARCHAR(100) NOT NULL UNIQUE,
    Password        VARCHAR(255) NOT NULL,
    Name            VARCHAR(255) NOT NULL,
    Dob             DATE NULL,
    PhoneNumber     VARCHAR(30) NULL UNIQUE,
    Degree          VARCHAR(255) NULL,
    Identification  VARCHAR(100) NULL UNIQUE,
    Address         VARCHAR(1000) NULL,
    Email           VARCHAR(255) NULL UNIQUE,
    BankName        VARCHAR(255) NULL,
    BankNumber      VARCHAR(100) NULL,
    Img             VARCHAR(1000) NULL,
    Role            ENUM('ADMIN','SECRETARY','USER') NOT NULL DEFAULT 'USER',
    is_active       TINYINT(1) NOT NULL DEFAULT 1,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_user_department FOREIGN KEY (Department_id) REFERENCES department(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meeting (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    MeetingCode             VARCHAR(50) NOT NULL UNIQUE,
    DepartmentId            BIGINT NULL,
    Room_id                 BIGINT NULL,
    Name                    VARCHAR(500) NOT NULL,
    description             TEXT NULL,
    StartTime               DATETIME(6) NOT NULL,
    Endtime                 DATETIME(6) NOT NULL,
    creator_id              BIGINT NOT NULL,
    Status                  ENUM('SCHEDULED','ACTIVE','ENDED') NOT NULL DEFAULT 'SCHEDULED',
    mode                    ENUM('FREE_MODE','MEETING_MODE') NOT NULL DEFAULT 'FREE_MODE',
    transcription_priority  ENUM('HIGH_PRIORITY','NORMAL_PRIORITY') NOT NULL DEFAULT 'NORMAL_PRIORITY',
    host_user_id            BIGINT NULL,
    secretary_user_id       BIGINT NULL,
    activated_at            DATETIME(6) NULL,
    ended_at                DATETIME(6) NULL,
    waiting_timeout_at      DATETIME(6) NULL,
    created_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_meeting_room FOREIGN KEY (Room_id) REFERENCES room(id),
    CONSTRAINT fk_meeting_department FOREIGN KEY (DepartmentId) REFERENCES department(id),
    CONSTRAINT fk_meeting_creator FOREIGN KEY (creator_id) REFERENCES `user`(id),
    CONSTRAINT fk_meeting_host FOREIGN KEY (host_user_id) REFERENCES `user`(id),
    CONSTRAINT fk_meeting_secretary FOREIGN KEY (secretary_user_id) REFERENCES `user`(id),
    INDEX idx_meeting_room_time (Room_id, StartTime, Endtime, Status),
    INDEX idx_meeting_status (Status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE member (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    Meeting_id   BIGINT NOT NULL,
    User_id      BIGINT NOT NULL,
    MeetingRole  ENUM('HOST','SECRETARY','REVIEWER','COMMITTEE_MEMBER','GUEST','MEMBER') NOT NULL DEFAULT 'MEMBER',
    added_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_member_meeting FOREIGN KEY (Meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
    CONSTRAINT fk_member_user FOREIGN KEY (User_id) REFERENCES `user`(id),
    UNIQUE KEY uk_member_meeting_user (Meeting_id, User_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE document (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    Meeting_id   BIGINT NOT NULL,
    User_id      BIGINT NOT NULL,
    Name         VARCHAR(500) NOT NULL,
    Content      VARCHAR(1000) NOT NULL,
    file_size    BIGINT NULL,
    file_type    VARCHAR(100) NULL,
    uploaded_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_doc_meeting FOREIGN KEY (Meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
    CONSTRAINT fk_doc_user FOREIGN KEY (User_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meeting_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    Member_id   BIGINT NOT NULL,
    Content     TEXT NOT NULL,
    CreateTime  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_msg_member FOREIGN KEY (Member_id) REFERENCES member(id) ON DELETE CASCADE,
    INDEX idx_msg_member_time (Member_id, CreateTime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
