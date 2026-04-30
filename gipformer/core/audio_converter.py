"""
Gipformer ASR Service - Audio Converter
Convert raw PCM (Float32 or Int16) bytes → WAV 16kHz mono.
"""

import logging
import math

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

logger = logging.getLogger(__name__)

TARGET_SAMPLE_RATE = 16000


def pcm_float32_to_wav(pcm_bytes: bytes, source_sample_rate: int, output_path: str) -> str:
    """Convert raw PCM Float32 bytes to a 16kHz mono WAV file.

    Args:
        pcm_bytes: Raw PCM audio data in Float32 little-endian format.
        source_sample_rate: Sample rate of the input PCM data.
        output_path: File path where the output WAV will be written.

    Returns:
        output_path

    Raises:
        ValueError: If pcm_bytes is empty or its length is not a multiple of 4.
    """
    if not pcm_bytes:
        raise ValueError("Empty PCM data")

    if len(pcm_bytes) % 4 != 0:
        raise ValueError(
            f"PCM Float32 data length ({len(pcm_bytes)} bytes) is not a multiple of 4"
        )

    logger.debug(
        "pcm_float32_to_wav: input_size=%d bytes, source_rate=%d, output=%s",
        len(pcm_bytes),
        source_sample_rate,
        output_path,
    )

    samples = np.frombuffer(pcm_bytes, dtype=np.float32).copy()

    # Resample to 16kHz if needed
    if source_sample_rate != TARGET_SAMPLE_RATE:
        gcd = math.gcd(source_sample_rate, TARGET_SAMPLE_RATE)
        up = TARGET_SAMPLE_RATE // gcd
        down = source_sample_rate // gcd
        samples = resample_poly(samples, up, down).astype(np.float32)

    # Clip to valid float range
    samples = np.clip(samples, -1.0, 1.0)

    sf.write(output_path, samples, TARGET_SAMPLE_RATE, subtype="PCM_16")
    return output_path


def pcm_int16_to_wav(pcm_bytes: bytes, source_sample_rate: int, output_path: str) -> str:
    """Convert raw PCM Int16 bytes to a 16kHz mono WAV file.

    Args:
        pcm_bytes: Raw PCM audio data in Int16 little-endian format.
        source_sample_rate: Sample rate of the input PCM data.
        output_path: File path where the output WAV will be written.

    Returns:
        output_path

    Raises:
        ValueError: If pcm_bytes is empty or its length is not a multiple of 2.
    """
    if not pcm_bytes:
        raise ValueError("Empty PCM data")

    if len(pcm_bytes) % 2 != 0:
        raise ValueError(
            f"PCM Int16 data length ({len(pcm_bytes)} bytes) is not a multiple of 2"
        )

    logger.debug(
        "pcm_int16_to_wav: input_size=%d bytes, source_rate=%d, output=%s",
        len(pcm_bytes),
        source_sample_rate,
        output_path,
    )

    int16_samples = np.frombuffer(pcm_bytes, dtype=np.int16)

    # Normalize Int16 → Float32
    samples = int16_samples.astype(np.float32) / 32768.0

    # Resample to 16kHz if needed
    if source_sample_rate != TARGET_SAMPLE_RATE:
        gcd = math.gcd(source_sample_rate, TARGET_SAMPLE_RATE)
        up = TARGET_SAMPLE_RATE // gcd
        down = source_sample_rate // gcd
        samples = resample_poly(samples, up, down).astype(np.float32)

    # Clip to valid float range
    samples = np.clip(samples, -1.0, 1.0)

    sf.write(output_path, samples, TARGET_SAMPLE_RATE, subtype="PCM_16")
    return output_path


def wav_to_float32_samples(wav_path: str) -> tuple:
    """Read a WAV file and return float32 samples with sample rate.

    Args:
        wav_path: Path to the WAV file to read.

    Returns:
        Tuple of (float32 samples array, sample_rate).
        Stereo audio is converted to mono by averaging channels.
    """
    samples, sample_rate = sf.read(wav_path, dtype="float32")

    # Convert stereo to mono
    if samples.ndim > 1:
        samples = samples.mean(axis=1)

    return samples, sample_rate


def ensure_16khz_mono_wav(input_path: str, output_path: str) -> str:
    """Read an existing WAV file and ensure it is 16kHz mono, writing to output_path.

    Args:
        input_path: Path to the source WAV file.
        output_path: Path where the 16kHz mono WAV will be written.

    Returns:
        output_path
    """
    logger.debug(
        "ensure_16khz_mono_wav: input=%s, output=%s",
        input_path,
        output_path,
    )

    samples, sample_rate = wav_to_float32_samples(input_path)

    if sample_rate != TARGET_SAMPLE_RATE:
        gcd = math.gcd(sample_rate, TARGET_SAMPLE_RATE)
        up = TARGET_SAMPLE_RATE // gcd
        down = sample_rate // gcd
        samples = resample_poly(samples, up, down).astype(np.float32)

    samples = np.clip(samples, -1.0, 1.0)

    sf.write(output_path, samples, TARGET_SAMPLE_RATE, subtype="PCM_16")
    return output_path


def pcm_bytes_to_wav(
    pcm_bytes: bytes,
    source_sample_rate: int,
    output_path: str,
    dtype: str = "float32",
) -> str:
    """Dispatcher: convert raw PCM bytes to a 16kHz mono WAV file.

    Args:
        pcm_bytes: Raw PCM audio bytes.
        source_sample_rate: Sample rate of the input PCM data.
        output_path: File path where the output WAV will be written.
        dtype: PCM data type — ``"float32"`` (default) or ``"int16"``.

    Returns:
        output_path

    Raises:
        ValueError: If pcm_bytes is empty, has wrong alignment, or dtype is unknown.
    """
    if dtype == "float32":
        return pcm_float32_to_wav(pcm_bytes, source_sample_rate, output_path)
    elif dtype == "int16":
        return pcm_int16_to_wav(pcm_bytes, source_sample_rate, output_path)
    else:
        raise ValueError(f"Unsupported dtype: {dtype!r}. Expected 'float32' or 'int16'.")
