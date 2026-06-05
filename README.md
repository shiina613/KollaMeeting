# KollaMeeting

Hệ thống thư ký ảo cho phòng họp trực tuyến: quản lý lịch họp, nhúng Jitsi/JaaS, điều phối chế độ họp, ghi âm, phiên âm tiếng Việt, tạo biên bản DOCX/PDF và ký số PDF.

Tài liệu chuẩn để đối chiếu repo là DOCX 3.8 đã nộp. File [docs/DOCX_ALIGNMENT.md](docs/DOCX_ALIGNMENT.md) map các claim chính trong Word sang code path, endpoint, schema và test.

## Tính năng chính

- Quản lý phòng ban, phòng họp, người dùng theo vai trò `ADMIN`, `SECRETARY`, `USER`.
- Thư ký tạo/sửa/xóa cuộc họp chưa diễn ra; host/chủ trì là user active bất kỳ; secretary của meeting phải có role `SECRETARY`.
- Thành viên meeting có `MeetingRole`: `HOST`, `SECRETARY`, `REVIEWER`, `COMMITTEE_MEMBER`, `GUEST`, `MEMBER`.
- Nhúng Jitsi Meet hoặc JaaS bằng Jitsi External API. Backend không xử lý SDP/ICE/media WebRTC.
- STOMP WebSocket cho control events, presence, raise hand, meeting mode và tin nhắn cuộc họp.
- `/ws/audio` nhận PCM 16 kHz mono trong `MEETING_MODE`, lưu WAV chunk, đẩy Redis queue cho ASR.
- ASR service FastAPI dùng PhoWhisper-medium CT2 `int8_float16` mặc định; Gipformer là backend thay thế.
- Kết thúc meeting tạo transcript, biên bản DOCX/PDF, PDF có thể ký số bằng keystore PKCS#12/JKS.
- Docker Compose deploy toàn bộ KollaMeeting stack và công bố demo bằng Cloudflare Quick Tunnel.

## Kiến trúc demo

```text
Internet
  -> Cloudflare Edge
  -> cloudflared container
  -> Nginx :8888
       /      -> frontend :3000
       /api   -> backend  :8080
       /ws    -> backend  :8080

backend -> MySQL :3306
backend -> Redis :6379
backend -> asr-service :8000

Video media -> meet.jit.si hoặc JaaS bên ngoài
```

Không self-host Jitsi trong Docker stack demo. Jitsi self-host cần UDP media (JVB port 10000), trong khi Cloudflare Tunnel phù hợp HTTP/TCP. Vì vậy Docker stack chỉ chạy thành phần thuộc KollaMeeting; Jitsi/JaaS bên ngoài xử lý media.

## Docker Compose services

- `frontend`: React 18 + Vite.
- `backend`: Spring Boot 3.2, REST `/api/v1`, STOMP `/ws`, binary `/ws/audio`.
- `mysql`: MySQL 8.0, Flyway migrations.
- `redis`: queue/cache cho ASR và state realtime ngắn hạn.
- `asr-service`: FastAPI + PhoWhisper/Gipformer.
- `nginx`: reverse proxy cho frontend/backend/ws.
- `cloudflared`: Quick Tunnel demo hoặc Named Tunnel khi có domain.

## Chạy demo bằng một lệnh

Chuẩn bị `.env`:

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
.\scripts\start.ps1
```

WSL2/Linux:

```bash
./scripts/start.sh
```

Script có nhiệm vụ:

1. Kiểm tra Docker đang chạy.
2. Build/start backend, mysql, redis, asr-service, nginx và cloudflared.
3. Lấy Quick Tunnel URL từ log `cloudflared`.
4. Cập nhật URL browser cần dùng trong `.env`.
5. Rebuild/start frontend để Vite bake đúng URL mới.
6. In URL cuối cùng dạng `https://xxx.trycloudflare.com`.

Quick Tunnel là chế độ demo/nghiệm thu ngắn hạn 2-3 ngày, mỗi ngày chạy một lần. URL sẽ đổi sau mỗi lần chạy script. Vận hành lâu dài cần Cloudflare Named Tunnel/domain cố định qua `CLOUDFLARE_TUNNEL_TOKEN`.

## Truy cập local

```text
http://localhost:8888
https://localhost:8443
http://localhost:3000
http://localhost:8080/api/v1
http://localhost:8000/health
```

## ASR model

Model PhoWhisper CT2 local đặt tại:

```text
asr-service/models/phowhisper-medium-ct2-int8_float16/
```

Thư mục `asr-service/models/` bị ignore để không commit model weights. Docker Compose mount thư mục này vào `/app/models:ro`. Nếu chưa có model local, đặt `ASR_BACKEND=gipformer` để dùng Gipformer tải từ Hugging Face hoặc chuẩn bị model PhoWhisper trước khi demo.

## API chính

