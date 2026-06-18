# Quantization trong KollaMeeting

## 1. Bản chất

Quantization là kỹ thuật giảm độ chính xác số học của mô hình AI để giảm dung lượng, giảm RAM/VRAM và tăng tốc suy luận. Thay vì dùng toàn bộ trọng số ở FP32, mô hình có thể được convert sang dạng nhẹ hơn như INT8, FP16 hoặc kiểu lai.

Trong KollaMeeting, quantization xuất hiện ở module ASR, cụ thể là PhoWhisper chạy qua CTranslate2/faster-whisper.

Mục tiêu không phải là nghiên cứu thuật toán lượng tử hóa mới, mà là làm cho mô hình nhận dạng giọng nói chạy được trên phần cứng mục tiêu, ví dụ GPU 4 GB, với tốc độ đủ nhanh cho pipeline họp.

## 2. File liên quan

ASR runtime:

- `asr-service/config.py`
- `asr-service/core/recognizer.py`
- `asr-service/job_queue/worker.py`

Docker/config:

- `docker-compose.yml`

Đánh giá và convert model:

- `evaluation/phowhisper_quantization/convert_phowhisper_ct2.py`
- `evaluation/phowhisper_quantization/evaluate_gigaspeech2_ct2.py`
- `evaluation/whisper_prequantized/README.md`
- `slide.md`, `answer.md` chứa số liệu bảo vệ.

## 3. Cấu hình hiện tại

Trong `docker-compose.yml`, ASR service mặc định:

```text
ASR_BACKEND=phowhisper
PHOWHISPER_MODEL_PATH=models/phowhisper-medium-ct2-int8_float16
```

Trong `asr-service/config.py`:

```text
ASR_BACKEND = "phowhisper"
PHOWHISPER_MODEL_PATH = "models/phowhisper-medium-ct2-int8_float16"
PHOWHISPER_LANGUAGE = "vi"
```

`PHOWHISPER_COMPUTE_TYPE` nếu không set thì `recognizer.py` tự chọn:

```python
return "int8_float16" if settings.DEVICE.lower() == "cuda" else "int8"
```

Nghĩa là:

- Chạy GPU/CUDA: ưu tiên `int8_float16`.
- Chạy CPU: ưu tiên `int8`.

## 4. CT2 và faster-whisper là gì trong hệ thống?

PhoWhisper gốc là model Hugging Face. Để chạy tối ưu hơn, dự án convert model sang format CTranslate2. Sau đó runtime dùng `faster-whisper` để load model CT2.

Trong `asr-service/core/recognizer.py`, class `PhoWhisperRecognizer` load model:

```python
FasterWhisperModel(
    model_size_or_path=str(model_dir),
    device=device,
    compute_type=compute,
    num_workers=1,
    cpu_threads=settings.NUM_THREADS,
)
```

`compute_type` chính là chế độ tính toán/quantization khi inference.

## 5. Script convert PhoWhisper

File đang mở trong IDE của bạn:

```text
evaluation/phowhisper_quantization/convert_phowhisper_ct2.py
```

Script này wrap converter của CTranslate2:

```text
ctranslate2.converters.transformers
```

Nó hỗ trợ các lựa chọn:

```text
int8
int8_float32
int8_float16
int8_bfloat16
float16
bfloat16
```

Luồng convert:

1. Chọn model PhoWhisper: tiny/base/small/medium/large hoặc truyền `--model`.
2. Chọn `--quantization`, ví dụ `int8_float16`.
3. Tạo output folder dạng `<model>-ct2-<quantization>`.
4. Gọi converter của CTranslate2.
5. Ghi `quantization_manifest.json` để biết model nguồn, kiểu quantization, thời điểm convert và converter.

Ví dụ ý nghĩa:

```text
vinai/PhoWhisper-medium
-> CTranslate2
-> phowhisper-medium-ct2-int8_float16
```

## 6. Luồng inference trong KollaMeeting

Quantized model tham gia vào pipeline như sau:

1. Backend tạo WAV chunk từ audio WebSocket.
2. Backend đẩy `TranscriptionJob` vào Redis.
3. `asr-service/job_queue/worker.py` pop job.
4. Worker gọi `recognizer.transcribe(audio_path)`.
5. `PhoWhisperRecognizer` dùng faster-whisper load model CT2 đã quantized.
6. Model nhận WAV và trả về text.
7. Worker callback text về backend.

