
# Kolla Meeting — Frontend Design Brief

> Tài liệu mô tả chi tiết từng trang để thiết kế trên Stitch/Figma.
> Mỗi trang ghi rõ: **ai được truy cập**, layout, thành phần UI, API, trạng thái cần xử lý.

---

## Quy ước Role

| Role | Ký hiệu | Mô tả |
|------|---------|-------|
| Tất cả người dùng đã đăng nhập | `[ALL]` | ADMIN + SECRETARY + USER |
| Chỉ Admin | `[ADMIN]` | Quản trị viên hệ thống |
| Admin hoặc Secretary | `[ADMIN/SEC]` | Người có quyền tạo/quản lý meeting |
| Chỉ Host của meeting | `[HOST]` | SECRETARY/ADMIN được chỉ định làm Host |
| Chỉ Secretary của meeting | `[SEC]` | SECRETARY được chỉ định làm Secretary |
| Thành viên của meeting | `[MEMBER]` | Người được thêm vào meeting |

---

## Design System (dùng chung toàn app)

- **Font:** Inter hoặc Plus Jakarta Sans
- **Primary color:** Xanh dương đậm `#1E40AF` (trust, professional)
- **Accent:** Tím `#7C3AED` (action, highlight)
- **Success:** Xanh lá `#16A34A`
- **Danger:** Đỏ `#DC2626`
- **Warning:** Vàng cam `#D97706`
- **Background:** `#F8FAFC` (xám rất nhạt)
- **Card background:** `#FFFFFF`
- **Border radius:** 8–12px
- **Shadow:** Subtle drop shadow cho cards
- **Spacing base:** 4px

---

## Shared Layout — Sidebar + Header

> **QUAN TRỌNG:** Sidebar và Header phải HOÀN TOÀN GIỐNG NHAU trên tất cả trang sau login.
> Chỉ phần content area (bên phải sidebar, bên dưới header) thay đổi theo từng trang.
> **Truy cập:** `[ALL]` — trừ Login Page và Meeting Room Page.

---

### SIDEBAR — Mô tả chi tiết pixel-perfect

**Kích thước:**
- Expanded: rộng **240px**, full height màn hình, fixed position (không scroll theo content)
- Collapsed: rộng **64px** (chỉ hiện icon, ẩn text)
- Background: `#1E293B` (xanh đen đậm, dark sidebar)
- Transition collapse: smooth 200ms

**Vùng 1 — Logo (top, height 64px):**
- Background: `#0F172A` (tối hơn sidebar một chút, tạo phân cách)
- Căn giữa theo chiều dọc
- Expanded: Icon logo (24px) + text "**Kolla Meeting**" (font-size 16px, font-weight 600, màu `#FFFFFF`) cách nhau 10px
- Collapsed: Chỉ hiện icon logo (24px), căn giữa
- Border-bottom: 1px solid `#334155`

**Vùng 2 — Navigation Menu (middle, flex-grow):**
- Padding top: 16px
- Mỗi nav item:
  - Height: **44px**
  - Padding: 0 16px
  - Border-radius: **8px** (áp dụng cho phần bên trong, margin ngang 8px)
  - Display: flex, align-items: center, gap: 12px
  - Icon: 20px × 20px, màu `#94A3B8` (xám nhạt)
  - Text: font-size 14px, font-weight 500, màu `#CBD5E1`
  - Hover: background `#334155`, icon + text chuyển `#FFFFFF`
  - **Active (trang hiện tại):** background `#3B82F6` (xanh dương), icon + text `#FFFFFF`, font-weight 600
  - Collapsed: ẩn text, icon căn giữa, tooltip hiện tên khi hover

**Danh sách nav items (theo thứ tự từ trên xuống):**

```
Icon          Text              Route           Hiện với
─────────────────────────────────────────────────────────
HomeIcon      Dashboard         /dashboard      [ALL]
CalendarIcon  Cuộc họp          /meetings       [ALL]
SearchIcon    Tìm kiếm          /search         [ALL]
─────────────────────────────────────────────────────────
             (divider line 1px #334155, margin 8px 16px)
─────────────────────────────────────────────────────────
ShieldIcon    Quản trị          /admin/users    [ADMIN] only
```

> **Lưu ý:** Mục "Quản trị" chỉ render trong DOM khi user có role ADMIN. Không dùng CSS hide.

**Vùng 3 — User Info (bottom, height 72px):**
- Border-top: 1px solid `#334155`
- Background: `#0F172A`
- Padding: 12px 16px
- Layout: flex, align-items: center, gap: 10px
- **Avatar:** 36px × 36px, border-radius 50%, object-fit cover. Fallback: initials trên nền màu từ hash tên
- **Expanded — Text block:**
  - Dòng 1: Tên đầy đủ, font-size 13px, font-weight 600, màu `#F1F5F9`, truncate nếu dài
  - Dòng 2: Role badge inline — pill shape, font-size 10px, font-weight 700, uppercase
    - ADMIN: background `#DC2626`, text `#FFFFFF`
    - SECRETARY: background `#2563EB`, text `#FFFFFF`
    - USER: background `#475569`, text `#FFFFFF`
