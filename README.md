# KollaMeeting

Hệ thống quản lý cuộc họp doanh nghiệp tích hợp hội nghị video, phiên âm thời gian thực, ghi âm và tạo biên bản tự động.

---

## Tính năng chính

- **Quản lý cuộc họp** — tạo, lên lịch, kích hoạt, kết thúc với phát hiện xung đột lịch
- **Hội nghị video** — Jitsi Meet tự host, tích hợp trực tiếp vào giao diện
- **Phiên âm tự động** — Gipformer (sherpa-onnx) nhận audio PCM qua WebSocket, hỗ trợ GPU CUDA
- **Ghi âm** — bắt đầu/dừng ghi, lưu file, tải xuống
- **Biên bản cuộc họp** — quy trình Nháp → Xác nhận (Chủ trì) → Công bố (Thư ký), xuất PDF
- **Thông báo thời gian thực** — WebSocket STOMP/SockJS
- **Giơ tay phát biểu** — quản lý người phát biểu theo thời gian thực
- **Điểm danh** — ghi log tham gia/rời phòng kèm IP và thiết bị
- **Tìm kiếm** — full-text search trên cuộc họp và phiên âm
- **Phân quyền** — ADMIN / SECRETARY / USER với kiểm soát truy cập theo vai trò

---

## Kiến trúc hệ thống

```
[Internet]
    │ HTTPS
    ▼
[Cloudflare Edge]
    │ outbound tunnel
    ▼
cloudflared container
    │
    ▼
Nginx :8888 (HTTP plain, reverse proxy)
  ├── /          → Frontend  :3000  (React 18 + Vite)
  ├── /api       → Backend   :8080  (Spring Boot 3.2)
  └── /ws        → Backend   :8080  (WebSocket STOMP)

Nginx :8443 (HTTPS self-signed — LAN access)

Backend
  ├── MySQL  :3306  (dữ liệu chính, Flyway migrations)
  ├── Redis  :6379  (hàng đợi transcription, cache)
  └── Gipformer :8000  (FastAPI + sherpa-onnx ASR)

Video: meet.jit.si (Jitsi public — không self-host)
```

---

## Tech Stack

| Layer | Công nghệ |
|---|---|
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, Zustand, React Router v6 |
| Backend | Spring Boot 3.2, Spring Security, Spring WebSocket (STOMP), JPA/Hibernate |
| Database | MySQL 8.0, Flyway |
| Cache / Queue | Redis 7 |
| Video | Jitsi Meet (meet.jit.si public) |
| ASR | Gipformer — Python FastAPI + sherpa-onnx (CUDA / CPU ONNX) |
| Proxy / SSL | Nginx 1.25, Cloudflare Tunnel (cloudflared) |
| Container | Docker, Docker Compose |

---

## Yêu cầu

- Docker Engine ≥ 24 và Docker Compose v2
- (Tuỳ chọn) NVIDIA GPU + `nvidia-container-toolkit` để chạy Gipformer với CUDA

---

## Khởi động nhanh

### 1. Clone và cấu hình môi trường

```bash
git clone <repo-url> KollaMeeting
cd KollaMeeting
cp .env.example .env
```

Mở `.env` và điền các giá trị bắt buộc:

```dotenv
# MySQL — đổi mật khẩu trước khi deploy
MYSQL_ROOT_PASSWORD=strong-root-password
MYSQL_PASSWORD=strong-app-password

# JWT — tạo bằng: openssl rand -hex 32
JWT_SECRET=your-secret-at-least-32-chars

# Gipformer — cuda hoặc cpu
DEVICE=cuda
```

> Các biến `VITE_API_BASE_URL`, `VITE_WS_URL`, `CORS_ALLOWED_ORIGINS` sẽ được startup script **tự động cập nhật** sau mỗi lần khởi động — không cần điền thủ công.

### 2. Khởi động (1 lệnh duy nhất)

**Windows (PowerShell):**
```powershell
.\scripts\start.ps1
```

**WSL2 / Linux (bash):**
```bash
./scripts/start.sh
```

