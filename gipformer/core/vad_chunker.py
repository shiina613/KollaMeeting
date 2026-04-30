"""
Gipformer ASR Service - Adaptive VAD Chunker
Detects silence boundaries in a streaming audio buffer and emits chunks.

Design rules (Requirement 8.9):
  - Accumulated duration < 15 s  → silence threshold 2–3 s (default 2.5 s)
  - Accumulated duration ≥ 15 s  → silence threshold 0.5–1 s (default 0.75 s)
  - Hard cap: 30 s — force-emit a chunk regardless of silence
  - sequence_number is tracked per speaker_turn_id, starting at 1 and
    incrementing monotonically for every chunk emitted within that turn.
"""

from __future__ import annotations

import logging
import threading
import time
from dataclasses import dataclass, field
from typing import Callable, Dict, Optional

import numpy as np

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

SAMPLE_RATE: int = 16_000  # Hz — all audio must be 16 kHz mono

# Adaptive silence thresholds (seconds)
SHORT_TURN_SILENCE_THRESHOLD: float = 2.5   # used when accumulated < 15 s
LONG_TURN_SILENCE_THRESHOLD: float = 0.75   # used when accumulated ≥ 15 s
LONG_TURN_BOUNDARY: float = 15.0            # seconds — threshold switch point

# Hard cap
HARD_CAP_SECONDS: float = 30.0

# Silence detection
DEFAULT_SILENCE_ENERGY_THRESHOLD: float = 0.01  # RMS energy below this = silence
SILENCE_WINDOW_SAMPLES: int = 160               # 10 ms window at 16 kHz


# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------


@dataclass
class AudioChunk:
    """A finalized audio chunk ready for transcription."""

    speaker_turn_id: str
    sequence_number: int          # 1-based, monotonically increasing per turn
    samples: np.ndarray           # float32, 16 kHz mono
    sample_rate: int = SAMPLE_RATE
    duration_seconds: float = 0.0
    accumulated_before: float = 0.0  # total seconds accumulated before this chunk
    timestamp: float = field(default_factory=time.time)

    def __post_init__(self) -> None:
        if self.duration_seconds == 0.0 and len(self.samples) > 0:
            self.duration_seconds = len(self.samples) / self.sample_rate


# ---------------------------------------------------------------------------
# VAD helper
# ---------------------------------------------------------------------------


def _is_silence(
    samples: np.ndarray,
    energy_threshold: float = DEFAULT_SILENCE_ENERGY_THRESHOLD,
) -> bool:
    """Return True if the RMS energy of *samples* is below *energy_threshold*."""
    if len(samples) == 0:
        return True
    rms = float(np.sqrt(np.mean(samples.astype(np.float64) ** 2)))
    return rms < energy_threshold


def _compute_adaptive_silence_threshold(accumulated_seconds: float) -> float:
    """Return the silence duration threshold (seconds) for the given accumulated time.

    Args:
        accumulated_seconds: Total audio duration accumulated so far in this turn.

    Returns:
        Silence threshold in seconds:
        - SHORT_TURN_SILENCE_THRESHOLD (2.5 s) when accumulated < 15 s
        - LONG_TURN_SILENCE_THRESHOLD  (0.75 s) when accumulated ≥ 15 s
    """
    if accumulated_seconds < LONG_TURN_BOUNDARY:
        return SHORT_TURN_SILENCE_THRESHOLD
    return LONG_TURN_SILENCE_THRESHOLD


# ---------------------------------------------------------------------------
# VadChunker
# ---------------------------------------------------------------------------


