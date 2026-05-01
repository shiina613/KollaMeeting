"""
Gipformer ASR Service - API Routes
Implements all three endpoints (Requirement 8.10):
  - POST /jobs       → submit async transcription job → ZADD Redis
  - GET  /health     → service health check (model + Redis)
  - POST /transcribe → synchronous transcription (blocking)
"""

from __future__ import annotations

import logging
import time
import uuid
from datetime import datetime, timezone
from typing import Optional

import redis as redis_lib
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, status
from fastapi.responses import JSONResponse

from api.schemas import (
    HealthResponse,
    SynchronousTranscribeResponse,
    TranscriptionJobRequest,
    TranscriptionJobResponse,
)
from config import settings
from core.recognizer import GipformerRecognizer, get_recognizer
from job_queue.redis_queue import RedisQueue, create_redis_queue

logger = logging.getLogger(__name__)

router = APIRouter()

# ---------------------------------------------------------------------------
# Module-level singletons (lazy-initialised on first request)
# ---------------------------------------------------------------------------

_redis_queue: Optional[RedisQueue] = None
_redis_client: Optional[redis_lib.Redis] = None


def _get_redis_queue() -> RedisQueue:
    """Return the module-level RedisQueue singleton, creating it if needed."""
    global _redis_queue  # noqa: PLW0603
    if _redis_queue is None:
        _redis_queue = create_redis_queue(settings.REDIS_URL)
    return _redis_queue


def _get_raw_redis() -> redis_lib.Redis:
    """Return a raw Redis client for health-check pings."""
    global _redis_client  # noqa: PLW0603
    if _redis_client is None:
        _redis_client = redis_lib.from_url(settings.REDIS_URL, decode_responses=True)
    return _redis_client


# ---------------------------------------------------------------------------
# Startup time (for uptime calculation)
# ---------------------------------------------------------------------------

_startup_time: float = time.time()


# ---------------------------------------------------------------------------
# POST /jobs — submit async transcription job
# ---------------------------------------------------------------------------


@router.post(
    "/jobs",
    response_model=TranscriptionJobResponse,
    status_code=status.HTTP_202_ACCEPTED,
    tags=["transcription"],
    summary="Submit an async transcription job",
)
async def submit_job(request: TranscriptionJobRequest) -> TranscriptionJobResponse:
    """Accept a transcription job and push it onto the Redis priority queue.

    The job will be processed by the background worker thread.
    Returns 202 Accepted with the job status and queue position.

    Requirements 8.10:
      - HIGH_PRIORITY  → score = 1_000_000_000 - unix_ms
      - NORMAL_PRIORITY → score = -unix_ms (older jobs processed first)
    """
    queue = _get_redis_queue()

    # Build job details dict for Redis Hash storage
    job_details = {
        "job_id": request.job_id,
        "meeting_id": request.meeting_id,
        "speaker_id": request.speaker_id,
        "speaker_name": request.speaker_name,
        "speaker_turn_id": request.speaker_turn_id,
        "sequence_number": request.sequence_number,
        "priority": request.priority,
        "audio_path": request.audio_path,
        "callback_url": request.callback_url or settings.BACKEND_CALLBACK_URL,
        "status": "QUEUED",
        "created_at": datetime.now(timezone.utc).isoformat(),
    }

    try:
        queue.push(
            job_id=request.job_id,
            job_details=job_details,
            priority=request.priority,
        )
    except Exception as exc:
        logger.exception("Failed to push job %s to Redis queue", request.job_id)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"Failed to enqueue job: {exc}",
        ) from exc

    queue_position = queue.queue_length()

    logger.info(
        "Job %s queued (priority=%s, queue_depth=%d)",
        request.job_id,
        request.priority,
        queue_position,
    )

    return TranscriptionJobResponse(
        job_id=request.job_id,
        status="QUEUED",
        message=f"Job queued successfully. Queue depth: {queue_position}",
    )


# ---------------------------------------------------------------------------
# GET /health — service health check
# ---------------------------------------------------------------------------


@router.get(
    "/health",
    response_model=HealthResponse,
    tags=["health"],
    summary="Service health check",
)
async def health_check() -> HealthResponse:
    """Return service health status.

    Checks:
    - Whether the ASR model is loaded and ready
    - Whether Redis is reachable (PING)
    - Current queue depth
    - Service uptime

    Returns 200 when ready, 503 when the model is not yet loaded.
    """
    # Check model
    model_loaded = False
    try:
        recognizer = get_recognizer()
        model_loaded = recognizer.is_loaded()
    except Exception:
        model_loaded = False

    # Check Redis
    redis_connected = False
    queue_depth = 0
    try:
        r = _get_raw_redis()
        r.ping()
        redis_connected = True
        queue = _get_redis_queue()
        queue_depth = queue.queue_length()
    except Exception:
        redis_connected = False

    overall_status = "ready" if (model_loaded and redis_connected) else "degraded"

    response = HealthResponse(
        status=overall_status,
        model_loaded=model_loaded,
        redis_connected=redis_connected,
        version="1.0.0",
    )

    if not model_loaded:
        # Return 503 so Spring Boot health-check knows service is not ready
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=response.model_dump(),
        )

    return response


# ---------------------------------------------------------------------------
# POST /transcribe — synchronous transcription
# ---------------------------------------------------------------------------


@router.post(
    "/transcribe",
    response_model=SynchronousTranscribeResponse,
    tags=["transcription"],
    summary="Synchronous transcription (blocking)",
)
async def transcribe_sync(audio: UploadFile = File(...)) -> SynchronousTranscribeResponse:
    """Transcribe an uploaded WAV file synchronously.

    Accepts a multipart/form-data upload with field name ``audio``.
    Blocks until transcription is complete and returns the text.

    This endpoint is intended for testing and low-latency single-file use.
    For production batch processing, use POST /jobs instead.
    """
    import os
    import tempfile

    # Validate content type (accept audio/* or application/octet-stream)
    if audio.content_type and not (
        audio.content_type.startswith("audio/")
        or audio.content_type == "application/octet-stream"
    ):
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=f"Unsupported content type: {audio.content_type}. Expected audio/wav.",
        )

    # Load recognizer (may raise if model not loaded)
    try:
        recognizer = get_recognizer()
        if not recognizer.is_loaded():
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="ASR model is not yet loaded. Try again shortly.",
            )
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"ASR model unavailable: {exc}",
        ) from exc

    # Save upload to a temp file
    suffix = os.path.splitext(audio.filename or "audio.wav")[1] or ".wav"
    tmp_path: Optional[str] = None
    try:
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
            tmp_path = tmp.name
            content = await audio.read()
            tmp.write(content)

        start_ms = int(time.time() * 1000)
        text = recognizer.transcribe(tmp_path)
        processing_time_ms = int(time.time() * 1000) - start_ms

        logger.info(
            "Synchronous transcription completed in %d ms: %r",
            processing_time_ms,
            text[:80] if text else "",
        )

        return SynchronousTranscribeResponse(
            text=text,
            processing_time_ms=processing_time_ms,
        )

    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("Synchronous transcription failed")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Transcription failed: {exc}",
        ) from exc
    finally:
        if tmp_path and os.path.exists(tmp_path):
            try:
                os.unlink(tmp_path)
            except OSError:
                pass
