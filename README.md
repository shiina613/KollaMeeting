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

Trước khi chạy lần đầu, copy `.env.example` sang `.env`. GitHub có thể chặn push `.env`, nên repo chỉ track `.env.example`; hai file nên giống nhau ở trạng thái nộp.

Windows PowerShell:

```powershell
Copy-Item .env.example .env -Force
.\scripts\start.ps1
```

WSL2/Linux:

```bash
cp .env.example .env
./scripts/start.sh
```

Script có nhiệm vụ:

1. Kiểm tra Docker đang chạy.
2. Đọc/cập nhật `.env`, sinh secret demo hoặc `keys/signing.p12` nếu thiếu.
3. Build/start backend, mysql, redis, asr-service, nginx và cloudflared.
4. Lấy Quick Tunnel URL từ log `cloudflared`.
5. Cập nhật URL browser cần dùng trong `.env`.
6. Rebuild/start frontend để Vite bake đúng URL mới.
7. In URL cuối cùng dạng `https://xxx.trycloudflare.com`.

Quick Tunnel là chế độ demo/nghiệm thu ngắn hạn 2-3 ngày, mỗi ngày chạy một lần. URL sẽ đổi sau mỗi lần chạy script. Vận hành lâu dài cần Cloudflare Named Tunnel/domain cố định qua `CLOUDFLARE_TUNNEL_TOKEN`.

## Truy cập local

```text
http://localhost:8888
https://localhost:8443
http://localhost:3000
http://localhost:8080/api/v1
http://localhost:8000/health
```

## Dùng thử nhanh

### 1. Chuẩn bị

Cần Docker Desktop đang chạy. Copy `.env.example` sang `.env` trước khi chạy. Nếu máy không có GPU NVIDIA hoặc chưa muốn dùng model PhoWhisper local, đổi ASR sang CPU/Gipformer để demo nhẹ hơn:

```powershell
Copy-Item .env.example .env -Force
notepad .env
```

Trong `.env`, dùng các giá trị này nếu muốn chạy CPU:

```env
ASR_BACKEND=gipformer
DEVICE=cpu
```

Nếu đã có GPU và model local tại `asr-service/models/phowhisper-medium-ct2-int8_float16/`, có thể giữ mặc định `ASR_BACKEND=phowhisper` và `DEVICE=cuda`.

### 2. Khởi động hệ thống

Chạy từ thư mục gốc repo:

```powershell
.\scripts\start.ps1
```

Script sẽ build/start MySQL, Redis, backend, ASR service, nginx, frontend và cloudflared. Khi xong, terminal in ra URL dạng:

```text
>>> Kolla dang chay tai: https://<random>.trycloudflare.com
```

Mở URL đó để dùng thử từ trình duyệt. Nếu chỉ thử trên máy local, mở:

```text
http://localhost:8888
```

### 3. Đăng nhập demo

Tài khoản seed mặc định:

```text
EmployeeCode: admin
Password: admin
Role: ADMIN
```

Sau khi đăng nhập, vào trang quản trị để kiểm tra dữ liệu seed:

- Phòng ban: `BGD` / `Ban Giam doc`
- Phòng họp: `ROOM-MAIN` / `Phong hop chinh`

### 4. Tạo dữ liệu demo

Trong trang quản trị, tạo tối thiểu 2 người dùng:

- Một user role `SECRETARY`, ví dụ `sec01`.
- Một user role `USER`, ví dụ `host01` hoặc `member01`.

Gán cả hai vào phòng ban `BGD`. Mật khẩu có thể đặt tạm như `12345678` cho demo nội bộ.

### 5. Tạo và bắt đầu cuộc họp

Đăng xuất admin, đăng nhập bằng tài khoản `SECRETARY` vừa tạo, rồi:

1. Vào danh sách cuộc họp.
2. Tạo cuộc họp mới.
3. Chọn phòng ban `BGD`, phòng họp `ROOM-MAIN`, thời gian bắt đầu/kết thúc.
4. Lưu cuộc họp.
5. Vào chi tiết cuộc họp, thêm thành viên:
   - `sec01` với `MeetingRole.SECRETARY`.
   - `host01` với `MeetingRole.HOST` hoặc user khác với `MeetingRole.MEMBER`.