- **Collapsed:** Chỉ hiện avatar, căn giữa
- **Nút collapse/expand:** Icon `ChevronLeft` / `ChevronRight` (16px), màu `#64748B`, absolute position góc phải vùng này. Click toggle sidebar.

---

### HEADER — Mô tả chi tiết pixel-perfect

**Kích thước:**
- Height: **64px**, full width (trừ sidebar), fixed top
- Background: `#FFFFFF`
- Border-bottom: 1px solid `#E2E8F0`
- Box-shadow: `0 1px 3px rgba(0,0,0,0.06)`
- Padding: 0 24px
- Layout: flex, align-items: center, justify-content: space-between

**Bên trái — Breadcrumb:**
- Font-size: 14px, màu `#64748B`
- Trang hiện tại: font-weight 600, màu `#1E293B`
- Separator: `/` màu `#CBD5E1`
- Ví dụ: `Cuộc họp / Chi tiết cuộc họp`

**Bên phải — Actions (flex, gap 8px):**

1. **Notification Bell:**
   - Icon `BellIcon` 20px, màu `#64748B`
   - Wrapper: 36px × 36px, border-radius 8px, hover background `#F1F5F9`
   - Badge số: 18px × 18px, border-radius 50%, background `#DC2626`, text `#FFFFFF`, font-size 10px, font-weight 700
   - Badge vị trí: top-right của icon, offset -4px -4px
   - Badge ẩn khi count = 0
   - Click → mở Notification Panel (slide-out từ phải)

2. **User Avatar Dropdown:**
   - Avatar: 36px × 36px, border-radius 50%, cursor pointer
   - Hover: ring 2px `#3B82F6`
   - Click → dropdown menu xuất hiện bên dưới, align-right:
     ```
     ┌─────────────────────────┐
     │ 👤 Tên đầy đủ           │
     │    role@department      │
     ├─────────────────────────┤
     │ 🙍 Hồ sơ cá nhân        │  → /profile
     │ 🔒 Đổi mật khẩu         │  → /profile/password
     ├─────────────────────────┤
     │ 🚪 Đăng xuất            │  → POST /auth/logout → /login
     └─────────────────────────┘
     ```
   - Dropdown: background `#FFFFFF`, border 1px `#E2E8F0`, border-radius 10px, shadow `0 4px 16px rgba(0,0,0,0.12)`
   - Mỗi item: height 40px, padding 0 16px, font-size 14px, hover background `#F8FAFC`
   - Divider: 1px `#F1F5F9`
   - "Đăng xuất": màu text `#DC2626`

---

### LAYOUT TỔNG THỂ (áp dụng cho mọi trang có sidebar)

```
┌──────────────────────────────────────────────────────────┐
│  HEADER (height: 64px, full width)                       │
├────────────┬─────────────────────────────────────────────┤
│            │                                             │
│  SIDEBAR   │   CONTENT AREA                              │
│  (240px    │   (flex-grow: 1, overflow-y: auto)          │
│   fixed)   │   padding: 24px                             │
│            │                                             │
│            │                                             │
└────────────┴─────────────────────────────────────────────┘
```

- Content area background: `#F8FAFC`
- Content area padding: 24px
- Max content width: 1200px (căn giữa nếu màn hình rộng hơn)

---

## Danh sách tất cả trang

| # | Người truy cập | Tên trang | Route |
|---|---------------|-----------|-------|
| 1 | Public | Login Page | `/login` |
| 2 | `[ALL]` | Dashboard | `/dashboard` |
| 3 | `[ALL]` | Meeting List | `/meetings` |
| 4 | `[ADMIN/SEC]` | Create Meeting | `/meetings/new` |
| 5 | `[ADMIN/SEC]` | Edit Meeting | `/meetings/{id}/edit` |
| 6 | `[ALL]` | Meeting Detail | `/meetings/{id}` |
| 7 | `[MEMBER]` | Meeting Room | `/meetings/{id}/room` |
| 8 | `[MEMBER]` | Minutes Page | `/meetings/{id}/minutes` |
| 9 | `[ALL]` | Search Page | `/search` |
| 10 | `[ALL]` | User Profile | `/profile` |
| 11 | `[ALL]` | Change Password | `/profile/password` |
| 12 | `[ADMIN]` | User Management | `/admin/users` |
| 13 | `[ADMIN]` | Room & Department | `/admin/rooms` |
| 14 | `[ADMIN]` | Storage Dashboard | `/admin/storage` |
| — | `[ALL]` | Notification Panel | *(component, slide-out)* |

---

---

## Page 1 — [Public] Login Page

**Route:** `/login`
**Truy cập:** Chỉ người chưa đăng nhập. Đã đăng nhập → redirect `/dashboard`.

### Layout
Split screen:
- **Trái (40%):** Branding — logo lớn, tagline "Hệ thống họp trực tuyến nội bộ", background gradient xanh dương
- **Phải (60%):** Form đăng nhập căn giữa dọc, max-width 400px

