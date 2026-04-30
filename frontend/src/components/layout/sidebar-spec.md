# Sidebar Specification — Source of Truth

> Trích xuất trực tiếp từ Stitch design (project 3653554561404883208, screen "Tổng quan - Kolla Meeting").
> File này là reference duy nhất khi code Sidebar. KHÔNG tự sáng tạo thêm.

---

## HTML Structure (từ Stitch)

```html
<nav class="bg-white font-inter text-sm font-medium fixed left-0 top-0 h-full w-64
            border-r border-slate-200 flex flex-col py-6 z-50">

  <!-- Logo section -->
  <div class="px-6 mb-8 flex flex-col gap-1">
    <h1 class="text-lg font-black text-blue-600 uppercase tracking-wider">Kolla</h1>
    <span class="text-slate-500 text-xs uppercase tracking-widest font-semibold">Meeting</span>
  </div>

  <!-- Nav items (flex-grow) -->
  <div class="flex-1 flex flex-col gap-1 w-full">

    <!-- ACTIVE item -->
    <a class="bg-blue-50 text-blue-600 border-r-4 border-blue-600 rounded-none
              flex items-center gap-3 px-4 py-3 transition-colors duration-150">
      <span class="material-symbols-outlined" style="font-variation-settings: 'FILL' 1;">
        dashboard
      </span>
      <span>Dashboard</span>
    </a>

    <!-- INACTIVE item -->
    <a class="text-slate-600 flex items-center gap-3 px-4 py-3
              hover:bg-slate-50 hover:text-slate-900 transition-colors duration-150">
      <span class="material-symbols-outlined">video_chat</span>
      <span>Meetings</span>
    </a>

  </div>

  <!-- Bottom section -->
  <div class="mt-auto flex flex-col gap-1 w-full pt-4 border-t border-slate-200">
    <a class="text-slate-600 flex items-center gap-3 px-4 py-3
              hover:bg-slate-50 hover:text-slate-900 transition-colors duration-150">
      <span class="material-symbols-outlined">logout</span>
      <span>Logout</span>
    </a>
  </div>

</nav>
```

---

## Pixel-perfect Values

| Property | Value | Tailwind class |
|----------|-------|----------------|
| Width | 256px | `w-64` |
| Position | fixed, left-0, top-0, full height | `fixed left-0 top-0 h-full` |
| Background | #ffffff | `bg-white` |
| Border right | 1px solid #e2e8f0 | `border-r border-slate-200` |
| Padding Y | 24px | `py-6` |
| Z-index | 50 | `z-50` |
| Font | Inter, 14px, medium | `font-inter text-sm font-medium` |

### Logo area
| Property | Value |
|----------|-------|
| Padding X | 24px (`px-6`) |
| Margin bottom | 32px (`mb-8`) |
| "Kolla" text | 18px, font-black (900), #2563eb (blue-600), uppercase, tracking-wider |
| Subtitle text | 12px, font-semibold (600), #64748b (slate-500), uppercase, tracking-widest |

### Nav item — INACTIVE
| Property | Value |
|----------|-------|
| Padding | 12px 16px (`px-4 py-3`) |
| Gap icon-text | 12px (`gap-3`) |
| Text color | #475569 (slate-600) |
| Icon color | #475569 (slate-600) |
| Hover background | #f8fafc (slate-50) |
| Hover text | #0f172a (slate-900) |
| Border radius | 0 (`rounded-none`) |
| Transition | 150ms ease-in-out |

### Nav item — ACTIVE
| Property | Value |
|----------|-------|
| Background | #eff6ff (blue-50) |
| Text color | #2563eb (blue-600) |
| Icon color | #2563eb (blue-600), FILL=1 |
| Border right | 4px solid #2563eb (`border-r-4 border-blue-600`) |
| Border radius | 0 (`rounded-none`) |
| Font weight | medium (500) |

### Bottom section
| Property | Value |
|----------|-------|
| Border top | 1px solid #e2e8f0 (`border-t border-slate-200`) |
| Padding top | 16px (`pt-4`) |

---

## Icons (Material Symbols Outlined)

Dùng Google Material Symbols Outlined. Load từ:
```html
<link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&display=swap" rel="stylesheet"/>
```

| Nav item | Icon name | FILL khi active |
|----------|-----------|-----------------|
| Dashboard | `dashboard` | FILL=1 |
| Cuộc họp | `video_chat` | FILL=1 |
| Tìm kiếm | `search` | FILL=1 |
| Quản trị | `admin_panel_settings` | FILL=1 |
| Hồ sơ | `person` | FILL=1 |
| Đăng xuất | `logout` | FILL=0 (không active) |

