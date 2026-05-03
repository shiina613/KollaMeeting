# Implementation Plan: JaaS Integration

## Overview

Tích hợp JaaS (Jitsi as a Service) của 8x8 vào Kolla Meeting. Backend thêm endpoint generate JWT token ký bằng RS256; frontend cập nhật `JitsiFrame` và `MeetingRoom` để dùng domain `8x8.vc` và truyền token. Hệ thống hỗ trợ fallback về `meet.jit.si` khi `JAAS_APP_ID` không được cấu hình.

## Tasks

- [x] 1. Thêm `ServiceUnavailableException` và handler vào `GlobalExceptionHandler`
  - Tạo file `backend/src/main/java/com/example/kolla/exceptions/ServiceUnavailableException.java`
  - Thêm `@ExceptionHandler(ServiceUnavailableException.class)` vào `GlobalExceptionHandler` trả về HTTP 503 với `ApiResponse.error(message)`
  - _Requirements: 1.19, 2.5_

- [x] 2. Tạo `JaasProperties` và đăng ký `@EnableConfigurationProperties`
  - Tạo file `backend/src/main/java/com/example/kolla/config/JaasProperties.java` với `@ConfigurationProperties(prefix = "jaas")`, các field `appId`, `apiKey`, `privateKey`, method `isEnabled()` và `extractKeyId()`
  - Thêm `@EnableConfigurationProperties(JaasProperties.class)` vào `KollaMeetingApplication`
  - Thêm binding vào `backend/src/main/resources/application.yml`:
    ```yaml
    jaas:
      app-id: ${JAAS_APP_ID:}
      api-key: ${JAAS_API_KEY:}
      private-key: ${JAAS_PRIVATE_KEY:}
    ```
  - _Requirements: 2.1, 2.2, 2.3, 2.5_

  - [x] 2.1 Viết unit test cho `JaasProperties`
    - Test `isEnabled()` trả về `false` khi `appId` null hoặc blank
    - Test `isEnabled()` trả về `true` khi `appId` có giá trị
    - Test `extractKeyId()` parse đúng từ format `vpaas-magic-cookie-{AppID}/{keyId}`
    - Test `extractKeyId()` trả về toàn bộ string khi không có dấu `/`
    - _Requirements: 2.1, 2.2, 2.5_

- [x] 3. Tạo `JaasTokenResponse` DTO
  - Tạo file `backend/src/main/java/com/example/kolla/responses/JaasTokenResponse.java` với field `token` (String) và `roomName` (String), dùng `@Data @AllArgsConstructor`
  - _Requirements: 1.18_

