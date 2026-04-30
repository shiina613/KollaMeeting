"""
Gipformer ASR Service - Configuration
Environment variable configuration using pydantic-settings.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # Redis
    REDIS_URL: str = "redis://redis:6379"

    # Backend callback
    BACKEND_CALLBACK_URL: str = "http://backend:8080/api/v1/transcription/callback"
    BACKEND_API_KEY: str = "internal-api-key"

    # Model
    MODEL_REPO_ID: str = "g-group-ai-lab/gipformer-65M-rnnt"
    MODEL_QUANTIZE: str = "int8"  # "fp32" or "int8"

    # Inference
    NUM_THREADS: int = 4
    DECODING_METHOD: str = "modified_beam_search"
    DEVICE: str = "cpu"  # "cpu" or "cuda"

    # Storage
    AUDIO_STORAGE_PATH: str = "/app/storage/audio_chunks"

    # Worker
    WORKER_POLL_INTERVAL_MS: int = 100

    # Server
    GIPFORMER_PORT: int = 8000
    LOG_LEVEL: str = "INFO"


settings = Settings()
