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

    # ── ASR backend selection ────────────────────────────────────────────────
    # "gipformer" → Gipformer-65M-RNNT (tiếng Việt thuần, rất nhanh trên CPU)
    # "whisper"   → Whisper via sherpa-onnx (VI+EN code-switching, cần GPU để realtime)
    ASR_BACKEND: str = "whisper"

    # ── Gipformer settings (khi ASR_BACKEND=gipformer) ──────────────────────
    MODEL_REPO_ID: str = "g-group-ai-lab/gipformer-65M-rnnt"
    MODEL_QUANTIZE: str = "int8"  # "fp32" | "int8"

    # ── Whisper settings (khi ASR_BACKEND=whisper) ───────────────────────────
    # Các model được hỗ trợ: tiny, base, small, medium, large-v2, large-v3,
    #   distil-large-v2, distil-large-v3, distil-large-v3.5
    # LƯU Ý: distil-* là English-only. Để hỗ trợ tiếng Việt dùng large-v3.
    # Khuyến nghị: large-v3 + int8_float16 (~2.2GB VRAM, multilingual VI+EN)
    WHISPER_MODEL: str = "large-v3"
    WHISPER_LANGUAGE: str = "vi"  # ngôn ngữ chính; Whisper tự nhận EN chêm vào

    # ── Inference chung ──────────────────────────────────────────────────────
    NUM_THREADS: int = 4
    DECODING_METHOD: str = "greedy_search"  # greedy_search | modified_beam_search
    DEVICE: str = "cuda"  # "cpu" | "cuda" — dùng "cpu" nếu không có GPU

    # Storage
    AUDIO_STORAGE_PATH: str = "/app/storage/audio_chunks"

    # Worker
    WORKER_POLL_INTERVAL_MS: int = 100

    # Server
    GIPFORMER_PORT: int = 8000
    LOG_LEVEL: str = "INFO"


settings = Settings()
