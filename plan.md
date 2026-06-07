# Thesis Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Làm KollaMeeting khớp DOCX 3.8 đã nộp, giữ đúng 7 bảng MySQL trong Word, lưu dữ liệu runtime theo thư mục meeting, dọn file thừa, commit và push lên GitHub.

**Architecture:** MySQL chỉ lưu 7 bảng nghiệp vụ Word. Dữ liệu vận hành như minutes, recordings, transcript, ASR job, attendance, audit lưu dưới `storage/meetings/<meeting_id>/...`; realtime state như notification, speaking permission, raise hand dùng RAM hoặc Redis. Backend chịu trách nhiệm schema/quyền/luồng nghiệp vụ; frontend phản ánh đúng required fields và quyền thao tác.

**Tech Stack:** Spring Boot 3.2, Java 17, Flyway, JPA/Hibernate, MySQL 8, Redis, FastAPI ASR, React 18, Vite, Vitest, Docker Compose, Git/GitHub.

---

## 0. Mốc hiện tại và quyết định đã khóa

- Backup hiện tại đã commit và push: `5f787c9 chore: backup current thesis alignment work` trên `origin/main`.
- Không sửa Word/DOCX đã nộp; mọi chỉnh sửa nằm trong codebase, docs, tests, scripts.
- Ưu tiên phần thiết kế CSDL trong Word: database sạch chỉ có 7 bảng.
- DOCX 3.8 là nguồn chuẩn khi mâu thuẫn với code hiện tại; nếu sửa theo DOCX có nguy cơ làm hệ thống mất ổn định, dừng lại hỏi người dùng trước khi sửa.
- Giữ uniqueness của `room.RoomCode` và `meeting.MeetingCode` ở tầng code/service, không enforce bằng unique constraint trong database vì DOCX chỉ ghi `NOT NULL` cho hai cột này.
- `document.Content` triển khai là đường dẫn/nội dung lưu tài liệu runtime; vẫn coi là khớp DOCX vì DOCX mô tả nội dung tài liệu kiểu `TEXT`, còn file vật lý nằm ngoài MySQL.
- Sửa trực tiếp `V1__initial_schema.sql` để database sạch khớp DOCX ngay từ đầu; không cần giữ tương thích Flyway cho dữ liệu test cũ.
- Quyết định chốt cho cột phụ ngoài DOCX: ưu tiên giữ đúng cột Word trong từng bảng. Chỉ giữ `meeting_message.CreateTime` vì DOCX có ghi; các timestamp phụ khác xóa nếu code có thể chuyển sang `Status`, `StartTime`, `Endtime`, file runtime, Redis hoặc RAM.
- Xóa các cột phụ đã chốt khỏi MySQL: `department.description`; `room.capacity`, `room.Department_id`; `user.is_active`; `meeting.creator_id`, `meeting.host_user_id`, `meeting.secretary_user_id`, `meeting.mode`, `meeting.transcription_priority`, `meeting.activated_at`, `meeting.ended_at`, `meeting.waiting_timeout_at`; `member.added_at`; `document.file_size`, `document.file_type`, `document.uploaded_at`; các `created_at`/`updated_at` không có trong DOCX.
- `user.Dob` giữ kiểu `DATE` vì là ngày sinh, dù DOCX ghi `Datetime`.
- `MeetingCode` sinh từ `meeting.id` theo format `MTG-%06d`; không unique DB, service vẫn phòng thủ nếu có path nhập code thủ công.
- `RoomCode` do admin nhập, không unique DB, service check trùng khi create/update.
- `member` không có DB unique `(Meeting_id, User_id)`; service check trùng trước khi thêm member.
- Quyền meeting lấy từ `member.MeetingRole`; không dùng cột riêng trên `meeting`. Sửa/xóa meeting cho mọi `SECRETARY` trong meeting. Chuyển chế độ họp và bật/tắt ưu tiên cho `HOST` hoặc `SECRETARY` trong meeting; member thường không thấy nút và backend trả `403` nếu gọi API trực tiếp.
- `MEETING_MODE`/`FREE_MODE` là runtime state, không lưu trong DB. Cuộc họp ưu tiên cao dùng một file runtime global, ví dụ `storage/runtime/high-priority-meeting.properties`; chỉ một meeting được ưu tiên tại một thời điểm, và file phải trống sau khi meeting ưu tiên kết thúc.
- Xóa `waiting_timeout_at` khỏi DB; thay bằng Redis TTL hoặc RAM fallback. Nếu meeting `ACTIVE` không còn `HOST`/`SECRETARY` online trong 3 phút, hệ thống tự kết thúc meeting.
- Activate meeting dùng `StartTime` làm thời gian bắt đầu thực tế: khi bấm bắt đầu, set `Status = ACTIVE` và `StartTime = now`. End meeting dùng `Endtime` làm thời gian kết thúc thực tế: set `Status = ENDED` và `Endtime = now`. Không giữ lịch gốc.
- Delete flow dùng service tự xóa/chặn theo thứ tự quan hệ, hạn chế cascade DB rộng nếu DOCX không ghi.
- Cột phụ chưa được chốt trong hội thoại này, ví dụ `meeting.description` nếu còn trong code, phải dừng hỏi người dùng trước khi giữ hoặc xóa.
- Không commit DOCX gốc, `.env`, keys, model weights, scratch scripts, report rỗng, build/test output.
- Nếu DB local đã chạy Flyway trước khi sửa schema, reset volume MySQL cho demo sạch: `docker compose down; docker volume rm kollameeting_mysql-data; .\scripts\start.ps1`.

