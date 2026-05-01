"""
Gipformer ASR Service - Redis Queue Operations
Priority-based job queue using Redis Sorted Set + Hash.

Priority scoring (Requirement 8.10):
  HIGH_PRIORITY  → score = 1_000_000_000 - unix_ms   (higher score = processed first)
  NORMAL_PRIORITY → score = unix_ms (inverted: 0 - unix_ms, so older jobs score higher)

The Sorted Set key is ``transcription:queue``.
Job details are stored in a Redis Hash at ``transcription:job:{job_id}``.
"""

from __future__ import annotations

import json
import logging
import time
from typing import Any, Dict, List, Optional

import redis

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

QUEUE_KEY = "transcription:queue"
JOB_KEY_PREFIX = "transcription:job:"

HIGH_PRIORITY_BASE = 1_000_000_000   # score offset for HIGH_PRIORITY jobs
# HIGH score  = HIGH_PRIORITY_BASE - unix_ms  → older jobs have higher score
# NORMAL score = -unix_ms                     → older jobs have higher score (less negative)


# ---------------------------------------------------------------------------
# Score helpers
# ---------------------------------------------------------------------------


def _priority_score(priority: str, unix_ms: Optional[int] = None) -> float:
    """Compute the Sorted Set score for a job.

    Higher score = processed first (ZPOPMAX pops the highest score).

    Args:
        priority: ``"HIGH_PRIORITY"`` or ``"NORMAL_PRIORITY"``.
        unix_ms: Unix timestamp in milliseconds. Defaults to current time.

    Returns:
        Float score suitable for ZADD.
    """
    if unix_ms is None:
        unix_ms = int(time.time() * 1000)

    if priority == "HIGH_PRIORITY":
        return float(HIGH_PRIORITY_BASE - unix_ms)
    else:
        # NORMAL: use negative unix_ms so older jobs (smaller unix_ms) have
        # a less-negative (higher) score and are processed first within NORMAL tier.
        return float(-unix_ms)


# ---------------------------------------------------------------------------
# RedisQueue
# ---------------------------------------------------------------------------


