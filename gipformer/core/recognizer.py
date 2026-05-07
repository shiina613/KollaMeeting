"""
Gipformer ASR Service - Recognizer Singleton

Hỗ trợ 2 backend qua biến môi trường ASR_BACKEND:
  - "gipformer" : Gipformer-65M-RNNT (tiếng Việt thuần, CPU nhanh)
  - "whisper"   : Whisper distil-large-v3 via sherpa-onnx (VI+EN code-switching, GPU)

Cả 2 đều expose cùng interface: .transcribe(wav_path) → str
"""

import logging
import threading
import time

try:
    import sherpa_onnx
except ImportError as _sherpa_import_error:
    sherpa_onnx = None  # type: ignore[assignment]
    _SHERPA_IMPORT_ERROR = _sherpa_import_error
else:
    _SHERPA_IMPORT_ERROR = None

try:
    import soundfile as sf
except ImportError:
    sf = None  # type: ignore[assignment]

try:
    from huggingface_hub import hf_hub_download
except ImportError:
    hf_hub_download = None  # type: ignore[assignment]

from config import Settings

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Gipformer model constants
# ---------------------------------------------------------------------------

_GIPFORMER_SAMPLE_RATE = 16000
_GIPFORMER_FEATURE_DIM = 80
_GIPFORMER_ONNX_FILES = {
    "fp32": {
        "encoder": "encoder-epoch-35-avg-6.onnx",
        "decoder": "decoder-epoch-35-avg-6.onnx",
        "joiner":  "joiner-epoch-35-avg-6.onnx",
    },
    "int8": {
        "encoder": "encoder-epoch-35-avg-6.int8.onnx",
        "decoder": "decoder-epoch-35-avg-6.int8.onnx",
        "joiner":  "joiner-epoch-35-avg-6.int8.onnx",
    },
}

# ---------------------------------------------------------------------------
# Singleton state
# ---------------------------------------------------------------------------

_recognizer: "GipformerRecognizer | WhisperRecognizer | None" = None
_lock = threading.Lock()


# ---------------------------------------------------------------------------
# Shared helpers
# ---------------------------------------------------------------------------

def _check_deps() -> None:
    if sherpa_onnx is None:
        raise ImportError(
            "sherpa-onnx is not installed. Install: pip install sherpa-onnx"
        ) from _SHERPA_IMPORT_ERROR
    if hf_hub_download is None:
        raise ImportError(
            "huggingface_hub is not installed. Install: pip install huggingface_hub"
        )
    if sf is None:
        raise ImportError(
            "soundfile is not installed. Install: pip install soundfile"
        )


def _read_wav(wav_path: str):
    """Read WAV → (samples float32 mono, sample_rate)."""
    samples, sample_rate = sf.read(wav_path, dtype="float32")
    if samples.ndim > 1:
        samples = samples.mean(axis=1)
    return samples, sample_rate


# ---------------------------------------------------------------------------
# GipformerRecognizer
# ---------------------------------------------------------------------------


class GipformerRecognizer:
    """Wraps sherpa-onnx OfflineRecognizer for Gipformer-65M-RNNT (Vietnamese)."""

    def __init__(self, settings: Settings) -> None:
        _check_deps()
        self._settings = settings
        self._recognizer: "sherpa_onnx.OfflineRecognizer | None" = None

        logger.info(
            "Loading Gipformer model (quantize=%s, threads=%d, decoding=%s, device=%s) …",
            settings.MODEL_QUANTIZE,
            settings.NUM_THREADS,
            settings.DECODING_METHOD,
            settings.DEVICE,
        )
        t0 = time.perf_counter()
        try:
            model_paths = self._download_model()
            self._recognizer = self._create_recognizer(model_paths)
        except Exception:
            logger.exception("Failed to load Gipformer model")
            raise
        logger.info("Gipformer model loaded in %.2f s", time.perf_counter() - t0)

    # ── Public API ────────────────────────────────────────────────────────────

    def transcribe(self, wav_path: str) -> str:
        if not self.is_loaded():
            raise RuntimeError("Recognizer not initialized")
        samples, sample_rate = _read_wav(wav_path)
        stream = self._recognizer.create_stream()
        stream.accept_waveform(sample_rate, samples)
        self._recognizer.decode_streams([stream])
        return stream.result.text.strip()

    def is_loaded(self) -> bool:
        return self._recognizer is not None

    # ── Private helpers ───────────────────────────────────────────────────────

    def _download_model(self) -> dict:
        quantize = self._settings.MODEL_QUANTIZE
        repo_id  = self._settings.MODEL_REPO_ID
        files    = _GIPFORMER_ONNX_FILES[quantize]

        logger.info("Downloading Gipformer %s model from %s …", quantize, repo_id)
        try:
            paths: dict = {}
            for key, filename in files.items():
                paths[key] = hf_hub_download(repo_id=repo_id, filename=filename)
            paths["tokens"] = hf_hub_download(repo_id=repo_id, filename="tokens.txt")
        except Exception:
            logger.exception("Gipformer model download failed")
            raise
        logger.info("Gipformer model files downloaded")
        return paths

    def _create_recognizer(self, model_paths: dict) -> "sherpa_onnx.OfflineRecognizer":
        return sherpa_onnx.OfflineRecognizer.from_transducer(
            encoder=model_paths["encoder"],
            decoder=model_paths["decoder"],
            joiner=model_paths["joiner"],
            tokens=model_paths["tokens"],
            num_threads=self._settings.NUM_THREADS,
            sample_rate=_GIPFORMER_SAMPLE_RATE,
            feature_dim=_GIPFORMER_FEATURE_DIM,
            decoding_method=self._settings.DECODING_METHOD,
            provider=self._settings.DEVICE,
        )