### Thành phần UI
- Logo + "Kolla Meeting" (trên form)
- Tiêu đề: "Đăng nhập"
- Input: Tên đăng nhập (icon 👤)
- Input: Mật khẩu (icon 🔒, toggle hiện/ẩn)
- Nút **"Đăng nhập"** (primary, full width)
- Alert lỗi inline: "Sai tên đăng nhập hoặc mật khẩu" (màu đỏ, hiện khi lỗi)
- Loading spinner trên nút khi đang gọi API

### Trạng thái
- Default → Loading → Error → Success (redirect dashboard)

### API
```
POST /api/v1/auth/login
Body:     { username, password }
Response: { accessToken, refreshToken, expiresIn, user: { id, name, role, department } }
```

---

## Page 2 — [ALL] Dashboard

**Route:** `/dashboard`
**Truy cập:** Tất cả người dùng đã đăng nhập.

### Layout
Sidebar + Header. Content: grid responsive.

### Thành phần UI

**Row 1 — 4 Stats cards:**
- 📅 Cuộc họp hôm nay (tổng)
- 🟢 Đang diễn ra (ACTIVE) — highlight xanh lá
- 🔵 Sắp diễn ra (SCHEDULED)
- ⚫ Đã kết thúc hôm nay (ENDED)

**Row 2 — Cuộc họp sắp diễn ra:**
- Tiêu đề "Cuộc họp sắp diễn ra"
- Bảng: Tên | Thời gian | Phòng | Vai trò của tôi | Trạng thái | Action
- Action: "Vào phòng" (ACTIVE), "Xem chi tiết"
- Tối đa 5 dòng + link "Xem tất cả →"

**Row 3 — Thông báo gần đây:**
- 5 thông báo mới nhất (icon loại, nội dung, thời gian tương đối)
- Link "Xem tất cả →"

### API
```
GET /api/v1/meetings?status=ACTIVE,SCHEDULED&limit=5&sort=startTime
GET /api/v1/notifications?limit=5
```

---

## Page 3 — [ALL] Meeting List Page

**Route:** `/meetings`
**Truy cập:** Tất cả. `[ADMIN/SEC]` thấy thêm nút "Tạo cuộc họp".

### Layout
Sidebar + Header. Content: full width.

### Thành phần UI

**Toolbar:**
- Tiêu đề "Danh sách cuộc họp"
- Bộ lọc: Date range picker | Dropdown Phòng | Dropdown Phòng ban | Dropdown Trạng thái
- Nút **"+ Tạo cuộc họp"** (primary) — chỉ `[ADMIN/SEC]`

**Bảng:**
| Cột | Nội dung |
|-----|----------|
| Tên cuộc họp | Bold, clickable → Meeting Detail |
| Thời gian | Ngày giờ bắt đầu – kết thúc (UTC+7) |
| Phòng họp | Tên phòng |
| Host | Avatar + tên |
| Trạng thái | Badge: 🟢 ACTIVE / 🔵 SCHEDULED / ⚫ ENDED |
| Actions | Xem / Vào phòng / Sửa `[ADMIN/SEC]` / Xóa `[ADMIN]` |

**Pagination:** số trang, 10/20/50 per page

### Trạng thái
- Loading skeleton rows
- Empty: "Chưa có cuộc họp nào" + nút tạo mới
- Error toast

### API
```
GET    /api/v1/meetings?page=0&size=10&status=&roomId=&departmentId=&from=&to=
DELETE /api/v1/meetings/{id}   [ADMIN]
```

---

## Page 4 — [ADMIN/SEC] Create Meeting

**Route:** `/meetings/new`
**Truy cập:** Chỉ ADMIN và SECRETARY.

### Layout
Sidebar + Header. Form 2 cột, max-width 800px, căn giữa.

### Thành phần UI

**Section 1 — Thông tin cơ bản:**
- Input: Tiêu đề (required)
- Textarea: Mô tả
- Date-time picker: Bắt đầu (required)
- Date-time picker: Kết thúc (required)

**Section 2 — Địa điểm:**
- Dropdown: Phòng ban → lọc danh sách phòng
- Dropdown: Phòng họp (required)
- **Room Availability Indicator** (hiện sau khi chọn phòng + thời gian):
  - ✅ Xanh lá: "Phòng trống trong khung giờ này"
  - ❌ Đỏ: "Phòng đã có cuộc họp: [tên] lúc [giờ]"

**Section 3 — Nhân sự:**
- Dropdown search: Host — chỉ user SECRETARY/ADMIN (required)
- Dropdown search: Secretary — chỉ user SECRETARY (required)
- Multi-select search: Thêm thành viên

**Footer:**
- Nút "Hủy" (secondary) | Nút "Tạo cuộc họp" (primary)

### Validation
- Kết thúc > Bắt đầu
- Phòng không trùng lịch (409 từ API → hiện inline error)
- Host và Secretary bắt buộc

### API
```
POST /api/v1/meetings
GET  /api/v1/rooms
GET  /api/v1/rooms/{id}/availability?from=&to=
GET  /api/v1/departments
GET  /api/v1/users?role=SECRETARY,ADMIN
```

