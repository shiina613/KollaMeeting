"""
Gipformer ASR Service - Backend Notifier
HTTP POST transcription results to Spring Boot callback endpoint.

Requirement 8.11: When Gipformer completes a job, it sends the result back
to the Backend_API via HTTP callback at POST /api/v1/transcription/callback.

Retry policy: up to 3 attempts with exponential back-off (1 s, 2 s, 4 s).
Timeout per attempt: 30 s.
"""

from __future__ import annotations

import logging
import time
from typing import Any, Dict, Optional

import httpx

from api.schemas import TranscriptionCallbackPayload

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

MAX_RETRIES: int = 3
BASE_BACKOFF_SECONDS: float = 1.0
REQUEST_TIMEOUT_SECONDS: float = 30.0


# ---------------------------------------------------------------------------
# BackendNotifier
# ---------------------------------------------------------------------------


class BackendNotifier:
    """Sends transcription results to the Spring Boot backend via HTTP POST.

    Args:
        callback_url: Full URL of the Spring Boot callback endpoint,
            e.g. ``"http://backend:8080/api/v1/transcription/callback"``.
        api_key: Internal API key sent in the ``X-Internal-Api-Key`` header.
        max_retries: Maximum number of delivery attempts (default 3).
        timeout: Per-request timeout in seconds (default 30).
    """

    def __init__(
        self,
        callback_url: str,
        api_key: str,
        max_retries: int = MAX_RETRIES,
        timeout: float = REQUEST_TIMEOUT_SECONDS,
    ) -> None:
        self._callback_url = callback_url
        self._api_key = api_key
        self._max_retries = max_retries
        self._timeout = timeout

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def notify(self, payload: TranscriptionCallbackPayload) -> bool:
        """Send *payload* to the backend callback endpoint.

        Retries up to :attr:`_max_retries` times with exponential back-off
        on network errors or 5xx responses.

        Args:
            payload: Completed transcription result.

        Returns:
            True if the backend acknowledged with 2xx, False otherwise.
        """
        body = payload.model_dump()
        headers = {
            "Content-Type": "application/json",
            "X-Internal-Api-Key": self._api_key,
        }

        last_error: Optional[Exception] = None

        for attempt in range(1, self._max_retries + 1):
            try:
                response = httpx.post(
                    self._callback_url,
                    json=body,
                    headers=headers,
                    timeout=self._timeout,
                )

                if response.is_success:
                    logger.info(
                        "Callback delivered for job %s (attempt %d/%d, status %d)",
                        payload.job_id,
                        attempt,
                        self._max_retries,
                        response.status_code,
                    )
                    return True

                # 4xx errors are not retried (client-side issue)
                if 400 <= response.status_code < 500:
                    logger.error(
                        "Callback rejected for job %s: HTTP %d — %s (not retrying)",
                        payload.job_id,
                        response.status_code,
                        response.text[:200],
                    )
                    return False

                # 5xx — log and retry
                logger.warning(
                    "Callback attempt %d/%d for job %s failed: HTTP %d",
                    attempt,
                    self._max_retries,
                    payload.job_id,
                    response.status_code,
                )
                last_error = RuntimeError(f"HTTP {response.status_code}")

            except httpx.TimeoutException as exc:
                logger.warning(
                    "Callback attempt %d/%d for job %s timed out: %s",
                    attempt,
                    self._max_retries,
                    payload.job_id,
                    exc,
                )
                last_error = exc

            except httpx.RequestError as exc:
                logger.warning(
                    "Callback attempt %d/%d for job %s network error: %s",
                    attempt,
                    self._max_retries,
                    payload.job_id,
                    exc,
                )
                last_error = exc

            # Exponential back-off before next attempt
            if attempt < self._max_retries:
                backoff = BASE_BACKOFF_SECONDS * (2 ** (attempt - 1))
                logger.debug(
                    "Waiting %.1f s before retry for job %s", backoff, payload.job_id
                )
                time.sleep(backoff)

        logger.error(
            "All %d callback attempts failed for job %s. Last error: %s",
            self._max_retries,
            payload.job_id,
            last_error,
        )
        return False

    def notify_from_dict(self, result: Dict[str, Any]) -> bool:
        """Convenience wrapper: build :class:`TranscriptionCallbackPayload` from *result* dict.

        Args:
            result: Dict containing all fields required by
                :class:`~api.schemas.TranscriptionCallbackPayload`.

        Returns:
            True if delivery succeeded, False otherwise.
        """
        payload = TranscriptionCallbackPayload(**result)
        return self.notify(payload)


# ---------------------------------------------------------------------------
# Factory helper
# ---------------------------------------------------------------------------


def create_notifier(callback_url: str, api_key: str) -> BackendNotifier:
    """Create a :class:`BackendNotifier` with default retry settings.

    Args:
        callback_url: Spring Boot callback URL.
        api_key: Internal API key.

    Returns:
        Configured :class:`BackendNotifier`.
    """
    return BackendNotifier(callback_url=callback_url, api_key=api_key)
