# Kế hoạch sửa codebase khớp DOCX 3.8 và dọn file thừa

## Tóm tắt

Mục tiêu là làm codebase khớp bản Word đã nộp, ưu tiên thiết kế **7 bảng MySQL** trong DOCX. Dữ liệu runtime không tạo thêm bảng trong database; lưu bằng file dưới `storage/meetings/<meeting_id>/...`, Redis hoặc RAM.

Hiện trạng đã kiểm:

- Backend alignment tests: pass.
- Frontend tests/build: pass.
- `git diff --check`: pass.
- `docker compose config --quiet`: pass.
- Worktree đang có nhiều file modified/untracked cần phân loại trước commit.
- Có file thừa hoặc cần phân loại: DOCX gốc, `scratch/search_meeting_roles.py`, `review.md` rỗng, cùng một số file hỗ trợ chưa rõ nên commit hay bỏ.

## Thay đổi chính

### Schema/API theo Word

- Giữ đúng 7 bảng: `user`, `department`, `room`, `meeting`, `member`, `document`, `meeting_message`.
- Siết `DepartmentCode`, `RoomCode`, `user.Department_id`, `meeting.DepartmentId`, `meeting.Room_id` thành required.
- Đổi `document.Content` sang `TEXT`; vẫn lưu relative file path để download file thật.
- Không tạo bảng runtime mới.

### Runtime storage

- Lưu transcript, ASR job, minutes, recordings, attendance, audit dưới `storage/meetings/<meeting_id>/...`.
- Persist attendance ra file thay vì chỉ RAM.
- Giữ notification, speaking permission và raise hand queue trong RAM hoặc Redis vì đây là realtime state, không thuộc 7 bảng Word.

### Quyền nghiệp vụ

- Upload tài liệu cuộc họp chỉ cho thư ký, phù hợp với Word.
- Create/update/delete meeting giữ Secretary-only khi meeting chưa diễn ra.
- Start/end meeting giữ theo mô tả thực nghiệm: host hoặc secretary được kết thúc; phần activate/comment/API text phải thống nhất với code thật.

### Dọn warning và tài liệu

- Thêm `@Builder.Default` cho DTO đang warning.
- Đổi helper/comment sai tên như `isHostOrAdmin` nếu thực tế không cho Admin bypass.
- Cập nhật README, DOCX alignment và database docs theo hướng 7 bảng + runtime file.

## Dọn dẹp file

- Không commit DOCX gốc đã nộp; đưa vào ignored/untracked cleanup.
- Xóa hoặc bỏ khỏi commit các file tạm:
  - `scratch/search_meeting_roles.py`
  - `review.md` nếu vẫn rỗng
  - artifact test/build nếu xuất hiện sau verification
- Phân loại file untracked trước khi commit:
  - Giữ nếu là code/test cần thiết: `RuntimeMeetingStateStore.java`, `SubmittedSchemaContractTest.java`, `MinutesConfirmationResponse.java`, `frontend/eslint.config.js`.
  - Giữ `database.md` chỉ nếu đây là tài liệu chính thức; nếu chỉ là ghi chú tạm thì bỏ khỏi commit.
- Trước commit cuối, `git status --short` chỉ còn file thay đổi có chủ đích, không có DOCX, scratch, empty report, secrets hoặc build output.
- Sau khi hoàn thành toàn bộ plan: dọn dẹp tất cả file thừa, kiểm tra lại `git status`, commit code với message rõ ràng, rồi push lên GitHub.

## Test plan

### Backend

- Chạy `cd backend; .\mvnw.cmd test`.
- Thêm hoặc đảm bảo test schema contract cho đúng 7 bảng và required constraints.
- Test upload tài liệu: secretary pass; member/admin/non-assigned user fail.
- Test attendance file persistence qua reload store.

### Frontend

- Chạy `cd frontend; npm run test`.
- Chạy `cd frontend; npm run build`.
- Đảm bảo form admin/user/department/room hiển thị required fields đúng schema Word.

### Repo checks

- Chạy `git diff --check`.
- Chạy `docker compose config --quiet`.
- Chạy `git status --short --untracked-files=all`.

## Điều kiện hoàn thành

- Code chạy theo đúng bản Word đã nộp ở các luồng chính: login, quản lý phòng ban/phòng họp/người dùng, tạo/sửa/xóa meeting, thêm tài liệu, họp Jitsi, `MEETING_MODE`, ASR, xuất DOCX/PDF và ký số PDF.
- Database sạch sau reset chỉ có đúng 7 bảng Word; không có runtime tables.
- Runtime data nằm đúng dưới `storage/meetings/<meeting_id>/...`, Redis hoặc RAM.
- Không còn claim sai trong README/docs/comments/OpenAPI về schema, quyền hoặc storage.
- Tất cả test/build/check ở Test plan pass.
- Worktree cuối sạch hoặc chỉ còn thay đổi có chủ đích đã sẵn sàng commit.
- Không commit DOCX gốc, `.env`, keys, model weights, scratch file, report rỗng hoặc build/test output.
- Sau cleanup cuối cùng, code đã được commit và push thành công lên remote GitHub hiện tại.
