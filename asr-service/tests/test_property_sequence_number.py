"""
Property 6: Sequence Number Monotonicity
Validates: Requirements 8.9

Properties:
  1. Sequence numbers start at 1 for each new speaker_turn_id
  2. Each subsequent chunk within the same turn has a strictly higher sequence number
  3. Sequence numbers from different turns are independent
  4. TurnRegistry.next_sequence() is strictly monotonically increasing per turn

Uses hypothesis for property-based testing.
"""

import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import threading
import uuid
from typing import List

import numpy as np
import pytest
from hypothesis import given, settings, assume
from hypothesis import strategies as st

from core.vad_chunker import AudioChunk, TurnRegistry, VadChunker, SAMPLE_RATE


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _make_silence(duration_seconds: float, sample_rate: int = SAMPLE_RATE) -> np.ndarray:
    """Generate a silent (near-zero) audio array."""
    n = int(duration_seconds * sample_rate)
    return np.zeros(n, dtype=np.float32)


def _make_speech(duration_seconds: float, sample_rate: int = SAMPLE_RATE) -> np.ndarray:
    """Generate a non-silent audio array (sine wave)."""
    n = int(duration_seconds * sample_rate)
    t = np.linspace(0, duration_seconds, n, dtype=np.float32)
    return (0.5 * np.sin(2 * np.pi * 440 * t)).astype(np.float32)


# ---------------------------------------------------------------------------
# Property 6a: TurnRegistry monotonicity
# ---------------------------------------------------------------------------


