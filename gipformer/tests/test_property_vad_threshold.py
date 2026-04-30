"""
Property 5: Adaptive VAD Threshold Function
Validates: Requirements 8.9

Property: For any audio duration d ≥ 0:
  - if d < 15.0  → threshold ∈ [2.0, 3.0]
  - if d ≥ 15.0  → threshold ∈ [0.5, 1.0]

Uses hypothesis for property-based testing.
"""

import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import pytest
from hypothesis import given, settings, assume
from hypothesis import strategies as st

from core.vad_chunker import (
    _compute_adaptive_silence_threshold,
    LONG_TURN_BOUNDARY,
    SHORT_TURN_SILENCE_THRESHOLD,
    LONG_TURN_SILENCE_THRESHOLD,
)


# ---------------------------------------------------------------------------
# Property 5: Adaptive VAD Threshold Function
# ---------------------------------------------------------------------------


class TestAdaptiveVADThreshold:
    """Property-based tests for _compute_adaptive_silence_threshold."""

    @given(st.floats(min_value=0.0, max_value=14.999, allow_nan=False, allow_infinity=False))
    @settings(max_examples=1000)
    def test_short_turn_threshold_in_range(self, duration: float) -> None:
        """Property: duration < 15s → threshold ∈ [2.0, 3.0]."""
        threshold = _compute_adaptive_silence_threshold(duration)
        assert 2.0 <= threshold <= 3.0, (
            f"For duration={duration:.3f}s (< 15s), expected threshold in [2.0, 3.0], "
            f"got {threshold}"
        )

    @given(st.floats(min_value=15.0, max_value=300.0, allow_nan=False, allow_infinity=False))
    @settings(max_examples=1000)
    def test_long_turn_threshold_in_range(self, duration: float) -> None:
        """Property: duration ≥ 15s → threshold ∈ [0.5, 1.0]."""
        threshold = _compute_adaptive_silence_threshold(duration)
        assert 0.5 <= threshold <= 1.0, (
            f"For duration={duration:.3f}s (≥ 15s), expected threshold in [0.5, 1.0], "
            f"got {threshold}"
        )

    @given(st.floats(min_value=0.0, max_value=300.0, allow_nan=False, allow_infinity=False))
    @settings(max_examples=2000)
    def test_threshold_always_positive(self, duration: float) -> None:
        """Property: threshold is always strictly positive for any duration."""
        threshold = _compute_adaptive_silence_threshold(duration)
        assert threshold > 0.0, (
            f"Threshold must be positive, got {threshold} for duration={duration}"
        )

    def test_boundary_exactly_15s(self) -> None:
        """Boundary: at exactly 15.0s, threshold switches to long-turn range."""
        threshold = _compute_adaptive_silence_threshold(15.0)
        assert 0.5 <= threshold <= 1.0, (
            f"At exactly 15.0s, expected long-turn threshold [0.5, 1.0], got {threshold}"
        )

    def test_boundary_just_below_15s(self) -> None:
        """Boundary: just below 15.0s uses short-turn threshold."""
        threshold = _compute_adaptive_silence_threshold(14.999)
        assert 2.0 <= threshold <= 3.0, (
            f"At 14.999s, expected short-turn threshold [2.0, 3.0], got {threshold}"
        )

    def test_zero_duration(self) -> None:
        """Edge case: zero duration uses short-turn threshold."""
        threshold = _compute_adaptive_silence_threshold(0.0)
        assert 2.0 <= threshold <= 3.0

    @given(
        st.floats(min_value=0.0, max_value=14.999, allow_nan=False, allow_infinity=False),
        st.floats(min_value=15.0, max_value=300.0, allow_nan=False, allow_infinity=False),
    )
    @settings(max_examples=500)
    def test_long_turn_threshold_strictly_less_than_short(
        self, short_duration: float, long_duration: float
    ) -> None:
        """Property: long-turn threshold is always strictly less than short-turn threshold.

        This ensures the VAD becomes more aggressive as the turn gets longer,
        cutting chunks sooner to avoid excessively long audio segments.
        """
        short_threshold = _compute_adaptive_silence_threshold(short_duration)
        long_threshold = _compute_adaptive_silence_threshold(long_duration)
        assert long_threshold < short_threshold, (
            f"Long-turn threshold ({long_threshold}) should be < "
            f"short-turn threshold ({short_threshold})"
        )

    @given(st.floats(min_value=0.0, max_value=300.0, allow_nan=False, allow_infinity=False))
    @settings(max_examples=1000)
    def test_threshold_is_deterministic(self, duration: float) -> None:
        """Property: same duration always produces the same threshold (pure function)."""
        t1 = _compute_adaptive_silence_threshold(duration)
        t2 = _compute_adaptive_silence_threshold(duration)
        assert t1 == t2, (
            f"Threshold must be deterministic: got {t1} then {t2} for duration={duration}"
        )
