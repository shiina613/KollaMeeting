# Gipformer ASR Service — Dockerfile
# Layer structure optimized for fast incremental rebuilds:
#   Layer 1: System packages          (hiếm thay đổi)
#   Layer 2: PyTorch + CUDA heavy     (hiếm thay đổi, ~5GB)
#   Layer 3: k2 + kaldifeat           (hiếm thay đổi, ~500MB)
#   Layer 4: Core ML packages         (thỉnh thoảng thay đổi)
#   Layer 5: App packages             (hay thay đổi nhất → đặt cuối)
#   Layer 6: App code                 (thay đổi thường xuyên)

FROM nvidia/cuda:12.8.0-cudnn-runtime-ubuntu22.04

# ── Layer 1: System packages ───────────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3.11 \
    python3.11-dev \
    python3-pip \
    python3.11-venv \
    libsndfile1 \
    ffmpeg \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/* \
    && update-alternatives --install /usr/bin/python3 python3 /usr/bin/python3.11 1 \
    && update-alternatives --install /usr/bin/python python /usr/bin/python3.11 1 \
    && python3 -m pip install --upgrade pip setuptools wheel

WORKDIR /app

# ── Layer 2: PyTorch + CUDA (heaviest layer, ~5GB, cache lâu nhất) ─────────
RUN pip install --no-cache-dir \
    --extra-index-url https://download.pytorch.org/whl/cu128 \
    torch==2.8.0 \
    torchaudio==2.8.0 \
    || echo "WARNING: PyTorch CUDA install failed, falling back to CPU-only"

# ── Layer 3: k2 + kaldifeat (~500MB, cache lâu) ────────────────────────────
RUN pip install --no-cache-dir \
    --find-links https://k2-fsa.github.io/k2/cuda.html \
    --find-links https://csukuangfj.github.io/kaldifeat/cuda.html \
    k2==1.24.4.dev20250807+cuda12.8.torch2.8.0 \
    kaldifeat==1.25.5.dev20250807+cuda12.8.torch2.8.0 \
    || echo "WARNING: k2/kaldifeat install failed, PyTorch inference unavailable"

# ── Layer 4: Core ML + ASR packages (thỉnh thoảng thay đổi) ───────────────
RUN pip install --no-cache-dir \
    "sherpa-onnx>=1.10.0" \
    "huggingface_hub>=0.20.0" \
    "scipy>=1.11.0" \
    "numpy>=1.20.0" \
    "soundfile>=0.12.0" \
    sentencepiece==0.2.1

# ── Layer 5: App/API packages (hay thay đổi → đặt cuối để cache hiệu quả) ──
RUN pip install --no-cache-dir \
    "fastapi>=0.110.0" \
    "uvicorn[standard]>=0.27.0" \
    "pydantic>=2.0.0" \
    "pydantic-settings>=2.0.0" \
    "httpx>=0.27.0" \
    "redis>=4.3.0" \
    "python-multipart>=0.0.9"

# ── Layer 6: App code (thay đổi thường xuyên nhất) ─────────────────────────
COPY . .

RUN mkdir -p /app/storage/audio_chunks

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
