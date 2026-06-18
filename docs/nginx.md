# Nginx trong KollaMeeting

## 1. Bản chất

Nginx trong KollaMeeting là reverse proxy đứng trước các container. Người dùng chỉ truy cập một cổng/host bên ngoài, còn Nginx định tuyến request vào đúng service nội bộ.

Nginx không xử lý nghiệp vụ họp, không chạy ASR, không quản lý database. Vai trò của nó là cổng vào:

- Frontend React.
- Backend Spring Boot API.
- STOMP WebSocket.
- Raw audio WebSocket.
- HTTPS/self-signed SSL hoặc HTTP fallback.

## 2. File liên quan

- `nginx/nginx.conf`
- `nginx/nginx-init.conf`
- `nginx/Dockerfile`
- `nginx/entrypoint.sh`
- `docker-compose.yml`

Trong `docker-compose.yml`, service `nginx` build từ thư mục `./nginx`, mount template config:

```text
./nginx/nginx.conf:/etc/nginx/templates/default.conf.template:ro
```

`entrypoint.sh` dùng `envsubst` để thay `${DOMAIN}` vào config trước khi Nginx chạy.

## 3. Các cổng của Nginx

Config hiện tại có 3 server block:

```text
8443 -> HTTPS main entry point
8080 -> HTTP redirect sang HTTPS 8443
8888 -> HTTP fallback cho LAN/tunnel
```

Trong `docker-compose.yml`, Cloudflared dùng:

```text
cloudflared -> http://nginx:8888
```

Vì vậy khi chạy tunnel, request từ ngoài có thể đi vào Nginx qua cổng 8888 trong mạng Docker.

## 4. Routing chính

Trong `nginx/nginx.conf`, các location chính:

```text
/          -> frontend:3000
/api       -> backend:8080
/ws        -> backend:8080/api/v1/ws
/ws/audio  -> backend:8080/api/v1/ws/audio
```

Ý nghĩa:

- `/` trả frontend.
- `/api` chuyển REST API về Spring Boot.
- `/ws` chuyển STOMP WebSocket.
- `/ws/audio` chuyển audio binary WebSocket.

Thứ tự location quan trọng: `/ws/audio` phải nằm riêng trước `/ws` để audio stream không bị route nhầm vào STOMP endpoint.

## 5. Vì sao `/ws/audio` và `/ws` tách riêng?

KollaMeeting có hai kênh WebSocket khác nhau:

```text
/ws       -> STOMP event realtime
/ws/audio -> binary audio PCM
```

`/ws` dùng cho event JSON như meeting update, notification, heartbeat.

`/ws/audio` dùng để frontend gửi raw PCM Int16 LE 16 kHz mono. Backend `AudioStreamHandler` nhận binary frame, cắt chunk, lưu WAV và tạo ASR job.

Nếu gộp hai loại này, server sẽ khó phân biệt STOMP frame và audio binary. Tách endpoint làm logic rõ ràng và giảm overhead cho audio stream.

## 6. WebSocket upgrade

Cả `/ws` và `/ws/audio` đều cần cấu hình:

```nginx
proxy_http_version 1.1;
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
proxy_read_timeout 86400s;
proxy_send_timeout 86400s;
```

Nếu thiếu `Upgrade` và `Connection`, trình duyệt không thể nâng cấp HTTP connection thành WebSocket. Nếu timeout quá ngắn, kết nối họp lâu có thể bị cắt giữa chừng.

## 7. Proxy API

Location `/api`:

```nginx
location /api {
    proxy_pass http://backend:8080;
    client_max_body_size 512m;
}
```

Backend Spring Boot có context path `/api/v1`, nên request public dạng:

```text
/api/v1/meetings
/api/v1/transcription/callback
/api/v1/meetings/{id}/minutes/confirm
```

Nginx giữ nguyên path và chuyển vào backend.

`client_max_body_size 512m` quan trọng cho các request upload/download file lớn, ví dụ tài liệu, audio hoặc biên bản.

## 8. Proxy frontend

Location `/`:

```nginx
location / {
    proxy_pass http://frontend:3000;
}
```

Trong môi trường Docker hiện tại, frontend là một service riêng. Nginx không trực tiếp serve file build từ disk, mà proxy sang frontend container.

Điều này khác với mô hình production thường gặp, nơi Nginx serve static files từ `/usr/share/nginx/html`. Ở repo này, Nginx đóng vai trò gateway, còn frontend container tự phục vụ app.

## 9. HTTPS và self-signed SSL

Server block 8443 dùng:

```nginx
ssl_certificate     /etc/letsencrypt/live/${DOMAIN}/fullchain.pem;
ssl_certificate_key /etc/letsencrypt/live/${DOMAIN}/privkey.pem;
```

`entrypoint.sh` có logic chuẩn bị certificate/self-signed certificate tùy môi trường. HTTP 8080 redirect sang:

```text
https://$host:8443$request_uri
```

Vì WebSocket đi theo scheme của trang:

- Trang HTTP dùng `ws://`.
- Trang HTTPS dùng `wss://`.

Nginx là nơi quyết định TLS ở lớp ngoài, backend và frontend phía trong vẫn giao tiếp qua HTTP trong Docker network.

## 10. Header forwarded

Nginx set các header:

```nginx
Host
X-Real-IP
X-Forwarded-For
X-Forwarded-Proto
```

Các header này giúp backend biết request gốc đến từ host/IP/protocol nào. Ở cổng 8888, config dùng `X-Real-IP $http_cf_connecting_ip` để lấy IP từ Cloudflare Tunnel nếu có.

## 11. Logic request end-to-end

Ví dụ người dùng mở app:

```text
Browser -> Nginx / -> frontend:3000
```

Gọi API:

```text
Browser -> Nginx /api/v1/meetings -> backend:8080/api/v1/meetings
```

STOMP realtime:

```text
Browser -> Nginx /ws -> backend:8080/api/v1/ws -> WebSocketConfig
```

Audio ASR:

```text
Browser -> Nginx /ws/audio -> backend:8080/api/v1/ws/audio -> AudioStreamHandler
```

## 12. Điểm cần nói khi bảo vệ

Nginx là gateway triển khai. Nó gom frontend, backend API và hai WebSocket endpoint vào một địa chỉ truy cập thống nhất. Cấu hình `/ws` và `/ws/audio` là điểm quan trọng vì hệ thống vừa có realtime event STOMP vừa có audio binary stream cho ASR. Nginx cũng xử lý TLS/redirect, header forwarded và giới hạn upload file.