## 1. File map cấp module

Không cần liệt kê từng file nhỏ khi thực thi. Worker cần tìm đúng file trong các module dưới đây bằng `rg` trước khi sửa.

### Backend schema và model mapping

- Phạm vi: Flyway migrations, schema integrity SQL, JPA models của 7 bảng Word.
- Trọng tâm: required columns, SQL types, `@Column`/`@JoinColumn`, không tạo thêm runtime tables.
- Test chính: `SubmittedSchemaContractTest`.

### Backend validation và nghiệp vụ

- Phạm vi: DTO create/update, service implementations cho department, room, user, meeting, document.
- Trọng tâm: required codes/department/room, upload tài liệu chỉ cho thư ký, builder defaults, helper quyền rõ tên.
- Test chính: service tests hiện có + test mới cho document permission.

### Backend runtime file storage

- Phạm vi: runtime store và repository wrappers cho dữ liệu ngoài MySQL.
- Trọng tâm: attendance phải persist dưới `storage/meetings/<meeting_id>/attendance/`, load lại được sau restart.
- Test chính: runtime store persistence tests.

### Frontend forms và API types

- Phạm vi: admin forms, meeting form, frontend service request types.
- Trọng tâm: UI bắt buộc nhập đúng các trường backend/schema yêu cầu; payload không gửi null cho trường Word required.
- Test chính: meeting form, user management, department/room management.

### Docs và repo hygiene

- Phạm vi: README, DOCX alignment docs, database docs, `.gitignore`, local cleanup.
- Trọng tâm: repo kể một câu chuyện thống nhất: 7 bảng DB + runtime file/RAM/Redis; không commit file thừa/secret.

---

## 2. Task checklist

### Task 1: Mở rộng schema contract test trước khi sửa schema

- [ ] Mở test schema contract hiện có.
- [ ] Thêm test đọc migration SQL và assert các cột Word quan trọng có type/nullability đúng:
  - `department.DepartmentCode`: `VARCHAR(100) NOT NULL UNIQUE`
  - `room.RoomCode`: `VARCHAR(100) NOT NULL` và không có `UNIQUE` constraint trong DB.
  - `user.Department_id`: `BIGINT NOT NULL`
  - `meeting.DepartmentId`: `BIGINT NOT NULL`
  - `meeting.Room_id`: `BIGINT NOT NULL`
  - `meeting.MeetingCode`: `VARCHAR(50) NOT NULL` và không có `UNIQUE` constraint trong DB.
  - `document.Content`: `TEXT`; code upload vẫn phải luôn ghi giá trị path/content hợp lệ.
- [ ] Thêm assertions kiểm từng bảng không còn cột phụ đã quyết định xóa:
  - `department`: chỉ còn `id`, `DepartmentCode`, `Name`.
  - `room`: chỉ còn `id`, `RoomCode`, `RoomName`.
  - `user`: giữ các cột Word, `Dob` là `DATE`, không có `is_active`, `created_at`, `updated_at`.
  - `meeting`: giữ các cột Word `id`, `MeetingCode`, `DepartmentId`, `Room_id`, `Name`, `StartTime`, `Endtime`, `Status`; không có creator/host/secretary/mode/priority/lifecycle timestamp columns; nếu gặp `description` thì dừng hỏi vì chưa chốt.
  - `member`: chỉ còn `id`, `User_id`, `Meeting_id`, `MeetingRole`, không có `added_at`, không có DB unique `(Meeting_id, User_id)`.
  - `document`: chỉ còn `id`, `Meeting_id`, `User_id`, `Name`, `Content`, không có file metadata columns.
  - `meeting_message`: chỉ còn `id`, `Member_id`, `Content`, `CreateTime`.
