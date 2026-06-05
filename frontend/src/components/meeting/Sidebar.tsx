/**
 * Sidebar — responsive sidebar for the MeetingRoom page.
 *
 * Wraps existing tab panels (ParticipantList, RaiseHandPanel, TranscriptionPanel)
 * with responsive behavior, dark theme, focus management, and drag-resize.
 *
 * Responsive layout:
 * - < 768px: full-screen overlay with close button
 * - 768px–1023px: fixed 280px, no resize handle
 * - ≥ 1024px: resizable 200–600px (default 288px)
 *
 * Dark theme: slate-800 background, slate-300/white text.
 * Tab bar: role="tablist", aria-selected, Arrow Left/Right keyboard navigation.
 * Tab panels: role="tabpanel", aria-labelledby.
 * Loading states: SkeletonLoader for participant/raise-hand panels.
 *
 * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 6.1, 6.2, 6.3, 6.4, 6.5,
 *              10.1, 10.2, 10.3, 10.4, 14.1, 14.2, 15.1, 15.2, 15.3, 15.4
 */

import { useRef, useCallback, useState } from 'react'
import { useResizable } from '../../hooks/useResizable'
import SkeletonLoader from './SkeletonLoader'
import ParticipantList from './ParticipantList'
import RaiseHandPanel from './RaiseHandPanel'
import TranscriptionPanel from './TranscriptionPanel'
import useMeetingStore from '../../store/meetingStore'
import type { TranscriptionSegment } from '../../utils/audioUtils'
import type { Meeting } from '../../types/meeting'

// ─── Types ────────────────────────────────────────────────────────────────────

export type SidebarTab = 'participants' | 'transcription' | 'raise-hand'

export interface SidebarProps {
  isOpen: boolean
  onClose: () => void
  activeTab: SidebarTab
  onTabChange: (tab: SidebarTab) => void
  meeting: Meeting
  isHost: boolean
  isSecretary: boolean
  currentUserId?: number
  /** Ref for focus management */
  sidebarRef: React.RefObject<HTMLDivElement>
  /** Ref for the toggle button (focus return target) */
  toggleButtonRef: React.RefObject<HTMLButtonElement>
  /** Realtime transcription segments — must come from MeetingRoom's useTranscription instance */
  segments: TranscriptionSegment[]
  /** Whether the ASR service is available */
  isTranscriptionAvailable: boolean
}

// ─── Tab definitions ──────────────────────────────────────────────────────────

interface TabDef {
  key: SidebarTab
  label: string
  icon: string
  id: string
  panelId: string
}

// ─── Constants ────────────────────────────────────────────────────────────────

const SIDEBAR_MIN = 200
const SIDEBAR_MAX = 600
const SIDEBAR_DEFAULT = 288

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * Sidebar
 *
 * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 6.1, 6.2, 6.3, 6.4, 6.5,
 *              10.1, 10.2, 10.3, 10.4, 14.1, 14.2, 15.1, 15.2, 15.3, 15.4
 */