class VadChunker:
    """Stateful VAD chunker for a single speaker turn.

    Usage::

        chunker = VadChunker(
            speaker_turn_id="uuid-...",
            on_chunk=lambda chunk: queue.push(chunk),
        )
        chunker.feed(pcm_float32_array)   # call repeatedly as audio arrives
        chunker.finalize()                # flush remaining audio at turn end

    Thread safety: :meth:`feed` and :meth:`finalize` are protected by an
    internal lock so they can be called from different threads.
    """

    def __init__(
        self,
        speaker_turn_id: str,
        on_chunk: Callable[[AudioChunk], None],
        energy_threshold: float = DEFAULT_SILENCE_ENERGY_THRESHOLD,
        sample_rate: int = SAMPLE_RATE,
    ) -> None:
        """
        Args:
            speaker_turn_id: UUID identifying this speaking turn.
            on_chunk: Callback invoked with each finalized :class:`AudioChunk`.
            energy_threshold: RMS energy below which a window is considered silent.
            sample_rate: Expected sample rate of incoming audio (must be 16 kHz).
        """
        self._turn_id = speaker_turn_id
        self._on_chunk = on_chunk
        self._energy_threshold = energy_threshold
        self._sample_rate = sample_rate

        self._buffer: list[np.ndarray] = []
        self._accumulated_seconds: float = 0.0
        self._silence_seconds: float = 0.0
        self._sequence_number: int = 0   # incremented before each emit
        self._finalized: bool = False
        self._lock = threading.Lock()

        logger.debug("VadChunker created for turn %s", speaker_turn_id)

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    @property
    def speaker_turn_id(self) -> str:
        return self._turn_id

    @property
    def accumulated_seconds(self) -> float:
        """Total audio duration accumulated so far (including current buffer)."""
        with self._lock:
            return self._accumulated_seconds

    @property
    def sequence_number(self) -> int:
        """Last emitted sequence number (0 if no chunk emitted yet)."""
        with self._lock:
            return self._sequence_number

    def feed(self, samples: np.ndarray) -> None:
        """Feed new audio samples into the chunker.

        Args:
            samples: float32 numpy array at *sample_rate* Hz (mono).

        Raises:
            RuntimeError: If :meth:`finalize` has already been called.
        """
        if len(samples) == 0:
            return

        with self._lock:
            if self._finalized:
                raise RuntimeError(
                    f"VadChunker for turn {self._turn_id} has already been finalized"
                )
            self._feed_locked(samples)

    def finalize(self) -> Optional[AudioChunk]:
        """Flush any remaining buffered audio as a final chunk.

        Returns:
            The emitted :class:`AudioChunk`, or *None* if the buffer was empty.
        """
        with self._lock:
            if self._finalized:
                return None
            self._finalized = True
            return self._emit_chunk_locked(reason="finalize")

    # ------------------------------------------------------------------
    # Private helpers (must be called with _lock held)
    # ------------------------------------------------------------------

    def _feed_locked(self, samples: np.ndarray) -> None:
        """Process *samples* while holding the lock."""
        window_size = SILENCE_WINDOW_SAMPLES
        offset = 0

        while offset < len(samples):
            window = samples[offset : offset + window_size]
            offset += window_size

            window_duration = len(window) / self._sample_rate
            self._buffer.append(window)
            self._accumulated_seconds += window_duration

            if _is_silence(window, self._energy_threshold):
                self._silence_seconds += window_duration
            else:
                self._silence_seconds = 0.0

            # Check hard cap first
            if self._accumulated_seconds >= HARD_CAP_SECONDS:
                logger.debug(
                    "Turn %s: hard cap %.1f s reached, emitting chunk",
                    self._turn_id,
                    self._accumulated_seconds,
                )
                self._emit_chunk_locked(reason="hard_cap")
                continue

            # Check adaptive silence threshold
            silence_threshold = _compute_adaptive_silence_threshold(
                self._accumulated_seconds
            )
            if self._silence_seconds >= silence_threshold:
                logger.debug(
                    "Turn %s: silence %.2f s ≥ threshold %.2f s (accumulated=%.1f s), emitting chunk",
                    self._turn_id,
                    self._silence_seconds,
                    silence_threshold,
                    self._accumulated_seconds,
                )
                self._emit_chunk_locked(reason="silence")

    def _emit_chunk_locked(self, reason: str = "unknown") -> Optional[AudioChunk]:
        """Concatenate buffer, emit chunk via callback, reset state.

        Must be called with *_lock* held.

        Returns:
            The emitted :class:`AudioChunk`, or *None* if buffer is empty.
        """
        if not self._buffer:
            return None

        samples = np.concatenate(self._buffer).astype(np.float32)
        duration = len(samples) / self._sample_rate
        accumulated_before = self._accumulated_seconds - duration

        self._sequence_number += 1
        chunk = AudioChunk(
            speaker_turn_id=self._turn_id,
            sequence_number=self._sequence_number,
            samples=samples,
            sample_rate=self._sample_rate,
            duration_seconds=duration,
            accumulated_before=accumulated_before,
        )

        logger.info(
            "Turn %s: emitting chunk seq=%d duration=%.2f s reason=%s",
            self._turn_id,
            self._sequence_number,
            duration,
            reason,
        )

        # Reset buffer and silence counter; keep accumulated_seconds running total
        self._buffer = []
        self._silence_seconds = 0.0

        try:
            self._on_chunk(chunk)
        except Exception:
            logger.exception(
                "on_chunk callback raised for turn %s seq=%d",
                self._turn_id,
                self._sequence_number,
            )

        return chunk


# ---------------------------------------------------------------------------
# Turn registry — tracks sequence numbers across multiple turns
# ---------------------------------------------------------------------------


class TurnRegistry:
    """Thread-safe registry mapping speaker_turn_id → last sequence_number.

    Useful for verifying monotonicity and for reconstructing ordering when
    chunks arrive out of order.
    """

    def __init__(self) -> None:
        self._turns: Dict[str, int] = {}
        self._lock = threading.Lock()

    def next_sequence(self, speaker_turn_id: str) -> int:
        """Return the next sequence number for *speaker_turn_id* (1-based).

        Atomically increments the counter and returns the new value.
        """
        with self._lock:
            current = self._turns.get(speaker_turn_id, 0)
            next_seq = current + 1
            self._turns[speaker_turn_id] = next_seq
            return next_seq

    def last_sequence(self, speaker_turn_id: str) -> int:
        """Return the last emitted sequence number (0 if none yet)."""
        with self._lock:
            return self._turns.get(speaker_turn_id, 0)

    def reset_turn(self, speaker_turn_id: str) -> None:
        """Remove a turn from the registry (e.g. after meeting ends)."""
        with self._lock:
            self._turns.pop(speaker_turn_id, None)

    def all_turns(self) -> Dict[str, int]:
        """Return a snapshot of all tracked turns."""
        with self._lock:
            return dict(self._turns)
