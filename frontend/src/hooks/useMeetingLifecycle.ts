/**
 * useMeetingLifecycle — handles join/leave API calls and meeting store initialization.
 *
 * Extracts the meeting lifecycle logic from MeetingRoom into a reusable hook:
 * - Sets the active meeting in the meeting store on mount
 * - Calls POST /meetings/{id}/join on mount (attendance tracking)
 * - Calls POST /meetings/{id}/leave on unmount
 * - Clears the active meeting from the store on unmount
 * - Accepts an optional `onJoinError` callback for error handling
 *
 * Requirements: 13.4
 */

import { useEffect, useRef } from 'react'
import { getActiveParticipants, joinMeeting, leaveMeeting } from '../services/meetingService'
import useMeetingStore from '../store/meetingStore'
import type { Meeting, Participant } from '../types/meeting'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface UseMeetingLifecycleOptions {
  /** The meeting to join */
  meeting: Meeting
  /** Called when the join API call fails */
  onJoinError?: (message: string) => void
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

/**
 * useMeetingLifecycle
 *
 * Manages the meeting join/leave lifecycle for the active meeting session.
 * On mount: sets the active meeting in the store and calls the join API.
 * On unmount: calls the leave API and clears the active meeting from the store.
 *
 * Requirements: 13.4
 */
export function useMeetingLifecycle(options: UseMeetingLifecycleOptions): void {
  const { meeting, onJoinError } = options
  const { setActiveMeeting, clearActiveMeeting, setParticipants } = useMeetingStore()
  const hasJoinedRef = useRef(false)

  useEffect(() => {
    setActiveMeeting(meeting)

    // Notify backend that the user has joined (creates attendance log)
    if (!hasJoinedRef.current) {
      hasJoinedRef.current = true
      joinMeeting(meeting.id)
        .then(() => getActiveParticipants(meeting.id))
        .then((res) => {
          const participants: Participant[] = (res.data ?? []).map((log) => ({
            userId: log.userId,
            userName: log.userFullName?.trim() || log.username || `User ${log.userId}`,
            isConnected: true,
          }))
          setParticipants(participants)
        })
        .catch(() => {
          onJoinError?.('Không thể ghi nhận tham gia cuộc họp.')
        })
    }

    return () => {
      // Notify backend that the user has left
      leaveMeeting(meeting.id).catch(() => {
        // Non-critical: leave notification failure is silently ignored
      })
      clearActiveMeeting()
    }
  }, [meeting.id]) // eslint-disable-line react-hooks/exhaustive-deps
}
