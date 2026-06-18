# Redis Queue trong KollaMeeting

## 1. Bản chất

Redis Queue là cơ chế đưa công việc vào hàng đợi để xử lý bất đồng bộ. Trong KollaMeeting, Redis không chỉ là cache, mà còn là lớp điều phối runtime cho các tác vụ cần phản hồi nhanh hoặc cần xử lý nền.

Điểm quan trọng: phiên âm ASR là tác vụ nặng. Backend không nên chờ PhoWhisper xử lý xong ngay trong luồng WebSocket audio. Thay vào đó, backend lưu audio chunk thành file WAV, tạo `TranscriptionJob`, đưa job vào Redis, rồi ASR service lấy job ra xử lý sau.

## 2. Redis được khai báo ở đâu?

Redis chạy bằng Docker Compose:

- `docker-compose.yml`: service `redis`, image `redis:7-alpine`.
- Redis bật append-only file bằng `redis-server --appendonly yes`.
- Backend nhận cấu hình qua `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`.
- ASR service nhận cấu hình qua `REDIS_URL=redis://redis:6379`.

Backend cấu hình Redis ở:

- `backend/src/main/java/com/example/kolla/config/RedisConfig.java`

File này tạo:

- `RedisConnectionFactory`.
- `RedisTemplate<String, String>`.
- `StringRedisTemplate`.

Các template dùng serializer dạng string để dữ liệu trong Redis dễ đọc bằng Redis CLI và tương thích với Python ASR worker.

## 3. Hàng đợi phiên âm ASR

Các file chính:

- `backend/src/main/java/com/example/kolla/websocket/AudioStreamHandler.java`
- `backend/src/main/java/com/example/kolla/services/AsrServiceClient.java`
- `backend/src/main/java/com/example/kolla/services/impl/TranscriptionQueueServiceImpl.java`
- `asr-service/job_queue/redis_queue.py`
- `asr-service/job_queue/worker.py`
- `backend/src/main/java/com/example/kolla/controllers/TranscriptionController.java`
- `backend/src/main/java/com/example/kolla/services/impl/TranscriptionServiceImpl.java`

Redis dùng 2 loại key chính:

```text
transcription:queue
transcription:job:{jobId}
```

Trong đó:

- `transcription:queue` là Redis Sorted Set.
- `transcription:job:{jobId}` là Redis Hash chứa metadata của job.

## 4. Vì sao dùng Sorted Set?

Sorted Set cho phép mỗi job có một `score`. Khi worker lấy job, nó dùng logic lấy job có score cao nhất trước.

Backend push job trong `TranscriptionQueueServiceImpl.pushJob()`:

```text
Redis Hash: transcription:job:{jobId}
Redis ZSET: transcription:queue -> member = jobId, score = priority score
```

ASR worker pop job trong `RedisQueue.pop()`:

```text
ZPOPMAX transcription:queue
HGETALL transcription:job:{jobId}
```

Bản chất của logic này là: Sorted Set giữ thứ tự ưu tiên, Hash giữ thông tin chi tiết để worker biết cần xử lý file nào, cuộc họp nào, người nói nào và callback về đâu.

## 5. Luồng hoạt động phiên âm

Luồng thực tế trong codebase:

1. Frontend gửi audio PCM 16 kHz mono qua WebSocket `/ws/audio`.
2. `AudioStreamHandler` nhận binary frame.
3. Backend tính RMS để phân biệt voice và silence.
4. Chỉ frame có tiếng nói được đưa vào buffer.
5. Khi gặp silence đủ lâu hoặc nhận text message `FINALIZE`, backend flush buffer.
6. Buffer PCM được ghi thành file WAV trong storage.
7. Backend tạo `TranscriptionJob` trạng thái `PENDING`.
8. `AsrServiceClient.submitJob()` đưa job vào queue.
9. `TranscriptionQueueServiceImpl.pushJob()` lưu Hash và thêm jobId vào Sorted Set.
10. ASR service worker poll Redis mỗi khoảng ngắn, mặc định 100 ms.
11. Worker `ZPOPMAX` job ưu tiên cao nhất.
12. Worker đọc `audio_path`, gọi `recognizer.transcribe(audio_path)`.
13. Worker POST kết quả về `/api/v1/transcription/callback`.
14. Backend lưu `TranscriptionSegment`, cập nhật job thành `COMPLETED`, rồi broadcast event nếu cần.

Sơ đồ ngắn:

```text
Frontend audio
  -> AudioStreamHandler
  -> WAV file + TranscriptionJob
  -> Redis ZSET/Hash
  -> ASR worker
  -> PhoWhisper/Gipformer
  -> callback backend
  -> TranscriptionSegment
  -> WebSocket event / biên bản
```

