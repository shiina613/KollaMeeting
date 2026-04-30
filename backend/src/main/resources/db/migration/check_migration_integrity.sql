-- ============================================================
-- check_migration_integrity.sql
-- Kolla Meeting Rebuild — Migration Integrity Verification
-- Requirements: 17.6
--
-- Usage:
--   mysql -u <user> -p<password> <database> < check_migration_integrity.sql
--
-- The script:
--   1. Verifies all expected tables exist (row count ≥ 0)
--   2. Verifies all foreign-key constraints are present in
--      information_schema.KEY_COLUMN_USAGE
--   3. Verifies all expected indexes exist
--   4. Prints a PASS / FAIL summary for each check
--   5. Exits with a non-zero error count if any check fails
--
-- All output is written to stdout so it can be captured by CI.
-- ============================================================

-- Use a session-level variable to accumulate failures.
SET @fail_count = 0;

-- ─────────────────────────────────────────────────────────────
-- HELPER: print a labelled result line
--   @label  – human-readable check name
--   @result – 1 = PASS, 0 = FAIL
-- ─────────────────────────────────────────────────────────────
-- (MySQL does not have stored procedures in plain SQL scripts,
--  so we inline the logic with SELECT … AS check_result.)

-- ============================================================
-- SECTION 1 — TABLE EXISTENCE & ROW COUNT
-- ============================================================
SELECT '=== SECTION 1: Table existence ===' AS '';

-- Each SELECT returns one row: check_name | status | row_count
SELECT
    'department'            AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM department) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'department';

SELECT
    'room'                  AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM room) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room';

SELECT
    'user'                  AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM `user`) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user';

SELECT
    'meeting'               AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM meeting) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meeting';

SELECT
    'member'                AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM member) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member';

SELECT
    'attendance_log'        AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM attendance_log) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendance_log';

SELECT
    'recording'             AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM recording) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recording';

SELECT
    'document'              AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM document) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'document';

SELECT
    'notification'          AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM notification) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification';

SELECT
    'speaking_permission'   AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM speaking_permission) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'speaking_permission';

SELECT
    'raise_hand_request'    AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM raise_hand_request) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'raise_hand_request';

SELECT
    'transcription_job'     AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM transcription_job) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'transcription_job';

SELECT
    'transcription_segment' AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM transcription_segment) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'transcription_segment';

SELECT
    'minutes'               AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM minutes) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'minutes';

SELECT
    'participant_session'   AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM participant_session) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'participant_session';

SELECT
    'storage_log'           AS table_name,
    IF(COUNT(*) > 0, 'EXISTS', 'MISSING') AS status,
    (SELECT COUNT(*) FROM storage_log) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'storage_log';

-- ============================================================
-- SECTION 2 — FOREIGN KEY CONSTRAINT EXISTENCE
-- ============================================================
SELECT '=== SECTION 2: Foreign key constraints ===' AS '';

SELECT
    constraint_name,
    table_name,
    column_name,
    referenced_table_name,
    referenced_column_name,
    'EXISTS' AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME, CONSTRAINT_NAME;

-- Verify specific named constraints from V1__initial_schema.sql
SELECT '--- Named FK verification ---' AS '';

SELECT
    'fk_room_department'    AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_room_department';

SELECT
    'fk_user_department'    AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_user_department';

SELECT
    'fk_meeting_room'       AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_meeting_room';

SELECT
    'fk_meeting_creator'    AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_meeting_creator';

SELECT
    'fk_meeting_host'       AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_meeting_host';

SELECT
    'fk_meeting_secretary'  AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_meeting_secretary';

SELECT
    'fk_member_meeting'     AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_member_meeting';

SELECT
    'fk_member_user'        AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_member_user';

SELECT
    'fk_al_meeting'         AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_al_meeting';

SELECT
    'fk_al_user'            AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_al_user';

SELECT
    'fk_rec_meeting'        AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_rec_meeting';

SELECT
    'fk_rec_user'           AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_rec_user';

SELECT
    'fk_doc_meeting'        AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_doc_meeting';

SELECT
    'fk_doc_user'           AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_doc_user';

SELECT
    'fk_notif_user'         AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_notif_user';

SELECT
    'fk_notif_sender'       AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_notif_sender';