---

## Page 5 — [ADMIN/SEC] Edit Meeting

**Route:** `/meetings/{id}/edit`
**Truy cập:** Chỉ ADMIN và SECRETARY. Chỉ khi meeting ở trạng thái SCHEDULED.

### Layout & Thành phần UI
Giống Page 4 (Create Meeting), nhưng:
- Pre-fill toàn bộ dữ liệu hiện tại
- Tiêu đề form: "Chỉnh sửa cuộc họp"
- Nút footer: "Hủy" | "Lưu thay đổi"
- Hiển thị warning nếu thay đổi phòng/thời gian: "Thay đổi này có thể ảnh hưởng đến thành viên đã được mời"

### API
```
GET /api/v1/meetings/{id}
PUT /api/v1/meetings/{id}
GET /api/v1/rooms/{id}/availability?from=&to=&excludeMeetingId={id}
```

---

## Page 6 — [ALL] Meeting Detail Page

**Route:** `/meetings/{id}`
**Truy cập:** Tất cả. Nội dung và actions hiển thị khác nhau theo role.

### Layout
Sidebar + Header. 2 cột: thông tin chính (trái 60%) + sidebar phụ (phải 40%).

### Thành phần UI

**Header card (full width):**
- Tên cuộc họp (H1)
- Badge trạng thái
- Thời gian bắt đầu – kết thúc (UTC+7)
- Phòng họp | Phòng ban
- Action buttons (phải, hiện theo role + trạng thái):
  - "Kích hoạt phòng họp" — `[HOST]` khi SCHEDULED
  - "Vào phòng họp" — `[MEMBER]` khi ACTIVE
  - "Chỉnh sửa" — `[ADMIN/SEC]` khi SCHEDULED
  - "Xóa" — `[ADMIN]` khi SCHEDULED

**Cột trái — Tabs:**

*Tab Thành viên:*
- Danh sách: Avatar | Tên | Badge vai trò (Host / Secretary / Member) | Trạng thái tham dự
- Nút "Thêm thành viên" — `[ADMIN/SEC]`
- Nút xóa thành viên — `[ADMIN/SEC]`

*Tab Tài liệu:*
- Danh sách: icon loại | tên | kích thước | người upload | ngày | Download / Xóa
- Nút "Upload tài liệu" — `[MEMBER]`

*Tab Ghi âm:*
- Danh sách recordings: tên | thời lượng | kích thước | ngày | Download / Xóa `[ADMIN]`
- Hiện khi ENDED

*Tab Biên bản:*
- Badge trạng thái biên bản
- Nút xem / download / link sang Minutes Page
- Hiện khi ENDED

*Tab Lịch sử tham dự:*
- Bảng: Tên | Giờ vào | Giờ ra | Thời gian tham dự
- Hiện khi ENDED

**Cột phải:**
- Host: avatar + tên
- Secretary: avatar + tên
- Mô tả cuộc họp
- Transcription priority badge (HIGH 🔴 / NORMAL ⚪)
- Nút đổi priority — `[ADMIN/SEC]`

### API
```
GET  /api/v1/meetings/{id}
GET  /api/v1/meetings/{id}/members
POST /api/v1/meetings/{id}/members        [ADMIN/SEC]
DELETE /api/v1/meetings/{id}/members/{userId}  [ADMIN/SEC]
POST /api/v1/meetings/{id}/activate       [HOST]
GET  /api/v1/meetings/{id}/documents
POST /api/v1/meetings/{id}/documents      [MEMBER]
GET  /api/v1/meetings/{id}/recordings
GET  /api/v1/meetings/{id}/minutes
PUT  /api/v1/meetings/{id}/priority       [ADMIN/SEC]
```

---

## Page 7 — [MEMBER] Meeting Room Page ⭐

**Route:** `/meetings/{id}/room`
**Truy cập:** Chỉ thành viên của meeting. Meeting phải ở trạng thái ACTIVE.
**Layout đặc biệt:** Toàn màn hình, KHÔNG có Sidebar/Header thông thường.

### Layout
- **Jitsi iframe (trái, ~65%):** Video conference full height
- **Control Panel (phải, ~35%):** Tất cả controls, scrollable

### Jitsi Area (trái)
- Iframe Jitsi Meet
- Overlay top: tên cuộc họp + đồng hồ đếm thời gian họp (HH:MM:SS)
- Overlay bottom: nút "🚪 Rời phòng" (đỏ)

### Control Panel — 6 sections (phải)

**A — Meeting Info Bar:**
- Tên cuộc họp (truncate nếu dài)
- Badge mode: `FREE MODE` (xanh lá) | `MEETING MODE` (tím)
- Badge priority: `🔴 LIVE` (nếu HIGH_PRIORITY + MEETING_MODE)

**B — Mode Toggle** *(chỉ `[HOST]`):*
- Toggle switch lớn: "Chế độ tự do" ↔ "Chế độ họp chính thức"
- Mô tả ngắn bên dưới
- Confirm dialog khi switch sang MEETING_MODE: "Tất cả micro sẽ bị tắt"

