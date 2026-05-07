# KollaMeeting — Live Test Cases (Dữ liệu thật, thao tác thật)

> **Môi trường**: Hệ thống Docker chạy qua Cloudflare Tunnel (`.\scripts\start.ps1`)
> **Base URL**: lấy từ output của start.ps1 → `https://xxx-yyy-zzz.trycloudflare.com`
> **LAN fallback**: `http://localhost:8888` (qua nginx) hoặc `http://localhost:8080` (backend trực tiếp)
> **Swagger UI**: `<BASE_URL>/api/v1/swagger-ui.html`
>
> **Seed data từ V3 migration:**
> - 1 phòng ban: `Ban Giám đốc` (id=1)
> - 1 phòng họp: `Phòng họp chính` (id=1, capacity=50)
> - 1 tài khoản mặc định: `admin / admin` (role: ADMIN)
>
> **Cần tạo thêm trước khi test:**
> - User `secretary1` (role: SECRETARY) — tạo qua Admin panel
> - User `user1` (role: USER) — tạo qua Admin panel

---

## SETUP: Dữ liệu nền cần chuẩn bị

| Bước | Hành động | Kết quả mong đợi |
|------|-----------|-----------------|
| S-01 | Chạy `.\scripts\start.ps1` | In ra URL tunnel, tất cả containers healthy |
| S-02 | Truy cập `<BASE_URL>` trên browser | Trang Login hiện ra |
| S-03 | Login `admin / admin` → vào Admin panel → Tạo user `secretary1` (SECRETARY, email: sec1@kolla.local) | Tạo thành công, hiện trong danh sách |
| S-04 | Tạo user `user1` (USER, email: user1@kolla.local) | Tạo thành công |
| S-05 | Kiểm tra Swagger: `<BASE_URL>/api/v1/swagger-ui.html` | Swagger UI load |

---

## PHASE 1: Authentication

### TC-01: Đăng nhập hệ thống

| ID | Hành động | Dữ liệu đầu vào | Kết quả mong đợi | Lớp |
|----|-----------|-----------------|-----------------|-----|
| TC-01-01 | Đăng nhập hợp lệ | `admin / admin` | Redirect đến Dashboard, header "Xin chào System Administrator" | Browser |
| TC-01-02 | Đăng nhập sai password | `admin / wrongpass` | Hiện lỗi "Invalid username or password", không redirect | Browser |
| TC-01-03 | Đăng nhập username không tồn tại | `nobody / admin` | Hiện lỗi tương tự | Browser |
| TC-01-04 | Login API trực tiếp (Swagger) | `POST /auth/login {"username":"admin","password":"admin"}` | HTTP 200, body chứa `accessToken`, `refreshToken`, `tokenType:"Bearer"`, `user.role:"ADMIN"` | API |
| TC-01-05 | Refresh token | `POST /auth/refresh` với refreshToken từ TC-01-04 | HTTP 200, `accessToken` mới (khác token cũ) | API |
| TC-01-06 | Đăng xuất → vào lại URL protected | Click Logout → truy cập `/meetings` | Redirect về Login page | Browser |
| TC-01-07 | Gọi API không có token | `GET /api/v1/meetings` không có header | HTTP 401 | API |

---

## PHASE 2: Quản lý User (Admin)

### TC-02: Tạo và quản lý người dùng

| ID | Hành động | Dữ liệu đầu vào | Kết quả mong đợi |
|----|-----------|-----------------|-----------------|
| TC-02-01 | Admin tạo SECRETARY | Login admin → Admin panel → Tạo user `secretary1`, role SECRETARY | Tài khoản tạo thành công, login được |
| TC-02-02 | Admin tạo USER | Tạo user `user1`, role USER | Tạo thành công |
| TC-02-03 | Login với tài khoản secretary1 | `secretary1 / [pass đã đặt]` | Vào Dashboard, menu có "Tạo cuộc họp" |
| TC-02-04 | Login với tài khoản user1 | `user1 / [pass đã đặt]` | Vào Dashboard, không có menu tạo cuộc họp |
| TC-02-05 | Reset password | Admin đổi password user1 | Login lại với password mới thành công |