# ---------------------------------------------------------------------------
# WhisperRecognizer
# ---------------------------------------------------------------------------


class WhisperRecognizer:
    """Wraps sherpa-onnx OfflineRecognizer for Whisper (multilingual VI+EN).

    Model: distil-large-v3 (mặc định) hoặc bất kỳ model Whisper nào được
    sherpa-onnx hỗ trợ.
    Download source: csukuangfj/sherpa-onnx-whisper-{model_name} trên HuggingFace.
    """

    def __init__(self, settings: Settings) -> None:
        _check_deps()
        self._settings = settings
        self._recognizer: "sherpa_onnx.OfflineRecognizer | None" = None

        logger.info(
            "Loading Whisper model (model=%s, language=%s, threads=%d, device=%s) …",
            settings.WHISPER_MODEL,
            settings.WHISPER_LANGUAGE,
            settings.NUM_THREADS,
            settings.DEVICE,
        )
        t0 = time.perf_counter()
        try:
            model_paths = self._download_model()
            self._recognizer = self._create_recognizer(model_paths)
        except Exception:
            logger.exception("Failed to load Whisper model")
            raise
        logger.info("Whisper model loaded in %.2f s", time.perf_counter() - t0)

    # ── Public API ────────────────────────────────────────────────────────────

    def transcribe(self, wav_path: str) -> str:
        if not self.is_loaded():
            raise RuntimeError("Whisper recognizer not initialized")
        samples, sample_rate = _read_wav(wav_path)
        stream = self._recognizer.create_stream()
        stream.accept_waveform(sample_rate, samples)
        self._recognizer.decode_streams([stream])
        return stream.result.text.strip()

    def is_loaded(self) -> bool:
        return self._recognizer is not None

    # ── Private helpers ───────────────────────────────────────────────────────

    def _download_model(self) -> dict:
        model_name = self._settings.WHISPER_MODEL
        repo_id    = f"csukuangfj/sherpa-onnx-whisper-{model_name}"

        logger.info("Downloading Whisper %s from %s …", model_name, repo_id)
        try:
            paths: dict = {}
            paths["encoder"] = hf_hub_download(
                repo_id=repo_id,
                filename=f"{model_name}-encoder.int8.onnx",
            )
            paths["decoder"] = hf_hub_download(
                repo_id=repo_id,
                filename=f"{model_name}-decoder.int8.onnx",
            )
            paths["tokens"] = hf_hub_download(
                repo_id=repo_id,
                filename=f"{model_name}-tokens.txt",
            )
        except Exception:
            logger.exception("Whisper model download failed (repo=%s)", repo_id)
            raise
        logger.info("Whisper model files downloaded")
        return paths

    def _create_recognizer(self, model_paths: dict) -> "sherpa_onnx.OfflineRecognizer":
        return sherpa_onnx.OfflineRecognizer.from_whisper(
            encoder=model_paths["encoder"],
            decoder=model_paths["decoder"],
            tokens=model_paths["tokens"],
            language=self._settings.WHISPER_LANGUAGE,
            task="transcribe",
            num_threads=self._settings.NUM_THREADS,
            decoding_method=self._settings.DECODING_METHOD,
            provider=self._settings.DEVICE,
        )


# ---------------------------------------------------------------------------
# Singleton accessor (factory tự chọn backend theo ASR_BACKEND)
# ---------------------------------------------------------------------------


def get_recognizer(
    settings: "Settings | None" = None,
) -> "GipformerRecognizer | WhisperRecognizer":
    """Return singleton ASR recognizer, tạo mới nếu chưa có.

    Backend được chọn theo settings.ASR_BACKEND:
      "gipformer" → GipformerRecognizer
      "whisper"   → WhisperRecognizer  (mặc định)

    Thread-safe (double-checked locking).
    """
    global _recognizer  # noqa: PLW0603

    if _recognizer is not None:
        return _recognizer

    with _lock:
        if _recognizer is not None:
            return _recognizer

        if settings is None:
            from config import settings as _default_settings  # noqa: PLC0415
            settings = _default_settings

        backend = (settings.ASR_BACKEND or "whisper").lower()
        if backend == "gipformer":
            _recognizer = GipformerRecognizer(settings)
        elif backend == "whisper":
            _recognizer = WhisperRecognizer(settings)
        else:
            raise ValueError(
                f"Unknown ASR_BACKEND={backend!r}. "
                "Valid values: 'gipformer', 'whisper'"
            )

    return _recognizer


# ---------------------------------------------------------------------------
# Startup helper
# ---------------------------------------------------------------------------


def load_recognizer_on_startup(settings: Settings) -> None:
    """Eagerly load singleton during FastAPI startup để warm-up model."""
    get_recognizer(settings)
