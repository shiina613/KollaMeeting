# Design Document: JaaS Integration

## Overview

Tích hợp JaaS (Jitsi as a Service) của 8x8 vào Kolla Meeting để thay thế `meet.jit.si` hiện tại. Mục tiêu là loại bỏ hai vấn đề: (1) moderator bị yêu cầu đăng nhập Google/GitHub khi tham gia phòng, (2) phiên embed bị giới hạn 5 phút.

JaaS free tier cung cấp 25 MAU/tháng, không giới hạn thời gian, không yêu cầu user đăng nhập Jitsi. Xác thực được thực hiện qua JWT token ký bằng RSA-256 private key do JaaS console cấp.

**Thay đổi chính:**
- Backend thêm endpoint `GET /api/v1/meetings/{id}/jaas-token` để generate JWT token theo chuẩn JaaS.
- Frontend cập nhật `JitsiFrame` để dùng domain `8x8.vc`, truyền JWT, và format `roomName` theo chuẩn JaaS.
- `MeetingRoom` fetch token trước khi render `JitsiFrame`.
- Config thêm `JAAS_APP_ID`, `JAAS_API_KEY`, `JAAS_PRIVATE_KEY` (backend) và `VITE_JAAS_APP_ID` (frontend).

**Backward compatibility:** Khi `JAAS_APP_ID` không được set, hệ thống fallback về `meet.jit.si` như hiện tại — không breaking change.

---

## Architecture

### High-Level Flow

```mermaid
sequenceDiagram
    participant Browser as Browser (React)
    participant Backend as Backend (Spring Boot)
    participant JaaS as JaaS (8x8.vc)

    Browser->>Backend: GET /api/v1/meetings/{id}/jaas-token<br/>(Authorization: Bearer <kolla-jwt>)
    Backend->>Backend: Verify Kolla JWT (JwtAuthenticationFilter)
    Backend->>Backend: Check user is member of meeting
    Backend->>Backend: Build JWT claims (user info, moderator flag, room, exp)
    Backend->>Backend: Sign JWT with RS256 private key (JAAS_PRIVATE_KEY)
    Backend-->>Browser: { token: "eyJ...", roomName: "{appId}/{meetingCode}" }

    Browser->>JaaS: JitsiMeetExternalAPI("8x8.vc", { roomName, jwt })
    JaaS->>JaaS: Validate JWT (RS256, kid lookup)
    JaaS-->>Browser: WebRTC video conference (no login prompt, no time limit)
```

### Component Diagram

```mermaid
graph TD
    subgraph Frontend
        MR[MeetingRoom]
        JF[JitsiFrame]
        JS[jaasService.ts]
    end

    subgraph Backend
        JTC[JaasTokenController]
        JTS[JaasTokenService]
        JP[JaasProperties]
        MS[MeetingService]
        JF2[JwtAuthenticationFilter]
    end

    subgraph External
        JAAS[8x8.vc JaaS]
    end

    MR -->|fetch token| JS
    JS -->|GET /meetings/{id}/jaas-token| JTC
    MR -->|jwt + roomName props| JF
    JF -->|JitsiMeetExternalAPI| JAAS

    JTC --> JTS
    JTS --> JP
    JTS --> MS
    JTC --> JF2
```

### Design Decisions

**1. Token generation server-side (không client-side)**
Private key không bao giờ rời khỏi backend. Frontend chỉ nhận token đã ký. Đây là yêu cầu bắt buộc của JaaS.

**2. Giữ JJWT 0.11.5 (không nâng version)**
Dự án đã dùng JJWT 0.11.5 (xác nhận trong `pom.xml`). JJWT 0.11.5 hỗ trợ RS256 đầy đủ qua `signWith(privateKey, SignatureAlgorithm.RS256)` và custom header claims qua `setHeaderParam("kid", ...)`. Không cần nâng version để tránh rủi ro breaking change.

**3. `JaasProperties` dùng `@ConfigurationProperties` thay vì `@Value`**
Nhất quán với pattern `FileStorageProperties` đã có trong project. Cần thêm `@EnableConfigurationProperties(JaasProperties.class)` vào một `@Configuration` class (hoặc `KollaMeetingApplication`).

