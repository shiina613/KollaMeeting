# Doi chieu DOCX 3.8 voi codebase KollaMeeting

Tai lieu chuan: `Nguyen Quang Tung - Xay dung he thong thu ky ao cho phong hop truc tuyen - version 3.8.docx`.

Muc tieu cua file nay la de reviewer/LLM co the doi chieu truc tiep cac claim trong Word voi code, schema, API va test hien tai. DOCX 3.8 co quyen uu tien cao hon code cu; cac mo rong chi duoc giu khi khong phu dinh Word.

## Kien truc va deploy

| Noi dung trong DOCX | Hien trang codebase | Bang chung |
|---|---|---|
| Frontend React/Vite chay tren trinh duyet | Frontend Docker service build Vite, duoc Nginx route `/` | `frontend/Dockerfile`, `docker-compose.yml`, `nginx/nginx.conf` |
| Backend Spring Boot xu ly nghiep vu, JWT, CRUD, meeting, ASR, minutes | Backend Docker service Spring Boot 3.2, REST `/api/v1`, STOMP `/ws`, binary `/ws/audio` | `backend/src/main/java/com/example/kolla/controllers`, `backend/src/main/java/com/example/kolla/websocket` |
| Jitsi/JaaS xu ly video/audio media; backend khong phai WebRTC signaling server | Frontend nhung Jitsi External API. Backend khong xu ly SDP/ICE/media WebRTC; backend chi xu ly business events va audio PCM rieng cho ASR | `frontend/src/components/meeting/JitsiFrame.tsx`, `backend/src/main/java/com/example/kolla/websocket/AudioStreamHandler.java` |
| Nginx reverse proxy route `/`, `/api`, `/ws`; co the dung Cloudflare Tunnel | Compose co `nginx` va `cloudflared`; Quick Tunnel route vao Nginx port `8888` | `docker-compose.yml`, `scripts/start.ps1`, `scripts/start.sh` |
| ASR FastAPI + PhoWhisper Medium CT2 int8_float16 la mac dinh | `asr-service` mac dinh `ASR_BACKEND=phowhisper`, model path `models/phowhisper-medium-ct2-int8_float16`; Gipformer la backend tuy chon | `asr-service/config.py`, `asr-service/core/recognizer.py`, `.env.example` |

Ghi chu deploy demo: toan bo thanh phan thuoc KollaMeeting duoc deploy bang Docker Compose: `frontend`, `backend`, `mysql`, `redis`, `asr-service`, `nginx`, `cloudflared`. Jitsi khong self-host trong stack demo vi Cloudflare Tunnel khong phu hop tunnel UDP WebRTC media; dung `meet.jit.si` hoac JaaS ben ngoai.

## Actor va phan quyen

| Claim DOCX | Code path | Test/chung cu |
|---|---|---|
| Admin quan ly phong ban, phong hop, nguoi dung | REST admin controllers/services, role `ADMIN` | `UserController`, `DepartmentServiceImpl`, `RoomServiceImpl` |
| Thu ky tao/sua/xoa cuoc hop chua dien ra | `POST/PUT/DELETE /meetings`; service chi cho update/delete khi `SCHEDULED` | `MeetingController`, `MeetingServiceImpl`, `MeetingThesisAlignmentTest` |
| Host/chu tri la nhan vien active bat ky | `createMeeting` validate host active user, khong yeu cau role `SECRETARY` | `MeetingServiceImpl#createMeeting`, `MeetingThesisAlignmentTest#createMeeting_allowsRegularEmployeeAsHostAndStoresMeetingRoles` |
| Thu ky cua meeting phai co role `SECRETARY` | `createMeeting`/`updateMeeting` validate role secretary | `MeetingServiceImpl` |
| Thanh vien co vai tro trong meeting nhu chu tri, thu ky, phan bien, uy vien | `MeetingRole` va cot vat ly `member.MeetingRole` | `MeetingRole.java`, `Member.java`, `V1__initial_schema.sql` |

## Schema DB vat ly

Bang/cot chinh da duoc can theo ten trong DOCX. Java entity van giu field idiomatic, nhung `@Column`/`@JoinColumn` map sang ten vat ly cua Word.