- [ ] Giữ test hiện có kiểm chỉ tạo 7 bảng và runtime repos không dùng JPA.
- [ ] Run failing test:

```powershell
cd backend
.\mvnw.cmd -Dtest=SubmittedSchemaContractTest test
```

Expected: trước khi thêm assertions mới, test hiện tại có thể PASS; sau khi thêm assertions mới, test phải FAIL vì schema hiện còn nullable, còn DB unique không theo DOCX, còn cột phụ ngoài Word, và `document.Content` còn `VARCHAR(1000)`.

### Task 2: Siết schema đúng Word nhưng vẫn chỉ 7 bảng

- [ ] Sửa trực tiếp `src/main/resources/db/migration/V1__initial_schema.sql` cho các cột ở Task 1 đúng type/nullability theo DOCX.
- [ ] Bỏ DB unique constraint khỏi `room.RoomCode` và `meeting.MeetingCode`; uniqueness của hai mã này kiểm ở tầng service/test.
- [ ] Bỏ DB unique constraint khỏi `member(Meeting_id, User_id)`; duplicate member được chặn ở service.
- [ ] Xóa các cột phụ khỏi migration theo quyết định mục 0: timestamp phụ, `is_active`, room department/capacity, meeting creator/host/secretary/mode/priority/lifecycle timestamps, member added time, document metadata, department description.
- [ ] Không thêm bảng runtime nào vào migration.
- [ ] Đảm bảo seed demo vẫn hợp lệ: department có code/name, room có code/name, admin user có department.
- [ ] Mở rộng SQL integrity check để báo PASS/FAIL cho 7 bảng, exact-ish Word columns, absence của runtime tables, các cột required, và absence của DB unique constraint trên `RoomCode`/`MeetingCode`/`member(Meeting_id, User_id)`.
- [ ] Run schema contract:

```powershell
cd backend
.\mvnw.cmd -Dtest=SubmittedSchemaContractTest test
```

Expected: PASS.

### Task 3: Đồng bộ JPA model mapping với schema

- [ ] Sửa annotation model cho `DepartmentCode`, `RoomCode`, `Department_id`, `DepartmentId`, `Room_id`, `MeetingCode` thành `nullable = false`.
- [ ] Bỏ `unique = true` khỏi mapping `RoomCode` và `MeetingCode`; giữ check trùng mã ở service.
- [ ] Sửa `Document.Content` mapping sang `columnDefinition = "TEXT"`, không dùng `length = 1000`; backend upload phải set path/content không blank.
- [ ] Xóa field/mapping JPA cho các cột phụ đã loại khỏi DB; không để Hibernate truy cập cột không còn tồn tại.
- [ ] Đổi source phân quyền meeting sang `Member.MeetingRole`; `Meeting` không còn quan hệ `creator`, `host`, `secretary`.
- [ ] Không biến runtime POJO thành `@Entity`.
- [ ] Run schema contract và compile nhanh:

```powershell
cd backend
.\mvnw.cmd -Dtest=SubmittedSchemaContractTest test
```

Expected: PASS.

### Task 4: Siết backend validation cho required fields

