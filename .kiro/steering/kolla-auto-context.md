---
inclusion: auto
description: Auto-loaded context for Kolla Meeting Rebuild — skill selection rules, tech stack reference, and key conventions.
---

# Kolla Meeting Rebuild — Auto Context

Đây là project rebuild hệ thống họp trực tuyến Kolla. Khi làm việc với project này, hãy tự động xác định và load skill phù hợp theo rules bên dưới.

## Skill Selection Rules

Khi nhận được task hoặc câu hỏi, tự động xác định skill cần thiết và thông báo cho user:

| Nếu task liên quan đến... | Load skill |
|---|---|
| Spring Boot, Java, Controller, Service, JPA, JWT, Security, Redis, Flyway, MySQL | `#kolla-backend-conventions` |
| React, TypeScript, Vite, Tailwind, Zustand, Axios, WebSocket, Jitsi, hooks, components | `#kolla-frontend-conventions` |
| Python, FastAPI, Gipformer, sherpa-onnx, VAD, audio, transcription, Redis queue worker | `#kolla-gipformer-context` |
| Architecture, ports, folder structure, domain concepts, env vars, Docker | `#kolla-project-context` |
| Task liên quan đến nhiều service | Load tất cả skills liên quan |

**Khi bắt đầu implement bất kỳ task nào**, hãy:
1. Đọc task description
2. Xác định skill(s) cần thiết theo bảng trên
3. Thông báo: *"Tôi sẽ dùng skill [X] cho task này"* và tự load nội dung skill đó vào context

## Quick Reference

**Tech stack:** React 18 + Vite + Tailwind | Spring Boot 3.2 | Python FastAPI | MySQL | Redis | Jitsi Meet

**Ports:** frontend:3000 | backend:8080 | gipformer:8000 | mysql:3306 | redis:6379 | jitsi:8443

**Spec files:**
- `.kiro/specs/kolla-meeting-rebuild/requirements.md`
- `.kiro/specs/kolla-meeting-rebuild/design.md`
- `.kiro/specs/kolla-meeting-rebuild/tasks.md`

**Key rules:**
- All datetimes: UTC+7 (Asia/Ho_Chi_Minh)
- API responses: always wrap in `ApiResponse<T>`
- Never store JWT in localStorage (Zustand memory only)
- File storage: local filesystem at `/app/storage/`
- Gipformer: Vietnamese ASR only, WAV 16kHz mono input, PyTorch CUDA (GPU) với auto-fallback ONNX int8 (CPU) khi OOM. `DEVICE=cuda` env var.
