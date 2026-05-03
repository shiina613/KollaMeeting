# Implementation Plan: cloudflare-tunnel-deploy

## Overview

Thay thế cơ chế expose dịch vụ Kolla Meeting từ WSL2 portproxy + Nginx self-signed SSL + Certbot sang Cloudflare Tunnel (`cloudflared`). Kế hoạch triển khai theo thứ tự: cập nhật docker-compose → cập nhật Nginx config → viết startup scripts → cập nhật .env.example → đánh dấu deprecated portproxy script.

## Tasks

- [x] 0. Readiness check — Kiểm tra workspace trước khi implement
  - Đây là checkpoint để agent xác nhận môi trường workspace đủ điều kiện trước khi bắt đầu các task tiếp theo. Không tạo file mới, không thay đổi code.
  - [x] 0.1 Xác nhận `docker-compose.yml` tồn tại và có service `cloudflared` (task 1 đã hoàn thành)
    - Kiểm tra file `docker-compose.yml` tồn tại trong workspace root
    - Kiểm tra service `cloudflared` đã có trong file
    - Kiểm tra không còn service `jitsi` và `certbot`
  - [x] 0.2 Xác nhận `nginx/nginx.conf` tồn tại và đọc được
    - Kiểm tra file `nginx/nginx.conf` tồn tại
    - Đọc nội dung để xác nhận có server block `listen 8888` và `listen 8443`
  - [x] 0.3 Xác nhận thư mục `scripts/` tồn tại
    - Kiểm tra thư mục `scripts/` có trong workspace
    - Liệt kê các file hiện có để biết cần tạo mới hay cập nhật
  - [x] 0.4 Xác nhận `.env.example` tồn tại
    - Kiểm tra file `.env.example` tồn tại để làm cơ sở cập nhật ở task 7
  - [x] 0.5 Xác nhận `README.md` tồn tại
    - Kiểm tra file `README.md` tồn tại để làm cơ sở cập nhật ở task 7
  - [x] 0.6 Xác nhận `scripts/setup-portproxy.ps1` tồn tại
    - Kiểm tra file tồn tại để đánh dấu deprecated ở task 7
  - Nếu bất kỳ check nào thất bại: dừng lại, báo cáo rõ file/thư mục nào còn thiếu trước khi tiếp tục.

- [x] 1. Cập nhật `docker-compose.yml` — thêm cloudflared, xóa jitsi và certbot
  - Thêm service `cloudflared` với image `cloudflare/cloudflared:latest`, command `tunnel --no-autoupdate --url http://nginx:8888`, `restart: unless-stopped`, thuộc `kolla-network`, `depends_on: nginx`
  - Xóa service `jitsi` và service `certbot`
  - Xóa volumes: `jitsi-web-config`, `jitsi-prosody-config`, `jitsi-jicofo-config`, `jitsi-jvb-config`, `certbot-webroot`, `certbot-certs`
  - Xóa volume mounts certbot trong `nginx` service
  - Xóa `depends_on: jitsi` trong `nginx` service
  - Cập nhật `frontend` build args: `VITE_JITSI_URL` default → `https://meet.jit.si`; xóa hardcode `VITE_API_BASE_URL` và `VITE_WS_URL` khỏi build args (để script cập nhật .env thay thế)
  - Cập nhật `backend` environment: `JITSI_SERVER_URL` default → `https://meet.jit.si`
  - Thêm comment upgrade path Named Tunnel (`CLOUDFLARE_TUNNEL_TOKEN`) trong service `cloudflared`
  - Đảm bảo Nginx vẫn expose port `8443` và `8888` ra host (LAN backward compat)
  - _Requirements: 1.1, 1.2, 1.4, 1.5, 1.6, 3.5, 6.1, 6.2, 6.3, 6.4, 10.4_