**4. Fallback mode**
Khi `JAAS_APP_ID` trống, `JaasProperties.isEnabled()` trả về `false`. Frontend kiểm tra `VITE_JAAS_APP_ID` để quyết định domain và room name format. `JitsiFrame` hiện tại dùng `JITSI_URL` env var — khi JaaS disabled, behavior không thay đổi.

**5. Token refresh tự động**
`MeetingRoom` set timer để re-fetch token trước khi hết hạn (55 phút sau khi fetch). Token mới được truyền xuống `JitsiFrame` qua prop — Jitsi External API hỗ trợ cập nhật JWT qua `executeCommand('overwriteConfig', { jwt })`.

**6. `JaasTokenResponse` đặt trong `responses/` package**
Nhất quán với tất cả response DTO hiện có (`MeetingResponse`, `MemberResponse`, v.v.) trong `com.example.kolla.responses`.

**7. Membership check dùng `MemberRepository.existsByMeetingIdAndUserId()`**
`MemberRepository` đã có sẵn method `existsByMeetingIdAndUserId(Long meetingId, Long userId)` — dùng trực tiếp, không cần query mới.

---

## Components and Interfaces

### Backend

#### `JaasProperties`

```java
// backend/src/main/java/com/example/kolla/config/JaasProperties.java
@Data
@ConfigurationProperties(prefix = "jaas")
public class JaasProperties {
    /** JaaS AppID từ 8x8 console. Ví dụ: vpaas-magic-cookie-abc123 */
    private String appId;

    /** Full API Key ID. Format: vpaas-magic-cookie-{AppID}/{keyId} */
    private String apiKey;

    /** RSA private key PEM (PKCS#8). Newlines được replace bằng \n trong env var. */
    private String privateKey;

    /** JaaS enabled khi appId không trống. */
    public boolean isEnabled() {
        return appId != null && !appId.isBlank();
    }

    /** Extract keyId từ apiKey. Ví dụ: "vpaas-magic-cookie-abc123/key456" → "key456" */
    public String extractKeyId() {
        if (apiKey == null) return "";
        int slash = apiKey.lastIndexOf('/');
        return slash >= 0 ? apiKey.substring(slash + 1) : apiKey;
    }
}
```

**Kích hoạt `@ConfigurationProperties`** — thêm vào `KollaMeetingApplication` hoặc một `@Configuration` class:
```java
@EnableConfigurationProperties(JaasProperties.class)
```

**`application.yml` binding:**
```yaml
jaas:
  app-id: ${JAAS_APP_ID:}
  api-key: ${JAAS_API_KEY:}
  private-key: ${JAAS_PRIVATE_KEY:}
```

#### `JaasTokenService`

```java
// backend/src/main/java/com/example/kolla/services/JaasTokenService.java
public interface JaasTokenService {
    /**
     * Generate JaaS JWT token cho user trong meeting.
     * @throws ServiceUnavailableException nếu JaaS chưa được cấu hình
     * @throws ForbiddenException nếu user không phải member
     * @throws ResourceNotFoundException nếu meeting không tồn tại
     */
    JaasTokenResponse generateToken(Long meetingId, User currentUser);
}
```

**Implementation — `JaasTokenServiceImpl`:**

