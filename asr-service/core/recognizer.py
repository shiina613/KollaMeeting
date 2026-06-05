"""
Kolla ASR Service - Recognizer Singleton

Hỗ trợ 2 backend qua biến môi trường ASR_BACKEND:
  - "phowhisper" : PhoWhisper-medium CT2 int8_float16 (VI + code-switching, GPU)
  - "gipformer"  : Gipformer-65M-RNNT (tiếng Việt thuần, CPU nhanh)
  - "whisper"    : alias của phowhisper (tương thích cấu hình cũ)

Cả 2 đều expose cùng interface: .transcribe(wav_path) → str
"""

import logging
import threading
import time
from pathlib import Path

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

try:
    from faster_whisper import WhisperModel as FasterWhisperModel
except ImportError:
    FasterWhisperModel = None  # type: ignore[assignment]

from config import Settings

logger = logging.getLogger(__name__)

_ASR_SERVICE_ROOT = Path(__file__).resolve().parent.parent

# ---------------------------------------------------------------------------
# ASR service model constants
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

_recognizer: "GipformerRecognizer | PhoWhisperRecognizer | None" = None
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


def _resolve_phowhisper_model_path(settings: Settings) -> Path:
    """Resolve PHOWHISPER_MODEL_PATH to an absolute directory with model.bin."""
    raw = Path(settings.PHOWHISPER_MODEL_PATH)
    model_dir = raw if raw.is_absolute() else _ASR_SERVICE_ROOT / raw
    model_bin = model_dir / "model.bin"
    if not model_bin.is_file():
        raise FileNotFoundError(
            f"PhoWhisper model not found at {model_dir} "
            f"(expected model.bin). Set PHOWHISPER_MODEL_PATH."
        )
    return model_dir


def _resolve_phowhisper_compute_type(settings: Settings) -> str:
    if settings.PHOWHISPER_COMPUTE_TYPE.strip():
        return settings.PHOWHISPER_COMPUTE_TYPE.strip()
    return "int8_float16" if settings.DEVICE.lower() == "cuda" else "int8"


# ---------------------------------------------------------------------------
# ASR serviceRecognizer
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
# PhoWhisperRecognizer
# ---------------------------------------------------------------------------


class PhoWhisperRecognizer:
    """PhoWhisper-medium backend qua faster-whisper (CTranslate2, int8_float16).

    Mô hình được đóng gói sẵn trong repo (PHOWHISPER_MODEL_PATH), fine-tune
    tiếng Việt từ Whisper, hỗ trợ code-switching Vi–En trong cuộc họp.
    """

    def __init__(self, settings: Settings) -> None:
        if FasterWhisperModel is None:
            raise ImportError(
                "faster-whisper không được cài. Thêm vào Dockerfile: "
                "pip install faster-whisper>=1.0.0"
            )
        self._settings = settings
        self._model: "FasterWhisperModel | None" = None

        device = settings.DEVICE.lower()
        compute = _resolve_phowhisper_compute_type(settings)
        model_dir = _resolve_phowhisper_model_path(settings)

        logger.info(
            "Loading PhoWhisper model via faster-whisper "
            "(path=%s, language=%s, device=%s, compute=%s) …",
            model_dir,
            settings.PHOWHISPER_LANGUAGE,
            device,
            compute,
        )
        t0 = time.perf_counter()
        try:
            self._model = FasterWhisperModel(
                model_size_or_path=str(model_dir),
                device=device,
                compute_type=compute,
                num_workers=1,
                cpu_threads=settings.NUM_THREADS,
            )
        except Exception:
            logger.exception("Failed to load PhoWhisper model via faster-whisper")
            raise
        logger.info("PhoWhisper model loaded in %.2f s", time.perf_counter() - t0)

    def transcribe(self, wav_path: str) -> str:
        if not self.is_loaded():
            raise RuntimeError("PhoWhisper recognizer not initialized")

        transcribe_kwargs: dict = {
            "language": self._settings.PHOWHISPER_LANGUAGE,
            "task": "transcribe",
            "beam_size": 5,
            "vad_filter": True,
            "vad_parameters": dict(min_silence_duration_ms=500),
        }
        if self._settings.PHOWHISPER_LANGUAGE == "vi":
            transcribe_kwargs["initial_prompt"] = "Đây là cuộc họp tiếng Việt."

        segments, _info = self._model.transcribe(wav_path, **transcribe_kwargs)
        return " ".join(seg.text.strip() for seg in segments).strip()

    def is_loaded(self) -> bool:
        return self._model is not None


# ---------------------------------------------------------------------------
# Singleton accessor (factory tự chọn backend theo ASR_BACKEND)
# ---------------------------------------------------------------------------


def get_recognizer(
    settings: "Settings | None" = None,
) -> "GipformerRecognizer | PhoWhisperRecognizer":
    """Return singleton ASR recognizer, tạo mới nếu chưa có.

    Backend được chọn theo settings.ASR_BACKEND:
      "phowhisper" | "whisper" → PhoWhisperRecognizer  (mặc định)
      "gipformer"              → GipformerRecognizer

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

        backend = (settings.ASR_BACKEND or "phowhisper").lower()
        if backend == "gipformer":
            _recognizer = GipformerRecognizer(settings)
        elif backend in {"phowhisper", "whisper"}:
            _recognizer = PhoWhisperRecognizer(settings)
        else:
            raise ValueError(
                f"Unknown ASR_BACKEND={backend!r}. "
                "Valid values: 'phowhisper', 'whisper', 'gipformer'"
            )

    return _recognizer


def load_recognizer_on_startup(settings: Settings) -> None:
    """Eagerly load singleton during FastAPI startup để warm-up model."""
    get_recognizer(settings)