---

## PHASE 3: Tạo và quản lý Cuộc họp

### TC-03: Meeting CRUD với dữ liệu thật

> **Precondition**: Đang login là `secretary1`

| ID | Hành động | Dữ liệu đầu vào | Kết quả mong đợi |
|----|-----------|-----------------|-----------------|
| TC-03-01 | Tạo cuộc họp hợp lệ | Title: "Họp Test Live", Room: Phòng họp chính, Start: [now+5min], End: [now+60min], Host: secretary1, Secretary: secretary1 | Tạo thành công, status=SCHEDULED, code 20 ký tự |
| TC-03-02 | Tạo meeting bị conflict | Tạo meeting 2 cùng phòng, cùng giờ với TC-03-01 | HTTP 409, lỗi "Phòng họp đã được đặt" |
| TC-03-03 | End time ≤ start time | Start=14:00, End=13:00 | HTTP 400, "End time must be after start time" |
| TC-03-04 | Xem chi tiết meeting | Click vào meeting vừa tạo | Hiển thị đầy đủ: title, room, host, status=SCHEDULED |
| TC-03-05 | USER không thể tạo meeting | Login user1 → thử tạo meeting | HTTP 403 |
| TC-03-06 | Thêm user1 vào meeting | Secretary1 → Meeting detail → Add member → user1 | user1 xuất hiện trong danh sách thành viên |
| TC-03-07 | user1 nhận notification | Login user1, xem notifications | Có thông báo "You have been added to a meeting" |

---

## PHASE 4: Meeting Lifecycle (Activate → Join → End)

### TC-04: Vòng đời cuộc họp

> **Precondition**: Meeting TC-03-01 đang ở trạng thái SCHEDULED

| ID | Hành động | Dữ liệu đầu vào | Kết quả mong đợi |
|----|-----------|-----------------|-----------------|
| TC-04-01 | Activate quá sớm (>30 phút trước) | Thử activate meeting có startTime là ngày mai | HTTP 400 "can only be activated within 30 minutes" |
| TC-04-02 | Activate meeting hợp lệ | Secretary1 click "Kích hoạt" với meeting startTime = now+5min | Status → ACTIVE, tất cả thành viên nhận notification "Meeting started" |
| TC-04-03 | Join meeting | Secretary1 click "Tham gia" | Vào phòng họp (Jitsi load), attendance log tạo |
| TC-04-04 | Join meeting bằng user1 | Mở tab khác, login user1, vào cùng meeting | user1 vào được phòng (là member) |
| TC-04-05 | USER không phải member thử join | Tạo user2 chưa được thêm vào meeting, thử join | HTTP 403 hoặc bị chặn |
| TC-04-06 | End meeting | Secretary1 click "Kết thúc" | Status → ENDED, attendance log đóng, minutes tự động tạo |
| TC-04-07 | Sau khi ENDED, thử join | Reload page, click Join | HTTP 400 "Meeting is not ACTIVE" |

---

## PHASE 5: Phiên âm (Transcription) — **QUAN TRỌNG: cần audio thật**

### TC-05: Speech-to-Text với giọng nói thật

> **Precondition**:
> - Meeting đang ACTIVE
> - Điện thoại đang phát talk show gần microphone máy tính
> - Transcription priority đặt thành HIGH_PRIORITY

| ID | Hành động | Dữ liệu đầu vào | Kết quả mong đợi |
|----|-----------|-----------------|-----------------|
| TC-05-01 | Đặt priority HIGH_PRIORITY | Admin/Secretary PUT `/meetings/{id}/priority` `{"priority":"HIGH_PRIORITY"}` | HTTP 200 |
| TC-05-02 | Vào phòng họp, mở mic | Click nút mic trong Jitsi | Mic active (icon sáng) |
| TC-05-03 | Nói/phát audio 30 giây | Nói hoặc để điện thoại phát audio gần mic | Gipformer nhận audio, xử lý |
| TC-05-04 | Quan sát transcription realtime | Xem panel phiên âm trong MeetingDetailPage | Các đoạn text xuất hiện theo thời gian thực (HIGH_PRIORITY broadcast qua WebSocket) |
| TC-05-05 | Kiểm tra segments qua API | `GET /api/v1/meetings/{id}/transcription` | List segments với text, speakerName, confidence |
| TC-05-06 | Kiểm tra job trong Redis | `docker exec kolla-redis redis-cli KEYS "transcription:*"` | Thấy job entries |
| TC-05-07 | Test với NORMAL_PRIORITY | Đổi priority → NORMAL, nói thêm 30s | Segments được lưu DB nhưng KHÔNG broadcast realtime (kiểm tra bằng không thấy text mới xuất hiện ngay) |
| TC-05-08 | Kiểm tra Gipformer log | `docker logs kolla-gipformer --tail=50` | Thấy "Processing job", "Callback sent" messages |

