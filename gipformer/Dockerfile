# Gipformer ASR Service — Dockerfile
# Supports both GPU (CUDA) and CPU-only (ONNX int8) inference.
# Set DEVICE=cuda in .env for GPU, DEVICE=cpu for CPU-only.
#
# Base image: CUDA 12.8 + cuDNN 9 on Ubuntu 22.04
# Falls back gracefully to CPU ONNX if GPU is unavailable.

FROM nvidia/cuda:12.8.0-cudnn-runtime-ubuntu22.04

# ── System dependencies ────────────────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3.11 \
    python3.11-dev \
    python3-pip \
    python3.11-venv \
    libsndfile1 \
    ffmpeg \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Make python3.11 the default python3
RUN update-alternatives --install /usr/bin/python3 python3 /usr/bin/python3.11 1 \
    && update-alternatives --install /usr/bin/python python /usr/bin/python3.11 1

# Upgrade pip
RUN python3 -m pip install --upgrade pip setuptools wheel

# ── Working directory ──────────────────────────────────────────────────────
WORKDIR /app

# ── Install Python dependencies ────────────────────────────────────────────
# Copy requirements first for Docker layer caching
COPY requirements.txt .

# Install CPU-safe packages first (always needed)
RUN pip install --no-cache-dir \
    fastapi>=0.110.0 \
    "uvicorn[standard]>=0.27.0" \
    pydantic>=2.0.0 \
    pydantic-settings>=2.0.0 \
    httpx>=0.27.0 \
    "redis>=4.3.0" \
    "scipy>=1.11.0" \
    "numpy>=1.20.0" \
    "soundfile>=0.12.0" \
    "sherpa-onnx>=1.10.0" \
    "huggingface_hub>=0.20.0" \
    "python-multipart>=0.0.9"

# Install PyTorch with CUDA 12.8 support (for GPU inference)
# If DEVICE=cpu, these packages are installed but not used
RUN pip install --no-cache-dir \
    --extra-index-url https://download.pytorch.org/whl/cu128 \
    torch==2.8.0 \
    torchaudio==2.8.0 \
    sentencepiece==0.2.1 \
    || echo "WARNING: PyTorch CUDA install failed, falling back to CPU-only"

# Install k2 and kaldifeat for PyTorch inference (optional, may fail on some platforms)
RUN pip install --no-cache-dir \
    --find-links https://k2-fsa.github.io/k2/cuda.html \
    --find-links https://csukuangfj.github.io/kaldifeat/cuda.html \
    k2==1.24.4.dev20250807+cuda12.8.torch2.8.0 \
    kaldifeat==1.25.5.dev20250807+cuda12.8.torch2.8.0 \
    || echo "WARNING: k2/kaldifeat install failed, PyTorch inference unavailable (ONNX still works)"

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
