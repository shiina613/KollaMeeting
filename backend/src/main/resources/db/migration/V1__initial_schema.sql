-- Fresh schema aligned with the submitted Word document.
-- Business tables only: department, room, user, meeting, member, document,
-- meeting_message. Runtime data such as minutes, recordings, transcripts,
-- notifications and audit logs lives in local storage or Redis/in-memory state.

CREATE TABLE department (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    DepartmentCode  VARCHAR(100) NOT NULL UNIQUE,
    Name            VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE room (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    RoomCode       VARCHAR(100) NOT NULL,
    RoomName       VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user` (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    Department_id   BIGINT NOT NULL,
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
    CONSTRAINT fk_user_department FOREIGN KEY (Department_id) REFERENCES department(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meeting (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    MeetingCode             VARCHAR(50) NOT NULL,
    DepartmentId            BIGINT NOT NULL,
    Room_id                 BIGINT NOT NULL,
    Name                    VARCHAR(500) NOT NULL,
    StartTime               DATETIME(6) NOT NULL,
    Endtime                 DATETIME(6) NOT NULL,
    Status                  ENUM('SCHEDULED','ACTIVE','ENDED') NOT NULL DEFAULT 'SCHEDULED',
    CONSTRAINT fk_meeting_room FOREIGN KEY (Room_id) REFERENCES room(id),
    CONSTRAINT fk_meeting_department FOREIGN KEY (DepartmentId) REFERENCES department(id),
    INDEX idx_meeting_room_time (Room_id, StartTime, Endtime, Status),
    INDEX idx_meeting_status (Status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE member (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    Meeting_id   BIGINT NOT NULL,
    User_id      BIGINT NOT NULL,
    MeetingRole  ENUM('HOST','SECRETARY','REVIEWER','COMMITTEE_MEMBER','GUEST','MEMBER') NOT NULL DEFAULT 'MEMBER',
    CONSTRAINT fk_member_meeting FOREIGN KEY (Meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
    CONSTRAINT fk_member_user FOREIGN KEY (User_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE document (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    Meeting_id   BIGINT NOT NULL,
    User_id      BIGINT NOT NULL,
    Name         VARCHAR(500) NOT NULL,
    Content      TEXT NOT NULL,
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
