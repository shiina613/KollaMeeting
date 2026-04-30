package com.example.kolla.enums;

/**
 * Lifecycle states for a meeting's Minutes record.
 *
 * <pre>
 *   DRAFT ──Host confirms──► HOST_CONFIRMED ──Secretary edits──► SECRETARY_CONFIRMED
 * </pre>
 *
 * Requirements: 25.1–25.5
 */
public enum MinutesStatus {

    /** Auto-generated draft PDF from TranscriptionSegments. Awaiting Host confirmation. */
    DRAFT,

    /** Host has confirmed the draft with a digital stamp. Awaiting Secretary edit/publish. */
    HOST_CONFIRMED,

    /** Secretary has edited and published the final minutes. */
    SECRETARY_CONFIRMED
}
