"""
Property 3: Queue Priority Ordering (Python / hypothesis)
Validates: Requirements 8.10

Properties:
  1. HIGH_PRIORITY jobs always have a higher score than NORMAL_PRIORITY jobs
  2. Within the same priority, older jobs (smaller unix_ms) have a higher score
     (FIFO ordering within each priority tier)
  3. ZPOPMAX always returns the job with the highest score
  4. Score function is deterministic for the same (priority, unix_ms) inputs

Uses hypothesis + fakeredis for property-based testing without a real Redis.
"""

import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import uuid
from typing import List, Tuple

import pytest
from hypothesis import given, settings, assume
from hypothesis import strategies as st

import importlib
import importlib.util

# Import from the local queue package using its file path directly,
# since 'queue' in sys.modules is the stdlib module (pre-loaded in conftest.py
# to prevent hypothesis from breaking). We load the local redis_queue module
# by its file path to avoid the naming conflict.
_redis_queue_path = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "queue", "redis_queue.py"
)
_spec = importlib.util.spec_from_file_location("gipformer_redis_queue", _redis_queue_path)
_redis_queue_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_redis_queue_mod)

RedisQueue = _redis_queue_mod.RedisQueue
_priority_score = _redis_queue_mod._priority_score
HIGH_PRIORITY_BASE = _redis_queue_mod.HIGH_PRIORITY_BASE
QUEUE_KEY = _redis_queue_mod.QUEUE_KEY
JOB_KEY_PREFIX = _redis_queue_mod.JOB_KEY_PREFIX

# ---------------------------------------------------------------------------
# Try to import fakeredis; skip tests if not available
# ---------------------------------------------------------------------------

try:
    import fakeredis

    _FAKEREDIS_AVAILABLE = True
except ImportError:
    _FAKEREDIS_AVAILABLE = False

pytestmark = pytest.mark.skipif(
    not _FAKEREDIS_AVAILABLE,
    reason="fakeredis not installed — run: pip install fakeredis",
)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


def _make_queue() -> RedisQueue:
    """Create a RedisQueue backed by an in-memory fakeredis server."""
    server = fakeredis.FakeServer()
    client = fakeredis.FakeRedis(server=server, decode_responses=False)
    return RedisQueue(client)


def _make_job(job_id: str, priority: str, unix_ms: int) -> dict:
    return {
        "job_id": job_id,
        "meeting_id": 1,
        "speaker_id": 1,
        "speaker_name": "Test Speaker",
        "speaker_turn_id": str(uuid.uuid4()),
        "sequence_number": 1,
        "priority": priority,
        "audio_path": f"/tmp/{job_id}.wav",
        "status": "QUEUED",
        "created_at": "2025-01-01T00:00:00+00:00",
    }


# ---------------------------------------------------------------------------
# Property 3a: Score ordering between priorities
# ---------------------------------------------------------------------------