---

## PHASE 6: Speaking Permission & Raise Hand (MEETING_MODE)

### TC-06: Luồng phát biểu có kiểm soát

> **Precondition**: Meeting ACTIVE, có secretary1 (host) và user1 (participant)

| ID | Hành động | Dữ liệu đầu vào | Kết quả mong đợi |
|----|-----------|-----------------|-----------------|
| TC-06-01 | Chuyển sang FREE_MODE (default) | Kiểm tra mode hiện tại | Mode = FREE_MODE, không cần xin phép |
| TC-06-02 | Switch sang MEETING_MODE | Host click "Chuyển sang Meeting Mode" | Mode = MEETING_MODE, broadcast WebSocket `MODE_CHANGED` |
| TC-06-03 | user1 giơ tay | user1 click "Raise Hand" | Request tạo (status=PENDING), Host thấy notification |
| TC-06-04 | Host xem danh sách raise hand | `GET /meetings/{id}/raise-hand` | Thấy request của user1 theo thứ tự thời gian |
| TC-06-05 | Host grant permission cho user1 | `POST /meetings/{id}/speaking-permission/{userId}` | Speaking permission tạo, `speakerTurnId` UUID mới, WebSocket broadcast `SPEAKING_PERMISSION_GRANTED` |
| TC-06-06 | user1 nhận permission → mic mở | Quan sát UI của user1 | Mic indicator active, audio capture bắt đầu |
| TC-06-07 | user1 phát biểu (nói vào mic) | Audio thật từ điện thoại hoặc nói trực tiếp | Transcription với speakerName=user1 được tạo |
| TC-06-08 | Grant idempotent | Grant lại cho cùng user1 | HTTP 200, trả về permission cũ (không tạo mới) |
| TC-06-09 | Grant cho user khác khi user1 đang nói | Grant cho admin | user1 bị revoke (`SPEAKING_PERMISSION_REVOKED`), admin được grant |
| TC-06-10 | Host revoke permission | `DELETE /meetings/{id}/speaking-permission` | Permission revoked, broadcast, mic user đóng |
| TC-06-11 | user1 hạ tay | user1 click "Lower Hand" | Request xóa |
| TC-06-12 | user1 rời meeting khi đang có permission | user1 click Leave trong Jitsi | Permission auto-revoke (`PARTICIPANT_LEFT`) |

---

## PHASE 7: WebSocket Events (Realtime)

### TC-07: Kiểm tra sự kiện realtime

> **Phương pháp**: Mở 2 tab browser (secretary1 + user1), quan sát events đồng thời

| ID | Hành động | Quan sát | Kết quả mong đợi |
|----|-----------|----------|-----------------|
| TC-07-01 | secretary1 activate meeting | Tab user1 đang mở Dashboard | user1 nhận thông báo "Meeting started" trong notification panel |
| TC-07-02 | Trong phòng họp: switch mode | Cả 2 tab trong room | Cả 2 tab cập nhật mode mới ngay lập tức |
| TC-07-03 | user1 raise hand | Tab secretary1 | Hiện badge/alert raise hand request |
| TC-07-04 | Grant speaking permission | Tab user1 | UI thay đổi (mic button active/green) |
| TC-07-05 | HIGH_PRIORITY transcription segment | Tab secretary1 đang xem meeting detail | Text phiên âm xuất hiện không cần reload |
| TC-07-06 | Ngắt mạng 10 giây rồi kết nối lại | Tắt/bật WiFi | STOMP client tự reconnect (backoff 1s→2s→4s...), `isConnected` trở về true |
| TC-07-07 | Heartbeat giữ attendance | Chờ 15 giây trong phòng họp | Redis key attendance vẫn alive (kiểm tra qua `docker exec kolla-redis redis-cli KEYS "*attendance*"`) |

