-- Add DOCX file paths for generated meeting minutes.
-- Kept idempotent because some development snapshots already included these
-- columns in V1__initial_schema.sql.
SET @add_draft_docx = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE minutes ADD COLUMN draft_docx_path VARCHAR(500) NULL AFTER draft_pdf_path',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'minutes'
      AND COLUMN_NAME = 'draft_docx_path'
);
PREPARE stmt FROM @add_draft_docx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_secretary_docx = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE minutes ADD COLUMN secretary_docx_path VARCHAR(500) NULL AFTER secretary_pdf_path',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'minutes'
      AND COLUMN_NAME = 'secretary_docx_path'
);
PREPARE stmt FROM @add_secretary_docx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
