# Requirements Document

## Introduction

Feature này thay thế cơ chế expose dịch vụ Kolla Meeting ra internet từ setup hiện tại (WSL2 portproxy + Nginx self-signed SSL + Certbot) sang **Cloudflare Tunnel** (`cloudflared`).

Vấn đề cốt lõi của setup hiện tại:
- WSL2 IP thay đổi sau mỗi lần restart Windows, buộc phải chạy lại `setup-portproxy.ps1` thủ công với quyền Administrator.
- Self-signed SSL gây cảnh báo trình duyệt; Let's Encrypt yêu cầu domain public và port 80/443 mở ra ngoài.
- Portproxy là một điểm lỗi đơn: nếu quên chạy, toàn bộ hệ thống không truy cập được từ ngoài.

Cloudflare Tunnel giải quyết tất cả vấn đề trên bằng cách thiết lập kết nối outbound từ WSL2 ra Cloudflare edge — không cần mở port, không cần IP tĩnh, SSL được Cloudflare quản lý tự động.

**Quyết định về loại tunnel:** Hệ thống sử dụng **Quick Tunnel** (`cloudflared tunnel --url`) — không cần domain, không cần tài khoản Cloudflare, không cần cấu hình trước. Cloudflare tự sinh link dạng `https://xxx-yyy-zzz.trycloudflare.com` mỗi lần khởi động. Do link thay đổi sau mỗi lần restart, startup script sẽ tự động: (1) đọc link mới từ log cloudflared, (2) cập nhật `VITE_API_BASE_URL`, `VITE_WS_URL`, `CORS_ALLOWED_ORIGINS` trong `.env`, (3) rebuild frontend container với link mới. Developer chỉ cần chạy 1 lệnh mỗi ngày. Khi có domain trong tương lai, có thể nâng lên Named Tunnel bằng cách thêm `CLOUDFLARE_TUNNEL_TOKEN` vào `.env` mà không cần thay đổi kiến trúc.

**Quyết định về Jitsi:** Jitsi Meet self-hosted dùng WebRTC UDP (port 10000) cho media stream — Cloudflare Tunnel không hỗ trợ UDP. Do đó, Jitsi self-hosted sẽ **không** được expose qua Cloudflare Tunnel. Thay vào đó, hệ thống sẽ chuyển sang dùng **Jitsi public** (`meet.jit.si`) hoặc một Jitsi instance có public IP riêng. Nginx sẽ không còn proxy `/meet` nữa; frontend sẽ trỏ trực tiếp đến Jitsi public URL.

---

## Glossary

- **Cloudflare_Tunnel**: Dịch vụ Cloudflare Zero Trust cho phép expose dịch vụ nội bộ ra internet qua kết nối outbound, không cần mở inbound port.
- **cloudflared**: Daemon client của Cloudflare Tunnel, chạy trong Docker container, duy trì kết nối đến Cloudflare edge.
- **Quick_Tunnel**: Chế độ tunnel không cần cấu hình, chạy bằng lệnh `cloudflared tunnel --url <local-url>`. Cloudflare tự sinh link ngẫu nhiên dạng `https://xxx.trycloudflare.com`, thay đổi mỗi lần restart.
- **Named_Tunnel**: Chế độ tunnel nâng cao, yêu cầu domain và Tunnel_Token, cho link cố định. Là upgrade path từ Quick_Tunnel.
- **Tunnel_URL**: Link public dạng `https://xxx-yyy-zzz.trycloudflare.com` do Cloudflare sinh ra khi dùng Quick_Tunnel. Được lưu vào `.env` sau mỗi lần startup.
- **Tunnel_Config**: File cấu hình YAML của cloudflared, định nghĩa ingress rules ánh xạ hostname → service nội bộ.
- **Nginx**: Reverse proxy nội bộ trong Docker network, nhận traffic từ cloudflared và route đến các service.
- **WSL2**: Windows Subsystem for Linux 2 — môi trường Linux chạy trên Windows, nơi Docker Desktop và toàn bộ stack đang chạy.
- **Portproxy**: Cơ chế `netsh interface portproxy` của Windows để forward port từ Windows host vào WSL2 IP.
- **JVB**: Jitsi Videobridge — component xử lý media stream UDP của Jitsi Meet self-hosted.
- **CORS_ALLOWED_ORIGINS**: Danh sách origin được phép gọi backend API, cần cập nhật khi domain thay đổi.
- **Ingress_Rule**: Một entry trong Tunnel_Config ánh xạ một hostname đến một service URL nội bộ.
- **Tunnel_Token**: Credential dạng token dùng để xác thực cloudflared với Cloudflare dashboard khi dùng Named_Tunnel.