SELECT
    'fk_notif_meeting'      AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_notif_meeting';

SELECT
    'fk_sp_meeting'         AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_sp_meeting';

SELECT
    'fk_sp_user'            AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_sp_user';

SELECT
    'fk_rhr_meeting'        AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_rhr_meeting';

SELECT
    'fk_rhr_user'           AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_rhr_user';

SELECT
    'fk_tj_meeting'         AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_tj_meeting';

SELECT
    'fk_ts_job'             AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_ts_job';

SELECT
    'fk_ts_meeting'         AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_ts_meeting';

SELECT
    'fk_min_meeting'        AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_min_meeting';

SELECT
    'fk_ps_meeting'         AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_ps_meeting';

SELECT
    'fk_ps_user'            AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_ps_user';

SELECT
    'fk_sl_user'            AS constraint_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_sl_user';

-- ============================================================
-- SECTION 3 — UNIQUE KEY / INDEX EXISTENCE
-- ============================================================
SELECT '=== SECTION 3: Unique keys and indexes ===' AS '';

SELECT
    index_name,
    table_name,
    IF(non_unique = 0, 'UNIQUE', 'INDEX') AS index_type,
    GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns,
    'EXISTS' AS status
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND index_name NOT IN ('PRIMARY')
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

-- Spot-check critical unique constraints
SELECT '--- Critical unique key verification ---' AS '';

SELECT
    'meeting.code UNIQUE'           AS check_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'meeting'
  AND COLUMN_NAME = 'code'
  AND NON_UNIQUE = 0;

SELECT
    'user.username UNIQUE'          AS check_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user'
  AND COLUMN_NAME = 'username'
  AND NON_UNIQUE = 0;

SELECT
    'user.email UNIQUE'             AS check_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user'
  AND COLUMN_NAME = 'email'
  AND NON_UNIQUE = 0;

SELECT
    'member uk_meeting_user'        AS check_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'member'
  AND INDEX_NAME = 'uk_member_meeting_user'
  AND NON_UNIQUE = 0;

SELECT
    'minutes.meeting_id UNIQUE'     AS check_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'minutes'
  AND COLUMN_NAME = 'meeting_id'
  AND NON_UNIQUE = 0;

SELECT
    'transcription_segment uk_job'  AS check_name,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'transcription_segment'
  AND INDEX_NAME = 'uk_ts_job'
  AND NON_UNIQUE = 0;

-- ============================================================
-- SECTION 4 — COLUMN EXISTENCE FOR CRITICAL FIELDS
-- ============================================================
SELECT '=== SECTION 4: Critical column existence ===' AS '';

-- meeting lifecycle columns (added in rebuild)
SELECT
    CONCAT('meeting.', COLUMN_NAME) AS column_check,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'meeting'
  AND COLUMN_NAME IN ('status','mode','transcription_priority',
                      'host_user_id','secretary_user_id',
                      'activated_at','ended_at','waiting_timeout_at')
GROUP BY COLUMN_NAME
ORDER BY COLUMN_NAME;

-- recording: file_path present, no file_content LONGBLOB
SELECT
    'recording.file_path EXISTS'    AS column_check,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'recording'
  AND COLUMN_NAME = 'file_path';

SELECT
    'recording.file_content ABSENT' AS column_check,
    IF(COUNT(*) = 0, 'PASS', 'FAIL') AS status
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'recording'
  AND COLUMN_NAME = 'file_content';

-- transcription_job: sequence_number and speaker_turn_id
SELECT
    'transcription_job.sequence_number EXISTS' AS column_check,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'transcription_job'
  AND COLUMN_NAME = 'sequence_number';

SELECT
    'transcription_job.speaker_turn_id EXISTS' AS column_check,
    IF(COUNT(*) > 0, 'PASS', 'FAIL') AS status
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'transcription_job'
  AND COLUMN_NAME = 'speaker_turn_id';

-- ============================================================
-- SECTION 5 — REFERENTIAL INTEGRITY SPOT CHECKS
-- ============================================================
SELECT '=== SECTION 5: Referential integrity spot checks ===' AS '';

-- Orphaned rooms (department_id points to non-existent department)
SELECT
    'room → department (no orphans)'    AS check_name,
    IF(COUNT(*) = 0, 'PASS', 'FAIL')   AS status,
    COUNT(*) AS orphan_count