```java
// backend/src/main/java/com/example/kolla/services/impl/JaasTokenServiceImpl.java
@Service
@RequiredArgsConstructor
public class JaasTokenServiceImpl implements JaasTokenService {

    private final JaasProperties jaasProperties;
    private final MeetingRepository meetingRepository;
    private final MemberRepository memberRepository;  // existsByMeetingIdAndUserId() đã có sẵn

    @Override
    public JaasTokenResponse generateToken(Long meetingId, User currentUser) {
        // 1. Check JaaS configured
        if (!jaasProperties.isEnabled()) {
            throw new ServiceUnavailableException("JaaS is not configured");
        }

        // 2. Find meeting
        Meeting meeting = meetingRepository.findById(meetingId)
            .orElseThrow(() -> new ResourceNotFoundException("Meeting not found: " + meetingId));

        // 3. Check membership — dùng MemberRepository.existsByMeetingIdAndUserId()
        if (!memberRepository.existsByMeetingIdAndUserId(meetingId, currentUser.getId())) {
            throw new ForbiddenException("User is not a member of this meeting");
        }

        // 4. Determine moderator flag — host hoặc secretary
        boolean isModerator = (meeting.getHost() != null
                && currentUser.getId().equals(meeting.getHost().getId()))
            || (meeting.getSecretary() != null
                && currentUser.getId().equals(meeting.getSecretary().getId()));

        // 5. Build and sign JWT
        String token = buildJwt(meeting, currentUser, isModerator);
        String roomName = jaasProperties.getAppId() + "/" + meeting.getCode();

        return new JaasTokenResponse(token, roomName);
    }

    private String buildJwt(Meeting meeting, User user, boolean isModerator) {
        // Parse private key, build claims, sign with RS256
        // See Data Models section for full JWT structure
        // Key parsing: replace literal \n with newlines, strip PEM headers,
        // decode Base64, use PKCS8EncodedKeySpec
    }
}
```

#### `JaasTokenController`

```java
// backend/src/main/java/com/example/kolla/controllers/JaasTokenController.java
@RestController
@RequestMapping("/meetings")
@RequiredArgsConstructor
@Tag(name = "JaaS", description = "JaaS JWT token generation")
@SecurityRequirement(name = "bearerAuth")
public class JaasTokenController {

    private final JaasTokenService jaasTokenService;

    /**
     * GET /api/v1/meetings/{id}/jaas-token
     * Generate JaaS JWT token cho user hiện tại.
     * Yêu cầu Kolla JWT hợp lệ trong Authorization header (handled by JwtAuthenticationFilter).
     * Requirements: 1.1–1.19
     */
    @GetMapping("/{id}/jaas-token")
    @Operation(summary = "Generate JaaS JWT token for a meeting")
    public ResponseEntity<ApiResponse<JaasTokenResponse>> getJaasToken(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        JaasTokenResponse response = jaasTokenService.generateToken(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
```

**Lưu ý về SecurityConfig:** Endpoint này KHÔNG cần thêm vào `permitAll()` trong `SecurityConfig`. Nó được bảo vệ bởi `JwtAuthenticationFilter` như tất cả các endpoint khác (`.anyRequest().authenticated()`). Không cần thay đổi `SecurityConfig`.

#### `JaasTokenResponse` DTO

```java
// backend/src/main/java/com/example/kolla/responses/JaasTokenResponse.java
@Data
@AllArgsConstructor
public class JaasTokenResponse {
    /** Signed JaaS JWT token */
    private String token;

    /** Room name in format {appId}/{meetingCode} */
    private String roomName;
}
```

### Frontend

#### `jaasService.ts`

```typescript
// frontend/src/services/jaasService.ts
export interface JaasTokenResponse {
  token: string
  roomName: string
}

export async function fetchJaasToken(meetingId: number): Promise<JaasTokenResponse> {
  const response = await apiClient.get<ApiResponse<JaasTokenResponse>>(
    `/meetings/${meetingId}/jaas-token`
  )
  return response.data.data
}
```

#### `JitsiFrame` — thay đổi

`JitsiFrame` hiện tại (`frontend/src/components/meeting/JitsiFrame.tsx`) dùng `JITSI_URL` env var và `JITSI_DOMAIN` được extract từ đó. Cần thêm logic chọn domain dựa trên `VITE_JAAS_APP_ID`:

```typescript
const JAAS_APP_ID = import.meta.env.VITE_JAAS_APP_ID ?? ''
const IS_JAAS = JAAS_APP_ID.length > 0

// Domain: 8x8.vc nếu JaaS enabled, giữ nguyên JITSI_DOMAIN nếu không
const EFFECTIVE_DOMAIN = IS_JAAS
  ? '8x8.vc'
  : JITSI_DOMAIN  // JITSI_DOMAIN đã được extract từ VITE_JITSI_URL như hiện tại

// Script URL: load từ domain tương ứng
const SCRIPT_SRC = IS_JAAS
  ? 'https://8x8.vc/external_api.js'
  : `${JITSI_URL}/external_api.js`  // giữ nguyên logic hiện tại khi JaaS disabled
```