Icon size: 24px (default Material Symbols)
Icon style: `font-variation-settings: 'FILL' 1` khi active, `'FILL' 0` khi inactive

---

## Navigation Items (theo role)

```
[ALL roles]
  dashboard     → /dashboard
  video_chat    → /meetings
  search        → /search

[ADMIN only — hiện sau divider]
  admin_panel_settings → /admin/users

[Bottom section — ALL roles]
  person  → /profile
  logout  → POST /auth/logout
```

---

## Tailwind Config (từ Stitch)

```js
// tailwind.config.ts — copy chính xác từ Stitch
theme: {
  extend: {
    colors: {
      "primary":           "#005bbf",
      "primary-container": "#1a73e8",
      "background":        "#f7f9ff",
      "surface":           "#f7f9ff",
      "surface-container-lowest": "#ffffff",
      "surface-container-low":    "#f1f4fa",
      "surface-container":        "#ebeef4",
      "on-surface":        "#181c20",
      "on-surface-variant":"#414754",
      "outline":           "#727785",
      "outline-variant":   "#c1c6d6",
      "secondary":         "#2b5bb5",
      "secondary-container":"#759efd",
      "error":             "#ba1a1a",
    },
    borderRadius: {
      DEFAULT: "0.125rem",  // 2px
      lg:      "0.25rem",   // 4px
      xl:      "0.5rem",    // 8px
      full:    "0.75rem",   // 12px
    },
    spacing: {
      xs:     "4px",
      sm:     "8px",
      md:     "16px",
      lg:     "24px",
      xl:     "32px",
      "2xl":  "48px",
      "3xl":  "64px",
      gutter: "24px",
    },
    fontFamily: {
      inter: ["Inter", "system-ui", "sans-serif"],
    },
    fontSize: {
      "body-sm": ["14px", { lineHeight: "20px", fontWeight: "400" }],
      "body-md": ["16px", { lineHeight: "24px", fontWeight: "400" }],
      "body-lg": ["18px", { lineHeight: "28px", fontWeight: "400" }],
      "button":  ["15px", { lineHeight: "20px", fontWeight: "500", letterSpacing: "0.01em" }],
      "label-md":["12px", { lineHeight: "16px", fontWeight: "600", letterSpacing: "0.05em" }],
      "h3":      ["24px", { lineHeight: "32px", fontWeight: "600" }],
      "h2":      ["32px", { lineHeight: "40px", fontWeight: "600", letterSpacing: "-0.01em" }],
      "h1":      ["40px", { lineHeight: "48px", fontWeight: "700", letterSpacing: "-0.02em" }],
    },
  }
}
```

---

## Screens trong Stitch project (14 screens)

| Screen ID | Tên | Route tương ứng |
|-----------|-----|-----------------|
| `56bca10c65a24acea7d8ef07e0759872` | Tổng quan - Kolla Meeting | `/dashboard` |
| `c7fc120e800f400aab6ba8d4004c3cbf` | Danh sách cuộc họp | `/meetings` |
| `800f0cdd5eb641329871dd1915d31002` | Tạo cuộc họp | `/meetings/new` |
| `719e1d84934d4df0841a279ac19e0fca` | Chỉnh sửa cuộc họp | `/meetings/{id}/edit` |
| `c09431bfa7c849d5ad74cb763ab3e7d2` | Chi tiết cuộc họp | `/meetings/{id}` |
| `ac046f97e2164a68bef7c42534f6273b` | Phòng họp trực tuyến | `/meetings/{id}/room` |
| `3c7a4b4badfe4073aa2e8ec2377fb649` | Biên bản (Bản nháp) | `/meetings/{id}/minutes` |
| `521b432e239541b5a822fd6f8918d026` | Tìm kiếm | `/search` |
| `dd38a889e9684fc9ad99970a772df9dd` | Trang cá nhân | `/profile` |
| `51fe1afbf505434a8ae63c6bcbbc4f91` | Đổi mật khẩu | `/profile/password` |
| `b5ccbf0c20ff45e29abf9f9285503931` | Quản lý người dùng | `/admin/users` |
| `16041d57f305463e800908dfd948295a` | Quản lý Phòng & Phòng ban | `/admin/rooms` |
| `90f6d79ea5b2429580cc122b603a4e39` | Quản lý Dung lượng | `/admin/storage` |
| `c650fc6a261245a49b507d0a244569f4` | Đăng nhập | `/login` |
| `15a0acaf10be451d9317a3e1cf8f2e3b` | Design Document | *(reference only)* |