**C — Participants:**
- Tiêu đề "Người tham dự (N)"
- List: Avatar | Tên | Badge (Host/Secretary) | 🎤 speaking indicator
- Người đang giữ Speaking Permission: highlight nền + icon mic xanh

**D — Raise Hand** *(chỉ hiện khi MEETING_MODE):*

`[MEMBER]` view:
- Nút lớn "✋ Xin phát biểu" (khi chưa raise)
- Nút "Hạ tay" màu cam (khi đã raise, đang chờ)
- Badge "🎤 Đang phát biểu" màu xanh (khi đang giữ permission)

`[HOST]` view:
- Tiêu đề "Hàng chờ phát biểu"
- List chronological: Avatar | Tên | Thời gian xin | Nút "Cho phép" (xanh) | Nút "Bỏ qua" (xám)
- Banner "Đang phát biểu: [Tên]" + nút "Thu hồi quyền" (đỏ)

**E — Live Transcription** *(chỉ HIGH_PRIORITY + MEETING_MODE):*
- Tiêu đề "Phiên âm trực tiếp 🔴"
- Scrollable, auto-scroll xuống khi có segment mới:
  ```
  [10:05:30] Nguyễn Văn A
  Xin chào, tôi muốn phát biểu về vấn đề ngân sách...

  [10:06:15] Trần Thị B
  Tôi đồng ý với ý kiến trên...
  ```
- Spinner "Đang phiên âm..." khi đang xử lý
- Banner "⚠️ Phiên âm không khả dụng" khi Gipformer down

**F — Meeting Controls** *(chỉ `[HOST/SEC]`):*
- Nút "🔴 Kết thúc cuộc họp" (đỏ)
- Confirm dialog: "Cuộc họp sẽ kết thúc và biên bản sẽ được tạo tự động"

### Trạng thái đặc biệt
- **Waiting Timeout:** Banner đỏ đếm ngược "Cuộc họp tự kết thúc sau: MM:SS"
- **Reconnecting:** Overlay mờ + spinner "Đang kết nối lại..."
- **Host transferred:** Toast "Quyền điều hành đã chuyển sang [Tên]"
- **Gipformer down:** Badge cảnh báo trên section E

### API & WebSocket
```
POST /api/v1/meetings/{id}/join
POST /api/v1/meetings/{id}/leave
POST /api/v1/meetings/{id}/mode                    [HOST]
POST /api/v1/meetings/{id}/raise-hand              [MEMBER]
DELETE /api/v1/meetings/{id}/raise-hand            [MEMBER]
GET  /api/v1/meetings/{id}/raise-hand              [HOST]
POST /api/v1/meetings/{id}/speaking-permission/{userId}  [HOST]
DELETE /api/v1/meetings/{id}/speaking-permission   [HOST]
POST /api/v1/meetings/{id}/end                     [HOST/SEC]

WS Subscribe: /topic/meeting/{id}
Events nhận: MODE_CHANGED, RAISE_HAND, HAND_LOWERED,
             SPEAKING_PERMISSION_GRANTED, SPEAKING_PERMISSION_REVOKED,
             PARTICIPANT_JOINED, PARTICIPANT_LEFT,
             HOST_TRANSFERRED, HOST_RESTORED,
             WAITING_TIMEOUT_STARTED, WAITING_TIMEOUT_CANCELLED,
             TRANSCRIPTION_SEGMENT, TRANSCRIPTION_UNAVAILABLE,
             TRANSCRIPTION_RECOVERED, MEETING_ENDED, PRIORITY_CHANGED
```

---

## Page 8 — [MEMBER] Minutes Page

**Route:** `/meetings/{id}/minutes`
**Truy cập:** Thành viên của meeting. Chỉ sau khi meeting ENDED.
**Hiển thị khác nhau theo role và trạng thái biên bản.**

### Layout
Sidebar + Header. 2 cột: PDF/Editor (trái 60%) + Action panel (phải 40%).

---

### Variant A — Trạng thái DRAFT

**Ai thấy:** Tất cả MEMBER. `[HOST]` thấy thêm nút xác nhận.

*Cột trái:*
- PDF viewer (iframe) — bản nháp tự động từ phiên âm
- Nút "⬇️ Tải bản nháp"

*Cột phải:*
- Badge "📄 Bản nháp — Chờ Host xác nhận"
- Thông tin: Tạo lúc [thời gian] | [N] đoạn phiên âm
- `[HOST]` thấy:
  - Nút "✅ Xác nhận biên bản" (primary)
  - Confirm dialog: "Chữ ký số sẽ được nhúng. Bạn chắc chắn?"
  - Reminder: "⏰ Vui lòng xác nhận trong 24 giờ"

---

### Variant B — Trạng thái HOST_CONFIRMED

**Ai thấy:** Tất cả MEMBER. `[SEC]` thấy editor để chỉnh sửa.

*Cột trái (Secretary view):*
- Rich text editor (TipTap) với nội dung phiên âm
- Toolbar: **B** *I* • 1. (bold, italic, bullet, numbered)
- Nội dung chỉnh sửa được trực tiếp