- [ ] Department create: `departmentCode` bắt buộc, không blank, unique.
- [ ] Department update: nếu gửi `departmentCode` thì không được blank; không có path clear về null.
- [ ] Department create/update không còn nhận/lưu `description`.
- [ ] Room create: chỉ `roomCode`, `roomName/name` bắt buộc; không còn `capacity` hoặc `departmentId`; room code unique ở service.
- [ ] Room update: nếu gửi `roomCode` thì không được blank; không có path clear về null.
- [ ] User create: `departmentId` bắt buộc và phải tồn tại.
- [ ] User update: không tạo path clear `departmentId` về null.
- [ ] Xóa `is_active` khỏi user create/update/auth/delete flow; chức năng xóa nhân viên theo Word không dùng vô hiệu hóa mềm.
- [ ] Meeting create: `roomId` và `departmentId` đều bắt buộc; không suy `departmentId` từ room.
- [ ] Meeting create sinh `MeetingCode = MTG-%06d` sau khi có `id`; nếu có path nhập/import code thủ công thì check trùng ở service.
- [ ] Meeting update: không cho clear `roomId` hoặc `departmentId`; khi đổi room không tự đổi department.
- [ ] Meeting create/update không còn nhận/lưu `creatorId`, `hostUserId`, `secretaryUserId`, `mode`, `transcriptionPriority`, `activatedAt`, `endedAt`, `waitingTimeoutAt`.
- [ ] Meeting member add/update: service check trùng `(meetingId, userId)` trước khi thêm vì DB không còn unique constraint.
- [ ] Sửa delete flow theo service từng bước: xóa/chặn dữ liệu liên quan rõ ràng, không dựa cascade rộng ngoài DOCX.
- [ ] Thêm `@Builder.Default` cho request defaults còn cần thiết sau khi bỏ transcription priority, tối thiểu `AddMemberRequest.java` (member role) nếu Lombok còn warning.
- [ ] Run targeted tests:

```powershell
cd backend
.\mvnw.cmd -Dtest=UserServiceTest,UserProfileThesisAlignmentTest,MeetingThesisAlignmentTest test
```

Expected: PASS sau khi test fixtures có valid department/room/code.

### Task 5: Chỉ thư ký được upload tài liệu cuộc họp

- [ ] Tạo test mới `backend/src/test/java/com/example/kolla/services/DocumentServiceImplTest.java` cho document service trước khi sửa code.
- [ ] Test cases bắt buộc:
  - Meeting member có `MeetingRole.SECRETARY` upload thành công.
  - Regular member upload bị forbidden.
  - Admin upload bị forbidden vì Word ghi actor là thư ký.
  - Secretary không liên quan meeting upload bị forbidden.
- [ ] Run failing test:

```powershell
cd backend
.\mvnw.cmd -Dtest=DocumentServiceImplTest test
```

Expected: sau khi tạo test mới, FAIL vì code hiện cho member/admin/secretary rộng hơn.

- [ ] Sửa upload permission: chỉ user là member của meeting với `MeetingRole.SECRETARY` được upload.
- [ ] Giữ list/get/download document cho meeting members như hiện tại.
- [ ] Cập nhật controller/OpenAPI/comment từ “any member may upload” sang “meeting secretary only”.
- [ ] Run lại document tests.

Expected: PASS.

### Task 6: Persist attendance runtime theo thư mục meeting

- [ ] Tạo test mới `backend/src/test/java/com/example/kolla/runtime/RuntimeMeetingStateStoreTest.java` trước khi sửa code.
- [ ] Test cases bắt buộc:
  - Save attendance tạo file `storage/meetings/<meeting_id>/attendance/<attendance_id>.properties`.
  - Store instance mới load lại attendance theo meeting.
  - Open attendance load lại được khi `leaveTime` trống.
  - Active attendance loại log đã có `leaveTime`.
- [ ] Run failing test:

```powershell
cd backend
.\mvnw.cmd -Dtest=RuntimeMeetingStateStoreTest test
```

Expected: sau khi tạo test mới, FAIL vì attendance hiện memory-only.

- [ ] Persist fields: `id`, `meetingId`, `userId`, `joinTime`, `leaveTime`, `durationSeconds`, `ipAddress`, `deviceInfo`.
- [ ] Runtime store cần inject `UserRepository` ngoài `MeetingRepository`, rồi resolve `Meeting` và `User` khi load attendance file.
- [ ] Các query attendance phải load từ file trước khi trả kết quả.
- [ ] Thêm runtime state cho meeting mode, không lưu `meeting.mode` trong MySQL. `FREE_MODE`/`MEETING_MODE` dùng RAM/Redis/file, load theo `meetingId` khi cần.
- [ ] Thêm runtime priority store global, ví dụ `storage/runtime/high-priority-meeting.properties`, để ghi meeting đang được ưu tiên cao tại thời điểm.
- [ ] Priority store rules: bật ưu tiên chỉ thành công khi file trống hoặc meeting trong file không tồn tại/đã `ENDED`; chỉ một meeting được ưu tiên; khi meeting ưu tiên kết thúc, file phải clear/trống.
- [ ] Thêm Redis TTL hoặc RAM fallback cho waiting timeout: khi meeting `ACTIVE` không còn `HOST`/`SECRETARY` online trong 3 phút, tự end meeting.
- [ ] Run runtime store tests.

