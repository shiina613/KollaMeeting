/**
 * TranscriptionPanel — displays real-time transcription segments for HIGH_PRIORITY meetings.
 *
 * - Returns null when the meeting is not HIGH_PRIORITY (Req 8.13)
 * - Shows a TRANSCRIPTION_UNAVAILABLE banner when Gipformer is down (Req 8.12)
 * - Renders segments sorted by (speakerTurnId, sequenceNumber) via useTranscription
 * - Auto-scrolls to the latest segment
 * - Groups consecutive segments from the same speaker into a single block
 *
 * Requirements: 8.12, 8.13
 */

import { useEffect, useRef } from 'react'
import useTranscription from '../../hooks/useTranscription'
import useMeetingStore from '../../store/meetingStore'
import type { TranscriptionSegment } from '../../utils/audioUtils'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface TranscriptionPanelProps {
  meetingId: number
  isHighPriority: boolean
}

// ─── Speaker block (consecutive segments from the same speaker turn) ──────────

interface SpeakerBlock {
  speakerTurnId: string
  speakerName: string
  segments: TranscriptionSegment[]
}

function groupIntoSpeakerBlocks(segments: TranscriptionSegment[]): SpeakerBlock[] {
  const blocks: SpeakerBlock[] = []
  for (const seg of segments) {
    const last = blocks[blocks.length - 1]
    if (last && last.speakerTurnId === seg.speakerTurnId) {
      last.segments.push(seg)
    } else {
      blocks.push({
        speakerTurnId: seg.speakerTurnId,
        speakerName: seg.speakerName,
        segments: [seg],
      })
    }
  }
  return blocks
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function UnavailableBanner() {
  return (
    <div
      className="flex items-center gap-2 mx-3 my-2 px-3 py-2 rounded-lg bg-error-container text-on-error-container text-body-sm"
      role="alert"
      aria-live="polite"
      data-testid="transcription-unavailable-banner"
    >
      <span className="material-symbols-outlined text-[16px] shrink-0" aria-hidden="true">
        warning
      </span>
      <span>Dịch vụ phiên âm tạm thời không khả dụng. Kết quả sẽ được lưu khi dịch vụ phục hồi.</span>
    </div>
  )
}

function EmptyState() {
  return (
    <div
      className="flex flex-col items-center justify-center h-full gap-3 text-on-surface-variant px-4"
      data-testid="transcription-empty-state"
    >
      <span className="material-symbols-outlined text-[40px] opacity-40" aria-hidden="true">
        mic
      </span>
      <p className="text-body-sm text-center">
        Phiên âm sẽ xuất hiện khi người phát biểu bắt đầu nói.
      </p>
    </div>
  )
}

interface SpeakerBlockCardProps {
  block: SpeakerBlock
  isLast: boolean
}

function SpeakerBlockCard({ block, isLast }: SpeakerBlockCardProps) {
  const text = block.segments.map((s) => s.text).join(' ')
  const firstSegment = block.segments[0]
  const time = new Date(firstSegment.segmentStartTime).toLocaleTimeString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZone: 'Asia/Ho_Chi_Minh',
  })

  return (
    <div
      className={`px-4 py-3 ${isLast ? '' : 'border-b border-outline-variant'}`}
      data-testid="transcription-segment-block"
      data-speaker-turn-id={block.speakerTurnId}
    >
      {/* Speaker name + timestamp */}
      <div className="flex items-center justify-between mb-1">
        <span className="text-label-sm font-semibold text-primary truncate max-w-[70%]">
          {block.speakerName}
        </span>
        <span className="text-label-sm text-on-surface-variant shrink-0 ml-2">
          {time}
        </span>
      </div>
      {/* Transcription text */}
      <p className="text-body-sm text-on-surface leading-relaxed">
        {text}
      </p>
    </div>
  )
}

// ─── Main component ───────────────────────────────────────────────────────────

/**
 * TranscriptionPanel
 *
 * Requirements: 8.12, 8.13
 */
export default function TranscriptionPanel({ meetingId: _meetingId, isHighPriority }: TranscriptionPanelProps) {
  // Only render for HIGH_PRIORITY meetings (Req 8.13)
  if (!isHighPriority) return null

  return <TranscriptionPanelInner />
}

/**
 * Inner component — separated so the hook is only called when isHighPriority is true.
 * This avoids running the transcription hook for NORMAL_PRIORITY meetings.
 */
function TranscriptionPanelInner() {
  const { isTranscriptionAvailable } = useMeetingStore()
  const { segments } = useTranscription()

  const scrollRef = useRef<HTMLDivElement>(null)

  // Auto-scroll to bottom when new segments arrive
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [segments])

  const blocks = groupIntoSpeakerBlocks(segments)
  const isEmpty = segments.length === 0

  return (
    <div
      className="flex flex-col h-full"
      data-testid="transcription-panel"
      aria-label="Phiên âm trực tiếp"
    >
      {/* Header */}
      <div className="flex items-center gap-2 px-4 py-3 border-b border-outline-variant shrink-0">
        <span className="material-symbols-outlined text-[18px] text-primary" aria-hidden="true">
          subtitles
        </span>
        <h3 className="text-body-sm font-semibold text-on-surface">Phiên âm trực tiếp</h3>
        {/* Live indicator */}
        <span className="ml-auto flex items-center gap-1 text-label-sm text-error">
          <span className="w-1.5 h-1.5 rounded-full bg-error animate-pulse" aria-hidden="true" />
          LIVE
        </span>
      </div>

      {/* Unavailable banner */}
      {!isTranscriptionAvailable && <UnavailableBanner />}

      {/* Segments list */}
      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto"
        role="log"
        aria-label="Danh sách phiên âm"
        aria-live="polite"
        aria-relevant="additions"
      >
        {isEmpty ? (
          <EmptyState />
        ) : (
          blocks.map((block, idx) => (
            <SpeakerBlockCard
              key={block.speakerTurnId}
              block={block}
              isLast={idx === blocks.length - 1}
            />
          ))
        )}
      </div>

      {/* Footer: segment count */}
      {!isEmpty && (
        <div className="px-4 py-2 border-t border-outline-variant shrink-0">
          <p className="text-label-sm text-on-surface-variant">
            {segments.length} đoạn phiên âm
          </p>
        </div>
      )}
    </div>
  )
}
