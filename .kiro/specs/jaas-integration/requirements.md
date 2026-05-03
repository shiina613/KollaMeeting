# Requirements Document

## Introduction

Tích hợp JaaS (Jitsi as a Service) của 8x8 vào hệ thống Kolla Meeting để thay thế `meet.jit.si` hiện tại. Hệ thống hiện tại gặp hai vấn đề: (1) moderator bị yêu cầu đăng nhập Google/GitHub khi tham gia phòng họp, (2) phiên embed bị giới hạn 5 phút. JaaS free tier cung cấp 25 MAU/tháng miễn phí, không giới hạn thời gian, không yêu cầu user đăng nhập Jitsi, và sử dụng domain `8x8.vc` thay vì `meet.jit.si`.

Thay đổi chính:
- **Backend**: Thêm endpoint `GET /meetings/{id}/jaas-token` để generate JWT token theo chuẩn JaaS cho từng user.
- **Frontend**: Cập nhật `JitsiFrame` để sử dụng domain `8x8.vc`, truyền JWT token, và format `roomName` theo chuẩn JaaS (`{appId}/{meetingCode}`).
- **Config**: Thêm biến môi trường `JAAS_APP_ID` và `JAAS_PRIVATE_KEY` vào backend; `VITE_JAAS_APP_ID` vào frontend.

## Glossary

- **JaaS**: Jitsi as a Service — dịch vụ video conferencing được quản lý bởi 8x8, sử dụng domain `8x8.vc`.
- **JaaS_Token_Service**: Service backend chịu trách nhiệm generate JWT token cho JaaS.
- **JitsiFrame**: Component React frontend nhúng Jitsi Meet qua `JitsiMeetExternalAPI`.
- **JWT**: JSON Web Token — token xác thực được ký bằng RS256 private key, dùng để xác thực user với JaaS.
- **AppID**: Định danh ứng dụng JaaS, lấy từ JaaS console (8x8.vc), dùng trong cả JWT và room name.
- **Private_Key**: RSA private key (PEM format) được tạo trong JaaS console, dùng để ký JWT.
- **Room_Name**: Tên phòng JaaS theo format `{AppID}/{meetingCode}`.
- **Meeting_Code**: Mã phòng họp 20 ký tự được generate bởi hệ thống Kolla.
- **MAU**: Monthly Active User — đơn vị tính giới hạn của JaaS free tier (25 MAU/tháng).
- **MeetingRoom**: Component React container chứa `JitsiFrame` và các panel phụ trợ.
- **SecurityConfig**: Cấu hình Spring Security của backend Kolla.

## Requirements

### Requirement 1: Backend — JaaS JWT Token Generation

**User Story:** As a meeting participant, I want the backend to generate a valid JaaS JWT token for me, so that I can join the video conference without being prompted to log in to a third-party account.

#### Acceptance Criteria

