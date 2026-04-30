/**
 * RaiseHandPanel — displays pending raise-hand requests in chronological order
 * and provides Grant/Revoke speaking permission controls for the Host.
 *
 * - Reads raiseHandRequests and speakingPermission from meetingStore (driven by WebSocket)
 * - Fetches initial list from REST API on mount
 * - Grant: calls POST /meetings/{id}/speaking-permission/{userId}
 * - Revoke: calls DELETE /meetings/{id}/speaking-permission
 *
 * Requirements: 22.3, 22.9
 */

import { useEffect, useState, useCallback } from 'react'
import useMeetingStore from '../../store/meetingStore'
import {
  listRaiseHandRequests,
  grantSpeakingPermission,
  revokeSpeakingPermission,
} from '../../services/meetingService'
import type { RaiseHandRequest } from '../../types/meeting'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface RaiseHandPanelProps {
  meetingId: number
  isHost: boolean
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getInitials(name: string): string {
  return name
    .split(' ')
    .map((part) => part.charAt(0).toUpperCase())
    .slice(0, 2)
    .join('')
}

function formatTime(isoString: string): string {
  try {
    return new Date(isoString).toLocaleTimeString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      timeZone: 'Asia/Ho_Chi_Minh',
    })
  } catch {
    return ''
  }
}

// ─── RaiseHandItem ────────────────────────────────────────────────────────────

interface RaiseHandItemProps {
  request: RaiseHandRequest
  isCurrentSpeaker: boolean
  isGranting: boolean
  isRevoking: boolean
  onGrant: (userId: number) => void
  onRevoke: () => void
}