---

## Requirements

### Requirement 1: Triển khai cloudflared container (Quick Tunnel)

**User Story:** As a developer, I want to run cloudflared as a Docker service using Quick Tunnel mode, so that the tunnel starts automatically with `docker compose up` without needing any account or domain.

#### Acceptance Criteria

1. THE `docker-compose.yml` SHALL include a `cloudflared` service sử dụng image `cloudflare/cloudflared:latest`.
2. THE `cloudflared` service SHALL chạy với lệnh `tunnel --no-autoupdate --url http://nginx:8888` để kích hoạt Quick Tunnel mode trỏ vào Nginx port 8888.
3. WHEN `docker compose up` được chạy, THE `cloudflared` service SHALL khởi động và log ra Tunnel_URL dạng `https://xxx-yyy-zzz.trycloudflare.com` trong vòng 30 giây.
4. THE `cloudflared` service SHALL thuộc `kolla-network` để có thể gọi trực tiếp đến `nginx` container theo tên service.
5. THE `cloudflared` service SHALL restart `unless-stopped` để tự khởi động lại sau khi WSL2 restart.
6. THE `cloudflared` service SHALL KHÔNG yêu cầu bất kỳ environment variable hay volume mount nào để hoạt động ở Quick Tunnel mode.

---

### Requirement 2: Tự động đọc Tunnel URL từ log

**User Story:** As a developer, I want the startup script to automatically extract the new tunnel URL from cloudflared logs, so that I don't need to manually find and copy the URL after each restart.

#### Acceptance Criteria

1. THE Startup_Script SHALL poll log của `cloudflared` container sau khi `docker compose up -d` hoàn thành, tìm pattern `https://[a-z0-9-]+\.trycloudflare\.com` trong output.
2. WHEN Tunnel_URL được tìm thấy trong log, THE Startup_Script SHALL lưu URL đó vào biến để dùng cho các bước tiếp theo.
3. WHEN Tunnel_URL không xuất hiện trong log sau 30 giây, THE Startup_Script SHALL in thông báo lỗi rõ ràng và dừng lại, không tiếp tục rebuild frontend với URL sai.
4. THE Startup_Script SHALL hỗ trợ cả PowerShell (Windows) và bash (WSL2) để đọc log từ `docker logs kolla-cloudflared`.

---

### Requirement 3: Tự động cập nhật .env và rebuild frontend

**User Story:** As a developer, I want the startup script to automatically update environment variables and rebuild the frontend with the new tunnel URL, so that the app works correctly without any manual configuration.

#### Acceptance Criteria

1. AFTER Tunnel_URL được xác định, THE Startup_Script SHALL cập nhật các biến sau trong file `.env`:
   - `VITE_API_BASE_URL=https://<tunnel-url>/api/v1`
   - `VITE_WS_URL=wss://<tunnel-url>/ws`
   - `CORS_ALLOWED_ORIGINS=https://<tunnel-url>`
2. THE Startup_Script SHALL dùng sed (bash) hoặc regex replace (PowerShell) để cập nhật từng dòng trong `.env` mà không xóa các biến khác.
3. AFTER `.env` được cập nhật, THE Startup_Script SHALL chạy `docker compose up -d --build frontend` để rebuild frontend container với build args mới.
4. WHEN rebuild hoàn thành, THE Startup_Script SHALL in Tunnel_URL ra màn hình theo format: `✅ Kolla đang chạy tại: https://<tunnel-url>`.
5. THE Startup_Script SHALL cũng cập nhật `VITE_API_BASE_URL` và `VITE_WS_URL` trong `docker-compose.yml` build args section nếu chúng được hardcode ở đó.

---

### Requirement 4: Loại bỏ WSL2 portproxy

**User Story:** As a developer, I want to remove the dependency on `setup-portproxy.ps1`, so that the system works correctly after every WSL2 restart without manual intervention.

#### Acceptance Criteria

