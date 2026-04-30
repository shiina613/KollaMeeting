# Kolla Meeting Rebuild — Project Context

## Overview

Hệ thống họp trực tuyến nội bộ với AI transcription tiếng Việt. Hỗ trợ 2–30 người/meeting, 5–7 meetings đồng thời.

## Spec Files

- Requirements: `.kiro/specs/kolla-meeting-rebuild/requirements.md`
- Design: `.kiro/specs/kolla-meeting-rebuild/design.md`
- Tasks: `.kiro/specs/kolla-meeting-rebuild/tasks.md`

## Tech Stack

| Service | Technology | Port |
|---------|-----------|------|
| Frontend | React 18 + Vite + Tailwind CSS | 3000 |
| Backend | Spring Boot 3.2 (Java 17) | 8080 |
| Gipformer | Python FastAPI + sherpa-onnx | 8000 |
| MySQL | MySQL 8 | 3306 |
| Redis | Redis 7 | 6379 |
| Jitsi | Jitsi Meet self-hosted | 8443 |

## Project Structure

```
KollaMeeting/
├── .kiro/
│   ├── specs/kolla-meeting-rebuild/   # Spec files
│   └── skills/                        # This file and others
├── backend/                           # Spring Boot project
├── frontend/                          # React + Vite project
├── gipformer/                         # Python FastAPI service (cloned from GitHub)
└── docker-compose.yml
```

## Host Environment (Windows 11)

| Tool | Version |
|------|---------|
| Docker | 29.4.0 |
| Docker Compose | v5.1.1 |
| Python | 3.12.10 |
| Java | OpenJDK 17.0.18 |
| Node.js | 24.14.0 |
| GPU | GTX 1650 Ti, 4GB VRAM (dev) → RTX 5060 Ti 16GB (prod) |

**Gipformer:** PyTorch CUDA mode. Dev test 1 meeting trên 1650 Ti, production 5-7 meetings trên 5060 Ti.

## Deployment

**Thiết kế mở — truy cập từ internet:**

```
Internet → DuckDNS domain → Router (port forward) → Nginx → Services
```

- **Domain:** DuckDNS miễn phí (e.g., `kolla.duckdns.org`) hoặc domain trả phí
- **SSL:** Let's Encrypt (certbot) — miễn phí, tự động renew
- **Nginx:** Reverse proxy + SSL termination
  - `/` → frontend:3000
  - `/api` → backend:8080
  - `/ws` → backend:8080 (WebSocket upgrade)
  - `meet.{domain}` → jitsi:8443
- **Router ports cần mở:** 80 (HTTP), 443 (HTTPS), 10000/udp (Jitsi WebRTC)
- **DDNS:** DuckDNS client tự cập nhật IP khi IP nhà thay đổi

**Docker Compose thêm services:** `nginx`, `certbot`

**Chi phí:** 0đ với DuckDNS + Let's Encrypt

## Key Domain Concepts

- **Meeting Lifecycle**: SCHEDULED → ACTIVE → ENDED
- **Meeting Modes**: FREE_MODE (all mics open) | MEETING_MODE (raise hand, 1 speaker)
- **Roles (system)**: ADMIN | SECRETARY | USER
- **Host**: SECRETARY/ADMIN assigned when creating meeting — controls meeting room
- **Secretary**: Backup host, edits minutes after meeting
- **Speaking_Permission**: Exclusive right to speak in MEETING_MODE, granted by Host
- **Transcription Priority**: HIGH_PRIORITY (1 meeting max, near-realtime display) | NORMAL_PRIORITY (save to DB only)

## Timezone

All datetimes: **UTC+7 (Asia/Ho_Chi_Minh)** — both server storage and API responses.

## File Storage Layout

```
/app/storage/
├── recordings/{meeting_id}/
├── documents/{meeting_id}/
├── audio_chunks/{meeting_id}/{speaker_turn_id}/
└── minutes/{meeting_id}/
```

## Service Communication

```
Frontend ──REST/WebSocket──► Spring Boot ──HTTP──► Gipformer FastAPI
                                    │                      │
                                  MySQL               Redis Queue
                                  Redis          (Sorted Set priority)
```

## Environment Variables (key ones)

**Backend:**
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`, `JWT_EXPIRATION`
- `FILE_STORAGE_PATH=/app/storage`
- `GIPFORMER_URL=http://gipformer:8000`
- `JITSI_URL=https://jitsi.kolla.local`
- `SPRING_REDIS_HOST=redis`, `SPRING_REDIS_PORT=6379`

**Frontend:**
- `VITE_API_BASE_URL=https://{DOMAIN}/api/v1`  (qua Nginx khi deploy; `http://localhost:8080/api/v1` khi dev local)
- `VITE_WS_URL=https://{DOMAIN}/ws`  (qua Nginx khi deploy; `http://localhost:8080/ws` khi dev local)
- `VITE_JITSI_URL=https://meet.{DOMAIN}`

**Gipformer:**
- `PORT=8000`
- `REDIS_HOST=redis`, `REDIS_PORT=6379`
- `BACKEND_CALLBACK_URL=http://backend:8080/api/v1/transcription/callback`
- `MODEL_QUANTIZE=int8`