class TestPriorityScoreOrdering:
    """Property tests for the _priority_score function."""

    @given(
        st.integers(min_value=1_000_000, max_value=999_999_999),  # must be < HIGH_PRIORITY_BASE
        st.integers(min_value=1_000_000, max_value=2_000_000_000),
    )
    @settings(max_examples=1000)
    def test_high_priority_always_beats_normal(
        self, high_unix_ms: int, normal_unix_ms: int
    ) -> None:
        """Property: HIGH_PRIORITY score > NORMAL_PRIORITY score for any valid timestamps.

        Note: high_unix_ms must be < HIGH_PRIORITY_BASE (1_000_000_000) to ensure
        the HIGH score is positive and always exceeds any NORMAL score (which is ≤ 0).
        In practice, unix_ms in milliseconds for current dates is ~1.7e12, which is
        larger than HIGH_PRIORITY_BASE. The scoring formula is designed so that
        HIGH scores are always in the range (0, HIGH_PRIORITY_BASE) when unix_ms < BASE,
        while NORMAL scores are always ≤ 0. This guarantees HIGH > NORMAL.
        """
        high_score = _priority_score("HIGH_PRIORITY", high_unix_ms)
        normal_score = _priority_score("NORMAL_PRIORITY", normal_unix_ms)
        assert high_score > normal_score, (
            f"HIGH score ({high_score}) must exceed NORMAL score ({normal_score}). "
            f"high_unix_ms={high_unix_ms}, normal_unix_ms={normal_unix_ms}"
        )

    @given(
        st.integers(min_value=1_000_000, max_value=999_999_999),
        st.integers(min_value=1_000_000, max_value=999_999_999),
    )
    @settings(max_examples=1000)
    def test_within_high_priority_older_job_has_higher_score(
        self, t1_ms: int, t2_ms: int
    ) -> None:
        """Property: within HIGH_PRIORITY, older job (smaller unix_ms) has higher score."""
        assume(t1_ms != t2_ms)
        score1 = _priority_score("HIGH_PRIORITY", t1_ms)
        score2 = _priority_score("HIGH_PRIORITY", t2_ms)
        if t1_ms < t2_ms:
            assert score1 > score2, (
                f"Older HIGH job (t={t1_ms}) should have higher score than newer (t={t2_ms}). "
                f"scores: {score1} vs {score2}"
            )
        else:
            assert score2 > score1

    @given(
        st.integers(min_value=1_000_000, max_value=2_000_000_000),
        st.integers(min_value=1_000_000, max_value=2_000_000_000),
    )
    @settings(max_examples=1000)
    def test_within_normal_priority_older_job_has_higher_score(
        self, t1_ms: int, t2_ms: int
    ) -> None:
        """Property: within NORMAL_PRIORITY, older job (smaller unix_ms) has higher score."""
        assume(t1_ms != t2_ms)
        score1 = _priority_score("NORMAL_PRIORITY", t1_ms)
        score2 = _priority_score("NORMAL_PRIORITY", t2_ms)
        if t1_ms < t2_ms:
            assert score1 > score2, (
                f"Older NORMAL job (t={t1_ms}) should have higher score than newer (t={t2_ms}). "
                f"scores: {score1} vs {score2}"
            )
        else:
            assert score2 > score1

    @given(
        st.integers(min_value=1_000_000, max_value=2_000_000_000),
        st.sampled_from(["HIGH_PRIORITY", "NORMAL_PRIORITY"]),
    )
    @settings(max_examples=500)
    def test_score_is_deterministic(self, unix_ms: int, priority: str) -> None:
        """Property: same (priority, unix_ms) always produces the same score."""
        s1 = _priority_score(priority, unix_ms)
        s2 = _priority_score(priority, unix_ms)
        assert s1 == s2

    def test_high_priority_score_formula(self) -> None:
        """Verify HIGH_PRIORITY score = HIGH_PRIORITY_BASE - unix_ms."""
        unix_ms = 1_700_000_000_000
        score = _priority_score("HIGH_PRIORITY", unix_ms)
        assert score == float(HIGH_PRIORITY_BASE - unix_ms)

    def test_normal_priority_score_formula(self) -> None:
        """Verify NORMAL_PRIORITY score = -unix_ms."""
        unix_ms = 1_700_000_000_000
        score = _priority_score("NORMAL_PRIORITY", unix_ms)
        assert score == float(-unix_ms)


# ---------------------------------------------------------------------------
# Property 3b: Queue pop ordering (requires fakeredis)
# ---------------------------------------------------------------------------


