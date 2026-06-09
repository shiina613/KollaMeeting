import { render, screen } from '@testing-library/react'
import TranscriptionPanel from './TranscriptionPanel'
import type { TranscriptionSegment } from '../../utils/audioUtils'

function segment(overrides: Partial<TranscriptionSegment>): TranscriptionSegment {
  return {
    jobId: 'job-1',
    speakerId: 7,
    speakerName: 'Nguyen Quang Tung',
    speakerDept: 'CNTT',
    speakerRole: 'HOST',
    speakerTurnId: 'turn-1',
    sequenceNumber: 1,
    text: 'old static text',
    confidence: null,
    segmentStartTime: '2026-06-09T09:52:00+07:00',
    ...overrides,
  }
}

describe('TranscriptionPanel', () => {
  it('animates only the words in the newest segment', () => {
    render(
      <TranscriptionPanel
        meetingId={5}
        isHighPriority
        isTranscriptionAvailable
        segments={[
          segment({
            jobId: 'job-old',
            sequenceNumber: 1,
            text: 'old static text',
          }),
          segment({
            jobId: 'job-new',
            sequenceNumber: 2,
            text: 'new words now',
            segmentStartTime: '2026-06-09T09:52:05+07:00',
          }),
        ]}
      />,
    )

    const animatedWords = screen.getAllByTestId('transcript-word')

    expect(animatedWords).toHaveLength(3)
    expect(animatedWords.map((word) => word.textContent)).toEqual(['new', 'words', 'now'])
    expect(animatedWords[0]).toHaveClass('transcript-word')
    expect(animatedWords[0]).toHaveStyle({ animationDelay: '0ms' })
    expect(animatedWords[1]).toHaveStyle({ animationDelay: '70ms' })
    expect(animatedWords[2]).toHaveStyle({ animationDelay: '140ms' })
    expect(screen.getByTestId('transcription-segment-block')).toHaveTextContent(
      'old static text new words now',
    )
  })
})
