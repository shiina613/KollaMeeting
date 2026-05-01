"""
Gipformer ASR Service - Background Queue Worker
Polls the Redis Sorted Set every 100 ms, processes the highest-priority job,
and delivers the result to the Spring Boot callback endpoint.

Requirements 8.10, 8.11:
  - Poll Redis queue every WORKER_POLL_INTERVAL_MS (default 100 ms)
  - Pop highest-priority job (ZPOPMAX)
  - Load WAV file from audio_path
  - Call recognizer.transcribe()
  - Call BackendNotifier.notify()
  - Update job status in Redis Hash
"""

from __future__ import annotations

import logging
import os
import threading
import time
from datetime import datetime, timezone
from typing import Any, Dict, Optional

from api.schemas import TranscriptionCallbackPayload
from callback.backend_notifier import BackendNotifier
from config import settings as _default_settings
from core.recognizer import GipformerRecognizer, get_recognizer
from job_queue.redis_queue import RedisQueue

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Job status constants (mirrors TranscriptionJobStatus enum in Spring Boot)
# ---------------------------------------------------------------------------

STATUS_QUEUED = "QUEUED"
STATUS_PROCESSING = "PROCESSING"
STATUS_COMPLETED = "COMPLETED"
STATUS_FAILED = "FAILED"


# ---------------------------------------------------------------------------
# Worker
# ---------------------------------------------------------------------------


class TranscriptionWorker:
    """Background daemon thread that drains the Redis transcription queue.

    Args:
        redis_queue: :class:`~queue.redis_queue.RedisQueue` instance.
        notifier: :class:`~callback.backend_notifier.BackendNotifier` instance.
        poll_interval_ms: How often to poll the queue (milliseconds).
        recognizer: Optional pre-loaded :class:`~core.recognizer.GipformerRecognizer`.
            If *None*, the singleton is fetched lazily on first job.
    """

    def __init__(
        self,
        redis_queue: RedisQueue,
        notifier: BackendNotifier,
        poll_interval_ms: int = 100,
        recognizer: Optional[GipformerRecognizer] = None,
    ) -> None:
        self._queue = redis_queue
        self._notifier = notifier
        self._poll_interval = poll_interval_ms / 1000.0  # convert to seconds
        self._recognizer = recognizer

        self._thread: Optional[threading.Thread] = None
        self._stop_event = threading.Event()
        self._running = False

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------

    def start(self) -> None:
        """Start the background worker thread."""
        if self._running:
            logger.warning("Worker is already running")
            return

        self._stop_event.clear()
        self._running = True
        self._thread = threading.Thread(
            target=self._run_loop,
            name="transcription-worker",
            daemon=True,
        )
        self._thread.start()
        logger.info(
            "TranscriptionWorker started (poll_interval=%.0f ms)",
            self._poll_interval * 1000,
        )

    def stop(self, timeout: float = 5.0) -> None:
        """Signal the worker to stop and wait for it to finish.

        Args:
            timeout: Maximum seconds to wait for the thread to join.
        """
        if not self._running:
            return

        logger.info("Stopping TranscriptionWorker …")
        self._stop_event.set()
        self._running = False

        if self._thread is not None:
            self._thread.join(timeout=timeout)
            if self._thread.is_alive():
                logger.warning("Worker thread did not stop within %.1f s", timeout)
            else:
                logger.info("TranscriptionWorker stopped")

    @property
    def is_running(self) -> bool:
        return self._running and (
            self._thread is not None and self._thread.is_alive()
        )

    # ------------------------------------------------------------------
    # Main loop
    # ------------------------------------------------------------------

    def _run_loop(self) -> None:
        """Poll the queue in a tight loop until stop is requested."""
        logger.debug("Worker loop started")
        while not self._stop_event.is_set():
            try:
                job = self._queue.pop()
                if job is None:
                    # Queue empty — sleep before next poll
                    self._stop_event.wait(timeout=self._poll_interval)
                    continue

                self._process_job(job)

            except Exception:
                logger.exception("Unexpected error in worker loop")
                # Brief pause to avoid tight error loops
                self._stop_event.wait(timeout=1.0)

        logger.debug("Worker loop exited")

    # ------------------------------------------------------------------
    # Job processing
    # ------------------------------------------------------------------

    def _process_job(self, job: Dict[str, Any]) -> None:
        """Process a single transcription job.

        Args:
            job: Job details dict from :meth:`~queue.redis_queue.RedisQueue.pop`.
        """
        job_id = job.get("job_id", "unknown")
        audio_path = job.get("audio_path", "")

        logger.info("Processing job %s (audio_path=%s)", job_id, audio_path)

        # Mark as PROCESSING in Redis Hash
        self._queue.update_job(job_id, {"status": STATUS_PROCESSING})

        start_ms = int(time.time() * 1000)

        try:
            # Validate audio file exists
            if not audio_path or not os.path.isfile(audio_path):
                raise FileNotFoundError(
                    f"Audio file not found: {audio_path!r}"
                )

            # Lazy-load recognizer
            recognizer = self._get_recognizer()

            # Transcribe
            text = recognizer.transcribe(audio_path)

            processing_time_ms = int(time.time() * 1000) - start_ms

            logger.info(
                "Job %s transcribed in %d ms: %r",
                job_id,
                processing_time_ms,
                text[:80] if text else "",
            )

            # Build callback payload
            segment_start_time = job.get("created_at") or datetime.now(
                timezone.utc
            ).isoformat()

            payload = TranscriptionCallbackPayload(
                job_id=job_id,
                meeting_id=int(job.get("meeting_id", 0)),
                speaker_id=int(job.get("speaker_id", 0)),
                speaker_name=str(job.get("speaker_name", "")),
                speaker_turn_id=str(job.get("speaker_turn_id", "")),
                sequence_number=int(job.get("sequence_number", 1)),
                text=text,
                confidence=None,  # sherpa-onnx does not expose per-segment confidence
                processing_time_ms=processing_time_ms,
                segment_start_time=segment_start_time,
            )

            # Deliver result to Spring Boot
            delivered = self._notifier.notify(payload)

            if delivered:
                self._queue.update_job(
                    job_id,
                    {
                        "status": STATUS_COMPLETED,
                        "completed_at": datetime.now(timezone.utc).isoformat(),
                    },
                )
            else:
                self._queue.update_job(
                    job_id,
                    {
                        "status": STATUS_FAILED,
                        "error_message": "Callback delivery failed after retries",
                    },
                )

        except FileNotFoundError as exc:
            logger.error("Job %s failed — audio file missing: %s", job_id, exc)
            self._queue.update_job(
                job_id,
                {"status": STATUS_FAILED, "error_message": str(exc)},
            )

        except Exception as exc:
            logger.exception("Job %s failed with unexpected error", job_id)
            self._queue.update_job(
                job_id,
                {"status": STATUS_FAILED, "error_message": str(exc)},
            )

    # ------------------------------------------------------------------
    # Recognizer accessor
    # ------------------------------------------------------------------

    def _get_recognizer(self) -> GipformerRecognizer:
        """Return the recognizer, loading it lazily if needed."""
        if self._recognizer is None:
            self._recognizer = get_recognizer(_default_settings)
        return self._recognizer