`JitsiFrameProps` không thay đổi — `jwt` và `meetingCode` đã có sẵn. Khi JaaS enabled, `MeetingRoom` truyền `roomName` (đã bao gồm appId prefix) vào `meetingCode` prop.

**Thay đổi trong `useEffect` khởi tạo API:**
- Dùng `EFFECTIVE_DOMAIN` thay vì `JITSI_DOMAIN` khi khởi tạo `JitsiMeetExternalAPI`
- Script được load từ `SCRIPT_SRC` thay vì `${JITSI_URL}/external_api.js`
- Error message hiển thị domain thực tế đang dùng (Requirement 3.6)

#### `MeetingRoom` — thay đổi

Thêm state và effect để fetch JaaS token. `MeetingRoom` hiện tại (`frontend/src/components/meeting/MeetingRoom.tsx`) render `JitsiFrame` với `meetingCode={meeting.meetingCode}` — cần cập nhật để truyền `roomName` khi JaaS enabled:

```typescript
const IS_JAAS = (import.meta.env.VITE_JAAS_APP_ID ?? '').length > 0

// State
const [jaasToken, setJaasToken] = useState<string | null>(null)
const [jaasRoomName, setJaasRoomName] = useState<string | null>(null)
const [jaasLoading, setJaasLoading] = useState(IS_JAAS)
const [jaasError, setJaasError] = useState<string | null>(null)
const tokenRefreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

// Fetch token on mount (JaaS only)
useEffect(() => {
  if (!IS_JAAS) return
  fetchToken()
  return () => {
    if (tokenRefreshTimerRef.current) clearTimeout(tokenRefreshTimerRef.current)
  }
}, [meeting.id])

async function fetchToken() {
  setJaasLoading(true)
  setJaasError(null)
  try {
    const { token, roomName } = await fetchJaasToken(meeting.id)
    setJaasToken(token)
    setJaasRoomName(roomName)
    // Schedule refresh at 55-minute mark (token expires in 60 min)
    tokenRefreshTimerRef.current = setTimeout(fetchToken, 55 * 60 * 1000)
  } catch (err) {
    setJaasError('Không thể lấy token JaaS. Vui lòng thử lại.')
  } finally {
    setJaasLoading(false)
  }
}
```

Render `JitsiFrame` với token — thay thế dòng hiện tại `meetingCode={meeting.meetingCode}`:
```typescript
// Khi JaaS enabled: hiển thị loading/error trước khi render JitsiFrame
if (IS_JAAS && jaasLoading) {
  return <LoadingOverlay message="Đang kết nối JaaS..." />
}
if (IS_JAAS && jaasError) {
  return <ErrorBanner message={jaasError} onRetry={fetchToken} />
}

<JitsiFrame
  ref={jitsiRef}
  meetingCode={IS_JAAS && jaasRoomName ? jaasRoomName : meeting.meetingCode}
  displayName={user?.fullName ?? user?.username ?? 'Khách'}
  jwt={IS_JAAS ? jaasToken ?? undefined : undefined}
  onVideoConferenceLeft={handleVideoConferenceLeft}
  className="w-full h-full"
/>
```

---

## Data Models

### JaaS JWT Structure

JWT được ký bằng RS256. Dưới đây là cấu trúc đầy đủ:

#### Header

```json
{
  "alg": "RS256",
  "kid": "vpaas-magic-cookie-{AppID}/{keyId}",
  "typ": "JWT"
}
```

- `kid`: Được JaaS dùng để lookup public key tương ứng. Format bắt buộc: `vpaas-magic-cookie-{AppID}/{keyId}`.
- `keyId` được extract từ `JAAS_API_KEY` (phần sau dấu `/` cuối cùng).

#### Payload (Claims)