- [-] 4. Implement `JaasTokenService` và `JaasTokenServiceImpl`
  - Tạo interface `backend/src/main/java/com/example/kolla/services/JaasTokenService.java` với method `generateToken(Long meetingId, User currentUser)`
  - Tạo `backend/src/main/java/com/example/kolla/services/impl/JaasTokenServiceImpl.java`:
    - Inject `JaasProperties`, `MeetingRepository`, `MemberRepository`
    - Kiểm tra `jaasProperties.isEnabled()` → throw `ServiceUnavailableException` nếu false
    - Tìm meeting bằng `meetingRepository.findById()` → throw `ResourceNotFoundException` nếu không tồn tại
    - Kiểm tra membership bằng `memberRepository.existsByMeetingIdAndUserId()` → throw `ForbiddenException` nếu không phải member
    - Xác định `isModerator`: `true` nếu user là host hoặc secretary của meeting
    - Build JWT với header `alg=RS256`, `kid=vpaas-magic-cookie-{AppID}/{keyId}`, claims `iss="chat"`, `aud="jitsi"`, `sub={AppID}`, `room={meetingCode}`, `iat`, `nbf=iat-10s`, `exp=iat+3600s`, `context.user` (id, name, email, avatar, moderator), `context.features`
    - Parse RSA private key từ PEM string (replace `\n` literal → newline, strip PEM headers, Base64 decode, `PKCS8EncodedKeySpec`)
    - Ký JWT bằng `signWith(privateKey, SignatureAlgorithm.RS256)` (JJWT 0.11.5)
    - Catch `InvalidKeyException` và log message chung chung — KHÔNG expose giá trị private key
    - Trả về `JaasTokenResponse(token, appId + "/" + meetingCode)`
  - _Requirements: 1.1–1.19, 2.4, 6.1, 6.2, 6.3, 6.4_

  - [x] 4.1 Viết property test — Property 1: JWT claims integrity
    - **Property 1: JWT claims integrity**
    - **Validates: Requirements 1.7, 1.8, 1.9, 1.10, 1.13, 1.15, 1.16**
    - Dùng jqwik `@Property(tries = 100)` với `@ForAll` generators cho meeting và member user
    - Generate token, decode JWT body (không verify signature), assert `iss="chat"`, `aud="jitsi"`, `sub=appId`, `room=meetingCode`, `context.user.id=userId.toString()`, `context.user.name`, `context.user.email`, `context.user.moderator`

  - [x] 4.2 Viết property test — Property 2: JWT expiry window
    - **Property 2: JWT expiry window**
    - **Validates: Requirements 1.11, 1.12**
    - Dùng jqwik `@Property(tries = 100)`
    - Generate token, decode, assert `exp = iat + 3600`, `nbf = iat - 10`

  - [x] 4.3 Viết property test — Property 3: Moderator flag correctness
    - **Property 3: Moderator flag correctness**
    - **Validates: Requirements 1.15, 1.16**
    - Dùng jqwik `@Property(tries = 100)` với generator `meetingsWithRoles` (meeting có host/secretary và danh sách regular members)
    - Assert host/secretary → `moderator: true`; regular member → `moderator: false`

  - [x] 4.4 Viết property test — Property 4: Room name format consistency
    - **Property 4: Room name format consistency**
    - **Validates: Requirements 1.10, 1.18**
    - Dùng jqwik `@Property(tries = 100)` với `@ForAll` generators cho `meetingCode` và `appId`
    - Assert `roomName = appId + "/" + meetingCode`; JWT `room` claim = `meetingCode` (không có appId prefix)

  - [x] 4.5 Viết property test — Property 5: Access control — non-members are rejected
    - **Property 5: Access control — non-members are rejected**
    - **Validates: Requirements 1.2, 1.3, 6.4**
    - Dùng jqwik `@Property(tries = 100)` với generator cho user không thuộc member list của meeting
    - Assert `generateToken()` throw `ForbiddenException` cho mọi non-member user

  - [x] 4.6 Viết property test — Property 6: Private key never exposed
    - **Property 6: Private key never exposed**
    - **Validates: Requirements 6.1**
    - Dùng jqwik `@Property(tries = 100)` với các error scenario (invalid key, missing config, v.v.)
    - Assert exception message không chứa giá trị của `JAAS_PRIVATE_KEY`

  - [~] 4.7 Viết unit test cho `JaasTokenServiceImpl` (example-based)
    - Test generate token thành công cho member
    - Test `moderator: true` cho host, `moderator: true` cho secretary
    - Test `moderator: false` cho regular member
    - Test `moderator: false` khi host là null
    - Test 403 khi user không phải member
    - Test 404 khi meeting không tồn tại
    - Test 503 khi `JAAS_APP_ID` trống
    - _Requirements: 1.2, 1.3, 1.15, 1.16, 1.19_

- [~] 5. Tạo `JaasTokenController`
  - Tạo file `backend/src/main/java/com/example/kolla/controllers/JaasTokenController.java`
  - Endpoint `GET /meetings/{id}/jaas-token` với `@AuthenticationPrincipal User currentUser`
  - Gọi `jaasTokenService.generateToken(id, currentUser)` và trả về `ResponseEntity.ok(ApiResponse.success(response))`
  - Thêm `@Tag`, `@Operation`, `@SecurityRequirement` cho OpenAPI
  - Không thêm endpoint vào `permitAll()` trong `SecurityConfig` — endpoint được bảo vệ bởi `JwtAuthenticationFilter` như các endpoint khác
  - _Requirements: 1.1, 1.6_

  - [~] 5.1 Viết integration test cho `JaasTokenController` với MockMvc
    - Test 200 OK với valid Kolla JWT và user là member
    - Test 401 khi không có Kolla JWT
    - Test 403 khi user không phải member
    - Test 404 khi meeting không tồn tại
    - Test 503 khi JaaS chưa được cấu hình
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.19_

- [~] 6. Checkpoint — Backend
  - Ensure all backend tests pass, ask the user if questions arise.