class RedisQueue:
    """Thin wrapper around Redis Sorted Set + Hash for transcription job management.

    Args:
        redis_client: A connected :class:`redis.Redis` instance.
    """

    def __init__(self, redis_client: redis.Redis) -> None:
        self._r = redis_client

    # ------------------------------------------------------------------
    # Push / Pop
    # ------------------------------------------------------------------

    def push(
        self,
        job_id: str,
        job_details: Dict[str, Any],
        priority: str,
        unix_ms: Optional[int] = None,
    ) -> float:
        """Add a job to the queue and store its details in a Hash.

        Uses a Redis pipeline so both operations are atomic from the
        client's perspective (they are sent in a single round-trip).

        Args:
            job_id: UUID string identifying the job.
            job_details: Dict of job metadata to store in the Hash.
            priority: ``"HIGH_PRIORITY"`` or ``"NORMAL_PRIORITY"``.
            unix_ms: Optional timestamp override (milliseconds since epoch).

        Returns:
            The computed priority score.
        """
        score = _priority_score(priority, unix_ms)

        # Serialize all values to strings for Redis Hash storage
        serialized: Dict[str, str] = {
            k: json.dumps(v) if not isinstance(v, str) else v
            for k, v in job_details.items()
        }
        serialized["priority"] = priority
        serialized["job_id"] = job_id

        pipe = self._r.pipeline()
        pipe.zadd(QUEUE_KEY, {job_id: score})
        pipe.hset(JOB_KEY_PREFIX + job_id, mapping=serialized)
        pipe.execute()

        logger.debug(
            "Pushed job %s to queue with priority=%s score=%.0f",
            job_id,
            priority,
            score,
        )
        return score

    def pop(self) -> Optional[Dict[str, Any]]:
        """Pop the highest-priority job from the queue and return its details.

        Uses ZPOPMAX to atomically remove and return the member with the
        highest score.

        Returns:
            Dict of job details (deserialized), or *None* if the queue is empty.
        """
        result = self._r.zpopmax(QUEUE_KEY, count=1)
        if not result:
            return None

        job_id_bytes, score = result[0]
        job_id = job_id_bytes.decode() if isinstance(job_id_bytes, bytes) else job_id_bytes

        details = self.get_job(job_id)
        if details is None:
            logger.warning("Job %s popped from queue but Hash not found", job_id)
            return {"job_id": job_id, "score": score}

        details["_score"] = score
        logger.debug("Popped job %s (score=%.0f) from queue", job_id, score)
        return details

    # ------------------------------------------------------------------
    # Job details (Hash)
    # ------------------------------------------------------------------

    def get_job(self, job_id: str) -> Optional[Dict[str, Any]]:
        """Retrieve job details from the Redis Hash.

        Args:
            job_id: UUID of the job.

        Returns:
            Deserialized dict, or *None* if the key does not exist.
        """
        raw: Dict[bytes, bytes] = self._r.hgetall(JOB_KEY_PREFIX + job_id)
        if not raw:
            return None

        result: Dict[str, Any] = {}
        for k, v in raw.items():
            key = k.decode() if isinstance(k, bytes) else k
            val_str = v.decode() if isinstance(v, bytes) else v
            try:
                result[key] = json.loads(val_str)
            except (json.JSONDecodeError, TypeError):
                result[key] = val_str

        return result

    def update_job(self, job_id: str, updates: Dict[str, Any]) -> None:
        """Update fields in the job's Redis Hash.

        Args:
            job_id: UUID of the job.
            updates: Dict of fields to set/overwrite.
        """
        serialized = {
            k: json.dumps(v) if not isinstance(v, str) else v
            for k, v in updates.items()
        }
        self._r.hset(JOB_KEY_PREFIX + job_id, mapping=serialized)

    def delete_job(self, job_id: str) -> None:
        """Remove a job's Hash from Redis (does not remove from queue).

        Args:
            job_id: UUID of the job.
        """
        self._r.delete(JOB_KEY_PREFIX + job_id)

    # ------------------------------------------------------------------
    # Queue inspection
    # ------------------------------------------------------------------

    def queue_length(self) -> int:
        """Return the number of jobs currently in the queue."""
        return self._r.zcard(QUEUE_KEY)

    def peek(self, count: int = 10) -> List[Dict[str, Any]]:
        """Return the top *count* jobs without removing them.

        Args:
            count: Maximum number of jobs to return.

        Returns:
            List of dicts with ``job_id`` and ``score`` keys, ordered by
            descending score (highest priority first).
        """
        results = self._r.zrange(QUEUE_KEY, 0, count - 1, desc=True, withscores=True)
        items = []
        for job_id_bytes, score in results:
            job_id = (
                job_id_bytes.decode()
                if isinstance(job_id_bytes, bytes)
                else job_id_bytes
            )
            items.append({"job_id": job_id, "score": score})
        return items

    def remove_from_queue(self, job_id: str) -> bool:
        """Remove a specific job from the Sorted Set (without deleting its Hash).

        Args:
            job_id: UUID of the job to remove.

        Returns:
            True if the job was present and removed, False otherwise.
        """
        removed = self._r.zrem(QUEUE_KEY, job_id)
        return bool(removed)

    def rescore(self, job_id: str, priority: str, unix_ms: Optional[int] = None) -> float:
        """Update the priority score of an existing job in the queue.

        Args:
            job_id: UUID of the job.
            priority: New priority level.
            unix_ms: Optional timestamp override.

        Returns:
            The new score.
        """
        score = _priority_score(priority, unix_ms)
        self._r.zadd(QUEUE_KEY, {job_id: score})
        logger.debug("Rescored job %s to %.0f (priority=%s)", job_id, score, priority)
        return score

    def flush_queue(self) -> None:
        """Remove all jobs from the Sorted Set (does not delete Hashes).

        Intended for testing and maintenance only.
        """
        self._r.delete(QUEUE_KEY)
        logger.warning("Transcription queue flushed")


# ---------------------------------------------------------------------------
# Factory helper
# ---------------------------------------------------------------------------


def create_redis_queue(redis_url: str) -> RedisQueue:
    """Create a :class:`RedisQueue` connected to *redis_url*.

    Args:
        redis_url: Redis connection URL, e.g. ``"redis://redis:6379"``.

    Returns:
        Connected :class:`RedisQueue` instance.
    """
    client = redis.from_url(redis_url, decode_responses=False)
    return RedisQueue(client)