---

## PHASE 8: Biên bản (Minutes)

### TC-08: Quy trình Minutes DRAFT → HOST_CONFIRMED → SECRETARY_CONFIRMED

> **Precondition**: Meeting đã ENDED (từ TC-04-06), có transcription segments

| ID | Hành động | Dữ liệu đầu vào | Kết quả mong đợi |
|----|-----------|-----------------|-----------------|
| TC-08-01 | Kiểm tra minutes tự động tạo sau khi meeting kết thúc | `GET /api/v1/meetings/{id}/minutes` | Minutes status=DRAFT, draftPdfPath có giá trị |
| TC-08-02 | Tải draft PDF | `GET /api/v1/meetings/{id}/minutes/download?version=draft` | File PDF download, mở ra thấy nội dung phiên âm với tên speaker |
| TC-08-03 | Kiểm tra nội dung PDF | Mở file PDF | Có tiêu đề "MEETING MINUTES — DRAFT", tên meeting, host, secretary, các đoạn phiên âm theo speaker |
| TC-08-04 | USER thử confirm minutes | user1 gọi `POST /meetings/{id}/minutes/confirm` | HTTP 403 "Only the meeting Host or an ADMIN may confirm" |
| TC-08-05 | Host confirm minutes | secretary1 (là host) confirm | Status → HOST_CONFIRMED, confirmedPdfPath tạo, Secretary nhận notification "Minutes confirmed" |
| TC-08-06 | Tải confirmed PDF | `GET /download?version=confirmed` | PDF có trang "CONFIRMATION STAMP" với tên host, timestamp, SHA-256 hash |
| TC-08-07 | Secretary edit và publish | secretary1 PUT `/meetings/{id}/minutes/edit` với contentHtml | Status → SECRETARY_CONFIRMED, secretaryPdfPath tạo, broadcast `MINUTES_PUBLISHED` |
| TC-08-08 | Tải secretary PDF | `GET /download?version=secretary` | PDF "MEETING MINUTES — FINAL" với nội dung đã edit |
| TC-08-09 | Idempotency: end meeting lần 2 rồi check minutes | Meeting đã ENDED, end lại (nếu được) | Minutes không tạo lại (idempotency check: `existsByMeetingId`) |

---

## PHASE 9: Tìm kiếm (Search)

### TC-09: Full-text search

| ID | Hành động | Dữ liệu đầu vào | Kết quả mong đợi |
|----|-----------|-----------------|-----------------|
| TC-09-01 | Tìm meeting theo title | Search "Họp Test" | Trả về meeting TC-03-01 |
| TC-09-02 | Tìm trong transcription | Search một từ đã được phiên âm (ví dụ: "xin chào") | Trả về meeting có segment chứa từ đó |
| TC-09-03 | Search keyword rỗng | `GET /search?q=` | HTTP 400 |
| TC-09-04 | Search không có kết quả | Search "xyzxyzxyz" | Kết quả rỗng, không lỗi |
| TC-09-05 | Phân trang search | Search khi có nhiều kết quả, `?page=0&size=5` | Kết quả đúng trang |

---

## PHASE 10: Ghi âm (Recording)

### TC-10: Start/Stop Recording

| ID | Hành động | Dữ liệu đầu vào | Kết quả mong đợi |
|----|-----------|-----------------|-----------------|
| TC-10-01 | Bắt đầu ghi âm | `POST /meetings/{id}/recordings/start` khi meeting ACTIVE | HTTP 200, recording status=RECORDING |
| TC-10-02 | Dừng ghi âm | `POST /recordings/{id}/stop` | status=COMPLETED, filePath có giá trị |
| TC-10-03 | Tải file ghi âm | `GET /recordings/{id}/download` | File audio download thành công |
| TC-10-04 | Ghi âm khi meeting không ACTIVE | Thử khi meeting ENDED | HTTP 400 |