*Cột trái (Member/Host view):*
- PDF viewer — bản Host đã xác nhận (có chữ ký số)

*Cột phải:*
- Badge "✅ Host đã xác nhận"
- Thông tin: Host [tên] xác nhận lúc [thời gian]
- `[SEC]` thấy: Nút "📤 Xuất bản biên bản" (primary) + confirm dialog

---

### Variant C — Trạng thái SECRETARY_CONFIRMED

**Ai thấy:** Tất cả MEMBER.

*Cột trái:*
- Tabs: "Bản Host xác nhận" | "Bản Secretary chỉnh sửa"
- PDF viewer tương ứng tab

*Cột phải:*
- Badge "🎉 Đã xuất bản"
- Host xác nhận: [tên] lúc [thời gian]
- Secretary xuất bản: [tên] lúc [thời gian]
- Download buttons:
  - "⬇️ Tải bản nháp (PDF)"
  - "⬇️ Tải bản Host xác nhận (PDF + chữ ký số)"
  - "⬇️ Tải bản Secretary (PDF)"

### API
```
GET  /api/v1/meetings/{id}/minutes
POST /api/v1/meetings/{id}/minutes/confirm          [HOST]
PUT  /api/v1/meetings/{id}/minutes/edit             [SEC]
GET  /api/v1/meetings/{id}/minutes/download?version=draft|confirmed|secretary
```

---

## Page 9 — [ALL] Search Page

**Route:** `/search`
**Truy cập:** Tất cả người dùng đã đăng nhập.

### Layout
Sidebar + Header. Content: full width.

### Thành phần UI

**Search bar (nổi bật, full width):**
- Input lớn + icon 🔍
- Placeholder: "Tìm kiếm cuộc họp, nội dung phiên âm..."
- Nút "Tìm kiếm"

**Tabs kết quả:**
- "📅 Cuộc họp (N kết quả)"
- "📝 Phiên âm (N kết quả)"

**Tab Cuộc họp — Bộ lọc phụ:**
- Date range | Phòng | Phòng ban | Người tạo

**Tab Cuộc họp — Kết quả (card list):**
- Tên meeting (bold, link) | Thời gian | Phòng | Host | Badge trạng thái

**Tab Phiên âm — Kết quả (card list):**
- Đoạn text với **highlight từ khóa**
- Người nói | Thời điểm | Tên cuộc họp (link)

**Pagination** cho cả 2 tab

### Trạng thái
- Initial: "Nhập từ khóa để tìm kiếm" (empty illustration)
- Loading: skeleton cards
- No results: "Không tìm thấy kết quả cho '[keyword]'"

### API
```
GET /api/v1/search/meetings?q=&from=&to=&roomId=&departmentId=&page=&size=
GET /api/v1/search/transcriptions?q=&page=&size=
```

---

## Page 10 — [ALL] User Profile Page

**Route:** `/profile`
**Truy cập:** Tất cả người dùng — xem và chỉnh sửa thông tin cá nhân của mình.

### Layout
Sidebar + Header. Content: 2 cột, max-width 900px, căn giữa.

### Thành phần UI

**Cột trái — Avatar & Info card:**
- Avatar lớn (120px) + nút "Đổi ảnh đại diện"
- Tên đầy đủ (H2)
- Badge role: đỏ (ADMIN) / xanh dương (SECRETARY) / xám (USER)
- Phòng ban
- Ngày tạo tài khoản

**Cột phải — Form chỉnh sửa:**
- Input: Họ tên (editable)
- Input: Email (editable)
- Input: Tên đăng nhập (readonly — không đổi được)
- Dropdown: Phòng ban (readonly — chỉ ADMIN đổi được)
- Nút "Lưu thay đổi" (primary)

**Section dưới — Hoạt động gần đây:**
- Danh sách 5 cuộc họp gần nhất đã tham dự
- Mỗi item: Tên meeting | Ngày | Vai trò | Thời gian tham dự

**Section — Bảo mật:**
- Link "🔒 Đổi mật khẩu" → navigate `/profile/password`
- Thông tin: "Đăng nhập lần cuối: [thời gian]"

### API
```
GET /api/v1/users/me
PUT /api/v1/users/{id}
GET /api/v1/meetings?memberId=me&limit=5&sort=startTime,desc
```

---

## Page 11 — [ALL] Change Password Page

**Route:** `/profile/password`
**Truy cập:** Tất cả người dùng — đổi mật khẩu của chính mình.

### Layout
Sidebar + Header. Form nhỏ, max-width 480px, căn giữa.

### Thành phần UI
- Tiêu đề "Đổi mật khẩu"
- Input: Mật khẩu hiện tại (required, toggle show/hide)
- Input: Mật khẩu mới (required, toggle show/hide)
  - Password strength indicator (weak/medium/strong)
- Input: Xác nhận mật khẩu mới (required)
- Nút "Cập nhật mật khẩu" (primary)
- Nút "Hủy" (secondary) → back to profile