class TestTurnRegistryMonotonicity:
    """Property tests for TurnRegistry sequence number tracking."""

    @given(st.integers(min_value=1, max_value=100))
    @settings(max_examples=200)
    def test_sequence_starts_at_one(self, _unused: int) -> None:
        """Property: first call to next_sequence for a new turn returns 1."""
        registry = TurnRegistry()
        turn_id = str(uuid.uuid4())
        seq = registry.next_sequence(turn_id)
        assert seq == 1, f"First sequence number must be 1, got {seq}"

    @given(st.integers(min_value=2, max_value=50))
    @settings(max_examples=200)
    def test_sequence_strictly_increasing(self, n_chunks: int) -> None:
        """Property: sequence numbers are strictly increasing (1, 2, 3, …, n)."""
        registry = TurnRegistry()
        turn_id = str(uuid.uuid4())
        sequences = [registry.next_sequence(turn_id) for _ in range(n_chunks)]

        for i, seq in enumerate(sequences):
            assert seq == i + 1, (
                f"Expected sequence {i + 1} at position {i}, got {seq}"
            )

        # Verify strict monotonicity
        for i in range(len(sequences) - 1):
            assert sequences[i] < sequences[i + 1], (
                f"Sequence not strictly increasing: {sequences[i]} >= {sequences[i + 1]}"
            )

    @given(st.integers(min_value=2, max_value=10))
    @settings(max_examples=100)
    def test_different_turns_are_independent(self, n_turns: int) -> None:
        """Property: sequence numbers for different turns are independent."""
        registry = TurnRegistry()
        turn_ids = [str(uuid.uuid4()) for _ in range(n_turns)]

        # Advance each turn by a different amount
        for i, turn_id in enumerate(turn_ids):
            for _ in range(i + 1):
                registry.next_sequence(turn_id)

        # Verify each turn has its own counter
        for i, turn_id in enumerate(turn_ids):
            last = registry.last_sequence(turn_id)
            assert last == i + 1, (
                f"Turn {i} should have last_sequence={i + 1}, got {last}"
            )

    def test_reset_turn_restarts_from_one(self) -> None:
        """After reset_turn, the next sequence for that turn starts at 1 again."""
        registry = TurnRegistry()
        turn_id = str(uuid.uuid4())

        for _ in range(5):
            registry.next_sequence(turn_id)

        assert registry.last_sequence(turn_id) == 5

        registry.reset_turn(turn_id)
        assert registry.last_sequence(turn_id) == 0

        seq = registry.next_sequence(turn_id)
        assert seq == 1

    def test_concurrent_increments_are_safe(self) -> None:
        """Property: concurrent calls to next_sequence produce unique, monotonic values."""
        registry = TurnRegistry()
        turn_id = str(uuid.uuid4())
        n_threads = 10
        n_calls_per_thread = 20
        results: List[int] = []
        lock = threading.Lock()

        def worker() -> None:
            for _ in range(n_calls_per_thread):
                seq = registry.next_sequence(turn_id)
                with lock:
                    results.append(seq)

        threads = [threading.Thread(target=worker) for _ in range(n_threads)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        total = n_threads * n_calls_per_thread
        assert len(results) == total
        # All values must be unique (no duplicates from race conditions)
        assert len(set(results)) == total, "Concurrent increments produced duplicate sequence numbers"
        # Values must cover 1..total exactly
        assert sorted(results) == list(range(1, total + 1))


# ---------------------------------------------------------------------------
# Property 6b: VadChunker sequence number monotonicity
# ---------------------------------------------------------------------------


class TestVadChunkerSequenceMonotonicity:
    """Property tests for VadChunker chunk sequence numbers."""

    def test_chunks_have_sequential_sequence_numbers(self) -> None:
        """Chunks emitted by VadChunker have sequence numbers 1, 2, 3, …"""
        emitted: List[AudioChunk] = []
        turn_id = str(uuid.uuid4())
        chunker = VadChunker(
            speaker_turn_id=turn_id,
            on_chunk=emitted.append,
            energy_threshold=0.01,
        )

        # Feed: speech → silence → speech → silence → speech → finalize
        # Each silence triggers a chunk cut
        for _ in range(3):
            chunker.feed(_make_speech(1.0))
            chunker.feed(_make_silence(3.0))  # > 2.5s threshold → triggers cut

        chunker.finalize()

        assert len(emitted) >= 3, f"Expected at least 3 chunks, got {len(emitted)}"

        for i, chunk in enumerate(emitted):
            assert chunk.sequence_number == i + 1, (
                f"Chunk {i}: expected sequence_number={i + 1}, got {chunk.sequence_number}"
            )

    def test_sequence_numbers_strictly_increasing_across_chunks(self) -> None:
        """Sequence numbers across all emitted chunks are strictly increasing."""
        emitted: List[AudioChunk] = []
        turn_id = str(uuid.uuid4())
        chunker = VadChunker(
            speaker_turn_id=turn_id,
            on_chunk=emitted.append,
            energy_threshold=0.01,
        )

        for _ in range(5):
            chunker.feed(_make_speech(0.5))
            chunker.feed(_make_silence(3.0))

        chunker.finalize()

        seqs = [c.sequence_number for c in emitted]
        for i in range(len(seqs) - 1):
            assert seqs[i] < seqs[i + 1], (
                f"Sequence numbers not strictly increasing: {seqs}"
            )

    def test_all_chunks_have_same_turn_id(self) -> None:
        """All chunks emitted by a VadChunker share the same speaker_turn_id."""
        emitted: List[AudioChunk] = []
        turn_id = str(uuid.uuid4())
        chunker = VadChunker(
            speaker_turn_id=turn_id,
            on_chunk=emitted.append,
            energy_threshold=0.01,
        )

        for _ in range(3):
            chunker.feed(_make_speech(1.0))
            chunker.feed(_make_silence(3.0))

        chunker.finalize()

        for chunk in emitted:
            assert chunk.speaker_turn_id == turn_id, (
                f"Chunk has wrong turn_id: {chunk.speaker_turn_id} != {turn_id}"
            )

    @given(st.integers(min_value=1, max_value=5))
    @settings(max_examples=30)
    def test_finalize_emits_last_chunk_with_next_sequence(self, n_prior_chunks: int) -> None:
        """After n silence-triggered chunks, finalize() emits one more chunk.

        The last chunk's sequence_number equals total chunks emitted.
        """
        emitted: List[AudioChunk] = []
        turn_id = str(uuid.uuid4())
        chunker = VadChunker(
            speaker_turn_id=turn_id,
            on_chunk=emitted.append,
            energy_threshold=0.01,
        )

        # Emit n_prior_chunks via silence cuts.
        # Use short speech + long silence to reliably trigger exactly one cut per iteration.
        # Keep total duration well below 15s to stay in the short-turn threshold zone
        # (2.5s silence threshold), so each 3s silence triggers exactly one cut.
        for _ in range(n_prior_chunks):
            chunker.feed(_make_speech(0.1))   # 0.1s speech
            chunker.feed(_make_silence(3.0))  # 3.0s silence > 2.5s threshold → one cut

        chunks_before_finalize = len(emitted)

        # Feed a tiny bit of speech that won't be cut by silence
        chunker.feed(_make_speech(0.1))

        # Finalize should emit the remaining buffer as one more chunk
        chunker.finalize()

        # Total chunks = chunks from silence cuts + 1 from finalize
        assert len(emitted) == chunks_before_finalize + 1, (
            f"finalize() should emit exactly 1 more chunk. "
            f"Before: {chunks_before_finalize}, after: {len(emitted)}"
        )

        # The last chunk must have the highest sequence number
        last_seq = emitted[-1].sequence_number
        assert last_seq == len(emitted), (
            f"Last chunk sequence_number ({last_seq}) should equal total chunks ({len(emitted)})"
        )
