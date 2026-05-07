# Kịch bản hội thoại test Speech-to-Text

> **Mục đích**: Kiểm tra độ chính xác nhận dạng giọng nói tiếng Việt trong các điều kiện khác nhau  
> **Thời lượng ước tính**: ~10–15 phút  
> **Số người**: 3 người (Host, Thư ký, Thành viên)  
> **Chủ đề**: Họp tổng kết dự án phần mềm

---

## Hướng dẫn trước khi test

1. Vào meeting ở chế độ **MEETING_MODE** (chế độ họp)
2. Host cấp quyền phát biểu cho từng người trước khi họ đọc
3. Đọc **to, rõ ràng**, tốc độ **vừa phải** (không quá nhanh)
4. Sau mỗi lượt, dừng **2–3 giây** rồi người tiếp theo mới bắt đầu
5. Quan sát panel "Phiên âm trực tiếp" bên phải

---

## Kịch bản: Họp tổng kết Sprint 3

---

### 🎙️ LƯỢT 1 — Host (tungnq)
> *Câu ngắn, phát âm chuẩn — test baseline*

> "Xin chào tất cả mọi người. Hôm nay chúng ta họp tổng kết sprint ba. Tôi muốn nghe báo cáo tiến độ từ từng thành viên."

---

### 🎙️ LƯỢT 2 — Thành viên (thuanld)
> *Câu có số và từ kỹ thuật — test số và thuật ngữ*

> "Trong sprint này tôi đã hoàn thành mười lăm task, đạt tám mươi bảy phần trăm so với kế hoạch. Tôi gặp vấn đề với API authentication ở endpoint đăng nhập. Lỗi trả về mã bốn trăm linh một."

---

### 🎙️ LƯỢT 3 — Thư ký (haihn)
> *Câu dài, liên tục — test xử lý câu dài*

> "Tôi đã tổng hợp tất cả các yêu cầu từ khách hàng trong tuần qua và nhận thấy rằng có ba vấn đề chính cần được giải quyết trước khi bàn giao sản phẩm, bao gồm việc tối ưu hóa hiệu suất trang chủ, sửa lỗi hiển thị trên thiết bị di động, và cập nhật tài liệu hướng dẫn sử dụng."

---

### 🎙️ LƯỢT 4 — Host (tungnq)
> *Câu hỏi ngắn — test dấu câu và ngữ điệu lên*

> "Anh Thuận ơi, vấn đề API đó đã được giải quyết chưa? Nếu chưa thì cần bao nhiêu ngày nữa?"

---

### 🎙️ LƯỢT 5 — Thành viên (thuanld)
> *Trả lời có số ngày và cam kết — test tính cam kết trong văn bản*

> "Dạ, vấn đề đó tôi đã tìm ra nguyên nhân rồi ạ. Do thiếu header Authorization trong request. Tôi cần thêm hai ngày để fix và viết unit test. Dự kiến hoàn thành vào thứ Sáu tuần này."

---

### 🎙️ LƯỢT 6 — Thư ký (haihn)
> *Từ viết tắt và tên riêng — test từ đặc biệt*

> "Tôi ghi nhận. Như vậy deadline cho bug này là ngày chín tháng năm. Anh Thuận nhớ push code lên GitHub và tạo pull request để team review nhé. Tôi sẽ cập nhật vào Jira."

---

### 🎙️ LƯỢT 7 — Host (tungnq)
> *Giọng có cảm xúc, nhấn mạnh — test khi nói có cảm xúc*

> "Rất tốt! Cả team đã làm việc rất chăm chỉ trong sprint này. Tôi đặc biệt ấn tượng với tốc độ xử lý của hệ thống. Chúng ta đã giảm thời gian phản hồi từ hai giây xuống còn năm trăm mili giây. Đây là một cải tiến rất đáng kể."

---

### 🎙️ LƯỢT 8 — Thành viên (thuanld)
> *Nói nhanh hơn một chút — test khi tốc độ tăng*

> "Cảm ơn anh. Tôi cũng muốn đề xuất chúng ta nên dùng Redis để cache các query phổ biến. Tôi đã thử nghiệm ở local và kết quả khá tốt, giảm được khoảng sáu mươi phần trăm số lần truy vấn database."

---

### 🎙️ LƯỢT 9 — Thư ký (haihn)
> *Tổng kết — câu đủ loại độ dài*

> "Vậy tôi xin tổng kết các quyết định trong buổi họp hôm nay. Một: anh Thuận fix bug API trước ngày chín tháng năm. Hai: team sẽ nghiên cứu giải pháp Redis trong sprint bốn. Ba: cập nhật tài liệu hướng dẫn trước khi bàn giao. Mọi người có ý kiến gì thêm không?"

---

### 🎙️ LƯỢT 10 — Host (tungnq)
> *Câu kết thúc — test câu ngắn cuối*

> "Không có gì thêm. Cảm ơn mọi người đã tham dự. Buổi họp kết thúc tại đây. Hẹn gặp lại vào thứ Hai tuần sau."

---

## Checklist đánh giá sau test

| # | Tiêu chí | Đạt | Không đạt | Ghi chú |
|---|----------|-----|-----------|---------|
| 1 | Nhận dạng câu ngắn đơn giản (lượt 1, 4, 10) | ☐ | ☐ | |
| 2 | Nhận dạng số (mười lăm, tám mươi bảy, bốn trăm linh một) | ☐ | ☐ | |
| 3 | Nhận dạng từ kỹ thuật (API, endpoint, GitHub, Redis, Jira) | ☐ | ☐ | |
| 4 | Nhận dạng câu dài liên tục (lượt 3) | ☐ | ☐ | |
| 5 | Phân biệt người nói (tên hiển thị đúng) | ☐ | ☐ | |
| 6 | Thứ tự đoạn phiên âm đúng theo thời gian | ☐ | ☐ | |
| 7 | Timestamp hiển thị đúng (không còn Invalid Date) | ☐ | ☐ | |
| 8 | Không mất đoạn khi chuyển người nói | ☐ | ☐ | |

---

> **Lưu ý**: Sau khi test xong, Host kết thúc meeting để hệ thống tổng hợp biên bản tự động.  
> Kiểm tra file biên bản được tạo ra có đủ nội dung không.
