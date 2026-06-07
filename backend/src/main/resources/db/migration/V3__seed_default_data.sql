-- ============================================================
-- V3__seed_default_data.sql
-- Default demo data for department, room and admin user.
-- Fresh schema uses the physical column names from DOCX 3.8.
-- ============================================================

INSERT INTO department (id, DepartmentCode, Name)
VALUES (1, 'BGD', 'Ban Giam doc')
ON DUPLICATE KEY UPDATE Name = VALUES(Name);

INSERT INTO room (id, RoomCode, RoomName)
VALUES (1, 'ROOM-MAIN', 'Phong hop chinh')
ON DUPLICATE KEY UPDATE RoomName = VALUES(RoomName);

-- EmployeeCode/login: admin | Password: admin
-- BCrypt hash cost 12
INSERT INTO user (id, EmployeeCode, Password, Name, Email, Role, Department_id)
VALUES (1, 'admin', '$2a$12$TZHod6ae3z2kAE1uWAsbDumT/u.a5pf9uAqTks3Dv1LvepI0I8Dm.',
        'System Administrator', 'admin@kolla.local', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE EmployeeCode = VALUES(EmployeeCode);