## 6. Logic ưu tiên

Job có enum `TranscriptionPriority`:

- `HIGH_PRIORITY`
- `NORMAL_PRIORITY`

Trong `TranscriptionQueueServiceImpl.computeScore()`, backend tính score để job ưu tiên cao luôn đứng trên job thường:

```text
HIGH_PRIORITY   -> score >= 1_000_000_000
NORMAL_PRIORITY -> score <  1_000_000_000
```

Worker dùng `ZPOPMAX`, nên score cao hơn được lấy trước. Mục tiêu là khi cuộc họp ở chế độ cần transcript nhanh, chunk của cuộc họp đó được xử lý trước các job thường.

Lưu ý: Python worker cũng có hàm `_priority_score()` trong `asr-service/job_queue/redis_queue.py`. Trong luồng chính hiện tại, backend là bên tạo job từ audio WebSocket, nhưng ASR service vẫn có API `/jobs` để submit job trực tiếp, nên Python cũng có logic push queue riêng.

## 7. Trạng thái job

`TranscriptionJobStatus` phản ánh vòng đời job:

```text
PENDING -> QUEUED -> PROCESSING -> COMPLETED
                         |
                         -> FAILED
```

Ý nghĩa:

- `PENDING`: job đã được tạo trong backend nhưng chưa đưa vào queue.
- `QUEUED`: job đã nằm trong Redis queue.
- `PROCESSING`: worker đã lấy job ra và đang chạy ASR.
- `COMPLETED`: ASR xử lý xong và callback thành công.
- `FAILED`: lỗi file audio, lỗi model hoặc callback thất bại.

ASR worker cập nhật trạng thái trong Redis Hash. Backend lưu trạng thái nghiệp vụ trong repository/runtime store để UI và API tra cứu.

## 8. Hàng đợi xin phát biểu

Ngoài ASR queue, Redis còn dùng cho hàng đợi xin phát biểu:

- `backend/src/main/java/com/example/kolla/services/impl/RaiseHandQueueServiceImpl.java`
- `backend/src/main/java/com/example/kolla/services/impl/RaiseHandServiceImpl.java`

Cơ chế cũng dùng Redis ZSET + HASH:

- ZSET lưu thứ tự người xin phát biểu theo thời điểm.
- HASH lưu metadata như tên người dùng, thời gian xin phát biểu.
- Có TTL để dữ liệu runtime tự hết hạn.

Luồng:

1. Người dùng bấm xin phát biểu.
2. `RaiseHandServiceImpl` kiểm tra quyền và tránh trùng request.
3. `RaiseHandQueueServiceImpl.enqueue()` đưa user vào Redis.
4. Host xem danh sách đang chờ.
5. Khi host cấp quyền hoặc người dùng hủy, entry bị remove khỏi Redis.

## 9. Redis cho tín hiệu runtime khác

Redis còn được dùng trong một số logic runtime:

- WebSocket CONNECT kiểm tra JWT blacklist trong `WebSocketConfig`.
- `MeetingLifecycleServiceImpl` dùng Redis TTL key cho timeout khi host/secretary vắng mặt.
- `MeetingModeServiceImpl` dùng Redis key để signal finalize audio chunk khi chuyển mode.

Điểm chung: đây là dữ liệu vận hành, tồn tại ngắn, cần nhanh và không nhất thiết là dữ liệu nghiệp vụ lâu dài như MySQL.

## 10. Vì sao thiết kế này hợp với KollaMeeting?

Nếu không dùng queue, mỗi audio chunk sẽ phải gọi ASR trực tiếp. Khi model chậm hoặc nhiều người nói cùng lúc, WebSocket/backend dễ bị nghẽn.

Dùng Redis Queue giúp:

- Tách luồng thu âm khỏi luồng nhận dạng giọng nói.
- Backend phản hồi nhanh hơn.
- ASR service có thể xử lý tuần tự hoặc mở rộng worker.
- Có thể ưu tiên cuộc họp quan trọng.
- Nếu ASR service tạm lỗi, job vẫn có trạng thái để requeue hoặc xử lý lại.

## 11. Điểm cần nói khi bảo vệ

Redis trong KollaMeeting không chỉ là cache. Nó là lớp điều phối thời gian thực cho:

- Queue phiên âm ASR.
- Queue xin phát biểu.
- JWT blacklist.
- Timeout và signal runtime.

Với ASR, bản chất là mô hình producer-consumer: backend là producer tạo job từ audio chunk, Redis giữ job theo độ ưu tiên, ASR worker là consumer xử lý job và callback kết quả về backend.
