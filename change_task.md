# Change Tasks — KollaMeeting

## [ ] TASK-001: Thu hồi quyền điều hành meeting của ADMIN

**Ngày ghi nhận:** 2026-05-07  
**Trạng thái:** ⏳ Chờ thực hiện  

### Mô tả

ADMIN hiện đang có quyền can thiệp trực tiếp vào việc điều hành meeting. Cần loại bỏ các quyền sau khỏi role ADMIN, chỉ để lại cho **Host** và **Secretary** (theo đúng nghiệp vụ):

| Endpoint | Mô tả | Hiện tại | Sau thay đổi |
|----------|-------|----------|--------------|
| `POST /meetings/{id}/activate` | Kích hoạt cuộc họp | Host + ADMIN | Host only |
| `POST /meetings/{id}/end` | Kết thúc cuộc họp | Host + Secretary + ADMIN | Host + Secretary only |
| `POST /meetings/{id}/mode` | Chuyển chế độ FREE_MODE ↔ MEETING_MODE | Host + ADMIN | Host only |
| `POST /meetings/{id}/speaking-permission/{userId}` | Cấp quyền phát biểu | Host + ADMIN | Host only |
| `DELETE /meetings/{id}/speaking-permission` | Thu hồi quyền phát biểu | Host + ADMIN | Host only |

### Phạm vi thay đổi dự kiến

- **Backend**: Sửa logic kiểm tra quyền trong các service tương ứng:
  - `MeetingLifecycleService.activateMeeting()`
  - `MeetingLifecycleService.endMeeting()`
  - `MeetingModeService.switchMode()`
  - `SpeakingPermissionService.grantPermission()`
  - `SpeakingPermissionService.revokePermission()`
- **Tài liệu**: Cập nhật `system_flow.md` và `README.md` bảng phân quyền

### Ghi chú

> ADMIN vẫn giữ toàn quyền quản lý User, Phòng ban, Phòng họp, và xóa Meeting/Recording. Chỉ loại bỏ quyền can thiệp vào luồng điều hành *trong* meeting.

---

## [ ] TASK-002: Cơ chế fallback Host và auto-end khi Host + Secretary vắng mặt

**Ngày ghi nhận:** 2026-05-07  
**Trạng thái:** ⏳ Chờ thực hiện  

### Mô tả nghiệp vụ

Khi Host đột ngột mất kết nối / rời phòng trong lúc meeting đang ACTIVE, hệ thống xử lý theo 2 tầng:

**Tầng 1 — Host biến mất, Secretary vẫn còn:**
- Secretary tự động được **nâng lên làm Host tạm thời** (host fallback)
- Secretary lúc này có đầy đủ quyền Host: chuyển mode, cấp quyền phát biểu, kết thúc họp
- Broadcast WebSocket event `HOST_CHANGED` tới tất cả thành viên

**Tầng 2 — Cả Host và Secretary biến mất:**
- Nếu đang ở **MEETING_MODE** → **tự động chuyển về FREE_MODE ngay lập tức**
- Thu hồi mọi speaking permission đang active
- Bắt đầu **đếm ngược 5 phút** (grace period)
- Nếu Host hoặc Secretary quay lại trong 5 phút → hủy đếm ngược, khôi phục bình thường
- Nếu hết 5 phút mà không ai quay lại → **auto-end meeting** (`ACTIVE → ENDED`)
- Broadcast `AUTO_END_WARNING` (kèm timestamp kết thúc dự kiến) khi bắt đầu đếm ngược
- Broadcast `MEETING_ENDED` khi auto-end

### Flow

```
Host disconnect
    │
    ├─ Secretary online? ──YES──► Promote Secretary → Host (HOST_CHANGED event)
    │
    └─ NO: Cả hai offline
            │
            ├─ Đang MEETING_MODE? → Switch to FREE_MODE ngay
            ├─ Revoke all speaking permissions
            ├─ Start 5-minute countdown (AUTO_END_WARNING event)
            │
            ├─ Host/Secretary quay lại trước 5 phút? → Cancel countdown
            └─ Hết 5 phút → Auto-end meeting (MEETING_ENDED event)
```

