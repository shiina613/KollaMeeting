"""
Gipformer ASR Service - FastAPI Application Entry Point
Vietnamese ASR service using sherpa-onnx.
"""

import logging
import logging.config

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from api.routes import router
from config import settings

# ---------------------------------------------------------------------------
# Logging configuration
# ---------------------------------------------------------------------------

logging.config.dictConfig(
    {
        "version": 1,
        "disable_existing_loggers": False,
        "formatters": {
            "default": {
                "format": "%(asctime)s [%(levelname)s] %(name)s: %(message)s",
                "datefmt": "%Y-%m-%dT%H:%M:%S",
            }
        },
        "handlers": {
            "console": {
                "class": "logging.StreamHandler",
                "formatter": "default",
            }
        },
        "root": {
            "level": settings.LOG_LEVEL,
            "handlers": ["console"],
        },
    }
)

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# FastAPI application
# ---------------------------------------------------------------------------

app = FastAPI(
    title="Gipformer ASR Service",
    version="1.0.0",
    description="Vietnamese ASR service using sherpa-onnx",
)

# CORS — internal service, allow all origins
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include API router (no prefix — routes are at root level)
app.include_router(router)


# ---------------------------------------------------------------------------
# Lifecycle events
# ---------------------------------------------------------------------------


@app.on_event("startup")
async def on_startup() -> None:
    """Log service start, load model, and start background worker."""
    logger.info("=== Gipformer ASR Service starting ===")
    logger.info("  REDIS_URL          : %s", settings.REDIS_URL)
    logger.info("  BACKEND_CALLBACK_URL: %s", settings.BACKEND_CALLBACK_URL)
    logger.info(
        "  BACKEND_API_KEY    : %s",
        settings.BACKEND_API_KEY[:4] + "****" if settings.BACKEND_API_KEY else "(not set)",
    )
    logger.info("  MODEL_REPO_ID      : %s", settings.MODEL_REPO_ID)
    logger.info("  MODEL_QUANTIZE     : %s", settings.MODEL_QUANTIZE)
    logger.info("  NUM_THREADS        : %d", settings.NUM_THREADS)
    logger.info("  DECODING_METHOD    : %s", settings.DECODING_METHOD)
    logger.info("  DEVICE             : %s", settings.DEVICE)
    logger.info("  AUDIO_STORAGE_PATH : %s", settings.AUDIO_STORAGE_PATH)
    logger.info("  WORKER_POLL_INTERVAL_MS: %d", settings.WORKER_POLL_INTERVAL_MS)
    logger.info("  GIPFORMER_PORT     : %d", settings.GIPFORMER_PORT)
    logger.info("  LOG_LEVEL          : %s", settings.LOG_LEVEL)

    # Load ASR model
    from core.recognizer import load_recognizer_on_startup

    try:
        load_recognizer_on_startup(settings)
    except Exception as e:
        logger.warning(
            "Model failed to load on startup: %s. Will retry on first request.", e
        )

    # Start background transcription worker
    from callback.backend_notifier import create_notifier
    from job_queue.redis_queue import create_redis_queue
    from job_queue.worker import start_worker

    try:
        redis_queue = create_redis_queue(settings.REDIS_URL)
        notifier = create_notifier(
            callback_url=settings.BACKEND_CALLBACK_URL,
            api_key=settings.BACKEND_API_KEY,
        )
        start_worker(
            redis_queue=redis_queue,
            notifier=notifier,
            poll_interval_ms=settings.WORKER_POLL_INTERVAL_MS,
        )
        logger.info("Background transcription worker started")
    except Exception as e:
        logger.error("Failed to start background worker: %s", e)

    logger.info("=== Service ready ===")


@app.on_event("shutdown")
async def on_shutdown() -> None:
    """Stop background worker and log service stop."""
    from job_queue.worker import stop_worker

    stop_worker(timeout=5.0)
    logger.info("=== Gipformer ASR Service shutting down ===")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=settings.GIPFORMER_PORT,
        log_level=settings.LOG_LEVEL.lower(),
    )
