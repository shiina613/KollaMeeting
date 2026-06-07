/**
 * Minutes type definitions.
 * Requirements: 25.1–25.7
 */

// ─── Minutes status enum ──────────────────────────────────────────────────────

export type MinutesStatus = 'DRAFT' | 'HOST_CONFIRMED' | 'SECRETARY_CONFIRMED'

export type MinutesVersion = 'draft' | 'confirmed' | 'secretary'

export type MinutesFormat = 'pdf' | 'docx'

// ─── Minutes domain model ─────────────────────────────────────────────────────

export interface Minutes {
  id: number
  meetingId: number
  status: MinutesStatus
  contentHtml?: string
  draftPdfPath?: string
  draftDocxPath?: string
  confirmedPdfPath?: string
  secretaryPdfPath?: string
  secretaryDocxPath?: string
  draftAvailable?: boolean
  confirmedAvailable?: boolean
  secretaryAvailable?: boolean
  draftDocxAvailable?: boolean
  secretaryDocxAvailable?: boolean
  createdAt: string   // ISO 8601 UTC+7
  updatedAt: string
  confirmedAt?: string
  confirmedByName?: string
  secretaryEditedAt?: string
}

export interface MinutesConfirmationResponse {
  minutes: Minutes
  signedPdfFileName: string
  signedPdfContentType: string
  signedPdfBase64: string
  signedPdfSha256: string
}

// ─── Request bodies ───────────────────────────────────────────────────────────

export interface EditMinutesRequest {
  contentHtml: string
}