- [x] 2. Cập nhật `nginx/nginx.conf` cho Cloudflare Tunnel
  - [x] 2.1 Cập nhật block `server { listen 8888 }` — forward `CF-Connecting-IP` và set `X-Forwarded-Proto: https`
    - Thay `X-Real-IP $remote_addr` → `X-Real-IP $http_cf_connecting_ip` trong tất cả location blocks của port 8888
    - Đảm bảo `X-Forwarded-Proto https` đã có trong tất cả location blocks của port 8888
    - Xóa block `location /meet` khỏi port 8888 (nếu có)
    - Giữ nguyên `proxy_read_timeout 86400s` và `proxy_send_timeout 86400s` cho WebSocket location `/ws`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 8.2, 8.3_
  - [x] 2.2 Xóa block `location /meet` khỏi port 8443 (HTTPS)
    - Xóa toàn bộ location block proxy đến `kolla-jitsi` trong server block port 8443
    - _Requirements: 5.3, 6.1_

- [x] 3. Checkpoint — Kiểm tra cấu hình docker-compose và nginx
  - Chạy các smoke tests sau để xác nhận cấu hình đúng:
    - `grep -q "cloudflared" docker-compose.yml`
    - `! grep -q "jitsi/web" docker-compose.yml`
    - `! grep -q "certbot/certbot" docker-compose.yml`
    - `! grep -q "location /meet" nginx/nginx.conf`
    - `grep -q "X-Forwarded-Proto https" nginx/nginx.conf`
    - `grep -q "cf_connecting_ip" nginx/nginx.conf`
  - Đảm bảo tất cả tests pass, hỏi người dùng nếu có vấn đề.

- [ ] 4. Viết `scripts/start.sh` (WSL2/bash)
  - [ ] 4.1 Implement hàm `check_docker()` — kiểm tra Docker daemon
    - Chạy `docker info` và kiểm tra exit code
    - Nếu thất bại: in thông báo lỗi cụ thể "Docker daemon không sẵn sàng. Hãy khởi động Docker Desktop." và `exit 1`
    - _Requirements: 11.2, 11.4_
  - [ ] 4.2 Implement hàm `start_services()` — khởi động services trừ frontend
    - Chạy `docker compose up -d --scale frontend=0`
    - Nếu thất bại: in output lỗi và `exit 1`
    - _Requirements: 11.2_
  - [ ] 4.3 Implement hàm `get_tunnel_url()` — poll log cloudflared, extract URL
    - Poll `docker logs kolla-cloudflared` mỗi 2 giây, timeout 30 giây
    - Dùng `grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com'` để extract URL (dùng `-E` thay `-P` để portable trên Alpine/macOS)
    - Nếu timeout: in "cloudflared không sinh URL sau 30s. Kiểm tra: docker logs kolla-cloudflared" và `exit 1`
    - Return URL qua stdout
    - _Requirements: 2.1, 2.2, 2.3, 2.4_
  - [ ] 4.4 Viết property test cho hàm extract URL (Property 1)
    - **Property 1: URL extraction từ log**
    - **Validates: Requirements 2.1, 2.2**
    - Tạo file `scripts/tests/test_url_extraction.sh` hoặc dùng fast-check/Hypothesis
    - Generator: sinh chuỗi log ngẫu nhiên, một số chứa valid `trycloudflare.com` URL, một số không
    - Assertion: hàm extract trả về URL khi và chỉ khi URL hợp lệ có mặt trong log
  - [ ] 4.5 Implement hàm `update_env_file()` — cập nhật .env với tunnel URL mới
    - Dùng `sed -i` để replace `VITE_API_BASE_URL`, `VITE_WS_URL`, `CORS_ALLOWED_ORIGINS`
    - `VITE_API_BASE_URL=https://<tunnel-url>/api/v1`
    - `VITE_WS_URL=wss://<tunnel-url>/ws` (scheme `https://` → `wss://`)
    - `CORS_ALLOWED_ORIGINS=https://<tunnel-url>`
    - Nếu `.env` không tồn tại: in ".env không tìm thấy. Chạy: cp .env.example .env" và `exit 1`
    - _Requirements: 3.1, 3.2, 7.1, 7.2, 7.3_
  - [ ] 4.6 Viết property test cho hàm update .env (Property 2 và 3)
    - **Property 2: .env update không phá vỡ các biến khác**
    - **Property 3: Derived URLs có scheme đúng**
    - **Validates: Requirements 3.1, 3.2, 7.2, 7.3, 8.4**
    - Generator: sinh `.env` file với tập hợp biến ngẫu nhiên (key=value pairs hợp lệ)
    - Assertion P2: sau khi update, tất cả biến không phải target vẫn giữ nguyên giá trị
    - Assertion P3: `VITE_API_BASE_URL` bắt đầu `https://` và kết thúc `/api/v1`; `VITE_WS_URL` bắt đầu `wss://` và kết thúc `/ws`; `CORS_ALLOWED_ORIGINS` bằng đúng tunnel URL
  - [ ] 4.7 Implement hàm `build_frontend()` — rebuild frontend container
    - Chạy `docker compose up -d --build frontend`
    - Nếu thất bại: in docker compose output và `exit 1`
    - _Requirements: 3.3_
  - [ ] 4.8 Implement hàm `print_success()` và wire toàn bộ script
    - In `✅ Kolla đang chạy tại: https://<tunnel-url>`
    - Gọi các hàm theo thứ tự: `check_docker` → `start_services` → `get_tunnel_url` → `update_env_file` → `build_frontend` → `print_success`
    - Đặt `set -e` ở đầu script
    - _Requirements: 3.4, 11.2, 11.3_

