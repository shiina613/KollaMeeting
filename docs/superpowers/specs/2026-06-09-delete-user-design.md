# Thiết Kế Tính Năng Xóa Người Dùng

## Bối Cảnh

Màn hình quản trị người dùng hiện có hành động vô hiệu hóa/kích hoạt lại tài khoản. Yêu cầu mới là đổi hành động này thành xóa người dùng, nhưng vẫn giữ nguyên icon `block` đang dùng trên nút hành động.

Schema hiện tại đang được căn theo tài liệu Word đã nộp. Bảng `user` đang được các bảng khác tham chiếu, đặc biệt là `member.User_id` và `document.User_id`. Vì vậy, nếu xóa thẳng một user đã từng tham gia cuộc họp hoặc tải tài liệu lên hệ thống, dữ liệu lịch sử có thể bị hỏng hoặc bị database chặn do ràng buộc khóa ngoại.

## Mục Tiêu

Thay luồng vô hiệu hóa người dùng bằng luồng xóa người dùng. Hệ thống cần xóa hoàn toàn user khi an toàn, đồng thời vẫn bảo toàn lịch sử cuộc họp, tài liệu và biên bản khi xóa cứng có nguy cơ làm hỏng dữ liệu liên quan.

## Hành Vi Đề Xuất

Backend xử lý xóa người dùng theo 2 nhánh:

1. Nếu user chưa có dữ liệu lịch sử liên quan, xóa cứng record trong bảng `user`.
2. Nếu user đã được tham chiếu bởi thành viên cuộc họp, tài liệu hoặc dữ liệu lịch sử khác, không xóa record. Thay vào đó, thực hiện xóa an toàn bằng cách ẩn và ẩn danh hóa record user hiện có.

Xóa an toàn nghĩa là:

- Đổi mã nhân viên/username sang giá trị đánh dấu, ví dụ `deleted-user-<id>`.
- Đổi họ tên thành `Người dùng đã xóa`.
- Xóa các thông tin cá nhân có thể để trống: email, số điện thoại, ngày sinh, học vị, CCCD/CMND, địa chỉ, ngân hàng, số tài khoản, ảnh đại diện.
- Đổi mật khẩu sang giá trị ngẫu nhiên không thể đoán.
- Vô hiệu hóa các token hiện có để tài khoản không thể tiếp tục đăng nhập.
- Giữ nguyên `id`, `Department_id` và các record lịch sử để dữ liệu cuộc họp, tài liệu, biên bản vẫn đọc được.
- Loại user đã xóa khỏi danh sách quản trị, danh sách user active, danh sách chọn chủ trì/thư ký/thành viên và kết quả tìm kiếm user.

## Thiết Kế Frontend

Trong `frontend/src/components/admin/UserManagement.tsx`:

- Thay import và lời gọi `toggleUserActive` bằng `deleteUser`.
- Đổi tên state/modal từ nhóm toggle/deactivate sang nhóm delete.
- Giữ nguyên icon material `block` trên nút hành động trong từng dòng user.
- Đổi tooltip, aria-label, tiêu đề dialog và nút xác nhận sang ngữ nghĩa xóa người dùng.
- Dialog cần cảnh báo rõ đây là thao tác xóa người dùng. Nếu user có lịch sử liên quan, backend sẽ tự chuyển sang xóa an toàn để không làm hỏng dữ liệu cũ.
- Sau khi xóa thành công, đóng dialog và tải lại danh sách user.
- Nếu xóa thất bại, hiển thị message từ backend khi có. Nếu không có message, dùng fallback: `Không thể xóa người dùng. Vui lòng thử lại.`

Trong `frontend/src/services/userService.ts`:

- Giữ hàm `deleteUser(id)` gọi `DELETE /users/{id}`.
- Có thể giữ `toggleUserActive` nếu cần tương thích ngược, nhưng UI quản trị không dùng luồng này nữa.

## Thiết Kế Backend

Trong `backend/src/main/java/com/example/kolla/controllers/UserController.java`:

- Thêm endpoint `DELETE /users/{id}`.
- Chỉ cho phép role `ADMIN` gọi endpoint này bằng Spring Security pre-authorization với biểu thức `hasRole('ADMIN')`.
- Gọi `userService.deleteUser(id, currentUser)`.
- Trả về `ApiResponse<Void>` khi thao tác thành công.

Trong `UserServiceImpl`:

- Giữ bảo vệ không cho admin xóa chính tài khoản đang đăng nhập.
- Kiểm tra user có dữ liệu tham chiếu hay không, không chỉ kiểm tra cuộc họp `SCHEDULED`/`ACTIVE`.
- Nếu không có tham chiếu, xóa cứng user bằng `userRepository.delete(target)`.
- Nếu có tham chiếu, thực hiện xóa an toàn/anonymize thay vì xóa record.
- Vô hiệu hóa token trong cả 2 trường hợp xóa cứng và xóa an toàn.

Trong `UserRepository` và các repository liên quan:

- Bổ sung query kiểm tra user có dữ liệu lịch sử hay không, tối thiểu gồm membership và document theo schema hiện tại.
- Loại user có username dạng `deleted-user-<id>` khỏi list/search/candidate query.

## Xử Lý Lỗi

- Nếu admin cố xóa chính mình, trả `BadRequestException` với message rõ ràng.
- Nếu target user không tồn tại, trả `ResourceNotFoundException`.
- Nếu database phát sinh lỗi ràng buộc ngoài dự kiến, không hiển thị raw SQL cho frontend.
- Frontend hiển thị lỗi từ backend nếu có, nếu không thì dùng message fallback dễ hiểu.

## Kiểm Thử

Backend cần test:

- `DELETE /users/{id}` chỉ cho `ADMIN` và gọi đúng service.
- User không có dữ liệu liên quan bị xóa cứng.
- User có dữ liệu liên quan bị ẩn danh hóa, không bị xóa record.
- Không thể xóa chính tài khoản đang đăng nhập.
- User đã xóa an toàn không còn xuất hiện trong list/search/candidate.

Frontend cần test:

- Nút hành động vẫn dùng icon `block` nhưng mở dialog xóa người dùng.
- Bấm xác nhận gọi `deleteUser(id)`.
- Xóa thành công thì đóng dialog và reload danh sách.
- Xóa thất bại thì hiện lỗi.

## Ngoài Phạm Vi

- Không thêm cột mới như `deleted_at` hoặc `is_deleted`, để tránh lệch schema đang căn theo tài liệu Word.
- Không cascade-delete cuộc họp, thành viên, tài liệu, biên bản, recording hoặc transcript.
- Không đổi icon `block` vì user yêu cầu giữ nguyên icon đó.