1. THE `JaaS_Token_Service` SHALL expose endpoint `GET /api/v1/meetings/{id}/jaas-token` requiring a valid Kolla JWT in the `Authorization` header.
2. WHEN a request to `GET /api/v1/meetings/{id}/jaas-token` is received, THE `JaaS_Token_Service` SHALL verify that the requesting user is a member of the meeting with the given `id`.
3. IF the requesting user is not a member of the meeting, THEN THE `JaaS_Token_Service` SHALL return HTTP 403 Forbidden.
4. IF the meeting with the given `id` does not exist, THEN THE `JaaS_Token_Service` SHALL return HTTP 404 Not Found.
5. WHEN generating a JaaS JWT, THE `JaaS_Token_Service` SHALL set the JWT `kid` header to `vpaas-magic-cookie-{AppID}/{keyId}` where `keyId` is extracted from the `JAAS_API_KEY` environment variable.
6. WHEN generating a JaaS JWT, THE `JaaS_Token_Service` SHALL set the JWT `alg` header to `RS256`.
7. WHEN generating a JaaS JWT, THE `JaaS_Token_Service` SHALL set the `iss` claim to `chat`.
8. WHEN generating a JaaS JWT, THE `JaaS_Token_Service` SHALL set the `aud` claim to `jitsi`.
9. WHEN generating a JaaS JWT, THE `JaaS_Token_Service` SHALL set the `sub` claim to `{AppID}`.
10. WHEN generating a JaaS JWT, THE `JaaS_Token_Service` SHALL set the `room` claim to `{meetingCode}` (chỉ meeting code, không bao gồm AppID prefix).
11. WHEN generating a JaaS JWT, THE `JaaS_Token_Service` SHALL set the `exp` claim to the current time plus 3600 seconds (1 giờ).
12. WHEN generating a JaaS JWT, THE `JaaS_Token_Service` SHALL set the `nbf` claim to the current time minus 10 seconds (để tránh clock skew).
13. WHEN generating a JaaS JWT, THE `JaaS_Token_Service` SHALL include a `context.user` object containing `id` (user ID as string), `name` (full name), `email`, and `avatar` (empty string if not available).
14. WHEN generating a JaaS JWT, THE `JaaS_Token_Service` SHALL include a `context.features` object with `livestreaming: false`, `outbound-call: false`, `sip-outbound-call: false`, and `transcription: false`.
15. WHEN the requesting user is the host or secretary of the meeting, THE `JaaS_Token_Service` SHALL set `context.user.moderator` to `true` in the JWT.
16. WHEN the requesting user is not the host or secretary of the meeting, THE `JaaS_Token_Service` SHALL set `context.user.moderator` to `false` in the JWT.
17. THE `JaaS_Token_Service` SHALL sign the JWT using the RSA private key loaded from the `JAAS_PRIVATE_KEY` environment variable.
18. THE `JaaS_Token_Service` SHALL return the generated JWT as a JSON response with field `token` (string) and `roomName` (string, format `{AppID}/{meetingCode}`).
19. IF the `JAAS_APP_ID` or `JAAS_PRIVATE_KEY` environment variables are not configured, THEN THE `JaaS_Token_Service` SHALL return HTTP 503 Service Unavailable with a descriptive error message.

### Requirement 2: Backend — Configuration & Dependencies

**User Story:** As a system administrator, I want JaaS credentials to be configurable via environment variables, so that I can manage them securely without modifying source code.

#### Acceptance Criteria