### Validation
- Mật khẩu mới ≥ 8 ký tự
- Xác nhận phải khớp mật khẩu mới
- Không được trùng mật khẩu cũ

### Trạng thái
- Success: Toast "Đổi mật khẩu thành công. Vui lòng đăng nhập lại." → redirect login
- Error: "Mật khẩu hiện tại không đúng"

### API
```
PUT /api/v1/users/{id}/change-password
Body: { currentPassword, newPassword }
```

---

## Page 12 — [ADMIN] User Management

**Route:** `/admin/users`
**Truy cập:** Chỉ ADMIN.

### Layout
Sidebar + Header. Content: full width.

### Thành phần UI

**Toolbar:**
- Tiêu đề "Quản lý người dùng"
- Search input: tìm theo tên / email / username
- Dropdown: Role (Tất cả / ADMIN / SECRETARY / USER)
- Dropdown: Phòng ban
- Nút "**+ Tạo người dùng**" (primary)

**Bảng:**
| Cột | Nội dung |
|-----|----------|
| Người dùng | Avatar + Tên + Email |
| Username | Tên đăng nhập |
| Role | Badge: 🔴 ADMIN / 🔵 SECRETARY / ⚫ USER |
| Phòng ban | Tên |
| Ngày tạo | Định dạng ngày |
| Actions | ✏️ Sửa / 🔑 Reset mật khẩu / 🗑️ Xóa |

**Modal Tạo/Sửa User:**
- Input: Họ tên (required)
- Input: Tên đăng nhập (required, unique)
- Input: Email
- Input: Mật khẩu (chỉ khi tạo mới)
- Dropdown: Role (required)
- Dropdown: Phòng ban
- Nút Lưu / Hủy

**Modal Reset Mật khẩu:**
- Warning: "Mật khẩu tạm thời sẽ được tạo. Người dùng cần đổi mật khẩu khi đăng nhập lần tiếp."
- Nút "Xác nhận" (đỏ) / "Hủy"
- Sau khi reset: hiển thị mật khẩu tạm thời để admin thông báo

**Modal Xóa User:**
- Warning nếu user đang có meeting membership: "User này đang là thành viên của N cuộc họp"
- Nút "Xác nhận xóa" (đỏ) / "Hủy"

### API
```
GET    /api/v1/users?page=&size=&role=&departmentId=&q=
POST   /api/v1/users
PUT    /api/v1/users/{id}
DELETE /api/v1/users/{id}
POST   /api/v1/users/{id}/reset-password
GET    /api/v1/departments
```

---

## Page 13 — [ADMIN] Room & Department Management

**Route:** `/admin/rooms`
**Truy cập:** Chỉ ADMIN.

### Layout
Sidebar + Header. Content: Tabs ngang.

### Tab 1: Phòng ban

**Toolbar:** Tiêu đề "Phòng ban" + Nút "+ Tạo phòng ban"

**Bảng:**
| Cột | Nội dung |
|-----|----------|
| Tên phòng ban | |
| Số phòng họp | |
| Số thành viên | |
| Actions | ✏️ Sửa / 🗑️ Xóa |

**Modal Tạo/Sửa Phòng ban:**
- Input: Tên (required)
- Textarea: Mô tả
- Nút Lưu / Hủy

### Tab 2: Phòng họp

**Toolbar:** Tiêu đề "Phòng họp" + Dropdown filter phòng ban + Nút "+ Tạo phòng họp"

**Bảng:**
| Cột | Nội dung |
|-----|----------|
| Tên phòng | |
| Phòng ban | |
| Sức chứa | N người |
| Trạng thái hiện tại | 🟢 Trống / 🔴 Đang sử dụng |
| Actions | ✏️ Sửa / 🗑️ Xóa |

**Modal Tạo/Sửa Phòng họp:**
- Input: Tên phòng (required)
- Dropdown: Phòng ban (required)
- Input: Sức chứa (số người)
- Textarea: Mô tả / vị trí
- Nút Lưu / Hủy

**Xóa phòng:** Không cho xóa nếu có SCHEDULED/ACTIVE meeting → hiện warning.

### API
```
GET    /api/v1/departments
POST   /api/v1/departments
PUT    /api/v1/departments/{id}
DELETE /api/v1/departments/{id}
GET    /api/v1/rooms
POST   /api/v1/rooms
PUT    /api/v1/rooms/{id}
DELETE /api/v1/rooms/{id}
```

---

## Page 14 — [ADMIN] Storage Dashboard

**Route:** `/admin/storage`
**Truy cập:** Chỉ ADMIN.

### Layout
Sidebar + Header. Content: full width.

### Thành phần UI

**Section 1 — Tổng quan:**
- Progress bar lớn: "Đã dùng X GB / Y GB (Z%)"
- Màu: xanh lá (<60%) → vàng (60–80%) → đỏ (>80%)
- Warning banner đỏ nếu >80%: "⚠️ Dung lượng sắp đầy!"

**Section 2 — Breakdown (4 cards ngang):**
- 🎥 Ghi âm: X GB
- 📄 Tài liệu: X GB
- 🎤 Audio chunks: X GB
- 📋 Biên bản PDF: X GB

