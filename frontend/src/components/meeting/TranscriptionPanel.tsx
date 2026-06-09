/**
 * TranscriptionPanel — displays real-time transcription segments for HIGH_PRIORITY meetings.
 *
 * - Returns null when the meeting is not HIGH_PRIORITY (Req 8.13)
 * - Shows a TRANSCRIPTION_UNAVAILABLE banner when ASR service is down (Req 8.12)
 * - Renders segments sorted by (speakerTurnId, sequenceNumber) via useTranscription
 * - Auto-scrolls to the latest segment
 * - Groups consecutive segments from the same speaker into a single block
 *
 * Requirements: 8.12, 8.13
 */

import { Fragment, useEffect, useRef } from 'react'
import type { TranscriptionSegment } from '../../utils/audioUtils'
import { formatJavaZonedTime } from '../../utils/parseJavaZonedDateTime'
import type { MeetingRole } from '../../types/meeting'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface TranscriptionPanelProps {
  meetingId: number
  isHighPriority: boolean
  /** Sorted transcription segments — passed from MeetingRoom's useTranscription instance.
   *  Must NOT be read from a separate useTranscription() call inside this component,
   *  which would create an isolated instance that never receives events. */
  segments: TranscriptionSegment[]
  /** Whether the ASR service is currently available. */
  isTranscriptionAvailable: boolean
}

const MEETING_ROLE_LABELS: Record<MeetingRole, string> = {
  HOST: 'Chu tri',
  SECRETARY: 'Thu ky',
  REVIEWER: 'Phan bien',
  COMMITTEE_MEMBER: 'Uy vien',
  GUEST: 'Khach moi',
  MEMBER: 'Thanh vien',
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
      className="flex items-center gap-2 mx-3 my-2 px-3 py-2 rounded-lg bg-red-900/30 text-red-300 text-body-sm"
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
      className="flex flex-col items-center justify-center h-full gap-3 text-slate-400 px-4"
      data-testid="transcription-empty-state"
    >
      <span className="material-symbols-outlined text-[48px] opacity-60" aria-hidden="true">
        mic
      </span>
      <p className="text-body-sm text-center">
        Phiên âm sẽ xuất hiện khi người phát biểu bắt đầu nói.
      </p>
      <p className="text-body-sm text-center text-slate-500">
        Cuộc họp cần ở chế độ MEETING_MODE để phiên âm hoạt động.
      </p>
    </div>
  )
}

interface SpeakerBlockCardProps {
  block: SpeakerBlock
  isLast: boolean
}

interface AnimatedTranscriptTextProps {
  text: string
  animate: boolean
}

function AnimatedTranscriptText({ text, animate }: AnimatedTranscriptTextProps) {
  const trimmed = text.trim()
  if (!trimmed) return null
  if (!animate) return <>{trimmed}</>

  return (
    <>
      {trimmed.split(/\s+/).map((word, idx, words) => (
        <Fragment key={`${word}-${idx}`}>
          <span
            className="transcript-word"
            data-testid="transcript-word"
            style={{ animationDelay: `${idx * 70}ms` }}
          >
            {word}
          </span>
          {idx < words.length - 1 ? ' ' : null}
        </Fragment>
      ))}
    </>
  )
}

function SpeakerBlockCard({ block, isLast }: SpeakerBlockCardProps) {
  const firstSegment = block.segments[0]

  const time = formatJavaZonedTime(firstSegment.segmentStartTime ?? '', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
  const timeDisplay = time === '—' ? '--:--:--' : time

  // Format: "Tên - Phòng ban - UserId (2 digits)"
  const userId = String(firstSegment.speakerId).padStart(2, '0')
  const dept = firstSegment.speakerDept?.trim()
  const role = firstSegment.speakerRole ? MEETING_ROLE_LABELS[firstSegment.speakerRole] : undefined
  const speakerInfo = dept
    ? `${block.speakerName} - ${role ? `${role} - ` : ''}${dept} - ${userId}`
    : `${block.speakerName} - ${role ? `${role} - ` : ''}${userId}`

  return (
    <div
      className={`px-3 py-2.5 ${isLast ? '' : 'border-b border-slate-700'}`}
      data-testid="transcription-segment-block"
      data-speaker-turn-id={block.speakerTurnId}
    >
      {/* Format: Tên - Phòng - UserId: Nội dung <HH:MM:SS> */}
      <p className="text-body-sm text-slate-200 leading-relaxed break-words">
        <span className="font-semibold text-blue-300">{speakerInfo}:</span>
        <span className="text-slate-200">
          {' '}
          {block.segments.map((segment, idx) => (
            <Fragment key={segment.jobId}>
              {idx > 0 ? ' ' : null}
              <AnimatedTranscriptText
                text={segment.text}
                animate={isLast && idx === block.segments.length - 1}
              />
            </Fragment>
          ))}
          {' '}
        </span>
        <span className="text-slate-400 font-mono text-label-sm">&lt;{timeDisplay}&gt;</span>
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
export default function TranscriptionPanel({
  meetingId: _meetingId,
  isHighPriority,
  segments,
  isTranscriptionAvailable,
}: TranscriptionPanelProps) {
  // Only render for HIGH_PRIORITY meetings (Req 8.13)
  if (!isHighPriority) return null

  return (
    <TranscriptionPanelInner
      segments={segments}
      isTranscriptionAvailable={isTranscriptionAvailable}
    />
  )
}

/**
 * Inner component — separated so the hook is only called when isHighPriority is true.
 * Receives segments and availability from the parent to avoid creating a separate
 * hook instance that would never receive events.
 */
function TranscriptionPanelInner({
  segments,
  isTranscriptionAvailable,
}: Pick<TranscriptionPanelProps, 'segments' | 'isTranscriptionAvailable'>) {

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
      <div className="flex items-center gap-2 px-4 py-3 border-b border-slate-700 shrink-0">
        <span className="material-symbols-outlined text-[18px] text-blue-300" aria-hidden="true">
          subtitles
        </span>
        <h3 className="text-body-sm font-semibold text-slate-200">Phiên âm trực tiếp</h3>
        {/* Live indicator */}
        <span className="ml-auto flex items-center gap-1 text-label-sm text-red-400">
          <span className="w-1.5 h-1.5 rounded-full bg-red-400 animate-pulse" aria-hidden="true" />
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
    </div>
  )
}