```json
{
  "iss": "chat",
  "aud": "jitsi",
  "sub": "{AppID}",
  "room": "{meetingCode}",
  "iat": 1700000000,
  "nbf": 1699999990,
  "exp": 1700003600,
  "context": {
    "user": {
      "id": "42",
      "name": "Nguyễn Văn A",
      "email": "nguyenvana@example.com",
      "avatar": "",
      "moderator": true
    },
    "features": {
      "livestreaming": false,
      "outbound-call": false,
      "sip-outbound-call": false,
      "transcription": false
    }
  }
}
```

| Claim | Giá trị | Ghi chú |
|-------|---------|---------|
| `iss` | `"chat"` | Bắt buộc theo JaaS spec |
| `aud` | `"jitsi"` | Bắt buộc theo JaaS spec |
| `sub` | `{AppID}` | AppID từ `JAAS_APP_ID` |
| `room` | `{meetingCode}` | Chỉ meeting code, không có AppID prefix |
| `iat` | Unix timestamp | Thời điểm tạo token |
| `nbf` | `iat - 10` | Trừ 10s để tránh clock skew |
| `exp` | `iat + 3600` | Hết hạn sau 1 giờ |
| `context.user.id` | User ID (string) | Phải là string, không phải number |
| `context.user.moderator` | `true/false` | `true` nếu user là host hoặc secretary |

#### Room Name Format

| Scenario | `roomName` truyền vào JitsiMeetExternalAPI |
|----------|-------------------------------------------|
| JaaS enabled | `{AppID}/{meetingCode}` (ví dụ: `vpaas-magic-cookie-abc123/ABCDEF1234567890ABCD`) |
| JaaS disabled (fallback) | `{meetingCode}` (ví dụ: `ABCDEF1234567890ABCD`) |

**Lưu ý quan trọng:** Claim `room` trong JWT chỉ chứa `{meetingCode}` (không có AppID prefix), nhưng `roomName` truyền vào External API phải có format `{AppID}/{meetingCode}`.

### Environment Variables

