package com.example.kolla.enums;

/**
 * Represents the category of a stored file.
 * Each value maps to a sub-directory under the base storage path.
 * Requirements: 6.1
 */
public enum FileType {

    /** Video/audio recordings of meetings. Sub-dir: recordings */
    RECORDING("recordings"),

    /** Documents uploaded by participants. Sub-dir: documents */
    DOCUMENT("documents"),

    /** Raw audio chunks used for transcription. Sub-dir: audio_chunks */
    AUDIO_CHUNK("audio_chunks"),

    /** Generated meeting minutes (PDF/DOCX). Sub-dir: minutes */
    MINUTES("minutes");

    private final String dirName;

    FileType(String dirName) {
        this.dirName = dirName;
    }

    /** Returns the sub-directory name for this file type. */
    public String getDirName() {
        return dirName;
    }
}
