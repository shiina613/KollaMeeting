/**
 * MeetingRoomPage — protected page for an active meeting session.
 *
 * Loads the meeting by ID from the URL, validates that it is ACTIVE,
 * then renders the MeetingRoom container.
 *
 * Requirements: 4.6, 5.1, 5.3
 */

import { useEffect, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import MeetingRoom from '../components/meeting/MeetingRoom'
import { getMeeting } from '../services/meetingService'
import type { Meeting } from '../types/meeting'

// ─── MeetingRoomPage ──────────────────────────────────────────────────────────

export default function MeetingRoomPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const [meeting, setMeeting] = useState<Meeting | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const meetingId = Number(id)

  useEffect(() => {
    if (!meetingId) {
      setError('ID cuộc họp không hợp lệ.')
      setLoading(false)
      return
    }

    getMeeting(meetingId)
      .then((res) => {
        const m = res.data
        if (m.status !== 'ACTIVE') {
          // Meeting is not active — redirect to detail page
          navigate(`/meetings/${meetingId}`, { replace: true })
          return
        }
        setMeeting(m)
      })
      .catch(() => {
        setError('Không thể tải thông tin cuộc họp.')
      })
      .finally(() => setLoading(false))
  }, [meetingId, navigate])

  // ── Loading ────────────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-900">
        <div className="flex flex-col items-center gap-3 text-white">
          <div className="w-10 h-10 border-4 border-white border-t-transparent rounded-full animate-spin" />
          <p className="text-body-sm text-slate-300">Đang tải phòng họp...</p>
        </div>
      </div>
    )
  }

  // ── Error ──────────────────────────────────────────────────────────────────

  if (error || !meeting) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-slate-900 text-white gap-4">
        <span className="material-symbols-outlined text-5xl text-slate-400" aria-hidden="true">
          error_outline
        </span>
        <p className="text-body-md text-slate-300">{error ?? 'Không tìm thấy cuộc họp'}</p>
        <Link
          to="/meetings"
          className="text-primary hover:underline text-body-sm"
        >
          Quay lại danh sách cuộc họp
        </Link>
      </div>
    )
  }

  // ── Meeting room ───────────────────────────────────────────────────────────

  return <MeetingRoom meeting={meeting} />
}
