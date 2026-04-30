# Kolla Gipformer Service — Context & Conventions

## Overview

Python FastAPI service wrapping the Gipformer Vietnamese ASR model. Handles audio transcription with priority-based job queue.

## Model Details

- **Model**: `gipformer-65M-rnnt` (Zipformer Transducer architecture)
- **HuggingFace**: `g-group-ai-lab/gipformer-65M-rnnt`
- **Inference**: PyTorch CUDA (GPU) với auto-fallback về ONNX int8 (CPU) khi OOM
- **Quantization**: fp32 trên GPU, int8 trên CPU fallback
- **Hardware**: 
  - Dev: GTX 1650 Ti 4GB — test 1 meeting
  - Production: RTX 5060 Ti 16GB — 5-7 meetings đồng thời
- **Fallback logic**: Startup thử GPU → OOM/CUDA unavailable → tự động dùng CPU ONNX
- **Docker**: GPU passthrough enabled; `DEVICE=cuda` env var (set `cpu` để force CPU)
- **Input**: WAV file, 16kHz, mono, float32
- **Language**: Vietnamese only
- **RTF**: < 0.1 trên GPU; ~0.3-0.5 trên CPU fallback
- **Parameters**: 65M

## Project Structure

```
gipformer/
├── main.py                    # FastAPI app entry point
├── config.py                  # Environment variable config
├── api/
│   ├── routes.py              # /transcribe, /health, /jobs endpoints
│   └── schemas.py             # Pydantic request/response models
├── core/
│   ├── recognizer.py          # sherpa-onnx singleton recognizer
│   ├── vad_chunker.py         # Adaptive VAD + hard cap logic
│   └── audio_converter.py    # PCM → WAV 16kHz mono conversion
├── queue/
│   ├── redis_queue.py         # Redis Sorted Set operations
│   └── worker.py              # Background worker thread
└── callback/
    └── backend_notifier.py   # HTTP POST result to Spring Boot
```

## REST API Endpoints

### POST /jobs — Submit transcription job
```json
// Request
{
  "job_id": "uuid-v4",
  "meeting_id": 123,
  "speaker_id": 456,
  "speaker_name": "Nguyễn Văn A",
  "speaker_turn_id": "uuid-v4",
  "sequence_number": 3,
  "priority": "HIGH_PRIORITY",
  "audio_path": "/app/storage/audio_chunks/123/turn-uuid/chunk_3_job-uuid.wav",
  "callback_url": "http://backend:8080/api/v1/transcription/callback"
}

// Response 202
{ "job_id": "uuid-v4", "status": "QUEUED", "queue_position": 2 }
```

### GET /health — Service health check
```json
// Response 200 (ready) or 503 (not ready)
{
  "status": "ready",       // "ready" | "loading" | "error"
  "model_loaded": true,
  "queue_depth": 5,
  "uptime_seconds": 3600
}
```

### POST /transcribe — Synchronous transcription
```
// Request: multipart/form-data, field "audio" = WAV file
// Response 200
{ "text": "xin chào tôi muốn phát biểu", "processing_time_ms": 450, "rtf": 0.15 }
```

## Callback to Spring Boot

```json
// POST http://backend:8080/api/v1/transcription/callback
{
  "job_id": "uuid-v4",
  "meeting_id": 123,
  "speaker_id": 456,
  "speaker_name": "Nguyễn Văn A",
  "speaker_turn_id": "uuid-v4",
  "sequence_number": 3,
  "text": "xin chào tôi muốn phát biểu về vấn đề này",
  "confidence": 0.92,
  "processing_time_ms": 450,
  "segment_start_time": "2025-01-01T10:05:30+07:00"
}
```

## Redis Queue

```python
# Key: transcription:queue (Sorted Set)
# Score: HIGH_PRIORITY = 1_000_000_000 - unix_ms (higher score = processed first)
#        NORMAL_PRIORITY = unix_ms inverted (older jobs processed first within same priority)

# Push job
redis.zadd("transcription:queue", {job_id: score})
redis.hset(f"transcription:job:{job_id}", mapping=job_details)

# Pop highest priority job (worker loop)
result = redis.zpopmax("transcription:queue", count=1)
```

## Adaptive VAD Chunking Logic

```python
def get_vad_threshold(buffer_duration_seconds: float) -> float:
    """
    Returns silence threshold in seconds based on buffer duration.
    Property 5: if duration < 15s → threshold in [2.0, 3.0]
                if duration >= 15s → threshold in [0.5, 1.0]
    """
    if buffer_duration_seconds < 15.0:
        return 2.5  # middle of [2.0, 3.0]
    else:
        return 0.75  # middle of [0.5, 1.0]

# Hard cap: if buffer_duration >= 30s → force cut immediately
# Sequence number: starts at 1, increments per chunk within same speaker_turn_id
```

