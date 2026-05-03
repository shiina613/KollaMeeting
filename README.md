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
Browser
  │
  ▼
Nginx :8443 (HTTPS, reverse proxy)
  ├── /          → Frontend  :3000  (React 18 + Vite)
  ├── /api       → Backend   :8080  (Spring Boot 3.2)
  ├── /ws        → Backend   :8080  (WebSocket STOMP)
  └── /meet      → Jitsi     (internal)

Backend
  ├── MySQL  :3306  (dữ liệu chính, Flyway migrations)
  ├── Redis  :6379  (hàng đợi transcription, cache)
  └── Gipformer :8000  (FastAPI + sherpa-onnx ASR)

Certbot  (tự động gia hạn SSL Let's Encrypt)
```

---

## Tech Stack

| Layer | Công nghệ |
|---|---|
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, Zustand, React Router v6 |
| Backend | Spring Boot 3.2, Spring Security, Spring WebSocket (STOMP), JPA/Hibernate |
| Database | MySQL 8.0, Flyway |
| Cache / Queue | Redis 7 |
| Video | Jitsi Meet (self-hosted) |
| ASR | Gipformer — Python FastAPI + sherpa-onnx (CUDA / CPU ONNX) |
| Proxy / SSL | Nginx 1.25, Certbot (Let's Encrypt), DuckDNS |
| Container | Docker, Docker Compose |

---

## Yêu cầu

- Docker Engine ≥ 24 và Docker Compose v2
- (Tuỳ chọn) NVIDIA GPU + `nvidia-container-toolkit` để chạy Gipformer với CUDA

---

## Cài đặt nhanh

### 1. Clone và cấu hình môi trường

```bash
git clone <repo-url> KollaMeeting
cd KollaMeeting
cp .env.example .env
```

Mở `.env` và điền các giá trị bắt buộc:

```dotenv
# Domain công khai (dùng cho Nginx + SSL)
DOMAIN=kolla.example.com

# DuckDNS (nếu dùng DuckDNS)
DUCKDNS_TOKEN=your-token
DUCKDNS_SUBDOMAIN=kolla
CERTBOT_EMAIL=admin@example.com

# MySQL — đổi mật khẩu trước khi deploy
MYSQL_ROOT_PASSWORD=strong-root-password
MYSQL_PASSWORD=strong-app-password

# JWT — tạo bằng: openssl rand -hex 32
JWT_SECRET=your-secret-at-least-32-chars

# URL frontend gọi đến backend
VITE_API_BASE_URL=https://kolla.example.com/api/v1

# CORS — danh sách origin được phép (phân cách bằng dấu phẩy)
CORS_ALLOWED_ORIGINS=https://kolla.example.com

# Gipformer — cuda hoặc cpu
DEVICE=cuda
```

### 2. Khởi động

```bash
docker compose up -d
```

Lần đầu build mất khoảng 5–10 phút. Sau khi xong, truy cập:

- **Ứng dụng**: `https://<DOMAIN>:8443`
- **API Docs (Swagger)**: `https://<DOMAIN>:8443/api/v1/swagger-ui.html`
- **Health check**: `https://<DOMAIN>:8443/api/v1/actuator/health`

### 3. Chạy local (không cần domain / SSL)

```bash
# Chỉ cần đổi các biến sau trong .env
VITE_API_BASE_URL=http://localhost:8080/api/v1
CORS_ALLOWED_ORIGINS=http://localhost:3000

docker compose up -d frontend backend mysql redis gipformer
```

Truy cập `http://localhost:3000`.

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
| `DOMAIN` | ✅ | Domain công khai |
| `JWT_SECRET` | ✅ | Khoá ký JWT (≥ 32 ký tự) |
| `MYSQL_ROOT_PASSWORD` | ✅ | Mật khẩu root MySQL |
| `MYSQL_PASSWORD` | ✅ | Mật khẩu user MySQL |
| `VITE_API_BASE_URL` | ✅ | URL backend từ phía trình duyệt |
| `CORS_ALLOWED_ORIGINS` | ✅ | Origin được phép gọi API (phân cách bằng `,`) |
| `GIPFORMER_CALLBACK_API_KEY` | ✅ | API key nội bộ cho Gipformer callback |
| `DEVICE` | — | `cuda` hoặc `cpu` cho Gipformer (mặc định: `cuda`) |
| `DUCKDNS_TOKEN` | — | Token DuckDNS nếu dùng dynamic DNS |

> **Lưu ý:** Khi đổi mạng / IP, cập nhật `VITE_API_BASE_URL` và `CORS_ALLOWED_ORIGINS` trong `.env` rồi rebuild frontend:
> ```bash
> docker compose up -d --build frontend backend
> ```

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
