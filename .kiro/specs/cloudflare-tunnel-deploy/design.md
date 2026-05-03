# Design Document — cloudflare-tunnel-deploy

## Overview

Feature này thay thế cơ chế expose dịch vụ Kolla Meeting ra internet từ setup WSL2 portproxy + Nginx self-signed SSL + Certbot sang **Cloudflare Tunnel** (`cloudflared`). Mục tiêu là loại bỏ hoàn toàn sự phụ thuộc vào IP tĩnh, port forwarding thủ công, và quản lý SSL certificate, đồng thời cung cấp một quy trình khởi động một lệnh duy nhất sau mỗi lần reboot.

### Vấn đề hiện tại

| Vấn đề | Nguyên nhân | Hậu quả |
|--------|-------------|---------|
| WSL2 IP thay đổi sau restart | WSL2 không có IP tĩnh | Phải chạy `setup-portproxy.ps1` với quyền Admin mỗi ngày |
| Self-signed SSL | Không có domain public | Cảnh báo trình duyệt, không thể dùng Let's Encrypt |
| Portproxy là single point of failure | Phụ thuộc vào Windows netsh | Nếu quên chạy, toàn bộ hệ thống không truy cập được từ ngoài |
| Jitsi self-hosted dùng UDP | WebRTC media stream qua UDP port 10000 | Không thể tunnel qua Cloudflare (chỉ hỗ trợ TCP/HTTP) |

### Giải pháp

```
[Internet] ──HTTPS──► [Cloudflare Edge] ──outbound tunnel──► [cloudflared container]
                                                                        │
                                                                        ▼
                                                              [Nginx :8888 (HTTP plain)]
                                                                        │
                                                    ┌───────────────────┼───────────────────┐
                                                    ▼                   ▼                   ▼
                                            [frontend:3000]    [backend:8080]    [backend:8080/ws]
```

**Cloudflare Quick Tunnel** được chọn vì:
- Không cần domain, không cần tài khoản Cloudflare
- Không cần mở inbound port trên Windows/WSL2
- SSL được Cloudflare quản lý tự động (HTTPS từ client đến Cloudflare edge)
- Kết nối outbound từ WSL2 → Cloudflare, không bị ảnh hưởng bởi WSL2 IP thay đổi

**Trade-off chấp nhận được:**
- Tunnel URL thay đổi sau mỗi lần restart (giải quyết bằng startup script tự động)
- Jitsi self-hosted không tương thích → chuyển sang `meet.jit.si` public

---

## Architecture

### Kiến trúc tổng thể sau khi thay đổi

```
┌─────────────────────────────────────────────────────────────────────┐
│  Windows Host                                                        │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  WSL2 / Docker Desktop                                        │  │
│  │                                                               │  │
│  │  ┌─────────────┐    ┌──────────────────────────────────────┐ │  │
│  │  │ cloudflared │───►│ Nginx :8888 (HTTP plain)             │ │  │
│  │  │ (Quick      │    │  /        → frontend:3000            │ │  │
│  │  │  Tunnel)    │    │  /api     → backend:8080             │ │  │
│  │  └──────┬──────┘    │  /ws      → backend:8080 (WS)       │ │  │
│  │         │           └──────────────────────────────────────┘ │  │
│  │         │ outbound                                            │  │
│  │         ▼                                                     │  │
│  │  [Cloudflare Edge]                                            │  │
│  │                                                               │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────────┐  │  │
│  │  │ frontend │  │ backend  │  │gipformer │  │ mysql/redis │  │  │
│  │  │  :3000   │  │  :8080   │  │  :8000   │  │             │  │  │
│  │  └──────────┘  └──────────┘  └──────────┘  └─────────────┘  │  │
│  │                                                               │  │
│  │  LAN access: Nginx :8443 (HTTPS self-signed) / :8888 (HTTP)  │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### Luồng khởi động (startup flow)

```
start.ps1 / start.sh
        │
        ├─1─► Kiểm tra Docker Desktop đang chạy (ps1 only)
        │
        ├─2─► docker compose up -d (tất cả service trừ frontend)
        │
        ├─3─► Poll docker logs kolla-cloudflared
        │         └─ Tìm pattern: https://[a-z0-9-]+\.trycloudflare\.com
        │         └─ Timeout 30s → error nếu không tìm thấy
        │
        ├─4─► Cập nhật .env:
        │         VITE_API_BASE_URL=https://<tunnel-url>/api/v1
        │         VITE_WS_URL=wss://<tunnel-url>/ws
        │         CORS_ALLOWED_ORIGINS=https://<tunnel-url>
        │
        ├─5─► docker compose up -d --build frontend
        │
        └─6─► In: ✅ Kolla đang chạy tại: https://<tunnel-url>