6. Bấm bắt đầu cuộc họp trong vòng 30 phút trước `StartTime`.

Sau khi meeting `ACTIVE`, vào phòng họp để kiểm tra Jitsi, chat, raise hand, mode/priority controls và điểm danh runtime.

### 6. Thử tài liệu, ghi âm, biên bản

Trong cuộc họp:

- User có `MeetingRole.SECRETARY` upload tài liệu ở phần tài liệu cuộc họp.
- User thường không thấy hoặc không được phép upload tài liệu.
- `HOST` hoặc `SECRETARY` có thể đổi chế độ họp và bật/tắt ưu tiên cao.
- Kết thúc cuộc họp để backend tạo transcript/minutes/runtime files.
- Vào phần biên bản để xem/sửa/xác nhận, sau đó tải DOCX/PDF nếu dữ liệu đã sẵn sàng.

File runtime nằm trong Docker volume `app-storage`, mount vào backend tại `/app/storage`. Xem nhanh bằng:

```powershell
docker exec -it kolla-backend sh -lc "find /app/storage -maxdepth 4 -type f | head -50"
```

### 7. Kiểm tra trạng thái và dừng hệ thống

Kiểm tra container:

```powershell
docker compose ps
```

Xem log backend hoặc ASR:

```powershell
docker logs kolla-backend --tail 100
docker logs kolla-asr-service --tail 100
```

Dừng hệ thống, giữ dữ liệu:

```powershell
docker compose down
```

Reset sạch database demo về 7 bảng Word và seed mặc định:

```powershell
docker compose down
docker volume rm kollameeting_mysql-data
.\scripts\start.ps1
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

Database sạch sau reset chỉ có đúng 7 bảng Word ở trên. Dữ liệu vận hành như biên bản, ghi âm, transcript, trạng thái ASR, notification runtime và audit log không tạo bảng riêng trong database; chúng được lưu trong `storage/meetings/<meeting_id>/...`, Redis hoặc in-memory state.

## File runtime local

Sau khi chạy hệ thống, file sinh ra theo từng cuộc họp nằm dưới:

- Biên bản: `storage/meetings/<meeting_id>/minutes/`
- Ghi âm: `storage/meetings/<meeting_id>/recordings/`
- Transcript và audio chunk ASR: `storage/meetings/<meeting_id>/transcript/`
- Tài liệu upload: `storage/meetings/<meeting_id>/documents/`
- Lịch sử điểm danh: `storage/meetings/<meeting_id>/attendance/`
- Audit file runtime: `storage/meetings/<meeting_id>/audit/`

Keystore ký PDF nằm trong `keys/` là cấu hình bảo mật local, không phải dữ liệu nghiệp vụ và không thuộc database.

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

Nếu máy đang có database test cũ từ các migration trước, reset volume MySQL rồi chạy lại startup script để Flyway tạo schema sạch đúng 7 bảng Word:

```powershell
docker compose down
docker volume rm kollameeting_mysql-data
.\scripts\start.ps1
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
  storage/        runtime files under storage/meetings/<meeting_id>/, ignored by git
  keys/           local PDF signing keystore/private keys for thesis snapshot
  docs/           repo alignment documents
  docker-compose.yml
  .env.example
  plan.md
```

## Repo hygiene

Repo snapshot nộp đồ án có track DOCX gốc, key ký PDF demo, WAV demo và model PhoWhisper quantized local. File `.env` không track vì GitHub có thể chặn; dùng `.env.example` làm bản nộp và copy sang `.env` khi chạy.

Vẫn không commit:

- `frontend/node_modules/`, `backend/target/`, `frontend/dist/`.
- `frontend/playwright-report/`, `frontend/test-results/`.
- Cache test như `.pytest_cache/`, `.hypothesis/`, `__pycache__/`.
- Runtime storage sinh khi chạy app.
