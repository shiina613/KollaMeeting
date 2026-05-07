# Danh sách tài khoản hệ thống KollaMeeting

> **Password hiện tại của tất cả tài khoản: `12345678`**
> Cập nhật lần cuối: 2026-05-07

---

## Tài khoản hệ thống

| # | Username | Password | Role | Họ tên | Ghi chú |
|---|----------|----------|------|--------|---------|
| 1 | `admin` | `12345678` | ADMIN | System Administrator | Tài khoản quản trị hệ thống |
| 2 | `tungnq` | `12345678` | SECRETARY | Nguyễn Quang Tùng | |
| 3 | `thuanld` | `12345678` | USER | Lê Đức Thuận | |
| 4 | `haihn` | `12345678` | SECRETARY | Huỳnh Ngọc Hải | |
| 5 | `vinhct` | `12345678` | USER | Cao Thanh Vinh | |
| 6 | `secretary1` | `12345678` | SECRETARY | Thu Ký 1 | |
| 7 | `user1` | `12345678` | USER | Người Dùng 1 | |

---

## Phân quyền theo Role (sau TASK-001)

| Quyền | ADMIN | SECRETARY (của meeting) | HOST | USER |
|-------|-------|------------------------|------|------|
| Activate meeting | ❌ | ✅ (chỉ meeting của mình) | ✅ | ❌ |
| End meeting | ❌ | ✅ | ✅ | ❌ |
| Switch mode (FREE ↔ MEETING) | ❌ | ❌ | ✅ | ❌ |
| Grant/Revoke speaking permission | ❌ | ❌ | ✅ | ❌ |
| Xem danh sách meeting | ✅ | ✅ | ✅ | ✅ |
| Tạo/Sửa/Xóa meeting | ✅ | — | — | — |
| Quản lý users | ✅ | — | — | — |

---

> ⚠️ **Lưu ý bảo mật**: File này chỉ dùng cho môi trường **development/test**.
> Không commit file này lên production repository.
