/**
 * Minutes type definitions.
 * Requirements: 25.1–25.7
 */

// ─── Minutes status enum ──────────────────────────────────────────────────────

export type MinutesStatus = 'DRAFT' | 'HOST_CONFIRMED' | 'SECRETARY_CONFIRMED'

export type MinutesVersion = 'draft' | 'confirmed' | 'secretary'

// ─── Minutes domain model ─────────────────────────────────────────────────────

export interface Minutes {
  id: number
  meetingId: number
  status: MinutesStatus
  contentHtml?: string
  draftPdfPath?: string
  confirmedPdfPath?: string
  secretaryPdfPath?: string
  createdAt: string   // ISO 8601 UTC+7
  updatedAt: string
  confirmedAt?: string
  confirmedByName?: string
  secretaryEditedAt?: string
}

// ─── Request bodies ───────────────────────────────────────────────────────────

export interface EditMinutesRequest {
  contentHtml: string
}