Expected: PASS.

### Task 7: Đổi tên helper quyền gây hiểu nhầm

- [ ] Đổi helper public đang tên `isHostOrAdmin` thành tên đúng hành vi thật, ví dụ `isHostOfMeeting`, và implementation phải đọc từ `member.MeetingRole`, không đọc `meeting.host_user_id`.
- [ ] Tạo helper rõ nghĩa cho `isSecretaryOfMeeting`, `isHostOrSecretaryOfMeeting`, `canManageMeeting`, `canControlMeetingRuntime` nếu cần; tất cả dựa trên `member.MeetingRole`.
- [ ] Update call sites liên quan đến activate/end meeting, mode switch, priority switch, raise hand, speaking permission, minutes confirmation nếu compile báo lỗi.
- [ ] Activate meeting: vẫn chỉ cho trong vòng 30 phút trước `StartTime`; khi activate thì set `Status = ACTIVE` và overwrite `StartTime = now`.
- [ ] End meeting: set `Status = ENDED`, overwrite `Endtime = now`, clear high-priority runtime file nếu file đang trỏ meeting đó.
- [ ] Mode switch và priority switch chỉ cho `HOST` hoặc `SECRETARY` trong meeting; member thường gọi API phải nhận `403`.
- [ ] Cập nhật comments/OpenAPI cho activate/end/mode/priority theo code thật.
- [ ] Run lifecycle tests:

```powershell
cd backend
.\mvnw.cmd -Dtest=MeetingLifecycleIntegrationTest,MeetingLifecycleApiIntegrationTest test
```

Expected: PASS.

### Task 8: Frontend required fields và controls khớp backend/schema

- [ ] Frontend request types: department code, room code, user department, meeting room, meeting department phải required khi create.
- [ ] Frontend request/response types xóa các field DB đã bỏ: room `capacity`/`departmentId`, department `description`, user `isActive`, meeting `creatorId`/`hostId`/`secretaryId`/`mode`/`transcriptionPriority`/`activatedAt`/`endedAt`/`waitingTimeoutAt`, document metadata, member `addedAt`.
- [ ] Department form: hiển thị required marker cho department code và chặn submit blank.
- [ ] Department form không còn input `description`.
- [ ] Room form chỉ còn `roomCode` và `roomName/name`; hiển thị required marker cho room code/name và chặn submit blank; bỏ `capacity` và `departmentId`.
- [ ] User create form: department bắt buộc; blank department chặn submit.
- [ ] Meeting form: giữ room bắt buộc, thêm/giữ department bắt buộc, payload luôn gửi numeric `roomId` và `departmentId`; bỏ host/secretary fields nếu frontend đang gửi về cột meeting, phân vai qua member API.
- [ ] Meeting detail/room UI: nút bắt đầu/kết thúc, chuyển chế độ họp, bật/tắt ưu tiên chỉ hiển thị cho user có `MeetingRole.HOST` hoặc `MeetingRole.SECRETARY`; member thường không thấy nút.
- [ ] Priority UI chuyển từ lựa chọn lúc tạo meeting sang nút bật/tắt trong phòng họp; hiển thị trạng thái nếu meeting hiện tại đang là high-priority runtime meeting.
- [ ] Tests bắt buộc:
  - Meeting form room required vẫn pass.
  - Meeting form department required blocks submit.
  - User management blank department blocks create submit.
  - Department management blank department code blocks submit.
  - Room management blank room code blocks submit.
  - Regular meeting member does not see mode/priority controls.
  - Host or secretary sees mode/priority controls.
- [ ] Run frontend tests/build:

```powershell
cd frontend
npm run test
npm run build
```

Expected: PASS.

### Task 9: Docs kể một câu chuyện thống nhất

- [ ] README ghi database clean reset chỉ có 7 bảng Word.
- [ ] README/database doc ghi rõ các bảng MySQL giữ đúng cột Word đã chốt, kèm ngoại lệ `user.Dob` dùng `DATE` vì là ngày sinh.
- [ ] README ghi runtime file locations:
  - `storage/runtime/high-priority-meeting.properties`
  - `storage/meetings/<meeting_id>/minutes/`
  - `storage/meetings/<meeting_id>/recordings/`
  - `storage/meetings/<meeting_id>/transcript/`
  - `storage/meetings/<meeting_id>/attendance/`
  - `storage/meetings/<meeting_id>/documents/`
  - `storage/meetings/<meeting_id>/audit/`