- [ ] 5. Viết `scripts/start.ps1` (Windows PowerShell)
  - [ ] 5.1 Implement `Check-DockerRunning` và `Wait-DockerDaemon` — kiểm tra Docker Desktop
    - `Check-DockerRunning`: kiểm tra process `Docker Desktop` đang chạy
    - `Wait-DockerDaemon`: poll `docker info` với timeout 60 giây, mỗi 5 giây
    - Nếu timeout: in "Docker daemon không sẵn sàng sau 60s" và `exit 1`
    - Script KHÔNG yêu cầu quyền Administrator
    - _Requirements: 11.1, 11.4, 11.5_
  - [ ] 5.2 Implement `Start-Services` — khởi động services trừ frontend
    - Chạy `docker compose up -d --scale frontend=0`
    - Nếu thất bại: in output lỗi và `exit 1`
    - _Requirements: 11.1_
  - [ ] 5.3 Implement `Get-TunnelUrl` — poll log cloudflared, extract URL
    - Poll `docker logs kolla-cloudflared` mỗi 2 giây, timeout 30 giây
    - Dùng regex `https://[a-z0-9-]+\.trycloudflare\.com` để extract URL
    - Nếu timeout: in thông báo lỗi cụ thể và `exit 1`
    - _Requirements: 2.1, 2.2, 2.3, 2.4_
  - [ ] 5.4 Implement `Update-EnvFile` — cập nhật .env với tunnel URL mới
    - Dùng PowerShell regex replace để update `VITE_API_BASE_URL`, `VITE_WS_URL`, `CORS_ALLOWED_ORIGINS`
    - Nếu `.env` không tồn tại: in hướng dẫn và `exit 1`
    - _Requirements: 3.1, 3.2, 7.1, 7.2, 7.3_
  - [ ] 5.5 Viết property test cho PowerShell URL extraction và .env update (Property 1, 2, 3)
    - **Property 1: URL extraction từ log**
    - **Property 2: .env update không phá vỡ các biến khác**
    - **Property 3: Derived URLs có scheme đúng**
    - **Validates: Requirements 2.1, 2.2, 3.1, 3.2, 7.2, 7.3**
    - Viết Pester tests hoặc dùng fast-check qua Node.js để test các helper functions
  - [ ] 5.6 Implement `Build-Frontend` và `Print-Success`, wire toàn bộ script
    - `Build-Frontend`: chạy `docker compose up -d --build frontend`
    - `Print-Success`: in `✅ Kolla đang chạy tại: https://<tunnel-url>`
    - Gọi các hàm theo thứ tự đúng với error handling ở mỗi bước
    - _Requirements: 3.3, 3.4, 11.1, 11.3_