1. THE `scripts/setup-portproxy.ps1` SHALL được giữ lại trong repository nhưng được đánh dấu deprecated trong comment header.
2. THE `README` hoặc deployment documentation SHALL ghi rõ rằng portproxy không còn cần thiết khi dùng Cloudflare Tunnel.
3. WHEN WSL2 restart và Docker services khởi động lại, THE `cloudflared` service SHALL tự động kết nối lại mà không cần can thiệp thủ công.
4. THE Nginx ports `8443` và `8888` SHALL vẫn được expose ra Windows host (qua Docker port mapping) để hỗ trợ truy cập LAN nội bộ khi cần.

---

### Requirement 5: Cập nhật Nginx config cho Cloudflare Tunnel

**User Story:** As a developer, I want Nginx to correctly handle requests forwarded by cloudflared, so that backend services receive the correct client IP and protocol information.

#### Acceptance Criteria

1. THE `Nginx` SHALL set header `X-Forwarded-Proto: https` cho tất cả request đến từ cloudflared trên port 8888, vì Cloudflare đã terminate SSL trước khi forward.
2. THE `Nginx` SHALL forward header `CF-Connecting-IP` (real client IP từ Cloudflare) đến upstream services thông qua `X-Real-IP`.
3. THE `Nginx` SHALL loại bỏ block proxy `/meet` khỏi cấu hình khi Jitsi self-hosted không còn được expose qua tunnel.
4. WHEN `Nginx` nhận request trên port 8888, THE `Nginx` SHALL không redirect sang HTTPS (vì Cloudflare đã xử lý SSL).

---

### Requirement 6: Xử lý Jitsi — chuyển sang Jitsi public

**User Story:** As a developer, I want to replace self-hosted Jitsi with a public Jitsi instance, so that video conferencing works correctly without requiring UDP port exposure through Cloudflare Tunnel.

#### Acceptance Criteria

1. THE `docker-compose.yml` SHALL loại bỏ `jitsi` service (và các Jitsi-related volumes: `jitsi-web-config`, `jitsi-prosody-config`, `jitsi-jicofo-config`, `jitsi-jvb-config`).
2. THE `docker-compose.yml` SHALL loại bỏ `certbot` service và `certbot-webroot`, `certbot-certs` volumes vì SSL không còn được quản lý locally.
3. THE `VITE_JITSI_URL` environment variable SHALL trỏ đến Jitsi public instance (mặc định: `https://meet.jit.si`).
4. THE `JITSI_SERVER_URL` environment variable (backend) SHALL trỏ đến cùng Jitsi public instance.
5. WHEN `VITE_JITSI_URL` được set trong `.env`, THE Frontend SHALL load Jitsi `external_api.js` từ URL đó.
6. THE `.env.example` SHALL cập nhật `VITE_JITSI_URL` và `JITSI_SERVER_URL` với giá trị mặc định `https://meet.jit.si`.

---

### Requirement 7: Cập nhật CORS và environment variables

**User Story:** As a developer, I want all environment variables to reflect the new Cloudflare domain, so that CORS, API URLs, and WebSocket connections work correctly in production.

#### Acceptance Criteria

1. THE `CORS_ALLOWED_ORIGINS` environment variable SHALL được cập nhật để bao gồm domain Cloudflare (ví dụ: `https://kolla.example.com`).
2. THE `VITE_API_BASE_URL` SHALL trỏ đến `https://<cloudflare-domain>/api/v1`.
3. THE `VITE_WS_URL` SHALL trỏ đến `wss://<cloudflare-domain>/ws` (WebSocket qua Cloudflare Tunnel).
4. WHEN Cloudflare Tunnel forward WebSocket upgrade request, THE `Nginx` SHALL xử lý WebSocket upgrade đúng cách trên port 8888.
5. THE `.env.example` SHALL chứa tất cả các biến trên với placeholder `<cloudflare-domain>` và comment hướng dẫn.

---

### Requirement 8: Hỗ trợ WebSocket qua Cloudflare Tunnel

**User Story:** As a developer, I want WebSocket connections (STOMP/SockJS) to work through Cloudflare Tunnel, so that real-time features like notifications continue to function.

#### Acceptance Criteria

