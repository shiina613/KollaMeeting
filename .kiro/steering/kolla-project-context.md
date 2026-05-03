---
inclusion: auto
description: Project context cho Kolla Meeting — stack, ports, Cloudflare Tunnel setup, Nginx routing rules, startup script flow và các quy tắc quan trọng.
---

# Kolla Meeting — Project Context

## Stack & Ports

| Service | Tech | Port |
|---------|------|------|
| frontend | React 18 + Vite + Tailwind | 3000 |
| backend | Spring Boot 3.2 (Java) | 8080 |
| gipformer | Python FastAPI + sherpa-onnx | 8000 |
| mysql | MySQL 8.0 | 3306 |
| redis | Redis 7 | 6379 |
| nginx | Nginx reverse proxy | 8443 (HTTPS), 8888 (HTTP plain) |
| cloudflared | Cloudflare Quick Tunnel | — (outbound only) |

## Folder Structure

```
/
├── backend/          Spring Boot app (Maven)
├── frontend/         React + Vite app
├── gipformer/        Python FastAPI transcription service
├── nginx/            Nginx config + Dockerfile + entrypoint.sh
├── scripts/          Startup & utility scripts
│   ├── start.ps1     Windows one-command startup
│   ├── start.sh      WSL2/bash one-command startup
│   └── setup-portproxy.ps1  [DEPRECATED]
├── docker-compose.yml
├── .env              (gitignored, copy from .env.example)
└── .env.example
```

## Key Environment Variables

| Variable | Updated by | Purpose |
|----------|-----------|---------|
| `VITE_API_BASE_URL` | startup script | Backend API URL seen by browser |
| `VITE_WS_URL` | startup script | WebSocket URL (wss://) seen by browser |
| `CORS_ALLOWED_ORIGINS` | startup script | Allowed origins for Spring Boot CORS |
| `CLOUDFLARE_TUNNEL_TOKEN` | manual | Named Tunnel upgrade (leave empty for Quick Tunnel) |
| `VITE_JITSI_URL` | manual | Jitsi public URL (default: https://meet.jit.si) |
| `JITSI_SERVER_URL` | manual | Jitsi URL for backend JWT generation |

## Cloudflare Tunnel — Quick Tunnel Mode

- `cloudflared` runs as Docker service: `tunnel --no-autoupdate --url http://nginx:8888`
- Generates a new `https://xxx-yyy-zzz.trycloudflare.com` URL on every start
- Startup scripts auto-extract URL from logs and rebuild frontend
- No account, no domain, no token required

### Derived URL Rules

```
Tunnel URL:           https://abc-def-ghi.trycloudflare.com
VITE_API_BASE_URL  =  https://abc-def-ghi.trycloudflare.com/api/v1
VITE_WS_URL        =  wss://abc-def-ghi.trycloudflare.com/ws
CORS_ALLOWED_ORIGINS = https://abc-def-ghi.trycloudflare.com
```

## Nginx Routing (port 8888 — tunnel entry point)

```
/ws   → backend:8080  (WebSocket upgrade, proxy_read_timeout 86400s)
/api  → backend:8080  (REST API, client_max_body_size 512m)
/     → frontend:3000 (catch-all)
```

All locations on port 8888 must set:
- `X-Real-IP $http_cf_connecting_ip` (real client IP from Cloudflare)
- `X-Forwarded-Proto https` (Cloudflare already terminated SSL)

## Startup Script Flow (start.sh / start.ps1)

1. Check Docker daemon ready (timeout 60s)
2. `docker compose up -d --scale frontend=0`
3. Poll `docker logs kolla-cloudflared` for tunnel URL (timeout 30s)
4. Update `.env`: VITE_API_BASE_URL, VITE_WS_URL, CORS_ALLOWED_ORIGINS
5. `docker compose up -d --build frontend`
6. Print `✅ Kolla đang chạy tại: <tunnel-url>`

## Important Rules

- All datetimes: UTC+7 (Asia/Ho_Chi_Minh)
- API responses: always wrap in `ApiResponse<T>`
- Never store JWT in localStorage (Zustand memory only)
- File storage: local filesystem at `/app/storage/`
- Gipformer: Vietnamese ASR only, WAV 16kHz mono, CUDA with ONNX CPU fallback
- Jitsi: use `meet.jit.si` public (self-hosted removed — UDP/WebRTC incompatible with Cloudflare Tunnel)
- grep for URL extraction: use `-oE` not `-oP` (portable, works on Alpine/macOS)