function RaiseHandItem({
  request,
  isCurrentSpeaker,
  isGranting,
  isRevoking,
  onGrant,
  onRevoke,
}: RaiseHandItemProps) {
  return (
    <li
      className={`flex items-center gap-2.5 px-3 py-2.5 rounded-lg transition-colors
        ${isCurrentSpeaker ? 'bg-green-50 border border-green-200' : 'hover:bg-surface-container'}
      `}
      data-testid={`raise-hand-item-${request.userId}`}
      aria-label={`${request.userName}${isCurrentSpeaker ? ', đang phát biểu' : ''}, xin phát biểu lúc ${formatTime(request.requestedAt)}`}
    >
      {/* Avatar */}
      <div
        className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0
          text-label-md font-semibold
          ${isCurrentSpeaker
            ? 'bg-green-500 text-white ring-2 ring-green-400 ring-offset-1'
            : 'bg-amber-100 text-amber-700'
          }`}
        aria-hidden="true"
      >
        {getInitials(request.userName)}
      </div>

      {/* Name + time */}
      <div className="flex-1 min-w-0">
        <p className="text-body-sm font-medium text-on-surface truncate">
          {request.userName}
        </p>
        <p className="text-label-md text-on-surface-variant">
          {formatTime(request.requestedAt)}
        </p>
      </div>

      {/* Speaking indicator */}
      {isCurrentSpeaker && (
        <span
          className="material-symbols-outlined text-[18px] text-green-600 shrink-0"
          aria-hidden="true"
          title="Đang phát biểu"
          style={{ fontVariationSettings: "'FILL' 1" }}
        >
          mic
        </span>
      )}

      {/* Action buttons */}
      <div className="flex items-center gap-1 shrink-0">
        {isCurrentSpeaker ? (
          /* Revoke button — shown when this user is the current speaker */
          <button
            onClick={onRevoke}
            disabled={isRevoking}
            className="inline-flex items-center gap-1 px-2 py-1 rounded text-label-md font-medium
              bg-red-100 text-red-700 hover:bg-red-200 disabled:opacity-50 disabled:cursor-not-allowed
              transition-colors"
            aria-label={`Thu hồi quyền phát biểu của ${request.userName}`}
            data-testid={`revoke-btn-${request.userId}`}
          >
            {isRevoking ? (
              <div className="w-3 h-3 border-2 border-red-600 border-t-transparent rounded-full animate-spin" />
            ) : (
              <span className="material-symbols-outlined text-[14px]" aria-hidden="true">
                mic_off
              </span>
            )}
            Thu hồi
          </button>
        ) : (
          /* Grant button — shown for pending requests */
          <button
            onClick={() => onGrant(request.userId)}
            disabled={isGranting}
            className="inline-flex items-center gap-1 px-2 py-1 rounded text-label-md font-medium
              bg-green-100 text-green-700 hover:bg-green-200 disabled:opacity-50 disabled:cursor-not-allowed
              transition-colors"
            aria-label={`Cấp quyền phát biểu cho ${request.userName}`}
            data-testid={`grant-btn-${request.userId}`}
          >
            {isGranting ? (
              <div className="w-3 h-3 border-2 border-green-600 border-t-transparent rounded-full animate-spin" />
            ) : (
              <span className="material-symbols-outlined text-[14px]" aria-hidden="true">
                mic
              </span>
            )}
            Cấp quyền
          </button>
        )}
      </div>
    </li>
  )
}

// ─── RaiseHandPanel ───────────────────────────────────────────────────────────

/**
 * RaiseHandPanel
 *
 * Host-only view of pending raise-hand requests.
 * Requests are displayed in chronological order (oldest first — Req 22.9).
 * The Host can grant speaking permission to any pending requester,
 * or revoke permission from the current speaker.
 *
 * Requirements: 22.3, 22.9
 */
export default function RaiseHandPanel({ meetingId, isHost }: RaiseHandPanelProps) {
  const raiseHandRequests = useMeetingStore((s) => s.raiseHandRequests)
  const speakingPermission = useMeetingStore((s) => s.speakingPermission)
  const addRaiseHandRequest = useMeetingStore((s) => s.addRaiseHandRequest)

  const [grantingUserId, setGrantingUserId] = useState<number | null>(null)
  const [isRevoking, setIsRevoking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // ── Fetch initial list on mount ────────────────────────────────────────────

  useEffect(() => {
    if (!isHost) return

    listRaiseHandRequests(meetingId)
      .then((res) => {
        // Seed the store with the initial list (WebSocket events will keep it updated)
        const requests: RaiseHandRequest[] = res.data ?? []
        // Sort chronologically (oldest first) before seeding
        const sorted = [...requests].sort(
          (a, b) => new Date(a.requestedAt).getTime() - new Date(b.requestedAt).getTime(),
        )
        sorted.forEach((r) => addRaiseHandRequest(r))
      })
      .catch((err) => {
        console.error('[RaiseHandPanel] Failed to fetch raise-hand requests:', err)
      })
  }, [meetingId, isHost]) // eslint-disable-line react-hooks/exhaustive-deps

  // ── Grant speaking permission ──────────────────────────────────────────────

  const handleGrant = useCallback(
    async (userId: number) => {
      if (grantingUserId !== null || isRevoking) return

      setGrantingUserId(userId)
      setError(null)

      try {
        await grantSpeakingPermission(meetingId, userId)
        // The WebSocket SPEAKING_PERMISSION_GRANTED event will update the store
      } catch (err) {
        const message =
          (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
          'Không thể cấp quyền phát biểu. Vui lòng thử lại.'
        setError(message)
      } finally {
        setGrantingUserId(null)
      }
    },
    [meetingId, grantingUserId, isRevoking],
  )

  // ── Revoke speaking permission ─────────────────────────────────────────────

  const handleRevoke = useCallback(async () => {
    if (grantingUserId !== null || isRevoking) return

    setIsRevoking(true)
    setError(null)

    try {
      await revokeSpeakingPermission(meetingId)
      // The WebSocket SPEAKING_PERMISSION_REVOKED event will update the store
    } catch (err) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Không thể thu hồi quyền phát biểu. Vui lòng thử lại.'
      setError(message)
    } finally {
      setIsRevoking(false)
    }
  }, [meetingId, grantingUserId, isRevoking])

  // ── Guard: non-host sees nothing ───────────────────────────────────────────

  if (!isHost) return null

  // ── Build display list ─────────────────────────────────────────────────────
  // Show the current speaker at the top (if they raised their hand),
  // followed by remaining pending requests in chronological order.

  const currentSpeakerId = speakingPermission?.userId ?? null

  // Merge: if the current speaker is not in the raise-hand list (e.g. Host
  // granted directly), synthesise a minimal entry so the Revoke button shows.
  const speakerInList = raiseHandRequests.some((r) => r.userId === currentSpeakerId)
  const syntheticSpeakerEntry: RaiseHandRequest | null =
    speakingPermission && !speakerInList
      ? {
          userId: speakingPermission.userId,
          userName: speakingPermission.userName,
          requestedAt: speakingPermission.grantedAt,
        }
      : null

  const displayList: RaiseHandRequest[] = [
    ...(syntheticSpeakerEntry ? [syntheticSpeakerEntry] : []),
    ...raiseHandRequests,
  ]

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div
      className="flex flex-col h-full"
      data-testid="raise-hand-panel"
      aria-label="Danh sách xin phát biểu"
    >
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-outline-variant shrink-0">
        <div className="flex items-center gap-2">
          <span
            className="material-symbols-outlined text-[18px] text-amber-600"
            aria-hidden="true"
          >
            pan_tool
          </span>
          <h3 className="text-body-sm font-semibold text-on-surface">Xin phát biểu</h3>
        </div>

        {/* Count badge */}
        {displayList.length > 0 && (
          <span
            className="inline-flex items-center justify-center w-5 h-5 rounded-full
              bg-amber-500 text-white text-label-md font-bold"
            aria-label={`${displayList.length} yêu cầu`}
          >
            {displayList.length}
          </span>
        )}
      </div>

      {/* Error banner */}
      {error && (
        <div
          className="mx-3 mt-2 px-3 py-2 rounded-lg bg-error-container text-on-error-container
            text-body-sm"
          role="alert"
          data-testid="raise-hand-error"
        >
          {error}
        </div>
      )}

      {/* List */}
      <div className="flex-1 overflow-y-auto py-2">
        {displayList.length === 0 ? (
          <div
            className="flex flex-col items-center justify-center py-8 text-on-surface-variant"
            data-testid="raise-hand-empty"
          >
            <span
              className="material-symbols-outlined text-3xl mb-2"
              aria-hidden="true"
            >
              pan_tool
            </span>
            <p className="text-body-sm">Chưa có yêu cầu phát biểu</p>
          </div>
        ) : (
          <ul
            className="space-y-1 px-2"
            aria-label="Danh sách yêu cầu phát biểu theo thứ tự thời gian"
            data-testid="raise-hand-list"
          >
            {displayList.map((request) => (
              <RaiseHandItem
                key={request.userId}
                request={request}
                isCurrentSpeaker={request.userId === currentSpeakerId}
                isGranting={grantingUserId === request.userId}
                isRevoking={isRevoking && request.userId === currentSpeakerId}
                onGrant={handleGrant}
                onRevoke={handleRevoke}
              />
            ))}
          </ul>
        )}
      </div>

      {/* Footer hint */}
      <div className="px-4 py-2 border-t border-outline-variant shrink-0">
        <p className="text-label-md text-on-surface-variant">
          Yêu cầu được sắp xếp theo thứ tự thời gian
        </p>
      </div>
    </div>
  )
}
