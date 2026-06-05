"""
Kolla ASR Service - Configuration
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
    # "phowhisper" → PhoWhisper-medium CT2 int8_float16 (mặc định, VI + code-switching)
    # "gipformer"  → Gipformer-65M-RNNT (tiếng Việt thuần, CPU nhanh)
    # "whisper"    → alias của phowhisper (tương thích cấu hình cũ)
    ASR_BACKEND: str = "phowhisper"

    # ── PhoWhisper settings (khi ASR_BACKEND=phowhisper) ─────────────────────
    # Đường dẫn tới thư mục mô hình CTranslate2 đã lượng tử hóa (chứa model.bin).
    # Tương đối so với thư mục gốc asr-service/ hoặc tuyệt đối trong container (/app/...).
    PHOWHISPER_MODEL_PATH: str = "models/phowhisper-medium-ct2-int8_float16"
    PHOWHISPER_LANGUAGE: str = "vi"
    # int8_float16 cho GPU (khớp quantization_manifest); int8 trên CPU.
    PHOWHISPER_COMPUTE_TYPE: str = ""

    # ── ASR service settings (khi ASR_BACKEND=gipformer) ──────────────────────
    MODEL_REPO_ID: str = "g-group-ai-lab/gipformer-65M-rnnt"
    MODEL_QUANTIZE: str = "int8"  # "fp32" | "int8"

    # ── Inference chung ──────────────────────────────────────────────────────
    NUM_THREADS: int = 4
    DECODING_METHOD: str = "greedy_search"  # greedy_search | modified_beam_search
    DEVICE: str = "cuda"  # "cpu" | "cuda" — dùng "cpu" nếu không có GPU

    # Storage
    AUDIO_STORAGE_PATH: str = "/app/storage/audio_chunks"

    # Worker
    WORKER_POLL_INTERVAL_MS: int = 100

    # Server
    ASR_SERVICE_PORT: int = 8000
    LOG_LEVEL: str = "INFO"


settings = Settings()
