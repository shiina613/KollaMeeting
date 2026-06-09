ALTER TABLE meeting
    ADD COLUMN Mode ENUM('FREE_MODE','MEETING_MODE') NOT NULL DEFAULT 'FREE_MODE',
    ADD COLUMN TranscriptionPriority ENUM('HIGH_PRIORITY','NORMAL_PRIORITY') NOT NULL DEFAULT 'NORMAL_PRIORITY';

CREATE INDEX idx_meeting_mode ON meeting (Mode);
CREATE INDEX idx_meeting_transcription_priority ON meeting (TranscriptionPriority);
