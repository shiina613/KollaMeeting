# Gipformer ASR Service — Dockerfile (CPU/ONNX mode)
# Uses lightweight Python base image — no CUDA required.
# Set DEVICE=cpu in .env for CPU-only ONNX int8 inference.
# Switch to nvidia/cuda base image only when GPU inference is needed.

FROM python:3.11-slim

# ── System dependencies ────────────────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    libsndfile1 \
    ffmpeg \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Upgrade pip
RUN pip install --upgrade pip setuptools wheel

# ── Working directory ──────────────────────────────────────────────────────
WORKDIR /app

# ── Install Python dependencies (CPU-only, lightweight) ───────────────────
RUN pip install --no-cache-dir \
    fastapi>=0.110.0 \
    "uvicorn[standard]>=0.27.0" \
    pydantic>=2.0.0 \
    pydantic-settings>=2.0.0 \
    httpx>=0.27.0 \
    python-multipart>=0.0.9 \
    "redis>=4.3.0" \
    "scipy>=1.11.0" \
    "numpy>=1.20.0" \
    "soundfile>=0.12.0" \
    "sherpa-onnx>=1.10.0" \
    "huggingface_hub>=0.20.0"

# ── Copy application code ──────────────────────────────────────────────────
COPY . .

# ── Create storage directory ───────────────────────────────────────────────
RUN mkdir -p /app/storage/audio_chunks

# ── Expose port ────────────────────────────────────────────────────────────
EXPOSE 8000

# ── Health check ───────────────────────────────────────────────────────────
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

# ── Start service ──────────────────────────────────────────────────────────
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
