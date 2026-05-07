# Change Task: Adaptive VAD-based Audio Chunking

## Mục tiêu

Cải thiện pipeline thu âm trong `AudioStreamHandler.java` để:
1. **Lọc silence** khỏi buffer (cả 2 loại meeting) — tăng chất lượng STT.
2. **Normal meeting**: VAD lọc + auto-flush khi silence >= **2.5s**, hard cap 10 phút.
3. **Priority meeting**: VAD lọc + auto-flush theo ngưỡng silence thích ứng (1.5s / 0.8s / 0.3s) dựa trên thời lượng đã nói.

---

## Thiết kế logic chi tiết

### VAD — Phát hiện silence

- **Phương pháp**: Energy-based (RMS) trên từng PCM frame.
- **Công thức RMS**: `sqrt(mean(sample^2))` trên mảng Int16 LE.
- **Ngưỡng năng lượng**: `RMS < 300` → silence (PCM Int16 scale 0–32767).
  - Giá trị 300 tương đương ~-40dB, lọc được tiếng ồn phòng họp thông thường.
  - Cần log thực tế để fine-tune nếu cần.

### Normal meeting (NORMAL priority)

```
Mỗi PCM frame đến:
  ├─ isVoice (RMS >= 300) → add vào buffer, tăng voicedBytes, reset silenceDuration=0
  └─ isSilence (RMS < 300) → KHÔNG add vào buffer, tăng silenceDuration

Auto-flush trigger:
  IF silenceDuration >= 2500ms
     AND voicedBytes >= MIN_CHUNK_BYTES (1.5s)
  → FLUSH

Hard cap: 600s (10 phút voiced audio)
```

> Ngưỡng 2.5s đủ cao để không cắt giữa câu dù speaker dừng suy nghĩ lâu,
> đồng thời vẫn chunk sau mỗi đoạn phát biểu hoàn chỉnh.

### Priority meeting (HIGH/URGENT priority)

```
Mỗi PCM frame đến:
  ├─ isVoice → add vào buffer, tăng voicedBytes, reset silenceDuration=0
  └─ isSilence → KHÔNG add vào buffer, tăng silenceDuration

Auto-flush trigger:
  voicedSeconds = voicedBytes / (16000 * 2)
  silenceThreshold = getThreshold(voicedSeconds)

  IF silenceDuration >= silenceThreshold
     AND voicedBytes >= MIN_CHUNK_BYTES (1.5s = 48,000 bytes)
  → FLUSH

Hard cap: 180s (3 phút)
```

### Silence threshold — toàn bộ

**Normal meeting** (cố định):
```
silenceThreshold = 2500ms  (cố định, không phụ thuộc voiced duration)
```

**Priority meeting** (thích ứng theo voiced duration):
```
getThreshold(voicedSeconds):
  if voicedSeconds <= 15  → 1500ms
  if voicedSeconds <= 30  →  800ms
  if voicedSeconds >  30  →  300ms  (flush ngay khi VAD detect silence)
```


---

## Các hằng số cần thêm vào AudioStreamHandler.java