- [ ] 6. Checkpoint — Kiểm tra startup scripts
  - Verify `scripts/start.sh` tồn tại và có execute permission (`chmod +x`)
  - Verify `scripts/start.ps1` tồn tại
  - Chạy dry-run kiểm tra syntax: `bash -n scripts/start.sh`
  - Hỏi người dùng nếu có vấn đề.

- [ ] 7. Cập nhật `.env.example`, README và đánh dấu deprecated `setup-portproxy.ps1`
  - [ ] 7.1 Cập nhật `.env.example`
    - Xóa section "DOMAIN & SSL (Nginx + Certbot)": `DOMAIN`, `DUCKDNS_TOKEN`, `DUCKDNS_SUBDOMAIN`, `CERTBOT_EMAIL`
    - Thêm section "CLOUDFLARE TUNNEL" với `CLOUDFLARE_TUNNEL_TOKEN` (optional), `VITE_API_BASE_URL`, `VITE_WS_URL`, `CORS_ALLOWED_ORIGINS` với placeholder `<cloudflare-domain>` và comment hướng dẫn
    - Cập nhật `VITE_JITSI_URL` và `JITSI_SERVER_URL` default → `https://meet.jit.si`
    - _Requirements: 5.1, 6.6, 7.5_
  - [ ] 7.2 Đánh dấu deprecated `scripts/setup-portproxy.ps1`
    - Thêm comment header `# DEPRECATED: Không còn cần thiết khi dùng Cloudflare Tunnel.` vào đầu file
    - Giữ nguyên nội dung script (chỉ thêm comment, không xóa)
    - _Requirements: 4.1_
  - [ ] 7.3 Cập nhật `README.md` với hướng dẫn Cloudflare Tunnel
    - Thêm section "Khởi động nhanh" với 1 lệnh duy nhất: `.\scripts\start.ps1` (Windows) hoặc `./scripts/start.sh` (WSL2)
    - Ghi rõ `setup-portproxy.ps1` không còn cần thiết, chỉ giữ cho LAN fallback
    - Giải thích lý do Jitsi self-hosted bị thay bằng `meet.jit.si` (UDP/WebRTC không qua tunnel được)
    - Mô tả cách verify tunnel: kiểm tra `docker logs kolla-cloudflared`, truy cập URL từ browser ngoài LAN
    - Mô tả Named Tunnel upgrade path khi có domain
    - _Requirements: 4.2, 9.1, 9.2, 9.3, 9.4, 11.6_

- [ ] 8. Final checkpoint — Đảm bảo tất cả thay đổi nhất quán
  - Verify `docker-compose.yml` không còn reference đến `jitsi`, `certbot`, certbot volumes
  - Verify `docker-compose.yml` vẫn còn port mapping `8443:8443` và `8888:8888` trong nginx service
  - Verify `nginx/nginx.conf` không còn `location /meet`, có `cf_connecting_ip` trên port 8888
  - Verify `scripts/start.sh` tồn tại, có execute permission, và pass `bash -n` syntax check
  - Verify `scripts/start.ps1` tồn tại
  - Verify `scripts/setup-portproxy.ps1` có comment DEPRECATED
  - Verify `.env.example` có section CLOUDFLARE TUNNEL và không còn section DOMAIN & SSL
  - Verify `README.md` có hướng dẫn 1 lệnh khởi động
  - Đảm bảo tất cả kiểm tra pass, hỏi người dùng nếu có câu hỏi.

## Notes

- Tasks đánh dấu `*` là optional và có thể bỏ qua để triển khai nhanh hơn
- Property tests (4.4, 4.6, 5.5) kiểm tra logic thuần túy của URL extraction và .env manipulation
- Cloudflare Quick Tunnel không cần account hay domain — URL tự sinh mỗi lần restart
- Jitsi self-hosted bị loại bỏ vì Cloudflare Tunnel không hỗ trợ UDP/WebRTC (JVB port 10000)
- LAN access vẫn hoạt động qua Nginx port 8443 (HTTPS self-signed) và 8888 (HTTP plain)
- Named Tunnel upgrade path: thêm `CLOUDFLARE_TUNNEL_TOKEN` vào `.env` mà không cần thay đổi kiến trúc