- [ ] DOCX alignment doc giải thích mâu thuẫn trong Word: schema section 7 bảng có ưu tiên, runtime state như mode, high-priority meeting, waiting timeout, attendance/minutes/transcript/recording được triển khai bằng runtime file/RAM/Redis.
- [ ] Docs ghi meeting lifecycle mới: activate trong vòng 30 phút trước `StartTime`, activate overwrite `StartTime = now`, end overwrite `Endtime = now`, không lưu lịch gốc.
- [ ] Docs ghi quyền mới: meeting permissions lấy từ `member.MeetingRole`; mọi `SECRETARY` trong meeting được sửa/xóa meeting; `HOST` hoặc `SECRETARY` trong meeting được điều khiển mode/priority; member thường không thấy nút.
- [ ] Database doc nếu giữ lại phải khớp README; nếu không giữ thì xóa trước commit.
- [ ] `.gitignore` chặn DOCX gốc, storage, model weights, `.env`, keys, Playwright/test output, scratch artifacts.
- [ ] Bật ignore DOCX bằng cách đổi dòng `# *.docx` thành `*.docx`; không stage file Word đã nộp.

### Task 10: Full verification

- [ ] Run whitespace check:

```powershell
git diff --check
```

Expected: no output, exit code 0.

- [ ] Run backend tests:

```powershell
cd backend
.\mvnw.cmd test
```

Expected: BUILD SUCCESS.

- [ ] Run frontend tests/build:

```powershell
cd frontend
npm run test
npm run build
```

Expected: all tests pass, Vite build succeeds.

- [ ] Run Docker config check:

```powershell
docker compose config --quiet
```

Expected: no output, exit code 0.

### Task 11: Cleanup, commit, push

- [ ] Inspect repo:

```powershell
git status --short --untracked-files=all
```

- [ ] Remove or ignore local-only leftovers:
  - DOCX gốc đã nộp.
  - `scratch/search_meeting_roles.py` unless promoted to official script.
  - `review.md` if it exists and is empty.
  - generated build/test/storage/keys/model artifacts.
- [ ] Confirm staged files do not include secrets or DOCX:

```powershell
git diff --cached --name-status
```

- [ ] Commit:

```powershell
git add -u
git add -- README.md docs database.md backend frontend .gitignore plan.md
git commit -m "fix: align codebase with submitted thesis document"
```

- [ ] Push:

```powershell
git push origin main
```

Expected: push succeeds to `https://github.com/shiina613/KollaMeeting.git`.

---

## 3. Acceptance criteria

- Main workflows match DOCX 3.8: login, department/room/user management, meeting create/update/delete, document upload by meeting secretary, Jitsi meeting, runtime `MEETING_MODE`, runtime high-priority ASR, DOCX/PDF minutes, PDF digital signature.
- Clean MySQL reset creates only 7 Word tables, no runtime tables, and no extra DB columns that were explicitly removed in section 0.
- Required DB columns from Word are enforced by schema, backend validation, and frontend forms.
- Runtime data lives under `storage/meetings/<meeting_id>/...`, `storage/runtime/...`, Redis, or RAM.
- `MeetingCode` is generated as `MTG-%06d`; `RoomCode` and member duplicates are protected by service checks, not DB unique constraints.
- Meeting start/end actual times are stored in `StartTime`/`Endtime`; high-priority file is clear after the priority meeting ends; waiting timeout ends abandoned active meetings after 3 minutes without host/secretary.
- All checks in Task 10 pass.
- README/docs/comments/OpenAPI do not conflict with schema, storage, roles, or signing behavior.
- Final `git status --short --untracked-files=all` has no commit-worthy files left.
- Final commit is pushed to `origin/main`.

## 4. Known local leftovers after backup commit

These are intentionally not in backup commit `5f787c9` and must be handled during cleanup:

- DOCX gốc đã nộp: keep local, do not commit.
- `review.md`: if it exists, delete if empty or convert to official report before commit.
- `scratch/search_meeting_roles.py`: delete unless intentionally promoted to official script.
