"""
Unit tests for gipformer/core/audio_converter.py

Validates: Requirements 20.3
"""

import numpy as np
import pytest
import soundfile as sf

from core.audio_converter import (
    ensure_16khz_mono_wav,
    pcm_bytes_to_wav,
    pcm_float32_to_wav,
    pcm_int16_to_wav,
    wav_to_float32_samples,
)

TARGET_RATE = 16000


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _sine_float32_bytes(sample_rate: int, duration: float = 0.1, freq: float = 440.0) -> bytes:
    """Return raw float32 PCM bytes for a sine wave."""
    t = np.linspace(0, duration, int(sample_rate * duration), endpoint=False)
    samples = np.sin(2 * np.pi * freq * t).astype(np.float32)
    return samples.tobytes()


def _sine_int16_bytes(sample_rate: int, duration: float = 0.1, freq: float = 440.0) -> bytes:
    """Return raw int16 PCM bytes for a sine wave."""
    t = np.linspace(0, duration, int(sample_rate * duration), endpoint=False)
    samples = (np.sin(2 * np.pi * freq * t) * 32767).astype(np.int16)
    return samples.tobytes()


def _write_wav(path, samples: np.ndarray, sample_rate: int) -> None:
    """Write a WAV file using soundfile."""
    sf.write(str(path), samples, sample_rate)


# ---------------------------------------------------------------------------
# TestPcmFloat32ToWav
# ---------------------------------------------------------------------------

class TestPcmFloat32ToWav:

    def test_basic_conversion_same_rate(self, tmp_path):
        """1 second of 440 Hz at 16kHz → output should be 16kHz float32, ~16000 samples."""
        pcm = _sine_float32_bytes(TARGET_RATE, duration=1.0)
        out = str(tmp_path / "out.wav")
        pcm_float32_to_wav(pcm, TARGET_RATE, out)

        samples, rate = wav_to_float32_samples(out)
        assert rate == TARGET_RATE
        assert samples.dtype == np.float32
        # Allow ±5% tolerance on sample count
        assert abs(len(samples) - TARGET_RATE) < TARGET_RATE * 0.05

    def test_resampling_from_44100(self, tmp_path):
        """Float32 PCM at 44100 Hz → output WAV should be 16kHz."""
        pcm = _sine_float32_bytes(44100, duration=0.1)
        out = str(tmp_path / "out.wav")
        pcm_float32_to_wav(pcm, 44100, out)

        _, rate = wav_to_float32_samples(out)
        assert rate == TARGET_RATE

    def test_resampling_from_48000(self, tmp_path):
        """Float32 PCM at 48000 Hz → output WAV should be 16kHz."""
        pcm = _sine_float32_bytes(48000, duration=0.1)
        out = str(tmp_path / "out.wav")
        pcm_float32_to_wav(pcm, 48000, out)

        _, rate = wav_to_float32_samples(out)
        assert rate == TARGET_RATE

    def test_clipping(self, tmp_path):
        """Samples outside [-1, 1] must be clipped in the output."""
        # Create samples with values > 1.0 and < -1.0
        raw = np.array([2.0, -3.0, 1.5, -1.5, 0.5], dtype=np.float32)
        pcm = raw.tobytes()
        out = str(tmp_path / "out.wav")
        pcm_float32_to_wav(pcm, TARGET_RATE, out)

        samples, _ = wav_to_float32_samples(out)
        assert np.all(samples >= -1.0)
        assert np.all(samples <= 1.0)

    def test_empty_bytes_raises(self, tmp_path):
        """Empty bytes should raise ValueError."""
        out = str(tmp_path / "out.wav")
        with pytest.raises(ValueError):
            pcm_float32_to_wav(b"", TARGET_RATE, out)

    def test_misaligned_bytes_raises(self, tmp_path):
        """3 bytes (not multiple of 4) should raise ValueError."""
        out = str(tmp_path / "out.wav")
        with pytest.raises(ValueError):
            pcm_float32_to_wav(b"abc", TARGET_RATE, out)

    def test_output_file_created(self, tmp_path):
        """Output WAV file must exist after conversion."""
        pcm = _sine_float32_bytes(TARGET_RATE, duration=0.1)
        out = str(tmp_path / "out.wav")
        pcm_float32_to_wav(pcm, TARGET_RATE, out)

        assert (tmp_path / "out.wav").exists()


# ---------------------------------------------------------------------------
# TestPcmInt16ToWav
# ---------------------------------------------------------------------------

class TestPcmInt16ToWav:

    def test_basic_conversion_same_rate(self, tmp_path):
        """1 second of 440 Hz int16 at 16kHz → output should be 16kHz."""
        pcm = _sine_int16_bytes(TARGET_RATE, duration=1.0)
        out = str(tmp_path / "out.wav")
        pcm_int16_to_wav(pcm, TARGET_RATE, out)

        _, rate = wav_to_float32_samples(out)
        assert rate == TARGET_RATE

    def test_normalization(self, tmp_path):
        """Int16 max value (32767) should normalize to ~1.0 in float32 output."""
        # Single sample at max int16 value
        samples_int16 = np.array([32767], dtype=np.int16)
        pcm = samples_int16.tobytes()
        out = str(tmp_path / "out.wav")
        pcm_int16_to_wav(pcm, TARGET_RATE, out)

        samples, _ = wav_to_float32_samples(out)
        # After normalization: 32767 / 32768 ≈ 0.99997, then clipped to 1.0 by WAV PCM_16 encoding
        # The value should be close to 1.0
        assert abs(samples[0] - (32767 / 32768.0)) < 0.001 or abs(samples[0] - 1.0) < 0.001

    def test_resampling_from_44100(self, tmp_path):
        """Int16 PCM at 44100 Hz → output WAV should be 16kHz."""
        pcm = _sine_int16_bytes(44100, duration=0.1)
        out = str(tmp_path / "out.wav")
        pcm_int16_to_wav(pcm, 44100, out)

        _, rate = wav_to_float32_samples(out)
        assert rate == TARGET_RATE

    def test_empty_bytes_raises(self, tmp_path):
        """Empty bytes should raise ValueError."""
        out = str(tmp_path / "out.wav")
        with pytest.raises(ValueError):
            pcm_int16_to_wav(b"", TARGET_RATE, out)

    def test_misaligned_bytes_raises(self, tmp_path):
        """1 byte (not multiple of 2) should raise ValueError."""
        out = str(tmp_path / "out.wav")
        with pytest.raises(ValueError):
            pcm_int16_to_wav(b"a", TARGET_RATE, out)


