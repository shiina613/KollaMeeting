# Kolla ASR Service

Microservice phiên âm tiếng Việt cho KollaMeeting (FastAPI, port 8000).

## Engine mặc định

**PhoWhisper-medium** (CTranslate2, lượng tử hóa `int8_float16`) — mô hình nằm tại `models/phowhisper-medium-ct2-int8_float16/`.

## Backend tùy chọn

Đặt `ASR_BACKEND=gipformer` để dùng **Gipformer-65M-RNNT** (sherpa-onnx, tiếng Việt thuần) — tải từ HuggingFace khi khởi động.

| `ASR_BACKEND` | Mô tả |
|---|---|
| `phowhisper` (mặc định) | PhoWhisper-medium local, VI + code-switching |
| `whisper` | Alias của `phowhisper` |
| `gipformer` | Gipformer ONNX (backend thay thế) |

## Biến môi trường chính

- `ASR_BACKEND` — engine nhận dạng
- `PHOWHISPER_MODEL_PATH` — đường dẫn thư mục CT2 (mặc định `models/phowhisper-medium-ct2-int8_float16`)
- `DEVICE` — `cuda` hoặc `cpu`
- `REDIS_URL` — hàng đợi job transcription
- `BACKEND_CALLBACK_URL` — callback Spring Boot sau khi phiên âm xong

## Chạy local

```bash
cd asr-service
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

## Docker

```bash
docker compose up -d asr-service
```

## Script thử nghiệm Gipformer (tùy chọn)

- `infer_onnx.py` / `infer_pytorch.py` — chạy trực tiếp mô hình Gipformer-65M-RNNT, không qua FastAPI.
