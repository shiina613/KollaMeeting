/**
 * TranscriptionPanel — displays real-time transcription segments.
 *
 * Full implementation in task 19.3.
 * This stub renders a placeholder so MeetingRoom.tsx can compile.
 *
 * Requirements: 8.12, 8.13
 */

export interface TranscriptionPanelProps {
  meetingId: number
  isHighPriority: boolean
}

export default function TranscriptionPanel({ isHighPriority }: TranscriptionPanelProps) {
  if (!isHighPriority) return null

  return (
    <div
      className="flex flex-col h-full"
      data-testid="transcription-panel"
      aria-label="Phiên âm trực tiếp"
    >
      <div className="flex items-center gap-2 px-4 py-3 border-b border-outline-variant">
        <span className="material-symbols-outlined text-[18px] text-primary" aria-hidden="true">
          subtitles
        </span>
        <h3 className="text-body-sm font-semibold text-on-surface">Phiên âm trực tiếp</h3>
      </div>
      <div className="flex-1 flex items-center justify-center text-on-surface-variant">
        <p className="text-body-sm">Sẽ được implement ở task 19.3</p>
      </div>
    </div>
  )
}
