/**
 * RaiseHandPanel — displays pending raise-hand requests and grant/revoke controls.
 *
 * Full implementation in task 18.1.
 * This stub renders a placeholder so MeetingRoom.tsx can compile.
 *
 * Requirements: 22.3, 22.9
 */

export interface RaiseHandPanelProps {
  meetingId: number
  isHost: boolean
}

export default function RaiseHandPanel({ isHost }: RaiseHandPanelProps) {
  if (!isHost) return null

  return (
    <div
      className="flex flex-col h-full"
      data-testid="raise-hand-panel"
      aria-label="Danh sách xin phát biểu"
    >
      <div className="flex items-center gap-2 px-4 py-3 border-b border-outline-variant">
        <span className="material-symbols-outlined text-[18px] text-amber-600" aria-hidden="true">
          pan_tool
        </span>
        <h3 className="text-body-sm font-semibold text-on-surface">Xin phát biểu</h3>
      </div>
      <div className="flex-1 flex items-center justify-center text-on-surface-variant">
        <p className="text-body-sm">Sẽ được implement ở task 18.1</p>
      </div>
    </div>
  )
}
