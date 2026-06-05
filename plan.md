# Kế hoạch căn chỉnh KollaMeeting theo DOCX 3.8 và làm sạch repo

## Tóm tắt

DOCX 3.8 là tài liệu chuẩn cao nhất. Codebase, schema, README, API, UI, test, script demo và tài liệu repo phải thống nhất với bản Word đã nộp.

Điều kiện hoàn thành không chỉ là build pass. Điều kiện hoàn thành là: codebase và Word không còn xung đột rõ ràng; nếu LLM, giám khảo hoặc phản biện đọc repo để đối chiếu với Word thì không tìm được điểm hợp lý để trừ vì sai mô tả, sai kiến trúc, sai schema, sai luồng nghiệp vụ hoặc sai triển khai demo.

## Điều kiện hoàn thành

- Không còn mâu thuẫn giữa DOCX và repo về actor, vai trò cuộc họp, schema DB, luồng tạo/sửa/xóa meeting, Jitsi/JaaS/WebRTC, Docker/Cloudflare/Nginx, ASR, biên bản DOCX/PDF/audio và ký số PDF.
- README, `.env.example`, `docker-compose.yml`, scripts, code comments quan trọng và test name không kể câu chuyện khác với Word.
- Có `docs/DOCX_ALIGNMENT.md` map rõ phần DOCX, code path, endpoint/API, bảng/cột DB, test chứng minh và phần mở rộng không mâu thuẫn.
- `git status --short` sạch sau commit.
- Không track artifact demo/test/model nặng: DOCX gốc, model weights, WAV mẫu, Playwright report, test results, secrets.
- Backend test, frontend test, frontend build và cấu hình demo Docker đều pass.

## Thay đổi chính cần giữ đúng

- Làm sạch repo trước: ignore artifact, model weights, report, video, screenshot, runtime files và chuẩn hóa line ending.
- Schema DB vật lý khớp tên trong DOCX; code Java/TypeScript vẫn được dùng property idiomatic nếu JPA/API mapping rõ.
- Backend enforce đúng nghiệp vụ DOCX: thư ký tạo/sửa/xóa meeting chưa diễn ra; host là user active bất kỳ; secretary phải có role `SECRETARY`.
- Meeting creation tự thêm host và secretary vào `member` với `MeetingRole`.
- Meeting messages được lưu DB và broadcast STOMP `MEETING_MESSAGE_CREATED`.
- End meeting tạo transcript, DOCX, PDF ký số hoặc có thể ký, và audio tổng hợp.
- Frontend label/form/download/meeting room khớp ngôn ngữ và luồng trong Word.
- README ghi rõ demo deployment bằng Docker Compose + Nginx + Cloudflare Quick Tunnel + một script start.

## Triển khai Docker/demo

Toàn bộ thành phần thuộc KollaMeeting deploy bằng Docker Compose:

- `frontend`
- `backend`
- `mysql`
- `redis`
- `asr-service`
- `nginx`
- `cloudflared`

Không self-host Jitsi trong Docker stack demo.

- Video meeting dùng `meet.jit.si` hoặc JaaS bên ngoài.
- Frontend nhúng Jitsi bằng iframe/Jitsi External API.
- Backend không xử lý SDP/ICE/media WebRTC.
- Backend chỉ xử lý nghiệp vụ, STOMP control events và `/ws/audio` cho ASR.

Demo chính thức dùng một lệnh:

```powershell
.\scripts\start.ps1
```

hoặc:

```bash
./scripts/start.sh
```

Quick Tunnel là chế độ demo/nghiệm thu ngắn hạn 2-3 ngày, mỗi ngày chạy một lần. URL sẽ đổi sau mỗi lần chạy script. Vận hành lâu dài mới cần Named Tunnel/domain cố định. Script phải in ra URL truy cập cuối cùng sau khi start.

## Kiểm thử bắt buộc

```powershell
git diff --check
cd backend
.\mvnw.cmd test
cd ..\frontend
npm run test
npm run build
cd ..
docker compose config
```

Kiểm thử migration:

- Fresh Flyway trên MySQL mới.
- Chạy `backend/src/main/resources/db/migration/check_migration_integrity.sql`.

Demo smoke:

1. Chạy `.\scripts\start.ps1`.
2. Mở Quick Tunnel URL được in ra.
3. Đăng nhập thư ký.
4. Tạo meeting, chọn host active bất kỳ và secretary role `SECRETARY`.
5. Gán role thành viên trong meeting.
6. Vào Jitsi, bật meeting mode.
7. Gửi audio qua `/ws/audio`, nhận transcript.
8. Kết thúc meeting.
9. Tải DOCX/PDF/audio.

## Bảng chấp nhận theo Word

| Nhóm | Done khi |
|---|---|
| Actor/role | Admin, thư ký, nhân viên và `MeetingRole` khớp Word; host không bị ép role secretary/admin |
| DB | Bảng/cột vật lý chính dùng tên Word: `EmployeeCode`, `MeetingCode`, `DepartmentId`, `Room_id`, `MeetingRole`, `CreateTime`, ... |
| Meeting CRUD | Secretary tạo/sửa/xóa meeting `SCHEDULED`; không sửa/xóa meeting `ACTIVE`/`ENDED` |
| Jitsi/WebRTC | Repo ghi rõ Jitsi/JaaS ngoài xử lý media; backend không là signaling server SDP/ICE |
| ASR | `/ws/audio` chỉ nhận PCM khi `MEETING_MODE`, queue Redis, ASR FastAPI PhoWhisper mặc định |
| Minutes | Kết thúc meeting tạo transcript, DOCX/PDF; host confirm ký PDF; secretary publish final DOCX/PDF |
| Deploy | Docker Compose chạy toàn bộ stack KollaMeeting; Quick Tunnel demo; Named Tunnel chỉ là vận hành lâu dài |
| Repo hygiene | Không track artifact nặng/local; status sạch sau commit |

## Giả định

- DOCX 3.8 có quyền ưu tiên cao hơn code hiện tại.
- Quick Tunnel không mâu thuẫn với Word nếu repo ghi rõ đây là chế độ demo ngắn hạn.
- “Deploy tất cả lên Docker” nghĩa là deploy toàn bộ KollaMeeting stack bằng Docker; Jitsi media dùng dịch vụ ngoài vì self-host Jitsi qua Cloudflare Tunnel không phù hợp kỹ thuật.
- Mọi mở rộng không có trong Word chỉ được giữ nếu không làm sai hoặc phủ định nội dung Word.