# ---------------------------------------------------------------------------
# Module-level singleton
# ---------------------------------------------------------------------------

_worker_instance: Optional[TranscriptionWorker] = None
_worker_lock = threading.Lock()


def get_worker() -> Optional[TranscriptionWorker]:
    """Return the module-level worker singleton (may be None if not started)."""
    return _worker_instance


def start_worker(
    redis_queue: RedisQueue,
    notifier: BackendNotifier,
    poll_interval_ms: Optional[int] = None,
    recognizer: Optional[GipformerRecognizer] = None,
) -> TranscriptionWorker:
    """Create and start the module-level worker singleton.

    Idempotent: if a worker is already running, it is returned unchanged.

    Args:
        redis_queue: Connected :class:`~queue.redis_queue.RedisQueue`.
        notifier: Configured :class:`~callback.backend_notifier.BackendNotifier`.
        poll_interval_ms: Poll interval override (uses ``settings.WORKER_POLL_INTERVAL_MS``
            if *None*).
        recognizer: Optional pre-loaded recognizer.

    Returns:
        The running :class:`TranscriptionWorker`.
    """
    global _worker_instance  # noqa: PLW0603

    with _worker_lock:
        if _worker_instance is not None and _worker_instance.is_running:
            logger.debug("Worker already running, returning existing instance")
            return _worker_instance

        interval = poll_interval_ms if poll_interval_ms is not None else _default_settings.WORKER_POLL_INTERVAL_MS
        _worker_instance = TranscriptionWorker(
            redis_queue=redis_queue,
            notifier=notifier,
            poll_interval_ms=interval,
            recognizer=recognizer,
        )
        _worker_instance.start()
        return _worker_instance


def stop_worker(timeout: float = 5.0) -> None:
    """Stop the module-level worker singleton if running.

    Args:
        timeout: Maximum seconds to wait for the thread to join.
    """
    global _worker_instance  # noqa: PLW0603

    with _worker_lock:
        if _worker_instance is not None:
            _worker_instance.stop(timeout=timeout)
            _worker_instance = None