### Phạm vi thay đổi dự kiến

- **Backend**:
  - `AttendanceService` / `ParticipantSession`: phát hiện Host/Secretary disconnect
  - `MeetingLifecycleService`: thêm `triggerAutoEnd()`, `cancelAutoEnd()`
  - `MeetingModeService`: gọi `switchMode(FREE_MODE)` khi trigger
  - Scheduler (Spring `@Scheduled` hoặc Redis delayed queue): xử lý grace period 5 phút
  - WebSocket: broadcast các event mới `HOST_CHANGED`, `AUTO_END_WARNING`, `MEETING_ENDED`
- **Frontend**:
  - Hiển thị banner cảnh báo đếm ngược khi nhận `AUTO_END_WARNING`
  - Cập nhật UI Host khi nhận `HOST_CHANGED`

### Ghi chú

> "Biến mất" được định nghĩa là: WebSocket STOMP disconnect + không reconnect trong vòng **30 giây** (ngưỡng đề xuất, tránh nhạy cảm với mạng chập chờn).

---

## [ ] TASK-003: Phiên âm liên tục xuyên suốt các lần chuyển chế độ họp

**Ngày ghi nhận:** 2026-05-07  
**Trạng thái:** ⏳ Chờ thực hiện  

### Mô tả nghiệp vụ

Trong một cuộc họp, chế độ có thể chuyển nhiều lần:
```
MEETING_MODE → FREE_MODE (nghỉ giữa giờ) → MEETING_MODE → FREE_MODE → MEETING_MODE ...
```

**Quyết định thiết kế ✅:** Chỉ STT khi đang ở **MEETING_MODE**. Khi chuyển sang FREE_MODE, pipeline phiên âm **tạm dừng** (pause). Khi quay lại MEETING_MODE, tiếp tục ghi nối tiếp vào **cùng 1 TranscriptionJob** — không tạo job mới.

### Hành vi mong muốn

| Sự kiện | Hành vi |  
|---------|---------|  
| Meeting chuyển sang ACTIVE | Tạo **1 TranscriptionJob duy nhất** cho meeting |  
| Chuyển sang MEETING_MODE | **Resume** — bắt đầu/tiếp tục stream PCM audio → append vào job |  
| Chuyển sang FREE_MODE | **Pause** — dừng stream, không ghi vào job (nội dung nghỉ giữa giờ bị bỏ qua) |  
| Quay lại MEETING_MODE lần 2, 3... | Tiếp tục append vào **cùng job**, các segment được nối đuôi nhau |  
| Meeting ENDED | Đóng job, đẩy vào queue Gipformer để xử lý ASR toàn bộ |  

### Phạm vi thay đổi dự kiến

- **Backend**:
  - `TranscriptionJob`: thêm field `isPaused` (boolean) để kiểm soát trạng thái stream
  - `TranscriptionService`: đảm bảo 1 meeting = 1 `TranscriptionJob` (idempotent create khi ACTIVE)
  - `MeetingModeService.switchMode()`:
    - Chuyển → `MEETING_MODE`: set `job.isPaused = false`, resume nhận audio
    - Chuyển → `FREE_MODE`: set `job.isPaused = true`, dừng nhận audio
  - Audio WebSocket handler: kiểm tra `job.isPaused` trước khi append chunk, bỏ qua nếu đang pause
- **Frontend**:
  - Dừng stream PCM khi nhận event `MODE_CHANGED → FREE_MODE`
  - Bắt đầu lại stream PCM khi nhận event `MODE_CHANGED → MEETING_MODE`
- **Gipformer**: không thay đổi (nhận job và xử lý như hiện tại)

### Ghi chú

> Các `TranscriptionSegment` sẽ có timestamp riêng của từng lần phát biểu, nên biên bản vẫn phản ánh đúng thứ tự thời gian dù có các khoảng dừng giữa các phiên MEETING_MODE.