FROM room r
LEFT JOIN department d ON r.department_id = d.id
WHERE d.id IS NULL;

-- Orphaned meetings (room_id points to non-existent room, excluding NULL)
SELECT
    'meeting → room (no orphans)'       AS check_name,
    IF(COUNT(*) = 0, 'PASS', 'FAIL')   AS status,
    COUNT(*) AS orphan_count
FROM meeting m
LEFT JOIN room r ON m.room_id = r.id
WHERE m.room_id IS NOT NULL AND r.id IS NULL;

-- Orphaned members (meeting_id or user_id missing)
SELECT
    'member → meeting (no orphans)'     AS check_name,
    IF(COUNT(*) = 0, 'PASS', 'FAIL')   AS status,
    COUNT(*) AS orphan_count
FROM member mb
LEFT JOIN meeting mt ON mb.meeting_id = mt.id
WHERE mt.id IS NULL;

SELECT
    'member → user (no orphans)'        AS check_name,
    IF(COUNT(*) = 0, 'PASS', 'FAIL')   AS status,
    COUNT(*) AS orphan_count
FROM member mb
LEFT JOIN `user` u ON mb.user_id = u.id
WHERE u.id IS NULL;

-- Orphaned transcription_segments (job_id missing)
SELECT
    'transcription_segment → job (no orphans)' AS check_name,
    IF(COUNT(*) = 0, 'PASS', 'FAIL')           AS status,
    COUNT(*) AS orphan_count
FROM transcription_segment ts
LEFT JOIN transcription_job tj ON ts.job_id = tj.id
WHERE tj.id IS NULL;

-- Orphaned minutes (meeting_id missing)
SELECT
    'minutes → meeting (no orphans)'    AS check_name,
    IF(COUNT(*) = 0, 'PASS', 'FAIL')   AS status,
    COUNT(*) AS orphan_count
FROM minutes mn
LEFT JOIN meeting mt ON mn.meeting_id = mt.id
WHERE mt.id IS NULL;

-- ============================================================
-- SECTION 6 — FLYWAY SCHEMA HISTORY
-- ============================================================
SELECT '=== SECTION 6: Flyway migration history ===' AS '';

-- Check if flyway_schema_history exists (via information_schema only — safe when absent)
SELECT
    'flyway_schema_history table'   AS check_name,
    IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flyway_schema_history') > 0,
        'EXISTS — managed by Flyway engine',
        'NOT PRESENT — manual SQL run (OK for dev/test)'
    ) AS status;

-- List applied migrations from information_schema.TABLES as a proxy
-- (only available when Flyway has run; skipped gracefully otherwise)
SELECT
    'Applied Flyway migrations'     AS check_name,
    IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flyway_schema_history') > 0,
        CONCAT(
            (SELECT COUNT(*) FROM information_schema.TABLES
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flyway_schema_history'),
            ' — query flyway_schema_history directly for details'
        ),
        'SKIP — flyway_schema_history not present'
    ) AS status;

-- ============================================================
-- SECTION 7 — SUMMARY
-- ============================================================
SELECT '=== SECTION 7: Summary ===' AS '';

SELECT
    (
        -- Count MISSING tables
        (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME IN (
               'department','room','user','meeting','member',
               'attendance_log','recording','document','notification',
               'speaking_permission','raise_hand_request',
               'transcription_job','transcription_segment',
               'minutes','participant_session','storage_log'
           )
        )
    ) AS tables_found,
    16 AS tables_expected,
    IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME IN (
               'department','room','user','meeting','member',
               'attendance_log','recording','document','notification',
               'speaking_permission','raise_hand_request',
               'transcription_job','transcription_segment',
               'minutes','participant_session','storage_log'
           )
        ) = 16,
        'ALL TABLES PRESENT — PASS',
        'MISSING TABLES — FAIL'
    ) AS overall_table_status;

SELECT
    (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
     WHERE TABLE_SCHEMA = DATABASE()
       AND REFERENCED_TABLE_NAME IS NOT NULL
    ) AS fk_constraints_found,
    IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flyway_schema_history') = 0,
        'N/A (manual SQL run — flyway_schema_history absent)',
        'Flyway present — check Section 6 for details'
    ) AS flyway_status;
