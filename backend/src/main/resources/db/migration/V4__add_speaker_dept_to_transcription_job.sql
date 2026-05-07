-- V4: Add speaker_dept column to transcription_job
-- Stores the department name of the speaker at the time of audio capture.
-- Nullable to be backward-compatible with existing rows.
ALTER TABLE transcription_job
    ADD COLUMN speaker_dept VARCHAR(255) NULL AFTER speaker_name;