```java
// ── VAD ──────────────────────────────────────────────────────────────────
/** Ngưỡng RMS để phân biệt voice/silence. */
private static final double VAD_ENERGY_THRESHOLD = 300.0;

// ── Hard cap (thay MAX_BUFFER_BYTES) ─────────────────────────────────────
/** Hard cap Normal meeting: 10 phút voiced audio. */
private static final int NORMAL_MAX_VOICED_BYTES  = SAMPLE_RATE * FRAME_SIZE * 600;

/** Hard cap Priority meeting: 3 phút voiced audio. */
private static final int PRIORITY_MAX_VOICED_BYTES = SAMPLE_RATE * FRAME_SIZE * 180;

// ── Priority meeting adaptive threshold ──────────────────────────────────
/** Voiced bytes tương đương 15s. */
private static final int PRIORITY_PHASE1_BYTES = SAMPLE_RATE * FRAME_SIZE * 15;

/** Voiced bytes tương đương 30s. */
private static final int PRIORITY_PHASE2_BYTES = SAMPLE_RATE * FRAME_SIZE * 30;

/** Silence threshold Normal meeting: 2500ms. */
private static final long NORMAL_SILENCE_THRESHOLD_MS = 2500;

/** Silence threshold Priority phase 1 (voiced <= 15s): 1500ms. */
private static final long PRIORITY_SILENCE_PHASE1_MS = 1500;

/** Silence threshold Priority phase 2 (voiced 15–30s): 800ms. */
private static final long PRIORITY_SILENCE_PHASE2_MS = 800;

/** Silence threshold Priority phase 3 (voiced > 30s): 300ms (flush ngay). */
private static final long PRIORITY_SILENCE_PHASE3_MS = 300;

/** Minimum voiced audio trước khi cho phép flush: 1.5s. */
private static final int MIN_CHUNK_VOICED_BYTES = SAMPLE_RATE * FRAME_SIZE * 3 / 2;
```

---

## Trạng thái per-session cần thêm

Tạo thêm 2 map trong class:

```java
/** Số bytes voiced audio đã tích lũy (sau khi lọc silence). */
private final Map<String, AtomicInteger> voicedBytesCounters = new ConcurrentHashMap<>();

/** Timestamp (ms) khi silence bắt đầu; -1 nếu đang có voice. */
private final Map<String, Long> silenceStartTimes = new ConcurrentHashMap<>();
```

Khởi tạo trong `afterConnectionEstablished`:
```java
voicedBytesCounters.put(sessionId, new AtomicInteger(0));
silenceStartTimes.put(sessionId, -1L);
```

Xóa trong `afterConnectionClosed`:
```java
voicedBytesCounters.remove(sessionId);
silenceStartTimes.remove(sessionId);
```

---

## Thay đổi handleBinaryMessage

Logic mới thay thế phần tích lũy buffer hiện tại:

```java
protected void handleBinaryMessage(session, message) {
    // ... kiểm tra MEETING_MODE giữ nguyên ...

    byte[] payload = message.getPayload().array();
    boolean isVoice = computeRms(payload) >= VAD_ENERGY_THRESHOLD;

    Meeting meeting = meetingRepository.findById(meta.meetingId).orElse(null);
    boolean isPriority = meeting != null
        && meeting.getTranscriptionPriority() != TranscriptionPriority.NORMAL;

    if (isVoice) {
        // Ghi vào buffer
        buffer.put(payload);
        voicedBytesCounters.get(sessionId).addAndGet(payload.length);
        silenceStartTimes.put(sessionId, -1L); // reset silence timer
    } else {
        // Silence: không ghi vào buffer, track thời gian silence
        long now = System.currentTimeMillis();
        long silenceStart = silenceStartTimes.get(sessionId);
        if (silenceStart == -1L) {
            silenceStartTimes.put(sessionId, now); // bắt đầu silence
        } else {
            long silenceDurationMs = now - silenceStart;
            int voicedBytes = voicedBytesCounters.get(sessionId).get();
            // Chọn threshold theo loại meeting
            long threshold = isPriority
                ? getSilenceThreshold(voicedBytes)   // adaptive 3 giai đoạn
                : NORMAL_SILENCE_THRESHOLD_MS;        // cố định 2500ms
            if (silenceDurationMs >= threshold && voicedBytes >= MIN_CHUNK_VOICED_BYTES) {
                // Auto-flush
                flushBuffer(sessionId, session);
                resetSessionBuffer(sessionId);
            }
        }
    }

    // Hard cap check
    int maxBytes = isPriority ? PRIORITY_MAX_VOICED_BYTES : NORMAL_MAX_VOICED_BYTES;
    if (buffer.position() >= maxBytes) {
        flushBuffer(sessionId, session);
        resetSessionBuffer(sessionId);
    }
}
```

---

## Helper methods cần thêm

