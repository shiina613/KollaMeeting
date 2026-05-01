-- V2: Rename duration_s → duration_seconds in attendance_log
-- Fixes schema-validation mismatch between Hibernate entity and DB column name
-- Requirements: 5.5

ALTER TABLE attendance_log
    CHANGE COLUMN duration_s duration_seconds BIGINT NULL;
