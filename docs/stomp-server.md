# STOMP Server trong KollaMeeting

## 1. Bản chất

STOMP là giao thức nhắn tin chạy trên WebSocket. WebSocket chỉ tạo một kênh hai chiều giữa browser và server; STOMP bổ sung khái niệm destination, subscribe, publish, topic và queue.

Trong KollaMeeting, STOMP dùng cho các sự kiện realtime của cuộc họp:

- Thông báo cá nhân.
- Sự kiện trong phòng họp.
- Xin phát biểu/hủy xin phát biểu.
- Heartbeat để giữ attendance session sống.
- Thông báo biên bản sẵn sàng, đã xác nhận, đã publish.

Lưu ý: STOMP ở đây khác với Redis Queue. STOMP phục vụ realtime client-server. Redis Queue phục vụ xử lý nền giữa backend và ASR worker.

## 2. File liên quan

Backend:

- `backend/src/main/java/com/example/kolla/config/WebSocketConfig.java`
- `backend/src/main/java/com/example/kolla/websocket/MeetingEventPublisher.java`
- Các service gọi publisher: `RaiseHandServiceImpl`, `MeetingModeServiceImpl`, `SpeakingPermissionServiceImpl`, `MinutesServiceImpl`, `AttendanceServiceImpl`.

Frontend:

- `frontend/src/hooks/useWebSocket.ts`
- `frontend/package.json` dùng package `@stomp/stompjs`.

Nginx proxy:

- `nginx/nginx.conf`, location `/ws`.

## 3. Endpoint và broker trong backend

`WebSocketConfig` bật STOMP message broker bằng annotation:

```java
@EnableWebSocketMessageBroker
```

Backend đăng ký endpoint:

```text
/ws
/ws-sockjs
```

Trong Nginx, path public `/ws` được proxy về backend:

```text
/ws -> backend:8080/api/v1/ws
```

Do backend API có context path `/api/v1`, nên phía ngoài người dùng truy cập `/ws`, còn Nginx chuyển thành `/api/v1/ws`.

## 4. Destination prefix

Trong `configureMessageBroker()`:

```java
registry.enableSimpleBroker("/topic", "/queue");
registry.setApplicationDestinationPrefixes("/app");
registry.setUserDestinationPrefix("/user");
```

Ý nghĩa:

- `/topic`: broadcast cho nhiều client cùng subscribe.
- `/queue`: hàng đợi message trong STOMP, thường dùng cho message riêng.
- `/app`: message từ client gửi vào backend controller/service.
- `/user`: message riêng cho từng user đã xác thực.

KollaMeeting dùng simple in-memory broker của Spring, không dùng RabbitMQ/ActiveMQ làm STOMP broker ngoài.

## 5. Xác thực STOMP bằng JWT

Frontend tạo STOMP client trong `useWebSocket.ts`, gửi JWT ở CONNECT header:

```text
Authorization: Bearer <token>
```

Backend chặn frame `CONNECT` trong `configureClientInboundChannel()`.

Logic kiểm tra:

1. Lấy token từ native header `Authorization` hoặc fallback header `token`.
2. Gọi `jwtUtils.validateToken(token)`.
3. Kiểm tra token có trong Redis blacklist không.
4. Kiểm tra user-level invalidation key trong Redis không.
5. Nếu hợp lệ, tạo `UsernamePasswordAuthenticationToken` và gắn vào STOMP session.

Điểm quan trọng: nếu Redis lỗi khi kiểm tra blacklist, backend fail-closed, tức là từ chối kết nối. Cách này ưu tiên an toàn vì không muốn token đã revoke vẫn được dùng.

## 6. Frontend kết nối như thế nào?

`useWebSocket.ts` tự dựng URL:

```text
http  -> ws://host/ws
https -> wss://host/ws
```

Nếu có `VITE_WS_URL`, hook dùng biến môi trường đó. Nếu không, nó lấy `window.location.host`, nhờ vậy chạy được khi truy cập qua localhost, LAN IP hoặc tunnel.

Sau khi connect thành công, frontend subscribe:

```text
/user/queue/notifications
/topic/meeting/{meetingId}
```

Và cứ 15 giây publish heartbeat:

```text
/app/meeting/{meetingId}/heartbeat
```

Heartbeat này giúp backend không đánh dấu attendance session là stale. Comment trong code nói rõ: nếu thiếu heartbeat, upload audio có thể bị 403 vì attendance log đã bị đóng.

## 7. Luồng realtime trong cuộc họp

Luồng điển hình:

1. User mở trang meeting.
2. `useWebSocket()` tạo STOMP client.
3. Client CONNECT tới `/ws` kèm JWT.
4. Backend xác thực JWT.
5. Client subscribe `/topic/meeting/{meetingId}`.
6. Khi có sự kiện, backend dùng `MeetingEventPublisher`.
7. Publisher gọi `messagingTemplate.convertAndSend(...)`.
8. Tất cả client trong topic nhận event và cập nhật UI.

Ví dụ destination broadcast:

```text
/topic/meeting/12
```

Ví dụ notification cá nhân:

```text
/user/queue/notifications
```

## 8. STOMP dùng cho gì trong KollaMeeting?

Các nhóm sự kiện chính:

- Meeting lifecycle: bắt đầu, kết thúc, người tham gia vào/ra.
- Raise hand: người dùng xin phát biểu, hủy xin phát biểu.
- Speaking permission: host cấp hoặc thu hồi quyền phát biểu.
- Meeting mode: chuyển chế độ họp.
- Minutes: biên bản sẵn sàng, biên bản đã được host xác nhận, secretary publish.
- Notification cá nhân cho user.

`MeetingEventPublisher` là lớp gom logic gửi các event đó ra STOMP broker.

## 9. STOMP và audio WebSocket khác nhau thế nào?

KollaMeeting có 2 loại WebSocket:

```text
/ws       -> STOMP realtime event
/ws/audio -> raw binary audio stream
```

`/ws` dùng STOMP frame, dữ liệu thường là JSON event.

`/ws/audio` không dùng STOMP. Nó là WebSocket binary thuần, gửi PCM Int16 LE 16 kHz mono để backend tạo audio chunk và đưa vào ASR queue.

Tách 2 kênh này là hợp lý vì:

- Event realtime cần subscribe/topic/user queue.
- Audio cần stream binary liên tục, nhẹ và ít overhead.

## 10. Vai trò của Nginx với STOMP

Trong `nginx/nginx.conf`, location `/ws` có các header:

```nginx
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
proxy_read_timeout 86400s;
proxy_send_timeout 86400s;
```

Các header này cần cho WebSocket upgrade. Timeout dài giúp kết nối STOMP không bị Nginx cắt sớm trong lúc user đang họp.

## 11. Điểm cần nói khi bảo vệ

STOMP Server trong KollaMeeting là lớp realtime event bus giữa backend Spring Boot và frontend React. Nó không xử lý media Jitsi và không xử lý audio ASR. Nó chỉ gửi các sự kiện điều phối cuộc họp và thông báo UI. Backend dùng Spring simple broker, client dùng `@stomp/stompjs`, xác thực bằng JWT trong CONNECT frame và kiểm tra token blacklist qua Redis.
