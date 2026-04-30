export interface Notification {
  id: number
  title: string
  message: string
  type: 'MEETING_STARTED' | 'MEETING_ENDED' | 'MINUTES_PUBLISHED' | 'GENERAL'
  read: boolean
  createdAt: string  // ISO 8601 UTC+7
  meetingId?: number
}