## Audio Conversion

```python
# Input: raw PCM Int16 bytes from Spring Boot WebSocket
# Output: WAV file at 16kHz mono

import soundfile as sf
import numpy as np

def pcm_to_wav(pcm_bytes: bytes, output_path: str, sample_rate: int = 16000):
    audio = np.frombuffer(pcm_bytes, dtype=np.int16).astype(np.float32) / 32768.0
    sf.write(output_path, audio, sample_rate, subtype='PCM_16')
```

## Recognizer — GPU with CPU Fallback

```python
# core/recognizer.py
import torch, logging, os
logger = logging.getLogger(__name__)

_recognizer = None
_device = None

def create_recognizer():
    """Try GPU (PyTorch CUDA) first, fallback to CPU (ONNX int8) on OOM."""
    global _recognizer, _device

    use_gpu = os.getenv("DEVICE", "cuda") == "cuda"

    if use_gpu and torch.cuda.is_available():
        try:
            _recognizer = _load_pytorch_model(device="cuda")
            _device = "cuda"
            logger.info("Gipformer loaded on GPU (CUDA)")
            return
        except torch.cuda.OutOfMemoryError:
            logger.warning("GPU OOM — falling back to CPU ONNX int8")
            torch.cuda.empty_cache()
        except Exception as e:
            logger.warning(f"GPU load failed: {e} — falling back to CPU ONNX int8")

    # Fallback: CPU ONNX int8
    _recognizer = _load_onnx_model()
    _device = "cpu"
    logger.info("Gipformer loaded on CPU (ONNX int8)")

def transcribe(wav_path: str) -> str:
    """Transcribe audio file. Works regardless of GPU/CPU mode."""
    if _device == "cuda":
        return _transcribe_pytorch(wav_path)
    else:
        return _transcribe_onnx(wav_path)
```

**Config:**
```python
DEVICE = os.getenv("DEVICE", "cuda")  # "cuda" hoặc "cpu" để force CPU
```

**Docker Compose:**
```yaml
gipformer:
  environment:
    - DEVICE=cuda   # fallback tự động nếu GPU OOM
  deploy:
    resources:
      reservations:
        devices:
          - driver: nvidia
            count: 1
            capabilities: [gpu]
```

**Health endpoint báo device đang dùng:**
```json
{
  "status": "ready",
  "device": "cuda",        // hoặc "cpu" nếu đã fallback
  "model_loaded": true,
  "queue_depth": 5
}
```

## Worker Loop

```python
# queue/worker.py
import threading, time

def worker_loop():
    while True:
        result = redis.zpopmax("transcription:queue", count=1)
        if result:
            job_id, score = result[0]
            job = redis.hgetall(f"transcription:job:{job_id}")
            text = transcribe(job["audio_path"])
            notify_backend(job, text)
        else:
            time.sleep(0.1)  # 100ms poll interval

# Start as daemon thread on app startup
thread = threading.Thread(target=worker_loop, daemon=True)
thread.start()
```

## Startup Sequence

1. Load config from env vars
2. Download model from HuggingFace (if not cached)
3. Load sherpa-onnx recognizer (warm-up)
4. Set `model_loaded = True`
5. Start worker thread
6. Start FastAPI server
7. `/health` returns `"status": "ready"` only after step 4

## Testing (pytest + hypothesis)

```python
from hypothesis import given, settings, strategies as st

@given(st.floats(min_value=0.0, max_value=60.0))
@settings(max_examples=1000)
def test_adaptive_vad_threshold(duration):
    threshold = get_vad_threshold(duration)
    if duration < 15.0:
        assert 2.0 <= threshold <= 3.0
    else:
        assert 0.5 <= threshold <= 1.0
```

## Config (environment variables)

```python
# config.py
PORT = int(os.getenv("PORT", 8000))
REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", 6379))
BACKEND_CALLBACK_URL = os.getenv("BACKEND_CALLBACK_URL", "http://localhost:8080/api/v1/transcription/callback")
DEVICE = os.getenv("DEVICE", "cuda")        # "cuda" hoặc "cpu" để force CPU
MODEL_QUANTIZE = os.getenv("MODEL_QUANTIZE", "int8")  # dùng khi DEVICE=cpu (ONNX fallback)
STORAGE_PATH = os.getenv("STORAGE_PATH", "/app/storage")
```