```

### Quyết định thiết kế

| Quyết định | Lựa chọn | Lý do |
|-----------|----------|-------|
| Tunnel mode | Quick Tunnel | Không cần domain/account, phù hợp dev environment |
| Jitsi | meet.jit.si public | Cloudflare Tunnel không hỗ trợ UDP/WebRTC |
| Nginx port cho tunnel | 8888 (HTTP plain) | Cloudflare terminate SSL, Nginx không cần xử lý TLS |
| Script language | PowerShell + bash | Hỗ trợ cả Windows và WSL2 |
| Named Tunnel upgrade path | `CLOUDFLARE_TUNNEL_TOKEN` trong `.env` | Không cần thay đổi kiến trúc khi nâng cấp |

---

## Components and Interfaces

### 1. cloudflared Docker Service

**Image:** `cloudflare/cloudflared:latest`

**Command:** `tunnel --no-autoupdate --url http://nginx:8888`

**Cấu hình trong docker-compose.yml:**
```yaml
cloudflared:
  image: cloudflare/cloudflared:latest
  container_name: kolla-cloudflared
  restart: unless-stopped
  command: tunnel --no-autoupdate --url http://nginx:8888
  networks:
    - kolla-network
  depends_on:
    - nginx
```

**Không cần:**
- Environment variables (Quick Tunnel mode)
- Volume mounts
- Port mappings (kết nối outbound)

**Named Tunnel upgrade path** (khi có domain):
```yaml
# Thêm vào .env:
# CLOUDFLARE_TUNNEL_TOKEN=eyJ...
environment:
  TUNNEL_TOKEN: ${CLOUDFLARE_TUNNEL_TOKEN:-}
command: tunnel --no-autoupdate run
```

### 2. Nginx — cập nhật cấu hình

**Thay đổi so với hiện tại:**

| Thay đổi | Mô tả |
|---------|-------|
| Xóa block `/meet` | Jitsi không còn được proxy qua Nginx |
| Port 8888 — `X-Forwarded-Proto: https` | Cloudflare đã terminate SSL |
| Port 8888 — forward `CF-Connecting-IP` → `X-Real-IP` | Lấy real client IP từ Cloudflare |
| Xóa certbot volume mounts | SSL không còn quản lý locally |
| Giữ nguyên port 8443 | LAN access với self-signed cert |

**Cấu hình port 8888 sau khi cập nhật:**
```nginx
server {
    listen 8888;

    # WebSocket — STOMP/SockJS
    location /ws {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $http_cf_connecting_ip;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_read_timeout 86400s;
        proxy_send_timeout 86400s;
    }

    # Backend API
    location /api {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $http_cf_connecting_ip;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_read_timeout 60s;
        proxy_connect_timeout 10s;
        client_max_body_size 512m;
    }

    # Frontend
    location / {
        proxy_pass http://frontend:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $http_cf_connecting_ip;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

### 3. Startup Scripts

#### `scripts/start.ps1` (Windows PowerShell)

```
Interface:
  Input:  Không có (chạy trực tiếp)
  Output: Tunnel URL in ra stdout
  Exit:   0 = thành công, 1 = lỗi

Steps:
  1. Check-DockerRunning()     → Kiểm tra Docker Desktop process
  2. Wait-DockerDaemon()       → Timeout 60s, poll docker info
  3. Start-Services()          → docker compose up -d --scale frontend=0
  4. Get-TunnelUrl()           → Poll docker logs, timeout 30s, regex extract
  5. Update-EnvFile()          → Sed-equivalent regex replace trong .env
  6. Build-Frontend()          → docker compose up -d --build frontend
  7. Print-Success()           → In URL ra màn hình