1. THE `Tunnel_Config` SHALL không cần cấu hình đặc biệt cho WebSocket vì Cloudflare Tunnel hỗ trợ HTTP/1.1 upgrade tự động.
2. THE `Nginx` SHALL duy trì cấu hình WebSocket proxy (`Upgrade`, `Connection: upgrade`) trên port 8888 cho path `/ws`.
3. WHEN một WebSocket connection được thiết lập qua Cloudflare Tunnel, THE `Nginx` SHALL giữ connection mở với `proxy_read_timeout 86400s`.
4. THE `VITE_WS_URL` SHALL dùng scheme `wss://` (không phải `ws://`) vì Cloudflare terminate SSL.

---

### Requirement 9: Tài liệu hóa quy trình setup

**User Story:** As a developer, I want clear setup documentation for Cloudflare Tunnel deployment, so that I can reproduce the setup on a new machine or after a system reset.

#### Acceptance Criteria

1. THE deployment documentation SHALL liệt kê các bước: tạo tunnel trên Cloudflare Zero Trust dashboard, lấy tunnel token, cấu hình DNS CNAME record, set `CLOUDFLARE_TUNNEL_TOKEN` trong `.env`.
2. THE deployment documentation SHALL giải thích lý do Jitsi self-hosted không tương thích với Cloudflare Tunnel (UDP/WebRTC limitation).
3. THE deployment documentation SHALL mô tả cách verify tunnel hoạt động: kiểm tra log `cloudflared`, truy cập domain từ browser bên ngoài mạng LAN.
4. THE deployment documentation SHALL ghi rõ rằng portproxy (`setup-portproxy.ps1`) không còn cần thiết và chỉ giữ lại cho mục đích LAN fallback.

---

### Requirement 11: One-command startup sau khi tắt máy

**User Story:** As a developer, I want to start the entire stack with a single command after rebooting my PC, so that the tunnel URL is automatically updated and the app is ready to use without any manual steps.

#### Acceptance Criteria

1. THE project SHALL cung cấp `scripts/start.ps1` (Windows PowerShell) thực hiện tuần tự các bước sau mà không cần input từ người dùng:
   - Kiểm tra và khởi động Docker Desktop nếu chưa chạy
   - Chờ Docker daemon sẵn sàng (timeout 60 giây)
   - Chạy `docker compose up -d` cho tất cả service trừ frontend
   - Chờ và đọc Tunnel_URL từ log cloudflared (timeout 30 giây)
   - Cập nhật `.env` với Tunnel_URL mới
   - Rebuild và khởi động frontend container
   - In Tunnel_URL ra màn hình
2. THE project SHALL cung cấp `scripts/start.sh` (WSL2/bash) thực hiện các bước tương tự, bỏ qua bước khởi động Docker Desktop.
3. WHEN toàn bộ quá trình hoàn thành thành công, THE script SHALL in thông báo rõ ràng kèm Tunnel_URL để developer có thể chia sẻ hoặc truy cập ngay.
4. WHEN bất kỳ bước nào thất bại (Docker không khởi động, tunnel không sinh URL, rebuild lỗi), THE script SHALL dừng lại và in thông báo lỗi cụ thể chỉ rõ bước nào bị lỗi.
5. THE `start.ps1` SHALL không yêu cầu quyền Administrator để chạy.
6. THE `README` SHALL hướng dẫn cách dùng với đúng 1 lệnh: `.\scripts\start.ps1` (Windows) hoặc `./scripts/start.sh` (WSL2).
7. THE tổng thời gian chạy script (từ lúc gọi đến lúc in URL) SHALL không vượt quá 5 phút trong điều kiện bình thường.

---

### Requirement 10: Backward compatibility — LAN access

**User Story:** As a developer, I want to still access the application on the local network without Cloudflare Tunnel, so that development and testing on LAN continues to work.

#### Acceptance Criteria

1. THE `Nginx` SHALL vẫn lắng nghe trên port 8888 (HTTP plain) để hỗ trợ truy cập LAN trực tiếp.
2. THE `Nginx` SHALL vẫn lắng nghe trên port 8443 (HTTPS self-signed) để hỗ trợ truy cập LAN qua HTTPS.
3. WHILE `cloudflared` service không chạy, THE các service khác (frontend, backend, gipformer, mysql, redis) SHALL vẫn hoạt động bình thường.
4. THE `docker-compose.yml` SHALL cho phép chạy stack mà không có `cloudflared` bằng cách dùng `docker compose up --scale cloudflared=0` hoặc comment out service.