Script sẽ tự động:
1. Kiểm tra Docker Desktop đang chạy (Windows only)
2. Khởi động tất cả services trừ frontend
3. Đọc Cloudflare Tunnel URL từ log `cloudflared` (timeout 30s)
4. Cập nhật `VITE_API_BASE_URL`, `VITE_WS_URL`, `CORS_ALLOWED_ORIGINS` trong `.env`
5. Rebuild và khởi động frontend container
6. In URL để truy cập: `✅ Kolla đang chạy tại: https://xxx-yyy-zzz.trycloudflare.com`

Lần đầu build mất khoảng 5–10 phút. Sau khi xong, truy cập URL được in ra màn hình.

> **Lưu ý:** Tunnel URL thay đổi sau mỗi lần restart. Chỉ cần chạy lại `start.ps1` / `start.sh` để cập nhật tự động.

### 3. Verify tunnel hoạt động

```bash
# Kiểm tra cloudflared đã kết nối thành công
docker logs kolla-cloudflared

# Tìm dòng chứa URL dạng:
# +--------------------------------------------------------------------------------------------+
# |  Your quick Tunnel has been created! Visit it at (it may take some time to be reachable):  |
# |  https://xxx-yyy-zzz.trycloudflare.com                                                     |
# +--------------------------------------------------------------------------------------------+
```

Sau đó truy cập URL từ browser **bên ngoài mạng LAN** để xác nhận tunnel hoạt động.

### 4. Truy cập LAN (không cần tunnel)

Nginx vẫn expose port `8443` (HTTPS self-signed) và `8888` (HTTP plain) ra Windows host:

```
https://localhost:8443        # HTTPS (bỏ qua cảnh báo self-signed cert)
http://localhost:8888         # HTTP plain
```

> **Lưu ý:** `scripts/setup-portproxy.ps1` **không còn cần thiết** khi dùng Cloudflare Tunnel. Script này chỉ được giữ lại cho trường hợp cần truy cập LAN từ máy khác trong cùng mạng (LAN fallback).

### 5. Nâng cấp lên Named Tunnel (khi có domain cố định)

Khi muốn có URL cố định thay vì URL ngẫu nhiên mỗi lần restart:

1. Tạo tunnel trên [Cloudflare Zero Trust dashboard](https://one.dash.cloudflare.com/)
2. Cấu hình DNS CNAME record trỏ domain về tunnel
3. Lấy tunnel token và thêm vào `.env`:
   ```dotenv
   CLOUDFLARE_TUNNEL_TOKEN=eyJ...
   ```
4. Chạy lại `start.ps1` / `start.sh` như bình thường — không cần thay đổi kiến trúc.

### 6. Chạy local (không cần tunnel)

```bash
# Chỉ cần đổi các biến sau trong .env
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_WS_URL=ws://localhost:8080/ws
CORS_ALLOWED_ORIGINS=http://localhost:3000

docker compose up -d frontend backend mysql redis gipformer
```

Truy cập `http://localhost:3000`.

---

## Tại sao không dùng Jitsi self-hosted?

Jitsi Meet self-hosted dùng **WebRTC UDP** (JVB port 10000) cho media stream. Cloudflare Tunnel chỉ hỗ trợ TCP/HTTP — không thể tunnel UDP traffic. Do đó, Kolla chuyển sang dùng **Jitsi public** (`meet.jit.si`) để video call hoạt động qua tunnel mà không cần cấu hình thêm.

---

## Cấu trúc thư mục

```
KollaMeeting/
├── backend/          # Spring Boot 3.2
│   ├── src/main/java/com/example/kolla/
│   │   ├── config/       # Security, WebSocket, Redis, CORS
│   │   ├── controllers/  # REST endpoints
│   │   ├── models/       # JPA entities
│   │   ├── services/     # Business logic
│   │   ├── dto/          # Request/Response DTOs
│   │   └── enums/        # MeetingStatus, Role, ...
│   └── src/main/resources/db/migration/  # Flyway SQL
│
├── frontend/         # React 18 + Vite
│   └── src/
│       ├── pages/        # LoginPage, DashboardPage, MeetingRoomPage, ...
│       ├── components/   # Layout, shared UI
│       ├── store/        # Zustand stores (auth, notification)
│       ├── services/     # Axios API clients
│       └── router/       # AppRouter, ProtectedRoute
│
├── gipformer/        # Python FastAPI + sherpa-onnx (ASR)
├── nginx/            # nginx.conf template + entrypoint
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## API chính

Tất cả endpoint đều có prefix `/api/v1`. Xem đầy đủ tại Swagger UI.

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/auth/login` | Đăng nhập, nhận JWT |
| POST | `/auth/logout` | Đăng xuất |
| POST | `/auth/refresh` | Làm mới access token |
| GET | `/meetings` | Danh sách cuộc họp (phân trang, lọc) |
| POST | `/meetings` | Tạo cuộc họp mới |
| GET | `/meetings/{id}` | Chi tiết cuộc họp |
| PUT | `/meetings/{id}` | Cập nhật cuộc họp |
| DELETE | `/meetings/{id}` | Xoá cuộc họp |
| POST | `/meetings/{id}/activate` | Kích hoạt cuộc họp |
| POST | `/meetings/{id}/end` | Kết thúc cuộc họp |
| POST | `/meetings/{id}/join` | Tham gia phòng họp |
| POST | `/meetings/{id}/leave` | Rời phòng họp |
| GET | `/meetings/{id}/members` | Danh sách thành viên |
| POST | `/meetings/{id}/recordings/start` | Bắt đầu ghi âm |
| POST | `/recordings/{id}/stop` | Dừng ghi âm |
| GET | `/recordings/{id}/download` | Tải file ghi âm |
| GET | `/meetings/{id}/transcription` | Lấy kết quả phiên âm |
| GET/PUT | `/meetings/{id}/minutes` | Biên bản cuộc họp |
| GET | `/notifications` | Danh sách thông báo |
| GET | `/search` | Tìm kiếm cuộc họp / phiên âm |
| GET | `/users` | Quản lý người dùng (ADMIN) |
| GET | `/departments` | Quản lý phòng ban (ADMIN) |
| GET | `/rooms` | Quản lý phòng họp (ADMIN) |

### WebSocket

| Endpoint | Giao thức | Mô tả |
|---|---|---|
| `/ws` | STOMP/SockJS | Thông báo, sự kiện cuộc họp, giơ tay |
| `/ws/audio` | Binary WebSocket | Stream PCM audio đến Gipformer |

**Subscribe topics:**
- `/topic/meeting/{meetingId}` — sự kiện broadcast cho tất cả thành viên
- `/user/queue/notifications` — thông báo cá nhân
- `/user/queue/errors` — lỗi cá nhân

---

## Phân quyền

| Vai trò | Quyền |
|---|---|
| `ADMIN` | Toàn quyền: quản lý user, phòng ban, phòng họp, tất cả cuộc họp |
| `SECRETARY` | Tạo/sửa cuộc họp, quản lý biên bản, công bố biên bản |
| `USER` | Xem và tham gia cuộc họp được mời |

---

## Biến môi trường quan trọng

| Biến | Bắt buộc | Mô tả |
|---|---|---|
| `JWT_SECRET` | ✅ | Khoá ký JWT (≥ 32 ký tự) |
| `MYSQL_ROOT_PASSWORD` | ✅ | Mật khẩu root MySQL |
| `MYSQL_PASSWORD` | ✅ | Mật khẩu user MySQL |
| `GIPFORMER_CALLBACK_API_KEY` | ✅ | API key nội bộ cho Gipformer callback |
| `VITE_API_BASE_URL` | auto | URL backend từ phía trình duyệt — tự động cập nhật bởi startup script |
| `VITE_WS_URL` | auto | WebSocket URL — tự động cập nhật bởi startup script |
| `CORS_ALLOWED_ORIGINS` | auto | Origin được phép gọi API — tự động cập nhật bởi startup script |
| `CLOUDFLARE_TUNNEL_TOKEN` | — | Token Named Tunnel (để trống cho Quick Tunnel mode) |
| `DEVICE` | — | `cuda` hoặc `cpu` cho Gipformer (mặc định: `cuda`) |

> **Lưu ý:** Khi đổi mạng / IP hoặc sau mỗi lần restart, chạy lại `.\scripts\start.ps1` (Windows) hoặc `./scripts/start.sh` (WSL2) để cập nhật URL tự động.

---

## Phát triển

### Backend

```bash
cd backend
./mvnw spring-boot:run
# Chạy tests
./mvnw test
```

### Frontend

```bash
cd frontend
npm install
npm run dev       # dev server tại http://localhost:5173
npm run test      # Vitest
npm run lint      # ESLint
```

---

## Giấy phép

Dự án nội bộ — Đồ án tốt nghiệp.