```java
/** Tính RMS của PCM frame (Int16 LE). */
private static double computeRms(byte[] pcmFrame) { ... }

/** Trả về silence threshold (ms) theo lượng voiced audio đã tích lũy. */
private long getSilenceThreshold(int voicedBytes) { ... }

/** Reset buffer + voiced counter + silence timer cho session. */
private void resetSessionBuffer(String sessionId) { ... }
```

---

## Files cần sửa

| File | Loại thay đổi |
|------|--------------|
| `backend/src/main/java/com/example/kolla/websocket/AudioStreamHandler.java` | Sửa chính: thêm VAD, adaptive flush, hard cap mới |

> Không cần sửa Gipformer, Frontend, hoặc các service khác.
> `faster-whisper` đã có `vad_filter=True` server-side — đây là tầng lọc bổ sung độc lập.

---

## Skill — Thứ tự implement

1. **Thêm hằng số** VAD và hard cap mới vào đầu class.
2. **Thêm 2 map** `voicedBytesCounters` và `silenceStartTimes`.
3. **Cập nhật lifecycle** (`afterConnectionEstablished`, `afterConnectionClosed`) để init/cleanup 2 map mới.
4. **Implement `computeRms()`** — đọc Int16 LE từ byte array, tính RMS.
5. **Implement `getSilenceThreshold()`** — 3 giai đoạn.
6. **Implement `resetSessionBuffer()`** — reset buffer + 2 counter.
7. **Refactor `handleBinaryMessage()`** — thay logic buffer bằng VAD pipeline.
8. **Cập nhật `handleTextMessage()` (FINALIZE)** — gọi `resetSessionBuffer()` sau flush.
9. **Cập nhật `afterConnectionClosed()`** — cleanup 2 map mới.

---

## Test plan

### Unit test (Java)

| Test | Mô tả | Expected |
|------|-------|---------|
| `testComputeRms_silence` | Frame toàn số 0 | RMS = 0.0 |
| `testComputeRms_voice` | Frame sine wave biên độ lớn | RMS >= 300 |
| `testGetSilenceThreshold_phase1` | voicedBytes = 15s | 1500ms |
| `testGetSilenceThreshold_phase2` | voicedBytes = 20s | 800ms |
| `testGetSilenceThreshold_phase3` | voicedBytes = 45s | 300ms |

### Manual test (dùng test_speech_scenario.md)

**Test A — Normal meeting, auto-flush sau silence 2.5s**:
1. Set priority = NORMAL.
2. Nói 1 câu (~5s), dừng **3s** (>2.5s).
3. **Expected**: Auto-flush sau ~3s silence → 1 WAV file ~5s voiced.

**Test A2 — Normal meeting, KHÔNG flush khi dừng 2s**:
1. Nói 1 câu (~5s), dừng **2s** (<2.5s), nói tiếp.
2. **Expected**: Không flush, tiếp tục tích lũy cùng 1 buffer.

**Test B — Priority meeting, phase 1 (voiced <= 15s)**:
1. Set priority = HIGH.
2. Nói 10s, dừng 2s (>1.5s).
3. **Expected**: Auto-flush sau 2s im lặng → 1 WAV file ~10s voiced audio.

**Test C — Priority meeting, phase 2 (voiced 15–30s)**:
1. Nói liên tục 20s, dừng 1s (>0.8s, <1.5s).
2. **Expected**: Auto-flush sau 1s → 1 WAV file ~20s.

**Test D — Priority meeting, phase 3 (voiced >30s)**:
1. Nói liên tục 35s, dừng 0.5s.
2. **Expected**: Auto-flush ngay sau ~300ms silence → WAV file ~35s.

**Test E — Minimum chunk size**:
1. Priority meeting, nói "à" (~0.5s), dừng 2s.
2. **Expected**: KHÔNG flush (voiced < MIN_CHUNK 1.5s).

**Test F — Hard cap Normal meeting**:
1. Simulate 10 phút voiced audio liên tục (600s).
2. **Expected**: Auto-flush khi đạt 600s, tạo 1 WAV file.
