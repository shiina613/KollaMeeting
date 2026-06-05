/**
 * ParticipantList — real-time list of participants in the active meeting.
 *
 * Driven by WebSocket events via the meetingStore.
 * Displays a speaking indicator for the participant holding Speaking_Permission.
 *
 * Requirements: 5.6, 11.1, 11.4
 */

import { useState, useCallback } from 'react'
import useMeetingStore from '../../store/meetingStore'
import type { Participant } from '../../types/meeting'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface ParticipantListProps {
  /** ID of the current user — used to highlight "you" */
  currentUserId?: number
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getInitials(name: string): string {
  return name
    .split(' ')
    .map((part) => part.charAt(0).toUpperCase())
    .slice(0, 2)
    .join('')
}

// ─── ParticipantItem ──────────────────────────────────────────────────────────

interface ParticipantItemProps {
  participant: Participant
  isSpeaking: boolean
  isCurrentUser: boolean
}

function ParticipantItem({ participant, isSpeaking, isCurrentUser }: ParticipantItemProps) {
  return (
    <li
      className={`flex items-center gap-2.5 px-3 py-2 rounded-lg transition-colors
        ${isSpeaking ? 'bg-green-900/30' : 'hover:bg-slate-700'}
        ${!participant.isConnected ? 'opacity-50' : ''}
      `}
      aria-label={`${participant.userName}${isCurrentUser ? ' (bạn)' : ''}${isSpeaking ? ', đang phát biểu' : ''}${!participant.isConnected ? ', đã ngắt kết nối' : ''}`}
    >
      {/* Avatar */}
      <div
        className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 text-label-md font-semibold
          ${isSpeaking
            ? 'bg-green-500 text-white ring-2 ring-green-400 ring-offset-1'
            : 'bg-primary/20 text-blue-300'
          }`}
        aria-hidden="true"
      >
        {getInitials(participant.userName)}
      </div>

      {/* Name */}
      <div className="flex-1 min-w-0">
        <span className="text-body-sm text-slate-200 truncate block">
          {participant.userName}
          {isCurrentUser && (
            <span className="text-slate-400 text-label-md ml-1">(bạn)</span>
          )}
        </span>
      </div>

      {/* Speaking indicator */}
      {isSpeaking && (
        <span
          className="material-symbols-outlined text-[18px] text-green-400 shrink-0"
          aria-hidden="true"
          title="Đang phát biểu"
          style={{ fontVariationSettings: "'FILL' 1" }}
        >
          mic
        </span>
      )}

      {/* Disconnected indicator */}
      {!participant.isConnected && (
        <span
          className="material-symbols-outlined text-[16px] text-slate-500 shrink-0"
          aria-hidden="true"
          title="Đã ngắt kết nối"
        >
          wifi_off
        </span>
      )}
    </li>
  )
}

// ─── ParticipantList ──────────────────────────────────────────────────────────

/**
 * ParticipantList
 *
 * Reads participants and speaking permission from the meeting store.
 * Updates in real-time as WebSocket events arrive.
 *
 * Requirements: 5.6
 */
export default function ParticipantList({ currentUserId }: ParticipantListProps) {
  const participants = useMeetingStore((s) => s.participants)
  const speakingPermission = useMeetingStore((s) => s.speakingPermission)

  const connected = participants.filter((p) => p.isConnected)
  const disconnected = participants.filter((p) => !p.isConnected)

  const activeMeeting = useMeetingStore((s) => s.activeMeeting)
  const [copied, setCopied] = useState(false)

  const handleCopyMeetingCode = useCallback(async () => {
    const code = activeMeeting?.meetingCode
    if (!code) return
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // Fallback: ignore clipboard errors silently
    }
  }, [activeMeeting?.meetingCode])

  if (participants.length === 0) {
    return (
      <div
        className="flex flex-col items-center justify-center py-8 text-slate-400 px-4"
        data-testid="participant-list-empty"
      >
        <span
          className="material-symbols-outlined text-[48px] opacity-60 mb-3"
          aria-hidden="true"
        >
          group
        </span>
        <p className="text-body-sm text-center mb-3">
          Thành viên sẽ xuất hiện khi họ tham gia cuộc họp.
        </p>
        {activeMeeting?.meetingCode && (
          <button
            onClick={handleCopyMeetingCode}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-body-sm font-medium
              bg-primary/20 text-blue-300 hover:bg-primary/30 transition-colors"
            aria-label="Sao chép mã cuộc họp"
            data-testid="copy-meeting-code-btn"
          >
            <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
              {copied ? 'check' : 'content_copy'}
            </span>
            {copied ? 'Đã sao chép!' : 'Sao chép mã cuộc họp'}
          </button>
        )}
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-1" data-testid="participant-list">
      {/* Connected participants */}
      <div className="mb-1">
        <p className="text-label-md text-slate-400 px-3 mb-1">
          Đang tham gia ({connected.length})
        </p>
        <ul className="space-y-0.5" aria-label="Danh sách thành viên đang tham gia">
          {connected.map((p) => (
            <ParticipantItem
              key={p.userId}
              participant={p}
              isSpeaking={speakingPermission?.userId === p.userId}
              isCurrentUser={p.userId === currentUserId}
            />
          ))}
        </ul>
      </div>

      {/* Disconnected participants */}
      {disconnected.length > 0 && (
        <div>
          <p className="text-label-md text-slate-400 px-3 mb-1">
            Đã rời ({disconnected.length})
          </p>
          <ul className="space-y-0.5" aria-label="Danh sách thành viên đã rời">
            {disconnected.map((p) => (
              <ParticipantItem
                key={p.userId}
                participant={p}
                isSpeaking={false}
                isCurrentUser={p.userId === currentUserId}
              />
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
