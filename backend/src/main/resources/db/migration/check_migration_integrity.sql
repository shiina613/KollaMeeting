-- Verifies that a clean KollaMeeting database matches the submitted Word
-- schema boundary: exactly seven business tables and no runtime tables.
--
-- Usage:
--   mysql -u <user> -p<password> <database> < check_migration_integrity.sql

SET @expected_table_count = 7;

SELECT '=== Expected Word tables ===' AS section_name;

SELECT
    t.table_name,
    IF(COUNT(i.TABLE_NAME) = 1, 'PASS', 'FAIL') AS status
FROM (
    SELECT 'department' AS table_name
    UNION ALL SELECT 'room'
    UNION ALL SELECT 'user'
    UNION ALL SELECT 'meeting'
    UNION ALL SELECT 'member'
    UNION ALL SELECT 'document'
    UNION ALL SELECT 'meeting_message'
) t
LEFT JOIN information_schema.TABLES i
    ON i.TABLE_SCHEMA = DATABASE()
   AND i.TABLE_NAME = t.table_name
GROUP BY t.table_name
ORDER BY t.table_name;

SELECT '=== Runtime tables must be absent ===' AS section_name;

SELECT
    t.table_name,
    IF(COUNT(i.TABLE_NAME) = 0, 'PASS', 'FAIL') AS status
FROM (
    SELECT 'minutes' AS table_name
    UNION ALL SELECT 'recording'
    UNION ALL SELECT 'transcription_job'
    UNION ALL SELECT 'transcription_segment'
    UNION ALL SELECT 'attendance_log'
    UNION ALL SELECT 'participant_session'
    UNION ALL SELECT 'notification'
    UNION ALL SELECT 'speaking_permission'
    UNION ALL SELECT 'storage_log'
    UNION ALL SELECT 'raise_hand_request'
) t
LEFT JOIN information_schema.TABLES i
    ON i.TABLE_SCHEMA = DATABASE()
   AND i.TABLE_NAME = t.table_name
GROUP BY t.table_name
ORDER BY t.table_name;

SELECT '=== Total table count ===' AS section_name;

SELECT
    COUNT(*) AS table_count,
    IF(COUNT(*) = @expected_table_count, 'PASS', 'FAIL') AS status
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE();

SELECT '=== Foreign keys ===' AS section_name;

SELECT
    constraint_name,
    table_name,
    column_name,
    referenced_table_name,
    referenced_column_name
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME, CONSTRAINT_NAME;