| Bang DOCX | Cot DOCX | Code/schema |
|---|---|---|
| `user` | `Id`, `Department_id`, `EmployeeCode`, `Name`, `Password`, `Dob`, `PhoneNumber`, `Degree`, `Identification`, `Address`, `Email`, `BankName`, `BankNumber`, `Img`, `Role` | `User.java`, `V1__initial_schema.sql`, `check_migration_integrity.sql` |
| `department` | `Id`, `DepartmentCode`, `Name` | `Department.java`, `V1__initial_schema.sql` |
| `room` | `Id`, `RoomCode`, `RoomName` | `Room.java`, `V1__initial_schema.sql` |
| `meeting` | `Id`, `MeetingCode`, `DepartmentId`, `Room_id`, `Name`, `StartTime`, `Endtime`, `Status` | `Meeting.java`, `V1__initial_schema.sql` |
| `member` | `Id`, `User_id`, `Meeting_id`, `MeetingRole` | `Member.java`, `V1__initial_schema.sql` |
| `document` | `Id`, `Meeting_id`, `User_id`, `Name`, `Content` | `Document.java`, `V1__initial_schema.sql` |
| `meeting_message` | `Id`, `Member_id`, `Content`, `CreateTime` | `MeetingMessage.java`, `V7__align_thesis_schema.sql` |

Mo rong khong mau thuan DOCX: cac bang/cot runtime nhu `minutes`, `recording`, `transcription_job`, `participant_session`, `host_user_id`, `secretary_user_id`, `mode`, `transcription_priority` la chi tiet trien khai de van hanh nghiep vu Word, khong thay the cac bang/cot Word.

## Luong nghiep vu chinh

| Luong | Endpoint/code | Dieu kien alignment |
|---|---|---|
| Tao meeting | `POST /api/v1/meetings` | Role `SECRETARY`; host la active user; secretary co role `SECRETARY`; tao `MeetingCode`; auto add host/secretary vao `member` voi `MeetingRole` |
| Sua meeting | `PUT /api/v1/meetings/{id}` | Role `SECRETARY`; chi meeting `SCHEDULED`; check xung dot phong/thoi gian |
| Xoa meeting | `DELETE /api/v1/meetings/{id}` | Role `SECRETARY`; chi meeting `SCHEDULED`; khong xoa meeting dang/da dien ra |
| Tin nhan trao doi | `GET/POST /api/v1/meetings/{meetingId}/messages` | Luu DB `meeting_message`, broadcast STOMP `MEETING_MESSAGE_CREATED` |
| Meeting mode va ASR | STOMP mode events + `/ws/audio` | Chi nhan audio khi meeting o `MEETING_MODE`; audio la PCM 16kHz mono, tao WAV chunk va queue Redis |
| Ket thuc meeting | `POST /api/v1/meetings/{id}/end` | Chuyen `ACTIVE -> ENDED`, tao transcript/minutes, ghi am tong hop |
| Bien ban | `/meetings/{id}/minutes` va `/download?format=pdf/docx` | Tao draft DOCX/PDF; host confirm ky PDF; secretary publish DOCX/PDF |

## Kiem thu bat buoc

Chay truoc khi coi la done:

```powershell
git diff --check
cd backend; .\mvnw.cmd test
cd ..\frontend; npm run test
npm run build
docker compose config
```

Kiem thu demo:

```powershell
.\scripts\start.ps1
```

Sau khi script in Quick Tunnel URL: dang nhap thu ky, tao meeting, gan host/secretary/member role, vao Jitsi, bat `MEETING_MODE`, gui audio, ket thuc meeting, tai DOCX/PDF/audio.

## Trang thai chap nhan

Codebase chi duoc coi la can xong khi:

- Schema/API/UI/README/script khong ke cau chuyen khac DOCX 3.8.
- Cac mo rong duoc giai thich la chi tiet trien khai, khong phu dinh Word.
- Khong track DOCX goc, model weights, WAV demo, Playwright report, secrets.
- Backend/frontend tests va build pass.
- `git status --short` sach sau commit.