1. THE `JaaS_Token_Service` SHALL read the JaaS AppID from the environment variable `JAAS_APP_ID`.
2. THE `JaaS_Token_Service` SHALL read the JaaS API Key ID from the environment variable `JAAS_API_KEY` (format: `vpaas-magic-cookie-{AppID}/{keyId}`).
3. THE `JaaS_Token_Service` SHALL read the RSA private key (PEM format, PKCS#8) from the environment variable `JAAS_PRIVATE_KEY`.
4. THE `JaaS_Token_Service` SHALL use the `io.jsonwebtoken:jjwt` library (version 0.12.x) for JWT generation.
5. WHERE `JAAS_APP_ID` is set to a non-empty value, THE `JaaS_Token_Service` SHALL treat JaaS as enabled.
6. THE backend `SecurityConfig` SHALL permit unauthenticated access to the `/api/v1/meetings/{id}/jaas-token` endpoint pattern only after validating the Kolla JWT via the existing `JwtAuthenticationFilter`.

### Requirement 3: Frontend — JitsiFrame JaaS Integration

**User Story:** As a meeting participant, I want the video conference to load using JaaS without time limits or login prompts, so that I can participate in meetings seamlessly.

#### Acceptance Criteria

1. WHEN `JitsiFrame` is rendered with a `jwt` prop, THE `JitsiFrame` SHALL pass the JWT token to `JitsiMeetExternalAPI` via the `jwt` option.
2. WHEN JaaS is enabled (i.e., `VITE_JAAS_APP_ID` is set), THE `JitsiFrame` SHALL use domain `8x8.vc` instead of `meet.jit.si`.
3. WHEN JaaS is enabled, THE `JitsiFrame` SHALL use `roomName` in the format `{VITE_JAAS_APP_ID}/{meetingCode}` when initializing `JitsiMeetExternalAPI`.
4. WHEN JaaS is disabled (i.e., `VITE_JAAS_APP_ID` is not set), THE `JitsiFrame` SHALL fall back to using `VITE_JITSI_URL` (default: `https://meet.jit.si`) and the plain `meetingCode` as room name.
5. THE `JitsiFrame` SHALL load the `external_api.js` script from the configured Jitsi domain (`8x8.vc` or `meet.jit.si`).
6. WHEN the `external_api.js` script fails to load, THE `JitsiFrame` SHALL display an error message indicating the domain that failed.

### Requirement 4: Frontend — JaaS Token Fetching in MeetingRoom

**User Story:** As a meeting participant, I want the system to automatically fetch a JaaS token before joining the video conference, so that I don't need to perform any manual authentication steps.

#### Acceptance Criteria

1. WHEN `MeetingRoom` mounts and JaaS is enabled, THE `MeetingRoom` SHALL call `GET /api/v1/meetings/{id}/jaas-token` to fetch the JaaS JWT token before rendering `JitsiFrame`.
2. WHILE the JaaS token is being fetched, THE `MeetingRoom` SHALL display a loading indicator to the user.
3. IF the JaaS token fetch fails, THEN THE `MeetingRoom` SHALL display an error message and provide a retry option.
4. WHEN the JaaS token is successfully fetched, THE `MeetingRoom` SHALL pass the token and `roomName` to `JitsiFrame` via the `jwt` and `meetingCode` props respectively.
5. THE `MeetingRoom` SHALL re-fetch the JaaS token if the token is within 5 minutes of expiry (i.e., before the 55-minute mark after initial fetch).
6. WHEN JaaS is disabled, THE `MeetingRoom` SHALL render `JitsiFrame` without a JWT token, using the plain `meetingCode`.

### Requirement 5: Environment Configuration

**User Story:** As a developer, I want all JaaS-related configuration to be documented in `.env.example`, so that I can set up the integration correctly.

#### Acceptance Criteria

1. THE `.env.example` file SHALL include `JAAS_APP_ID` with an empty default value and a comment explaining it is the JaaS AppID from the 8x8 console.
2. THE `.env.example` file SHALL include `JAAS_API_KEY` with an empty default value and a comment explaining it is the full API key ID in format `vpaas-magic-cookie-{AppID}/{keyId}`.
3. THE `.env.example` file SHALL include `JAAS_PRIVATE_KEY` with an empty default value and a comment explaining it is the RSA private key in PEM format (PKCS#8), with newlines replaced by `\n`.
4. THE `.env.example` file SHALL include `VITE_JAAS_APP_ID` with an empty default value and a comment explaining it must match `JAAS_APP_ID`.
5. WHEN `JAAS_APP_ID` is empty, THE system SHALL operate in fallback mode using `meet.jit.si` without JWT authentication.

### Requirement 6: Security

**User Story:** As a system administrator, I want JaaS tokens to be generated server-side and short-lived, so that private keys are never exposed to the browser and tokens cannot be reused indefinitely.

#### Acceptance Criteria

1. THE `JaaS_Token_Service` SHALL never expose the `JAAS_PRIVATE_KEY` value in any API response, log output, or error message.
2. THE `JaaS_Token_Service` SHALL generate a new JWT token for each request to `GET /api/v1/meetings/{id}/jaas-token`.
3. THE generated JWT token SHALL have an expiry (`exp`) of no more than 3600 seconds from the time of generation.
4. THE `JaaS_Token_Service` SHALL only generate tokens for users who are authenticated members of the requested meeting.
5. THE frontend SHALL store the JaaS JWT token only in React component state (không lưu vào localStorage hoặc sessionStorage).