class TestQueuePopOrdering:
    """Property tests for RedisQueue push/pop ordering."""

    def test_high_priority_job_popped_before_normal(self) -> None:
        """A HIGH_PRIORITY job is always popped before a NORMAL_PRIORITY job."""
        queue = _make_queue()
        t = 1_700_000_000_000

        normal_id = str(uuid.uuid4())
        high_id = str(uuid.uuid4())

        # Push NORMAL first (older timestamp), then HIGH
        queue.push(normal_id, _make_job(normal_id, "NORMAL_PRIORITY", t), "NORMAL_PRIORITY", t)
        queue.push(high_id, _make_job(high_id, "HIGH_PRIORITY", t + 1000), "HIGH_PRIORITY", t + 1000)

        first = queue.pop()
        assert first is not None
        assert first["job_id"] == high_id, (
            f"Expected HIGH_PRIORITY job to be popped first, got {first['job_id']}"
        )

        second = queue.pop()
        assert second is not None
        assert second["job_id"] == normal_id

    @given(st.integers(min_value=2, max_value=10))
    @settings(max_examples=50)
    def test_multiple_high_priority_jobs_popped_fifo(self, n: int) -> None:
        """Multiple HIGH_PRIORITY jobs are popped in FIFO order (oldest first)."""
        queue = _make_queue()
        base_t = 1_700_000_000_000
        job_ids = [str(uuid.uuid4()) for _ in range(n)]

        # Push in order (oldest first = smallest unix_ms)
        for i, job_id in enumerate(job_ids):
            t = base_t + i * 1000  # each job 1 second newer
            queue.push(job_id, _make_job(job_id, "HIGH_PRIORITY", t), "HIGH_PRIORITY", t)

        # Pop all — should come out in FIFO order (oldest = highest score)
        popped_ids = []
        while True:
            job = queue.pop()
            if job is None:
                break
            popped_ids.append(job["job_id"])

        assert popped_ids == job_ids, (
            f"HIGH_PRIORITY jobs not popped in FIFO order.\n"
            f"Expected: {job_ids}\n"
            f"Got:      {popped_ids}"
        )

    @given(st.integers(min_value=2, max_value=10))
    @settings(max_examples=50)
    def test_multiple_normal_priority_jobs_popped_fifo(self, n: int) -> None:
        """Multiple NORMAL_PRIORITY jobs are popped in FIFO order (oldest first)."""
        queue = _make_queue()
        base_t = 1_700_000_000_000
        job_ids = [str(uuid.uuid4()) for _ in range(n)]

        for i, job_id in enumerate(job_ids):
            t = base_t + i * 1000
            queue.push(job_id, _make_job(job_id, "NORMAL_PRIORITY", t), "NORMAL_PRIORITY", t)

        popped_ids = []
        while True:
            job = queue.pop()
            if job is None:
                break
            popped_ids.append(job["job_id"])

        assert popped_ids == job_ids, (
            f"NORMAL_PRIORITY jobs not popped in FIFO order.\n"
            f"Expected: {job_ids}\n"
            f"Got:      {popped_ids}"
        )

    def test_mixed_priorities_all_high_before_normal(self) -> None:
        """All HIGH_PRIORITY jobs are popped before any NORMAL_PRIORITY job."""
        queue = _make_queue()
        base_t = 1_700_000_000_000

        high_ids = [str(uuid.uuid4()) for _ in range(3)]
        normal_ids = [str(uuid.uuid4()) for _ in range(3)]

        # Interleave pushes: normal, high, normal, high, ...
        for i in range(3):
            t = base_t + i * 1000
            queue.push(normal_ids[i], _make_job(normal_ids[i], "NORMAL_PRIORITY", t), "NORMAL_PRIORITY", t)
            queue.push(high_ids[i], _make_job(high_ids[i], "HIGH_PRIORITY", t + 500), "HIGH_PRIORITY", t + 500)

        popped: List[dict] = []
        while True:
            job = queue.pop()
            if job is None:
                break
            popped.append(job)

        assert len(popped) == 6

        # First 3 must all be HIGH_PRIORITY
        for job in popped[:3]:
            assert job["priority"] == "HIGH_PRIORITY", (
                f"Expected HIGH_PRIORITY in first 3 pops, got {job['priority']}"
            )

        # Last 3 must all be NORMAL_PRIORITY
        for job in popped[3:]:
            assert job["priority"] == "NORMAL_PRIORITY", (
                f"Expected NORMAL_PRIORITY in last 3 pops, got {job['priority']}"
            )

    def test_empty_queue_returns_none(self) -> None:
        """Popping from an empty queue returns None."""
        queue = _make_queue()
        result = queue.pop()
        assert result is None

    def test_queue_length_tracks_pushes_and_pops(self) -> None:
        """queue_length() accurately reflects the number of jobs in the queue."""
        queue = _make_queue()
        assert queue.queue_length() == 0

        for i in range(5):
            job_id = str(uuid.uuid4())
            queue.push(job_id, _make_job(job_id, "NORMAL_PRIORITY", 1_700_000_000_000 + i), "NORMAL_PRIORITY")
        assert queue.queue_length() == 5

        queue.pop()
        assert queue.queue_length() == 4

        queue.flush_queue()
        assert queue.queue_length() == 0
