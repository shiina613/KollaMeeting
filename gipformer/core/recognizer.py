"""
Gipformer ASR Service - sherpa-onnx Recognizer Singleton
Loads the gipformer-65M-rnnt ONNX model once and exposes transcribe().
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
# Model constants
# ---------------------------------------------------------------------------

REPO_ID = "g-group-ai-lab/gipformer-65M-rnnt"
SAMPLE_RATE = 16000
FEATURE_DIM = 80

ONNX_FILES = {
    "fp32": {
        "encoder": "encoder-epoch-35-avg-6.onnx",
        "decoder": "decoder-epoch-35-avg-6.onnx",
        "joiner": "joiner-epoch-35-avg-6.onnx",
    },
    "int8": {
        "encoder": "encoder-epoch-35-avg-6.int8.onnx",
        "decoder": "decoder-epoch-35-avg-6.int8.onnx",
        "joiner": "joiner-epoch-35-avg-6.int8.onnx",
    },
}

# ---------------------------------------------------------------------------
# Singleton state
# ---------------------------------------------------------------------------

_recognizer: "GipformerRecognizer | None" = None
_lock = threading.Lock()


# ---------------------------------------------------------------------------
# GipformerRecognizer class
# ---------------------------------------------------------------------------


class GipformerRecognizer:
    """Wraps a sherpa-onnx OfflineRecognizer for Vietnamese ASR.

    Instantiate via :func:`get_recognizer` to ensure singleton semantics.
    """

    def __init__(self, settings: Settings) -> None:
        if sherpa_onnx is None:
            raise ImportError(
                "sherpa-onnx is not installed. "
                "Install it with: pip install sherpa-onnx"
            ) from _SHERPA_IMPORT_ERROR

        if hf_hub_download is None:
            raise ImportError(
                "huggingface_hub is not installed. "
                "Install it with: pip install huggingface_hub"
            )

        if sf is None:
            raise ImportError(
                "soundfile is not installed. "
                "Install it with: pip install soundfile"
            )

        self._settings = settings
        self._recognizer: "sherpa_onnx.OfflineRecognizer | None" = None

        logger.info(
            "Loading Gipformer model (quantize=%s, threads=%d, decoding=%s) …",
            settings.MODEL_QUANTIZE,
            settings.NUM_THREADS,
            settings.DECODING_METHOD,
        )
        t0 = time.perf_counter()

        try:
            model_paths = self._download_model()
            self._recognizer = self._create_recognizer(model_paths)
        except Exception:
            logger.exception("Failed to load Gipformer model")
            raise

        elapsed = time.perf_counter() - t0
        logger.info("Gipformer model loaded in %.2f s", elapsed)

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def transcribe(self, wav_path: str) -> str:
        """Transcribe a WAV file and return the recognised text.

        Args:
            wav_path: Path to a WAV audio file (16 kHz mono recommended).

        Returns:
            Stripped transcription string.

        Raises:
            RuntimeError: If the recognizer has not been initialised.
        """
        if not self.is_loaded():
            raise RuntimeError("Recognizer not initialized")

        samples, sample_rate = sf.read(wav_path, dtype="float32")

        # Convert stereo to mono if needed
        if samples.ndim > 1:
            samples = samples.mean(axis=1)

        stream = self._recognizer.create_stream()
        stream.accept_waveform(sample_rate, samples)
        self._recognizer.decode_streams([stream])

        return stream.result.text.strip()

    def is_loaded(self) -> bool:
        """Return True if the underlying recognizer is ready."""
        return self._recognizer is not None

    # ------------------------------------------------------------------
    # Private helpers
    # ------------------------------------------------------------------

    def _download_model(self) -> dict:
        """Download ONNX model files from HuggingFace.

        Returns:
            Dict mapping 'encoder', 'decoder', 'joiner', 'tokens' to local paths.

        Raises:
            Exception: Re-raises any download error after logging.
        """
        quantize = self._settings.MODEL_QUANTIZE
        repo_id = self._settings.MODEL_REPO_ID
        files = ONNX_FILES[quantize]

        logger.info("Downloading %s model from %s …", quantize, repo_id)
        try:
            paths: dict = {}
            for key, filename in files.items():
                paths[key] = hf_hub_download(repo_id=repo_id, filename=filename)
            paths["tokens"] = hf_hub_download(repo_id=repo_id, filename="tokens.txt")
        except Exception:
            logger.exception("Model download failed")
            raise

        logger.info("Model files downloaded successfully")
        return paths

    def _create_recognizer(
        self, model_paths: dict
    ) -> "sherpa_onnx.OfflineRecognizer":
        """Instantiate a sherpa-onnx OfflineRecognizer from downloaded paths.

        Args:
            model_paths: Dict with keys 'encoder', 'decoder', 'joiner', 'tokens'.

        Returns:
            Configured :class:`sherpa_onnx.OfflineRecognizer` instance.
        """
        return sherpa_onnx.OfflineRecognizer.from_transducer(
            encoder=model_paths["encoder"],
            decoder=model_paths["decoder"],
            joiner=model_paths["joiner"],
            tokens=model_paths["tokens"],
            num_threads=self._settings.NUM_THREADS,
            sample_rate=SAMPLE_RATE,
            feature_dim=FEATURE_DIM,
            decoding_method=self._settings.DECODING_METHOD,
        )


# ---------------------------------------------------------------------------
# Singleton accessor
# ---------------------------------------------------------------------------


def get_recognizer(settings: "Settings | None" = None) -> GipformerRecognizer:
    """Return the singleton :class:`GipformerRecognizer`, creating it if needed.

    Thread-safe: only one instance is ever created even under concurrent calls.

    Args:
        settings: :class:`~config.Settings` instance used on first creation.
                  Ignored on subsequent calls (cached instance is returned).
                  If *None* on first call, the module-level ``settings``
                  singleton from :mod:`config` is used.

    Returns:
        The singleton :class:`GipformerRecognizer`.

    Raises:
        ImportError: If sherpa-onnx or its dependencies are not installed.
        RuntimeError: If model loading fails.
    """
    global _recognizer  # noqa: PLW0603

    if _recognizer is not None:
        return _recognizer

    with _lock:
        # Double-checked locking
        if _recognizer is not None:
            return _recognizer

        if settings is None:
            from config import settings as _default_settings  # noqa: PLC0415

            settings = _default_settings

        _recognizer = GipformerRecognizer(settings)

    return _recognizer


# ---------------------------------------------------------------------------
# Startup helper
# ---------------------------------------------------------------------------


def load_recognizer_on_startup(settings: Settings) -> None:
    """Eagerly load the recognizer singleton during FastAPI startup.

    Calling this from the ``on_startup`` event warms up the model so that
    the first transcription request is not delayed by model loading.

    Args:
        settings: Application settings used to configure the recognizer.

    Raises:
        Exception: Re-raises any error that occurs during model loading so
                   the caller can decide whether to crash or continue.
    """
    get_recognizer(settings)