```

#### `scripts/start.sh` (WSL2/bash)

```
Interface:
  Input:  Không có
  Output: Tunnel URL in ra stdout
  Exit:   0 = thành công, 1 = lỗi

Steps:
  1. check_docker()            → docker info
  2. start_services()          → docker compose up -d --scale frontend=0
  3. get_tunnel_url()          → docker logs poll, timeout 30s, grep/sed extract
  4. update_env_file()         → sed -i replace trong .env
  5. build_frontend()          → docker compose up -d --build frontend
  6. print_success()           → In URL ra màn hình
```

### 4. docker-compose.yml — thay đổi

**Xóa:**
- Service `jitsi`
- Service `certbot`
- Volumes: `jitsi-web-config`, `jitsi-prosody-config`, `jitsi-jicofo-config`, `jitsi-jvb-config`
- Volumes: `certbot-webroot`, `certbot-certs`
- Volume mounts certbot trong nginx service

**Thêm:**
- Service `cloudflared`

**Cập nhật:**
- `nginx` service: xóa certbot volume mounts, giữ port 8888 và 8443
- `frontend` build args: `VITE_JITSI_URL` default → `https://meet.jit.si`
- `backend` environment: `JITSI_SERVER_URL` default → `https://meet.jit.si`

### 5. `.env.example` — cập nhật

**Xóa section:** DOMAIN & SSL (Nginx + Certbot), DuckDNS variables

**Thêm section:** Cloudflare Tunnel
```dotenv
# =============================================================================
# CLOUDFLARE TUNNEL
# =============================================================================
# Quick Tunnel: không cần cấu hình, URL tự sinh mỗi lần restart.
# Named Tunnel: set CLOUDFLARE_TUNNEL_TOKEN để dùng domain cố định.
# [optional — để trống cho Quick Tunnel mode]
CLOUDFLARE_TUNNEL_TOKEN=

# Tunnel URL hiện tại (được startup script tự động cập nhật)
# [auto-updated by scripts/start.ps1 or scripts/start.sh]
VITE_API_BASE_URL=https://<cloudflare-domain>/api/v1
VITE_WS_URL=wss://<cloudflare-domain>/ws
CORS_ALLOWED_ORIGINS=https://<cloudflare-domain>
```

---

## Data Models

Feature này không giới thiệu data model mới. Các thay đổi liên quan đến cấu hình và scripting:

### Tunnel URL

```
Format:   https://[a-z0-9]+-[a-z0-9]+-[a-z0-9]+\.trycloudflare\.com
Example:  https://abc-def-ghi.trycloudflare.com
Lifetime: Tồn tại cho đến khi cloudflared container restart
Source:   cloudflared container logs
```

### Derived URLs từ Tunnel URL

```
Tunnel URL:          https://abc-def-ghi.trycloudflare.com
VITE_API_BASE_URL:   https://abc-def-ghi.trycloudflare.com/api/v1
VITE_WS_URL:         wss://abc-def-ghi.trycloudflare.com/ws
CORS_ALLOWED_ORIGINS: https://abc-def-ghi.trycloudflare.com
```

**Quy tắc biến đổi:**
- `VITE_API_BASE_URL` = `<tunnel_url>` + `/api/v1`
- `VITE_WS_URL` = `<tunnel_url>` với scheme `https://` → `wss://` + `/ws`
- `CORS_ALLOWED_ORIGINS` = `<tunnel_url>` (không có path)

### .env file structure (sau khi cập nhật)

```
Key                    | Updated by script | Source
-----------------------|-------------------|------------------
VITE_API_BASE_URL      | Yes               | Derived from tunnel URL
VITE_WS_URL            | Yes               | Derived from tunnel URL
CORS_ALLOWED_ORIGINS   | Yes               | Derived from tunnel URL
CLOUDFLARE_TUNNEL_TOKEN| No                | Manual (Named Tunnel only)
VITE_JITSI_URL         | No                | Static: https://meet.jit.si
JITSI_SERVER_URL       | No                | Static: https://meet.jit.si
[other variables]      | No                | Unchanged
```

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