---

## PHASE 11: Admin — Quản lý Storage

### TC-11: Storage management

| ID | Hành động | Dữ liệu đầu vào | Kết quả mong đợi |
|----|-----------|-----------------|-----------------|
| TC-11-01 | Xem storage stats | `GET /api/v1/admin/storage/stats` (admin token) | JSON có tổng size, file count |
| TC-11-02 | USER thử xem storage | Gọi với user1 token | HTTP 403 |

---

## PHASE 12: Auto-end Timeout (Waiting Timeout)

### TC-12: Meeting tự động kết thúc khi không có Host/Secretary

> **Lưu ý**: Timeout = 10 phút. Test bằng cách quan sát Redis key.

| ID | Hành động | Quan sát | Kết quả mong đợi |
|----|-----------|----------|-----------------|
| TC-12-01 | Host rời meeting | secretary1 click Leave trong Jitsi | Redis key `meeting:{id}:waiting_timeout` xuất hiện với TTL 600s |
| TC-12-02 | Host quay lại trong vòng 10 phút | secretary1 join lại | Redis key bị xóa, `waiting_timeout_at` = null |
| TC-12-03 | Không ai quay lại sau 10 phút | Chờ (hoặc giảm timeout trong code để test nhanh) | Meeting tự động ENDED, minutes tự tạo |

---

## PHASE 13: Kiểm tra Container Health

### TC-13: Docker infrastructure

| ID | Lệnh | Kết quả mong đợi |
|----|------|-----------------|
| TC-13-01 | `docker ps` | Tất cả 6 containers (mysql, redis, backend, gipformer, frontend, nginx, cloudflared) đang running |
| TC-13-02 | `docker inspect kolla-backend --format={{.State.Health.Status}}` | `healthy` |
| TC-13-03 | `docker logs kolla-backend --tail=20` | Không có ERROR logs |
| TC-13-04 | `docker logs kolla-gipformer --tail=20` | Thấy "Server ready", không crash |
| TC-13-05 | `docker exec kolla-redis redis-cli ping` | `PONG` |
| TC-13-06 | Swagger UI | `<BASE_URL>/api/v1/swagger-ui.html` | Load thành công, hiện đủ endpoints |

---

## Thứ tự thực hiện test (recommended)

```
SETUP (S-01 đến S-05)
  ↓
TC-01 (Auth) — xác nhận login/logout
  ↓
TC-02 (User management) — tạo secretary1, user1
  ↓
TC-03 (Meeting CRUD) — tạo meeting, thêm member
  ↓
TC-04 (Lifecycle) — activate, join, end
  ↓
TC-05 (Transcription) — BẬT ĐIỆN THOẠI, test speech-to-text
  ↓
TC-06 (Speaking permission) — raise hand, grant, revoke
  ↓
TC-07 (WebSocket realtime) — 2 tab, quan sát events
  ↓
TC-08 (Minutes) — download PDF, confirm, publish
  ↓
TC-09 (Search) — tìm kiếm theo text đã phiên âm
  ↓
TC-10 (Recording) — ghi âm
  ↓
TC-11 (Admin) — storage stats
  ↓
TC-13 (Health check) — kiểm tra containers
```

---

## Kết quả test

| Phase | TC Count | Pass | Fail | Skip | Ghi chú |
|-------|----------|------|------|------|---------|
| SETUP | 5 | | | | |
| Auth | 7 | | | | |
| User Mgmt | 5 | | | | |
| Meeting CRUD | 7 | | | | |
| Lifecycle | 7 | | | | |
| Transcription | 8 | | | | ⭐ Cần audio thật |
| Speaking Perm | 12 | | | | |
| WebSocket | 7 | | | | |
| Minutes | 9 | | | | |
| Search | 5 | | | | |
| Recording | 4 | | | | |
| Admin Storage | 2 | | | | |
| Health Check | 6 | | | | |
| **Tổng** | **84** | | | | |
