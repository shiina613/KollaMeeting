# BẢN ĐỒ PHÂN BỔ DỮ LIỆU KOLLAMEETING

Tài liệu này phân loại chi tiết các thực thể dữ liệu trong hệ thống **KollaMeeting**, giúp người phát triển đối chiếu trực tiếp giữa thiết kế cơ sở dữ liệu quan hệ (RDBMS MySQL) mô tả trong Đồ án tốt nghiệp và hạ tầng lưu trữ thực tế (File/RAM/Redis) của ứng dụng.

---

## 1. Nhóm Cơ sở Dữ liệu Quan hệ (MySQL Database)

Đây là **7 bảng nghiệp vụ cốt lõi** được thiết kế vật lý trong MySQL database thông qua migration [V1__initial_schema.sql](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/resources/db/migration/V1__initial_schema.sql). Nhóm này khớp 100% về mặt danh mục bảng so với Thuyết minh đồ án tốt nghiệp.

| STT | Tên bảng MySQL | Class Entity trong Java | Mô tả chức năng |
| :--- | :--- | :--- | :--- |
| 1 | `user` | [User.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/models/User.java) | Quản lý thông tin tài khoản nhân viên, chức vụ và quyền hạn đăng nhập. |
| 2 | `department` | [Department.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/models/Department.java) | Quản lý thông tin cơ cấu phòng ban trong đơn vị. |
| 3 | `room` | [Room.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/models/Room.java) | Quản lý danh sách các phòng họp vật lý (không bắt buộc đối với họp online). |
| 4 | `meeting` | [Meeting.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/models/Meeting.java) | Lưu trữ thông tin cuộc họp (lịch trình, mã cuộc họp, chủ tọa, thư ký, trạng thái). |
| 5 | `member` | [Member.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/models/Member.java) | Bảng trung gian liên kết danh sách thành viên được phân quyền tham gia từng cuộc họp. |
| 6 | `document` | [Document.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/models/Document.java) | Quản lý file đính kèm cuộc họp (bao gồm file tài liệu tải lên và các bản ghi biên bản đã hoàn thành). |
| 7 | `meeting_message` | [MeetingMessage.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/models/MeetingMessage.java) | Lưu lịch sử các tin nhắn trao đổi trong phòng chat của cuộc họp. |

---

## 2. Nhóm Dữ liệu Vận hành & Runtime (File / RAM / Redis)

Các thực thể dưới đây là các lớp đối tượng Java (POJO) thông thường, **không có annotation `@Entity` và không tạo bảng trong MySQL**. Chúng được lưu trữ và truy xuất thông qua lớp điều phối trung gian [RuntimeMeetingStateStore.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/runtime/RuntimeMeetingStateStore.java).

### 2.1. Dữ liệu lưu trữ dạng File cục bộ (Local Filesystem)
Lưu tại thư mục cuộc họp trên server (`/storage/meetings/<meeting_id>/`) để tối ưu hóa hiệu năng, giảm tải cho MySQL:

* **Câu thoại đã phiên âm (`TranscriptionSegment`)**:
  * *Class mã nguồn:* [TranscriptionSegment.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/models/TranscriptionSegment.java)
  * *Dạng lưu trữ:* File văn bản định dạng JSON Lines (`segments.jsonl`). Mỗi câu nói của thành viên sau khi chạy mô hình ASR xong sẽ được ghi tiếp (append) vào tệp này.
* **Tiến trình phiên âm ASR (`TranscriptionJob`)**:
  * *Class mã nguồn:* [TranscriptionJob.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/models/TranscriptionJob.java)
  * *Dạng lưu trữ:* File cấu hình `.properties` nằm trong thư mục `/transcript/jobs/`.
* **Trạng thái Biên bản cuộc họp (`Minutes`)**:
  * *Class mã nguồn:* [Minutes.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/models/Minutes.java)
  * *Dạng lưu trữ:* File cấu hình `state.properties` nằm trong thư mục `/minutes/`.
* **Thông tin File ghi âm (`Recording`)**:
  * *Class mã nguồn:* [Recording.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/models/Recording.java)
  * *Dạng lưu trữ:* File cấu hình `.properties` nằm trong thư mục `/recordings/`.
* **Lịch sử điểm danh (`AttendanceLog`)**:
  * *Class mã nguồn:* [AttendanceLog.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/models/AttendanceLog.java)
  * *Dạng lưu trữ:* File cấu hình `.properties` lưu trữ lịch sử ra/vào cuộc họp.

### 2.2. Dữ liệu thời gian thực (In-Memory / RAM & Redis Cache)
Lưu tạm trên bộ nhớ của Server hoặc Redis cache để phản hồi tức thời cho các sự kiện WebSocket và tự động giải phóng khi kết thúc cuộc họp:

* **Hàng đợi xin phát biểu (Raise Hand Queue)**:
  * *Dạng lưu trữ:* Redis cache (thông qua [RaiseHandQueueServiceImpl.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/services/impl/RaiseHandQueueServiceImpl.java)).
* **Phiên kết nối WebSocket (`ParticipantSession`)**:
  * *Class mã nguồn:* [ParticipantSession.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/models/ParticipantSession.java)
  * *Dạng lưu trữ:* Lưu tạm trong RAM máy chủ (ConcurrentHashMap) để kiểm soát kết nối heartbeat.
* **Cấp quyền phát biểu (`SpeakingPermission`)**:
  * *Class mã nguồn:* [SpeakingPermission.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/models/SpeakingPermission.java)
  * *Dạng lưu trữ:* Lưu tạm trong RAM để điều phối quyền điều khiển Micro của phòng họp có kiểm soát.
* **Thông báo tức thời (`Notification`)**:
  * *Class mã nguồn:* [Notification.java](file:///c:/Users/quang/Documents/datn/Shiina/KollaMeeting/backend/src/main/java/com/example/kolla/models/Notification.java)
  * *Dạng lưu trữ:* Lưu tạm trong RAM để phục vụ đẩy thông báo thời gian thực về trình duyệt người dùng qua WebSocket.

---

> [!NOTE]
> **Kết luận**:
> Cấu hình cơ sở dữ liệu quan hệ MySQL thực tế của hệ thống **chỉ bao gồm đúng 7 bảng** nghiệp vụ. Thiết kế này trùng khớp hoàn chỉnh với thiết kế logic được vẽ và mô tả trong Thuyết minh đồ án tốt nghiệp, đảm bảo tính nhất quán tuyệt đối khi trình bày trước Hội đồng.