**Section 3 — Bulk Delete:**
- Tiêu đề "Xóa dữ liệu cũ"
- Dropdown: "Xóa ghi âm cũ hơn" → 1 tuần / 1 tháng / 3 tháng / Tùy chỉnh
- Nếu "Tùy chỉnh": input số ngày
- Nút "Xem trước" → hiện: "Sẽ xóa N file, tổng X GB"
- Nút "Xóa ngay" (đỏ) → Confirm dialog:
  - "Bạn sắp xóa N file (X GB). Không thể hoàn tác."
  - Nút "Xác nhận xóa" / "Hủy"

**Section 4 — Lịch sử xóa:**
- Bảng: Thời gian | Admin | Số file | Dung lượng giải phóng | Loại thao tác

### API
```
GET  /api/v1/storage/stats
POST /api/v1/storage/bulk-delete
     Body: { olderThanDays: 30, fileTypes: ["recordings"] }
```

---

## Component — [ALL] Notification Panel

**Không phải trang riêng.** Slide-out panel từ icon 🔔 trên Header.
**Truy cập:** Tất cả người dùng đã đăng nhập.

### Trigger & Behavior
- Click icon chuông → panel trượt ra từ phải (overlay, không đẩy layout)
- Badge số đỏ trên icon = số thông báo chưa đọc
- Click ngoài panel → đóng lại

### Thành phần UI
- Header: "Thông báo" + Nút "Đánh dấu tất cả đã đọc"
- Danh sách (scrollable):
  - Icon loại (📅 meeting / 📄 document / 📋 minutes / ⚙️ system)
  - Nội dung ngắn (1–2 dòng)
  - Thời gian tương đối ("5 phút trước")
  - Background khác biệt cho thông báo chưa đọc (nền xanh nhạt)
  - Click → navigate đến trang liên quan + mark as read
- Empty state: "Không có thông báo mới 🎉"

### API & WebSocket
```
GET /api/v1/notifications?page=0&size=20
PUT /api/v1/notifications/{id}/read
PUT /api/v1/notifications/read-all

WS Subscribe: /user/queue/notifications
```

---

## Tóm tắt — Bảng tất cả trang

| # | Người truy cập | Tên trang | Route | Độ phức tạp | Ưu tiên |
|---|---------------|-----------|-------|-------------|---------|
| 1 | Public | Login | `/login` | Thấp | 🔴 |
| 2 | `[ALL]` | Dashboard | `/dashboard` | Trung | 🔴 |
| 3 | `[ALL]` | Meeting List | `/meetings` | Trung | 🔴 |
| 4 | `[ADMIN/SEC]` | Create Meeting | `/meetings/new` | Trung | 🔴 |
| 5 | `[ADMIN/SEC]` | Edit Meeting | `/meetings/{id}/edit` | Trung | 🟡 |
| 6 | `[ALL]` | Meeting Detail | `/meetings/{id}` | Trung | 🟡 |
| 7 | `[MEMBER]` | **Meeting Room** | `/meetings/{id}/room` | **Cao** | 🔴 |
| 8 | `[MEMBER]` | Minutes | `/meetings/{id}/minutes` | Trung | 🟡 |
| 9 | `[ALL]` | Search | `/search` | Thấp | 🟢 |
| 10 | `[ALL]` | User Profile | `/profile` | Thấp | 🟡 |
| 11 | `[ALL]` | Change Password | `/profile/password` | Thấp | 🟡 |
| 12 | `[ADMIN]` | User Management | `/admin/users` | Trung | 🟢 |
| 13 | `[ADMIN]` | Room & Department | `/admin/rooms` | Thấp | 🟢 |
| 14 | `[ADMIN]` | Storage Dashboard | `/admin/storage` | Thấp | 🟢 |
| — | `[ALL]` | Notification Panel | *(component)* | Thấp | 🟡 |

---

## Ghi chú cho Stitch/Figma

- **Datetime:** Luôn hiển thị UTC+7 (Asia/Ho_Chi_Minh)
- **Role-based UI:** Dùng annotation hoặc variant để đánh dấu element nào chỉ hiện với role nào
- **Meeting Room:** Trang duy nhất full screen, không có Sidebar/Header
- **Minutes Page:** Cần thiết kế 3 variant riêng biệt (DRAFT / HOST_CONFIRMED / SECRETARY_CONFIRMED)
- **Responsive:** Desktop 1280px+ là primary; mobile cần thiết cho Login, Dashboard, Meeting List, Profile
- **Loading states:** Skeleton loading cho mọi danh sách và trang
- **Empty states:** Illustration + text cho mọi danh sách rỗng
- **Error states:** Toast notification góc trên phải cho lỗi API
- **Confirm dialogs:** Mọi hành động nguy hiểm (xóa, kết thúc họp, reset password, bulk delete)
- **Shared components cần thiết kế 1 lần:** Sidebar, Header, Notification Panel, Confirm Dialog, Pagination, Status Badge, Avatar, Role Badge