- [~] 7. Cập nhật `.env.example` với các biến JaaS
  - Thêm section `JAAS INTEGRATION` vào `.env.example` với:
    - `JAAS_APP_ID=` — AppID từ JaaS console (8x8.vc), ví dụ: `vpaas-magic-cookie-abc123`
    - `JAAS_API_KEY=` — Full API Key ID, format: `vpaas-magic-cookie-{AppID}/{keyId}`
    - `JAAS_PRIVATE_KEY=` — RSA private key PEM (PKCS#8), newlines replace bằng `\n`
    - `VITE_JAAS_APP_ID=` — Phải match `JAAS_APP_ID`, dùng để quyết định domain và room name format ở frontend
  - Khi `JAAS_APP_ID` trống, hệ thống fallback về `meet.jit.si` (không breaking change)
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [~] 8. Tạo `jaasService.ts` ở frontend
  - Tạo file `frontend/src/services/jaasService.ts`
  - Export interface `JaasTokenResponse { token: string; roomName: string }`
  - Export async function `fetchJaasToken(meetingId: number): Promise<JaasTokenResponse>` gọi `apiClient.get<ApiResponse<JaasTokenResponse>>(\`/meetings/${meetingId}/jaas-token\`)` và trả về `response.data.data`
  - _Requirements: 4.1_

  - [~] 8.1 Viết unit test cho `jaasService.ts`
    - Mock `apiClient`, verify request URL đúng format `/meetings/{id}/jaas-token`
    - Verify response mapping trả về `{ token, roomName }`
    - _Requirements: 4.1_

- [~] 9. Cập nhật `JitsiFrame` để hỗ trợ JaaS domain và room name
  - Trong `frontend/src/components/meeting/JitsiFrame.tsx`:
    - Thêm `const JAAS_APP_ID = import.meta.env.VITE_JAAS_APP_ID ?? ''` và `const IS_JAAS = JAAS_APP_ID.length > 0`
    - Tính `EFFECTIVE_DOMAIN`: `'8x8.vc'` nếu `IS_JAAS`, ngược lại giữ `JITSI_DOMAIN`
    - Tính `SCRIPT_SRC`: `'https://8x8.vc/external_api.js'` nếu `IS_JAAS`, ngược lại `${JITSI_URL}/external_api.js`
    - Cập nhật `useEffect` load script để dùng `SCRIPT_SRC` thay vì `${JITSI_URL}/external_api.js`
    - Cập nhật `useEffect` khởi tạo API để dùng `EFFECTIVE_DOMAIN` thay vì `JITSI_DOMAIN`
    - Cập nhật error message trong `scriptError` render để hiển thị domain thực tế (`EFFECTIVE_DOMAIN` hoặc `JITSI_URL`)
    - Prop `jwt` và `meetingCode` đã có sẵn — không thay đổi interface
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [~] 9.1 Viết unit test cho `JitsiFrame`
    - Khi `VITE_JAAS_APP_ID` được set: domain là `8x8.vc`, script src là `https://8x8.vc/external_api.js`
    - Khi `VITE_JAAS_APP_ID` không set: domain là `VITE_JITSI_URL`, fallback behavior không thay đổi
    - Khi script load thất bại: hiển thị error message với domain thực tế
    - _Requirements: 3.2, 3.4, 3.5, 3.6_

- [~] 10. Cập nhật `MeetingRoom` để fetch JaaS token và truyền vào `JitsiFrame`
  - Trong `frontend/src/components/meeting/MeetingRoom.tsx`:
    - Thêm `const IS_JAAS = (import.meta.env.VITE_JAAS_APP_ID ?? '').length > 0`
    - Thêm state: `jaasToken`, `jaasRoomName`, `jaasLoading` (khởi tạo `IS_JAAS`), `jaasError`
    - Thêm `tokenRefreshTimerRef` để schedule re-fetch
    - Thêm `useEffect` fetch token khi mount (chỉ khi `IS_JAAS`), cleanup timer khi unmount
    - Implement `fetchToken()`: gọi `fetchJaasToken(meeting.id)`, set state, schedule refresh sau 55 phút (`setTimeout(fetchToken, 55 * 60 * 1000)`)
    - Khi `IS_JAAS && jaasLoading`: render loading indicator thay vì `JitsiFrame`
    - Khi `IS_JAAS && jaasError`: render error banner với nút "Thử lại" gọi `fetchToken()`
    - Cập nhật `JitsiFrame` props: `meetingCode={IS_JAAS && jaasRoomName ? jaasRoomName : meeting.meetingCode}`, `jwt={IS_JAAS ? jaasToken ?? undefined : undefined}`
    - Token chỉ lưu trong React state — KHÔNG lưu vào `localStorage` hoặc `sessionStorage`
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 6.5_

  - [~] 10.1 Viết unit test cho `MeetingRoom` (JaaS scenarios)
    - Khi JaaS enabled: hiển thị loading indicator trong khi fetch token
    - Khi token fetch thành công: `JitsiFrame` nhận đúng `jwt` và `meetingCode` (roomName format)
    - Khi token fetch thất bại: hiển thị error banner với nút retry
    - Khi JaaS disabled: `JitsiFrame` render với `meeting.meetingCode` và không có `jwt`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.6_

- [~] 11. Final checkpoint — Ensure all tests pass
  - Ensure all backend and frontend tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Backend dùng JJWT 0.11.5 (đã có sẵn trong `pom.xml`) — không cần thêm dependency mới
- Property tests dùng jqwik (đã có sẵn trong `pom.xml`: `net.jqwik:jqwik:1.8.2`)
- `SecurityConfig` không cần thay đổi — endpoint được bảo vệ bởi `JwtAuthenticationFilter` như các endpoint khác
- Khi `JAAS_APP_ID` trống, hệ thống fallback về `meet.jit.si` — không breaking change
- `JAAS_PRIVATE_KEY` không bao giờ được xuất hiện trong log, response, hay error message