Feature này chủ yếu là cấu hình (docker-compose, nginx) và scripting (bash/PowerShell). Phần lớn acceptance criteria là SMOKE/INTEGRATION tests. Tuy nhiên, có một số logic thuần túy trong startup scripts (URL extraction, .env file update) đủ điều kiện cho property-based testing.

### Property 1: URL extraction từ log

*For any* chuỗi log từ cloudflared container, hàm extract URL phải trả về đúng URL nếu và chỉ nếu chuỗi log chứa pattern `https://[a-z0-9]+-[a-z0-9]+-[a-z0-9]+\.trycloudflare\.com`, và không trả về URL nếu pattern không có mặt.

**Validates: Requirements 2.1, 2.2**

### Property 2: .env update không phá vỡ các biến khác

*For any* file `.env` hợp lệ chứa tập hợp các biến tùy ý, sau khi startup script cập nhật `VITE_API_BASE_URL`, `VITE_WS_URL`, và `CORS_ALLOWED_ORIGINS` với một tunnel URL mới, tất cả các biến khác trong file phải giữ nguyên giá trị ban đầu.

**Validates: Requirements 3.1, 3.2**

### Property 3: Derived URLs có scheme đúng

*For any* tunnel URL hợp lệ dạng `https://xxx.trycloudflare.com`, các URL được sinh ra phải thỏa mãn:
- `VITE_API_BASE_URL` bắt đầu bằng `https://` và kết thúc bằng `/api/v1`
- `VITE_WS_URL` bắt đầu bằng `wss://` và kết thúc bằng `/ws`
- `CORS_ALLOWED_ORIGINS` bằng đúng tunnel URL (không có trailing slash, không có path)

**Validates: Requirements 3.1, 7.2, 7.3, 8.4**

### Property 4: Script error handling — mọi bước thất bại đều sinh error message cụ thể

*For any* bước trong startup sequence (Docker check, service start, URL extraction, .env update, frontend build), khi bước đó thất bại, script phải dừng lại và in thông báo lỗi xác định rõ bước nào bị lỗi (không in thông báo chung chung).

**Validates: Requirements 11.4**

---

## Error Handling

### Startup Script — Error Scenarios

| Bước | Lỗi có thể xảy ra | Xử lý |
|------|-------------------|-------|
| Docker check | Docker Desktop chưa chạy | In hướng dẫn khởi động Docker, exit 1 |
| Docker daemon wait | Timeout 60s | In "Docker daemon không sẵn sàng sau 60s", exit 1 |
| docker compose up | Service fail to start | In output của docker compose, exit 1 |
| URL extraction | Timeout 30s không tìm thấy URL | In "cloudflared không sinh URL sau 30s. Kiểm tra: docker logs kolla-cloudflared", exit 1 |
| .env update | File không tồn tại | In ".env không tìm thấy. Chạy: cp .env.example .env", exit 1 |
| Frontend rebuild | Build fail | In docker compose output, exit 1 |

### cloudflared — Reconnection

- `restart: unless-stopped` đảm bảo cloudflared tự restart nếu crash
- Mỗi lần restart sinh URL mới → startup script phải chạy lại để cập nhật URL
- Nếu cloudflared restart trong khi đang dùng: URL cũ sẽ không còn hoạt động, cần chạy lại startup script

### Nginx — Graceful Degradation

- Nếu cloudflared không chạy: Nginx vẫn hoạt động bình thường trên port 8443 (LAN)
- Nếu backend down: Nginx trả về 502 Bad Gateway
- WebSocket timeout: `proxy_read_timeout 86400s` (24h) để tránh disconnect không cần thiết

### Jitsi Public — Fallback

- Nếu `meet.jit.si` không khả dụng: Video call không hoạt động, nhưng các tính năng khác của Kolla vẫn bình thường
- Có thể override `VITE_JITSI_URL` trong `.env` để trỏ đến Jitsi instance khác

---

## Testing Strategy

### Phân loại tests

Feature này chủ yếu là infrastructure/configuration changes. Testing strategy tập trung vào:

1. **Smoke tests** — Kiểm tra cấu hình đúng (docker-compose, nginx.conf, .env.example)
2. **Unit/Property tests** — Kiểm tra logic thuần túy trong startup scripts
3. **Integration tests** — Kiểm tra end-to-end flow (manual hoặc CI với Docker)

### Property-Based Tests

Sử dụng **fast-check** (TypeScript/JavaScript) hoặc **Hypothesis** (Python) cho logic URL extraction và .env manipulation. Vì scripts là bash/PowerShell, các property tests sẽ được viết bằng cách extract logic thành helper functions có thể test độc lập.

**Cấu hình:** Minimum 100 iterations per property test.

#### Property 1: URL Extraction Correctness
```
Feature: cloudflare-tunnel-deploy, Property 1: URL extraction từ log
```
- Generator: Sinh chuỗi log ngẫu nhiên, một số chứa valid trycloudflare.com URL, một số không
- Assertion: Extract function trả về URL khi và chỉ khi URL hợp lệ có mặt trong log

#### Property 2: .env Non-Destructive Update
```
Feature: cloudflare-tunnel-deploy, Property 2: .env update không phá vỡ các biến khác
```
- Generator: Sinh .env file với tập hợp biến ngẫu nhiên (key=value pairs hợp lệ)
- Assertion: Sau khi update, tất cả biến không phải target vẫn giữ nguyên

#### Property 3: Derived URL Scheme Correctness
```
Feature: cloudflare-tunnel-deploy, Property 3: Derived URLs có scheme đúng
```
- Generator: Sinh tunnel URLs hợp lệ dạng `https://[word]-[word]-[word].trycloudflare.com`
- Assertion: Derived URLs có đúng scheme và path suffix

#### Property 4: Script Error Specificity
```
Feature: cloudflare-tunnel-deploy, Property 4: Script error handling
```
- Generator: Sinh failure scenarios cho từng bước trong startup sequence
- Assertion: Error message chứa tên bước bị lỗi

### Smoke Tests (Manual / CI)

```bash
# 1. Verify cloudflared service exists in docker-compose.yml
grep -q "cloudflared" docker-compose.yml

# 2. Verify jitsi service removed
! grep -q "jitsi/web" docker-compose.yml

# 3. Verify certbot service removed
! grep -q "certbot/certbot" docker-compose.yml

# 4. Verify nginx port 8888 has no HTTPS redirect
grep -A5 "listen 8888" nginx/nginx.conf | ! grep -q "return 301"

# 5. Verify X-Forwarded-Proto https on port 8888
grep -A20 "listen 8888" nginx/nginx.conf | grep -q "X-Forwarded-Proto https"

# 6. Verify /meet location block removed
! grep -q "location /meet" nginx/nginx.conf

# 7. Verify startup scripts exist
test -f scripts/start.ps1 && test -f scripts/start.sh

# 8. Verify setup-portproxy.ps1 has deprecation comment
grep -q "DEPRECATED" scripts/setup-portproxy.ps1
```

### Integration Tests (Manual)

1. **Tunnel startup test:** Chạy `./scripts/start.sh`, verify URL được in ra trong vòng 2 phút
2. **URL accessibility test:** Truy cập tunnel URL từ browser bên ngoài LAN
3. **WebSocket test:** Verify real-time notifications hoạt động qua tunnel URL
4. **LAN fallback test:** Dừng cloudflared, verify app vẫn truy cập được qua `https://localhost:8443`
5. **Restart test:** Restart WSL2, chạy lại startup script, verify URL mới hoạt động

### Unit Tests cho Script Logic

Các helper functions trong scripts nên được extract và test riêng:

```bash
# test_extract_tunnel_url.sh
test_extract_url_from_valid_log() {
    log="2024/01/01 +https://abc-def-ghi.trycloudflare.com | ..."
    result=$(extract_tunnel_url "$log")
    assert_equals "https://abc-def-ghi.trycloudflare.com" "$result"
}

test_extract_url_returns_empty_for_invalid_log() {
    log="2024/01/01 Starting tunnel..."
    result=$(extract_tunnel_url "$log")
    assert_empty "$result"
}
```