export default function Sidebar({
  isOpen,
  onClose,
  activeTab,
  onTabChange,
  meeting,
  isHost,
  isSecretary,
  currentUserId,
  segments,
  isTranscriptionAvailable,
  sidebarRef,
  toggleButtonRef: _toggleButtonRef,
}: SidebarProps) {
  const tabListRef = useRef<HTMLDivElement>(null)
  const [isLoading, setIsLoading] = useState(false)

  // ── Meeting state ──────────────────────────────────────────────────────────
  const mode = useMeetingStore((s) => s.mode)
  const isMeetingMode = mode === 'MEETING_MODE'
  const isHighPriority = meeting.transcriptionPriority === 'HIGH_PRIORITY'

  // ── Resizable hook (only enabled on lg+ breakpoint) ────────────────────────
  const { width, isDragging, isAtLimit, handleMouseDown } = useResizable({
    min: SIDEBAR_MIN,
    max: SIDEBAR_MAX,
    defaultWidth: SIDEBAR_DEFAULT,
    enabled: true, // JS-side always enabled; CSS hides handle on < lg
  })

  // ── Tab definitions (dynamic based on meeting state) ───────────────────────
  const tabs: TabDef[] = [
    {
      key: 'participants',
      label: 'Thành viên',
      icon: 'group',
      id: 'sidebar-tab-participants',
      panelId: 'sidebar-panel-participants',
    },
    ...(isMeetingMode && (isHost || isSecretary)
      ? [{
          key: 'raise-hand' as const,
          label: 'Xin phát biểu',
          icon: 'pan_tool',
          id: 'sidebar-tab-raise-hand',
          panelId: 'sidebar-panel-raise-hand',
        }]
      : []),
    ...(isHighPriority
      ? [{
          key: 'transcription' as const,
          label: 'Phiên âm',
          icon: 'subtitles',
          id: 'sidebar-tab-transcription',
          panelId: 'sidebar-panel-transcription',
        }]
      : []),
  ]

  // ── Keyboard navigation for tabs (Arrow Left/Right) ────────────────────────
  const handleTabKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return

      e.preventDefault()
      const currentIndex = tabs.findIndex((t) => t.key === activeTab)
      let nextIndex: number

      if (e.key === 'ArrowRight') {
        nextIndex = (currentIndex + 1) % tabs.length
      } else {
        nextIndex = (currentIndex - 1 + tabs.length) % tabs.length
      }

      onTabChange(tabs[nextIndex].key)

      // Move focus to the new tab button
      const tabButtons = tabListRef.current?.querySelectorAll<HTMLButtonElement>('[role="tab"]')
      tabButtons?.[nextIndex]?.focus()
    },
    [activeTab, tabs, onTabChange],
  )

  // ── Simulate loading state for skeleton (brief flash on tab change) ────────
  const handleTabClick = useCallback(
    (tabKey: SidebarTab) => {
      if (tabKey === activeTab) return
      setIsLoading(true)
      onTabChange(tabKey)
      // Brief loading state to show skeleton
      setTimeout(() => setIsLoading(false), 150)
    },
    [activeTab, onTabChange],
  )

  if (!isOpen) return null

  // ── Active tab definition ──────────────────────────────────────────────────
  const activeTabDef = tabs.find((t) => t.key === activeTab) ?? tabs[0]

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <>
      {/* Mobile overlay backdrop (< 768px) */}
      <div
        className="fixed inset-0 bg-black/50 z-40 md:hidden"
        onClick={onClose}
        aria-hidden="true"
        data-testid="sidebar-backdrop"
      />

      {/* Sidebar container */}
      <div
        ref={sidebarRef}
        className={`
          fixed inset-0 z-50 flex flex-col bg-slate-800 text-slate-300
          md:relative md:inset-auto md:z-auto md:w-[280px] md:shrink-0 md:border-l md:border-slate-700
          lg:shrink-0 lg:border-l lg:border-slate-700
        `}
        style={{
          // On lg+, use resizable width; on md, fixed 280px; on mobile, full-screen (handled by CSS)
          width: undefined,
        }}
        role="complementary"
        aria-label="Thanh bên cuộc họp"
        data-testid="meeting-sidebar"
      >
        {/* lg+ inline style for resizable width */}
        <style>{`
          @media (min-width: 1024px) {
            [data-testid="meeting-sidebar"] {
              width: ${width}px !important;
              position: relative !important;
              inset: auto !important;
              z-index: auto !important;
            }
          }
          @media (min-width: 768px) and (max-width: 1023px) {
            [data-testid="meeting-sidebar"] {
              width: 280px !important;
              position: relative !important;
              inset: auto !important;
              z-index: auto !important;
            }
          }
        `}</style>

        {/* ── Close button (mobile overlay only) ── */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-slate-700 md:hidden shrink-0">
          <h2 className="text-body-sm font-semibold text-white">Thanh bên</h2>
          <button
            onClick={onClose}
            className="p-1.5 rounded-md text-slate-300 hover:text-white hover:bg-slate-700 transition-colors"
            aria-label="Đóng thanh bên"
            data-testid="sidebar-close-button"
          >
            <span className="material-symbols-outlined text-[20px]" aria-hidden="true">
              close
            </span>
          </button>
        </div>

        {/* ── Resize handle (lg+ only) ── */}
        <div
          onMouseDown={handleMouseDown}
          className={`
            absolute left-0 top-0 bottom-0 w-2 cursor-col-resize z-10
            hidden lg:block
            transition-colors
            ${isDragging
              ? isAtLimit
                ? 'bg-red-500/40'
                : 'bg-primary/60'
              : isAtLimit
                ? 'hover:bg-red-500/40'
                : 'hover:bg-primary/40'
            }
          `}
          aria-hidden="true"
          title="Kéo để thay đổi kích thước"
          data-testid="sidebar-resize-handle"
        >
          {/* Visual indicator dots */}
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2
                          flex flex-col gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
            <span className="w-1 h-1 rounded-full bg-slate-400" />
            <span className="w-1 h-1 rounded-full bg-slate-400" />
            <span className="w-1 h-1 rounded-full bg-slate-400" />
          </div>
        </div>

        {/* ── Tab bar ── */}
        <div
          ref={tabListRef}
          className="flex border-b border-slate-700 overflow-x-auto shrink-0"
          role="tablist"
          aria-label="Nội dung thanh bên"
          data-testid="sidebar-tablist"
        >
          {tabs.map((tab) => (
            <button
              key={tab.key}
              id={tab.id}
              onClick={() => handleTabClick(tab.key)}
              onKeyDown={handleTabKeyDown}
              className={`flex items-center gap-1.5 px-3 py-2.5 text-label-md font-medium whitespace-nowrap
                          border-b-2 transition-colors flex-1 justify-center
                          ${activeTab === tab.key
                            ? 'border-primary text-white'
                            : 'border-transparent text-slate-300 hover:text-white'
                          }`}
              aria-selected={activeTab === tab.key}
              aria-controls={tab.panelId}
              role="tab"
              tabIndex={activeTab === tab.key ? 0 : -1}
              data-testid={`sidebar-tab-${tab.key}`}
            >
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                {tab.icon}
              </span>
              {/* Show label always on mobile overlay, icon-only on md, icon+label on lg+ */}
              <span className="inline md:hidden lg:inline">{tab.label}</span>
            </button>
          ))}
        </div>

        {/* ── Tab panel ── */}
        <div
          id={activeTabDef.panelId}
          className="flex-1 overflow-y-auto"
          role="tabpanel"
          aria-labelledby={activeTabDef.id}
          data-testid={`sidebar-panel-${activeTab}`}
        >
          {isLoading ? (
            <div className="py-3">
              <SkeletonLoader
                rows={activeTab === 'raise-hand' ? 3 : 4}
                variant={activeTab === 'raise-hand' ? 'raise-hand' : 'participant'}
              />
            </div>
          ) : (
            <>
              {activeTab === 'participants' && (
                <div className="py-2">
                  <ParticipantList currentUserId={currentUserId} />
                </div>
              )}

              {activeTab === 'raise-hand' && (
                <RaiseHandPanel
                  meetingId={meeting.id}
                  isHost={isHost || isSecretary}
                />
              )}

              {activeTab === 'transcription' && (
                <TranscriptionPanel
                  meetingId={meeting.id}
                  isHighPriority={isHighPriority}
                  segments={segments}
                  isTranscriptionAvailable={isTranscriptionAvailable}
                />
              )}
            </>
          )}
        </div>
      </div>
    </>
  )
}
