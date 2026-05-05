-- ============================================================
-- V3__seed_default_data.sql
-- Seed dữ liệu mặc định: department, room, admin user
-- ============================================================

-- Department mặc định
INSERT INTO department (id, name, description)
VALUES (1, 'Ban Giám đốc', 'Phòng ban quản trị hệ thống')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Room mặc định
INSERT INTO room (id, name, capacity, department_id)
VALUES (1, 'Phòng họp chính', 50, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Admin user mặc định
-- Username: admin | Password: admin
-- BCrypt hash cost 12
INSERT INTO user (id, username, password_hash, full_name, email, role, department_id, is_active)
VALUES (1, 'admin', '$2a$12$TZHod6ae3z2kAE1uWAsbDumT/u.a5pf9uAqTks3Dv1LvepI0I8Dm.',
        'System Administrator', 'admin@kolla.local', 'ADMIN', 1, 1)
ON DUPLICATE KEY UPDATE username = VALUES(username);