# ---------------------------------------------------------------------------
# TestWavToFloat32Samples
# ---------------------------------------------------------------------------

class TestWavToFloat32Samples:

    def test_read_mono_wav(self, tmp_path):
        """Write a known mono WAV and verify samples and rate are read back correctly."""
        original = np.array([0.1, 0.2, 0.3, -0.1, -0.2], dtype=np.float32)
        wav_path = str(tmp_path / "mono.wav")
        _write_wav(wav_path, original, TARGET_RATE)

        samples, rate = wav_to_float32_samples(wav_path)
        assert rate == TARGET_RATE
        np.testing.assert_allclose(samples, original, atol=1e-4)

    def test_stereo_to_mono_conversion(self, tmp_path):
        """Stereo WAV should be converted to mono (1D array)."""
        stereo = np.random.uniform(-0.5, 0.5, (1000, 2)).astype(np.float32)
        wav_path = str(tmp_path / "stereo.wav")
        _write_wav(wav_path, stereo, TARGET_RATE)

        samples, rate = wav_to_float32_samples(wav_path)
        assert samples.ndim == 1
        assert rate == TARGET_RATE

    def test_returns_float32(self, tmp_path):
        """Returned samples must have dtype float32."""
        original = np.array([0.5, -0.5, 0.0], dtype=np.float32)
        wav_path = str(tmp_path / "test.wav")
        _write_wav(wav_path, original, TARGET_RATE)

        samples, _ = wav_to_float32_samples(wav_path)
        assert samples.dtype == np.float32


# ---------------------------------------------------------------------------
# TestEnsure16khzMonoWav
# ---------------------------------------------------------------------------

class TestEnsure16khzMonoWav:

    def test_already_16khz_mono(self, tmp_path):
        """16kHz mono WAV should pass through and output is still 16kHz."""
        samples = np.sin(2 * np.pi * 440 * np.linspace(0, 0.1, 1600)).astype(np.float32)
        in_path = str(tmp_path / "in.wav")
        out_path = str(tmp_path / "out.wav")
        _write_wav(in_path, samples, TARGET_RATE)

        ensure_16khz_mono_wav(in_path, out_path)

        _, rate = wav_to_float32_samples(out_path)
        assert rate == TARGET_RATE

    def test_resamples_44100_to_16000(self, tmp_path):
        """44100 Hz mono WAV should be resampled to 16kHz."""
        samples = np.sin(2 * np.pi * 440 * np.linspace(0, 0.1, 4410)).astype(np.float32)
        in_path = str(tmp_path / "in.wav")
        out_path = str(tmp_path / "out.wav")
        _write_wav(in_path, samples, 44100)

        ensure_16khz_mono_wav(in_path, out_path)

        _, rate = wav_to_float32_samples(out_path)
        assert rate == TARGET_RATE

    def test_stereo_to_mono(self, tmp_path):
        """44100 Hz stereo WAV should be converted to 16kHz mono."""
        stereo = np.random.uniform(-0.5, 0.5, (4410, 2)).astype(np.float32)
        in_path = str(tmp_path / "in.wav")
        out_path = str(tmp_path / "out.wav")
        _write_wav(in_path, stereo, 44100)

        ensure_16khz_mono_wav(in_path, out_path)

        samples, rate = wav_to_float32_samples(out_path)
        assert rate == TARGET_RATE
        assert samples.ndim == 1


# ---------------------------------------------------------------------------
# TestPcmBytesToWav
# ---------------------------------------------------------------------------

class TestPcmBytesToWav:

    def test_dispatches_float32(self, tmp_path):
        """dtype='float32' should produce a valid 16kHz WAV file."""
        pcm = _sine_float32_bytes(TARGET_RATE, duration=0.1)
        out = str(tmp_path / "out.wav")
        pcm_bytes_to_wav(pcm, TARGET_RATE, out, dtype="float32")

        assert (tmp_path / "out.wav").exists()
        _, rate = wav_to_float32_samples(out)
        assert rate == TARGET_RATE

    def test_dispatches_int16(self, tmp_path):
        """dtype='int16' should produce a valid 16kHz WAV file."""
        pcm = _sine_int16_bytes(TARGET_RATE, duration=0.1)
        out = str(tmp_path / "out.wav")
        pcm_bytes_to_wav(pcm, TARGET_RATE, out, dtype="int16")

        assert (tmp_path / "out.wav").exists()
        _, rate = wav_to_float32_samples(out)
        assert rate == TARGET_RATE

    def test_unknown_dtype_raises(self, tmp_path):
        """Unknown dtype should raise ValueError."""
        pcm = _sine_float32_bytes(TARGET_RATE, duration=0.1)
        out = str(tmp_path / "out.wav")
        with pytest.raises(ValueError):
            pcm_bytes_to_wav(pcm, TARGET_RATE, out, dtype="float64")