Trong `PhoWhisperRecognizer.transcribe()`, tham số chính:

```python
language = "vi"
task = "transcribe"
beam_size = 5
vad_filter = True
initial_prompt = "Đây là cuộc họp tiếng Việt."
```

`vad_filter=True` giúp faster-whisper bỏ qua đoạn lặng ở mức model. Trước đó backend cũng đã có RMS/VAD để cắt chunk, nên hệ thống có hai lớp giảm nhiễu/lặng:

- Backend VAD để quyết định flush audio chunk.
- Model VAD filter để hỗ trợ inference.

## 7. Vì sao chọn PhoWhisper Medium CT2 int8_float16?

Theo tài liệu bảo vệ trong `slide.md` và `answer.md`, kết quả chính:

```text
PhoWhisper Medium CT2 int8_float16: WER 10.90%, RTF 0.452
PhoWhisper Large  CT2 int8_float16: WER  9.38%, RTF 0.748
```

Lý do chọn Medium CT2:

- Chạy ổn định hơn trên GPU 4 GB.
- RTF < 1, tức riêng bước ASR nhanh hơn thời lượng audio.
- WER đủ tốt cho biên bản có bước host/secretary kiểm tra lại.
- Large chính xác hơn nhưng chậm hơn và tốn VRAM hơn.
- PhoWhisper phù hợp hơn bối cảnh họp có tiếng Việt, tên riêng, thuật ngữ và code-switching so với mô hình chỉ thiên về tiếng Việt thuần.

Điểm cần nói chính xác: RTF < 1 chỉ chứng minh tốc độ inference của model, chưa chứng minh toàn pipeline realtime end-to-end, vì còn phụ thuộc chunking, queue, I/O file và callback.

## 8. int8_float16 nghĩa là gì?

`int8_float16` là kiểu lai thường dùng trên GPU:

- Trọng số có thể được nén INT8 để giảm bộ nhớ.
- Một phần tính toán/activation dùng FP16 để tận dụng GPU.

Ý nghĩa thực tế trong hệ thống:

- Giảm VRAM so với model full precision.
- Cho phép chạy model medium/large trong giới hạn phần cứng.
- Có thể đánh đổi một phần nhỏ độ chính xác.

Vì vậy dự án phải đánh giá WER/RTF sau khi convert, không chỉ giả định model nhẹ hơn là tốt hơn.

## 9. Liên hệ với Gipformer

`recognizer.py` hỗ trợ cả:

```text
ASR_BACKEND=phowhisper
ASR_BACKEND=gipformer
```

Gipformer có bản ONNX INT8, chạy CPU nhanh và tiếng Việt thuần tốt. Tuy nhiên mặc định hệ thống chọn PhoWhisper vì bài toán họp có thể có:

- Tên riêng.
- Thuật ngữ chuyên môn.
- Tiếng Anh chen trong tiếng Việt.
- Ngữ cảnh dài hơn.

Do đó quantization trong dự án không chỉ là giảm model, mà là bước làm cho lựa chọn PhoWhisper trở nên triển khai được.

## 10. Rủi ro và đánh đổi

Quantization có các đánh đổi:

- Có thể giảm độ chính xác so với FP32/FP16 gốc.
- Mỗi phần cứng có compute type tối ưu khác nhau.
- Nếu chọn sai compute type, có thể chậm hơn hoặc lỗi runtime.
- Cần benchmark trên dữ liệu gần với cuộc họp thật.

Trong `answer.md`, dự án cũng ghi rõ chưa nên khẳng định quantization không làm giảm độ chính xác nếu chưa có baseline FP16 chạy được trên cùng GPU. Cách trình bày đúng là: bản CT2 `int8_float16` là phương án khả thi, đã đo được WER/RTF trong điều kiện phần cứng mục tiêu.

## 11. Điểm cần nói khi bảo vệ

Quantization trong KollaMeeting là giải pháp triển khai ASR, không phải tính năng UI. Nó giúp PhoWhisper Medium chạy được trong pipeline backend-ASR-Redis với tài nguyên vừa phải. Script convert tạo model CT2 quantized, ASR service load model bằng faster-whisper, worker dùng model này để xử lý từng WAV chunk lấy từ Redis queue.
