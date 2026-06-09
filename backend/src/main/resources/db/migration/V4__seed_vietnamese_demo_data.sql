-- ============================================================
-- V4__seed_vietnamese_demo_data.sql
-- Vietnamese demo seed data for departments, rooms and users.
-- ============================================================

INSERT INTO department (id, DepartmentCode, Name)
VALUES
  (1, 'BGD', 'Ban Giám đốc'),
  (2, 'HCNS', 'Phòng Hành chính Nhân sự'),
  (3, 'KTCN', 'Phòng Kỹ thuật Công nghệ')
ON DUPLICATE KEY UPDATE
  DepartmentCode = VALUES(DepartmentCode),
  Name = VALUES(Name);

INSERT INTO room (id, RoomCode, RoomName)
VALUES
  (1, 'ROOM-MAIN', 'Phòng họp chính'),
  (2, 'ROOM-ONLINE', 'Phòng họp trực tuyến'),
  (3, 'ROOM-SEMINAR', 'Phòng hội thảo')
ON DUPLICATE KEY UPDATE
  RoomCode = VALUES(RoomCode),
  RoomName = VALUES(RoomName);

-- Password for all seeded personal accounts: 12345678
-- BCrypt hash cost 12.
INSERT INTO user (id, EmployeeCode, Password, Name, Email, Role, Department_id)
VALUES
  (2, 'tungnq', '$2a$12$AKw9HifzEm27dHfa.b5bOOtcMpkyc1mIIBy9sO6jiXzmO5l2zVzja',
   'Nguyễn Quang Tùng', 'tungnq@kolla.local', 'SECRETARY', 3),
  (3, 'vuongnq', '$2a$12$AKw9HifzEm27dHfa.b5bOOtcMpkyc1mIIBy9sO6jiXzmO5l2zVzja',
   'Ngô Quốc Vượng', 'vuongnq@kolla.local', 'USER', 3),
  (4, 'quanlv', '$2a$12$AKw9HifzEm27dHfa.b5bOOtcMpkyc1mIIBy9sO6jiXzmO5l2zVzja',
   'Lò Văn Quân', 'quanlv@kolla.local', 'USER', 3),
  (5, 'thongnv', '$2a$12$AKw9HifzEm27dHfa.b5bOOtcMpkyc1mIIBy9sO6jiXzmO5l2zVzja',
   'Nguyễn Văn Thông', 'thongnv@kolla.local', 'USER', 3),
  (6, 'khanhdk', '$2a$12$AKw9HifzEm27dHfa.b5bOOtcMpkyc1mIIBy9sO6jiXzmO5l2zVzja',
   'Đặng Kim Khánh', 'khanhdk@kolla.local', 'USER', 3),
  (7, 'manhdv', '$2a$12$AKw9HifzEm27dHfa.b5bOOtcMpkyc1mIIBy9sO6jiXzmO5l2zVzja',
   'Dương Văn Mạnh', 'manhdv@kolla.local', 'USER', 3)
ON DUPLICATE KEY UPDATE
  EmployeeCode = VALUES(EmployeeCode),
  Password = VALUES(Password),
  Name = VALUES(Name),
  Email = VALUES(Email),
  Role = VALUES(Role),
  Department_id = VALUES(Department_id);
