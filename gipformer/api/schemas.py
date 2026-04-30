"""
Gipformer ASR Service - Pydantic Request/Response Schemas
"""

from typing import Literal, Optional

from pydantic import BaseModel, ConfigDict, Field


# ---------------------------------------------------------------------------
# Request Models
# ---------------------------------------------------------------------------


class TranscriptionJobRequest(BaseModel):
    """Request model for submitting an async transcription job."""

    model_config = ConfigDict(str_strip_whitespace=True)

    job_id: str = Field(..., description="UUID of the transcription job")
    meeting_id: int = Field(..., description="ID of the meeting")
    speaker_id: int = Field(..., description="ID of the speaker")
    speaker_name: str = Field(..., description="Display name of the speaker")
    speaker_turn_id: str = Field(..., description="UUID identifying this speaking turn")
    sequence_number: int = Field(..., ge=1, description="Chunk sequence number (≥ 1)")
    priority: Literal["HIGH_PRIORITY", "NORMAL_PRIORITY"] = Field(
        ..., description="Job priority level"
    )
    audio_path: str = Field(..., description="Path to the WAV audio file")
    callback_url: Optional[str] = Field(
        None, description="Override callback URL (uses config default if omitted)"
    )


class SynchronousTranscribeRequest(BaseModel):
    """Request model for synchronous (blocking) transcription."""

    model_config = ConfigDict(str_strip_whitespace=True)

    audio_path: str = Field(..., description="Path to the WAV audio file")
    meeting_id: Optional[int] = Field(None, description="Optional meeting context ID")


# ---------------------------------------------------------------------------
# Response Models
# ---------------------------------------------------------------------------


class TranscriptionJobResponse(BaseModel):
    """Response returned after submitting an async transcription job."""

    model_config = ConfigDict(str_strip_whitespace=True)

    job_id: str = Field(..., description="UUID of the submitted job")
    status: str = Field(..., description="Current job status (e.g. QUEUED)")
    message: str = Field(..., description="Human-readable status message")


class TranscriptionCallbackPayload(BaseModel):
    """Payload sent to Spring Boot callback endpoint when a job completes."""

    model_config = ConfigDict(str_strip_whitespace=True)

    job_id: str = Field(..., description="UUID of the completed job")
    meeting_id: int = Field(..., description="ID of the meeting")
    speaker_id: int = Field(..., description="ID of the speaker")
    speaker_name: str = Field(..., description="Display name of the speaker")
    speaker_turn_id: str = Field(..., description="UUID of the speaking turn")
    sequence_number: int = Field(..., description="Chunk sequence number")
    text: str = Field(..., description="Transcribed text")
    confidence: Optional[float] = Field(None, description="Confidence score (0–1)")
    processing_time_ms: int = Field(
        ..., description="Time taken to process the audio in milliseconds"
    )
    segment_start_time: str = Field(
        ..., description="ISO 8601 timestamp of when the audio chunk started"
    )


class HealthResponse(BaseModel):
    """Health check response."""

    model_config = ConfigDict(str_strip_whitespace=True)

    status: str = Field(..., description="Service status (ok / degraded / error)")
    model_loaded: bool = Field(..., description="Whether the ASR model is loaded")
    redis_connected: bool = Field(..., description="Whether Redis is reachable")
    version: str = Field(default="1.0.0", description="Service version")


class SynchronousTranscribeResponse(BaseModel):
    """Response for synchronous transcription requests."""

    model_config = ConfigDict(str_strip_whitespace=True)

    text: str = Field(..., description="Transcribed text")
    processing_time_ms: int = Field(
        ..., description="Time taken to process the audio in milliseconds"
    )