Tất cả endpoint có prefix `/api/v1`.

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/auth/login` | Đăng nhập, nhận JWT |
| POST | `/auth/logout` | Đăng xuất |
| POST | `/auth/refresh` | Làm mới access token |
| GET | `/users/me` | Hồ sơ người dùng hiện tại |
| PUT | `/users/me` | Cập nhật hồ sơ cá nhân |
| POST | `/users/me/change-password` | Đổi mật khẩu |
| GET/POST | `/users` | Quản lý người dùng |
| GET/POST | `/departments` | Quản lý phòng ban |
| GET/POST | `/rooms` | Quản lý phòng họp |
| GET | `/meetings` | Danh sách cuộc họp |
| POST | `/meetings` | Thư ký tạo cuộc họp |
| GET | `/meetings/{id}` | Chi tiết cuộc họp |
| PUT | `/meetings/{id}` | Thư ký sửa meeting `SCHEDULED` |
| DELETE | `/meetings/{id}` | Thư ký xóa meeting `SCHEDULED` |
| GET/POST | `/meetings/{id}/members` | Danh sách/thêm thành viên và `MeetingRole` |
| POST | `/meetings/{id}/activate` | Bắt đầu cuộc họp |
| POST | `/meetings/{id}/end` | Kết thúc cuộc họp, tạo transcript/minutes/audio tổng hợp |
| POST | `/meetings/{id}/join` | Tham gia phòng họp |
| POST | `/meetings/{id}/leave` | Rời phòng họp |
| GET/POST | `/meetings/{id}/messages` | Tin nhắn cuộc họp, lưu DB và broadcast STOMP |
| GET | `/meetings/{id}/transcription` | Kết quả phiên âm |
| GET/PUT | `/meetings/{id}/minutes` | Xem/sửa biên bản |
| POST | `/meetings/{id}/minutes/confirm` | Host xác nhận và ký PDF |
| GET | `/meetings/{id}/minutes/download?format=pdf\|docx` | Tải biên bản PDF/DOCX |
| POST | `/meetings/{id}/recordings/start` | Bắt đầu ghi âm |
| POST | `/recordings/{id}/stop` | Dừng ghi âm |
| GET | `/recordings/{id}/download` | Tải audio |
| GET | `/search` | Tìm kiếm cuộc họp/phiên âm |

## WebSocket

| Endpoint | Giao thức | Mô tả |
|---|---|---|
| `/ws` | STOMP/SockJS | Meeting events, notifications, raise hand, messages |
| `/ws/audio` | Binary WebSocket | PCM audio đến backend để tạo ASR jobs |

Subscribe topics:

- `/topic/meeting/{meetingId}`: event realtime của meeting.
- `/user/queue/notifications`: thông báo cá nhân.
- `/user/queue/errors`: lỗi cá nhân.

Event quan trọng:

- `MEETING_STARTED`
- `MODE_CHANGED`
- `SPEAKING_PERMISSION_GRANTED`
- `TRANSCRIPTION_COMPLETED`
- `MEETING_MESSAGE_CREATED`
- `MINUTES_READY`
- `MEETING_ENDED`

## Schema alignment

Fresh Flyway schema dùng tên cột vật lý khớp DOCX cho các bảng chính:

- `user`: `Department_id`, `EmployeeCode`, `Name`, `Password`, `Dob`, `PhoneNumber`, `Degree`, `Identification`, `Address`, `Email`, `BankName`, `BankNumber`, `Img`, `Role`.
- `department`: `DepartmentCode`, `Name`.
- `room`: `RoomCode`, `RoomName`.
- `meeting`: `MeetingCode`, `DepartmentId`, `Room_id`, `Name`, `StartTime`, `Endtime`, `Status`.
- `member`: `User_id`, `Meeting_id`, `MeetingRole`.
- `document`: `Meeting_id`, `User_id`, `Name`, `Content`.
- `meeting_message`: `Member_id`, `Content`, `CreateTime`.

Các cột runtime như `mode`, `transcription_priority`, `host_user_id`, `secretary_user_id`, `draft_docx_path`, `confirmed_pdf_path` là phần triển khai bổ sung để hệ thống chạy thật, không thay thế schema Word.

## Kiểm thử

```powershell
git diff --check

cd backend
.\mvnw.cmd test

cd ..\frontend
npm run test
npm run build

cd ..
docker compose config
```

Kiểm tra migration MySQL:

```powershell
docker exec -i kolla-mysql mysql -uroot -p%MYSQL_ROOT_PASSWORD% kolla_meeting < backend/src/main/resources/db/migration/check_migration_integrity.sql
```

## Cấu trúc thư mục

```text
KollaMeeting/
  backend/        Spring Boot backend
  frontend/       React/Vite frontend
  asr-service/    FastAPI ASR service
  nginx/          reverse proxy
  scripts/        one-command demo startup scripts
  secrets/        local keystore/secrets, ignored by git
  docs/           repo alignment documents
  docker-compose.yml
  .env.example
  plan.md
```

## Repo hygiene

Không commit:

- DOCX gốc đã nộp.
- Model weights trong `asr-service/models/`.
- WAV demo/test trong `asr-service/data/`.
- `frontend/playwright-report/`, `frontend/test-results/`.
- `secrets/*`, `.env`, runtime storage và benchmark artifacts.