| Variable | Scope | Mô tả |
|----------|-------|-------|
| `JAAS_APP_ID` | Backend | AppID từ JaaS console. Ví dụ: `vpaas-magic-cookie-abc123` |
| `JAAS_API_KEY` | Backend | Full API Key ID. Format: `vpaas-magic-cookie-{AppID}/{keyId}` |
| `JAAS_PRIVATE_KEY` | Backend | RSA private key PEM (PKCS#8). Newlines replace bằng `\n` |
| `VITE_JAAS_APP_ID` | Frontend | Phải match `JAAS_APP_ID`. Dùng để quyết định domain và room name format |

### `application.yml` additions

```yaml
jaas:
  app-id: ${JAAS_APP_ID:}
  api-key: ${JAAS_API_KEY:}
  private-key: ${JAAS_PRIVATE_KEY:}
```

### `pom.xml` — Dependency Changes

Không cần thêm dependency mới. JJWT 0.11.5 đã có sẵn trong `pom.xml` (xác nhận: `<jjwt.version>0.11.5</jjwt.version>`) và hỗ trợ RS256:

```xml
<!-- Đã có — không thay đổi -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>${jjwt.version}</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
```

JJWT 0.11.5 hỗ trợ `signWith(PrivateKey, SignatureAlgorithm.RS256)` và custom header claims qua `setHeaderParam("kid", ...)`. Không cần nâng version.

**Lưu ý về RSA key parsing:** JJWT 0.11.5 yêu cầu `PrivateKey` object. Cần parse PEM string thủ công:
1. Replace `\n` literal thành newline thực
2. Strip `-----BEGIN PRIVATE KEY-----` / `-----END PRIVATE KEY-----` headers
3. Base64 decode
4. Dùng `KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes))`

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: JWT claims integrity

*For any* authenticated meeting member, the generated JaaS JWT token SHALL contain the correct `iss` (`"chat"`), `aud` (`"jitsi"`), `sub` (`{AppID}`), `room` (`{meetingCode}` without AppID prefix), `context.user.id` (user ID as string), `context.user.name`, `context.user.email`, and `context.user.moderator` values matching the input user and meeting data.

**Validates: Requirements 1.7, 1.8, 1.9, 1.10, 1.13, 1.15, 1.16**

### Property 2: JWT expiry window

*For any* generated JaaS JWT token, the `exp` claim SHALL be exactly 3600 seconds after the `iat` claim, and the `nbf` claim SHALL be exactly 10 seconds before the `iat` claim.

**Validates: Requirements 1.11, 1.12**

### Property 3: Moderator flag correctness

*For any* meeting with a host and/or secretary, generating a token for the host or secretary SHALL produce `moderator: true`, and generating a token for any other member (where host and secretary may be null) SHALL produce `moderator: false`.

**Validates: Requirements 1.15, 1.16**

### Property 4: Room name format consistency

*For any* meeting code and AppID, the `roomName` returned by the token endpoint SHALL equal `{AppID}/{meetingCode}`, and the `room` claim in the JWT SHALL equal `{meetingCode}` (without AppID prefix).

**Validates: Requirements 1.10, 1.18**

### Property 5: Access control — non-members are rejected

*For any* user who is not a member of a meeting, a call to `generateToken()` for that meeting SHALL throw `ForbiddenException` (resulting in HTTP 403).

**Validates: Requirements 1.2, 1.3, 6.4**

### Property 6: Private key never exposed

*For any* exception or error scenario during token generation (invalid key, missing config, etc.), the error message and response body SHALL NOT contain the value of `JAAS_PRIVATE_KEY`.

**Validates: Requirements 6.1**

---

## Error Handling

### Backend Error Scenarios

| Scenario | HTTP Status | Response |
|----------|-------------|----------|
| `JAAS_APP_ID` / `JAAS_PRIVATE_KEY` không được set | 503 Service Unavailable | `{ "error": "JaaS is not configured" }` |
| Meeting không tồn tại | 404 Not Found | `{ "error": "Meeting not found: {id}" }` |
| User không phải member | 403 Forbidden | `{ "error": "User is not a member of this meeting" }` |
| Kolla JWT không hợp lệ / thiếu | 401 Unauthorized | Handled by `JwtAuthenticationFilter` |
| Private key PEM không hợp lệ | 500 Internal Server Error | Log lỗi, trả về generic error (không expose key) |

**Quan trọng:** `JAAS_PRIVATE_KEY` không bao giờ được xuất hiện trong log, response, hay error message. `JaasTokenServiceImpl` phải catch `InvalidKeyException` và log message chung chung.

### Frontend Error Scenarios

| Scenario | Xử lý |
|----------|-------|
| Token fetch thất bại | Hiển thị error banner với nút "Thử lại" |
| Token fetch đang loading | Hiển thị loading spinner thay vì `JitsiFrame` |
| `external_api.js` load thất bại | `JitsiFrame` hiển thị error message với domain |
| Token hết hạn (refresh thất bại) | Hiển thị error banner, không tự động reload |

### `ServiceUnavailableException`

Cần thêm exception class mới và handler trong `GlobalExceptionHandler`:

```java
// backend/src/main/java/com/example/kolla/exceptions/ServiceUnavailableException.java
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
```

```java
// Trong GlobalExceptionHandler
@ExceptionHandler(ServiceUnavailableException.class)
public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(ServiceUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.error(ex.getMessage()));
}
```

---

## Testing Strategy

### Unit Tests (Example-based)

**Backend:**

1. `JaasTokenServiceImplTest` — test các scenario cụ thể:
   - Token được generate thành công cho member
   - Token có `moderator: true` cho host
   - Token có `moderator: true` cho secretary
   - Token có `moderator: false` cho member thường
   - Token có `moderator: false` khi host là null (meeting chưa assign host)
   - 403 khi user không phải member
   - 404 khi meeting không tồn tại
   - 503 khi JaaS chưa được cấu hình (`JAAS_APP_ID` trống)

2. `JaasTokenControllerTest` — integration test với MockMvc:
   - 200 OK với valid request
   - 401 khi không có Kolla JWT
   - 403 khi không phải member

3. `JaasPropertiesTest`:
   - `isEnabled()` trả về `false` khi `appId` trống hoặc null
   - `isEnabled()` trả về `true` khi `appId` có giá trị
   - `extractKeyId()` parse đúng từ full API key format `vpaas-magic-cookie-{AppID}/{keyId}`
   - `extractKeyId()` trả về toàn bộ string khi không có dấu `/`

**Frontend (Vitest + Testing Library):**

1. `jaasService.test.ts` — mock API call, verify request URL và response mapping
2. `JitsiFrame.test.tsx`:
   - Khi `VITE_JAAS_APP_ID` được set: domain là `8x8.vc`, script src là `https://8x8.vc/external_api.js`
   - Khi `VITE_JAAS_APP_ID` không set: domain là `VITE_JITSI_URL`, fallback behavior
   - Khi script load thất bại: hiển thị error message với domain
3. `MeetingRoom.test.tsx`:
   - Khi JaaS enabled: hiển thị loading indicator trong khi fetch token
   - Khi token fetch thành công: `JitsiFrame` nhận đúng `jwt` và `meetingCode` props
   - Khi token fetch thất bại: hiển thị error banner với nút retry
   - Khi JaaS disabled: `JitsiFrame` render với `meeting.meetingCode` và không có `jwt`

### Property-Based Tests (jqwik — backend)

Sử dụng jqwik (đã có trong `pom.xml`: `net.jqwik:jqwik:1.8.2`) để test các properties được định nghĩa ở trên.

**Property 1 — JWT claims integrity:**
```java
@Property(tries = 100)
// Feature: jaas-integration, Property 1: JWT claims integrity
void jwtClaimsMatchInput(@ForAll("validMeetings") Meeting meeting,
                          @ForAll("memberUsers") User user) {
    // Generate token, decode JWT body (without signature verification),
    // assert iss="chat", aud="jitsi", sub=appId, room=meeting.getCode(),
    // context.user.id=user.getId().toString(), context.user.name=user.getFullName()
}
```

**Property 2 — JWT expiry window:**
```java
@Property(tries = 100)
// Feature: jaas-integration, Property 2: JWT expiry window
void jwtExpiryWindowIsCorrect(@ForAll("validMeetings") Meeting meeting,
                               @ForAll("memberUsers") User user) {
    // Generate token, decode, assert exp = iat + 3600, nbf = iat - 10
}
```

**Property 3 — Moderator flag correctness:**
```java
@Property(tries = 100)
// Feature: jaas-integration, Property 3: Moderator flag correctness
void moderatorFlagIsCorrect(@ForAll("meetingsWithRoles") MeetingWithRoles data) {
    // For host/secretary → moderator: true; for regular members → moderator: false
    // MeetingWithRoles includes meeting, host, secretary, and a list of regular members
}
```

**Property 4 — Room name format consistency:**
```java
@Property(tries = 100)
// Feature: jaas-integration, Property 4: Room name format consistency
void roomNameFormatIsConsistent(@ForAll("meetingCodes") String meetingCode,
                                 @ForAll("appIds") String appId) {
    // roomName returned = appId + "/" + meetingCode
    // JWT room claim = meetingCode only (no appId prefix)
}
```

**Property 5 — Non-members are rejected:**
```java
@Property(tries = 100)
// Feature: jaas-integration, Property 5: Access control — non-members are rejected
void nonMembersAreRejected(@ForAll("meetings") Meeting meeting,
                             @ForAll("nonMemberUsers") User user) {
    // generateToken() throws ForbiddenException for any user not in member list
    assertThatThrownBy(() -> service.generateToken(meeting.getId(), user))
        .isInstanceOf(ForbiddenException.class);
}
```

**Property 6 — Private key never exposed:**
```java
@Property(tries = 100)
// Feature: jaas-integration, Property 6: Private key never exposed
void privateKeyNotExposedInErrors(@ForAll("errorScenarios") ErrorScenario scenario) {
    // For any error scenario (invalid key, missing config, etc.),
    // the exception message must not contain the private key value
    try {
        service.generateToken(scenario.meetingId(), scenario.user());
    } catch (Exception e) {
        assertThat(e.getMessage()).doesNotContain(TEST_PRIVATE_KEY_VALUE);
    }
}
```

Mỗi property test chạy tối thiểu 100 iterations (mặc định của jqwik, `tries = 100`).
