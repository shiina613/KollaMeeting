# Kolla Backend — Spring Boot Conventions

## Package Structure

```
com.example.kolla/
├── config/          # Spring configs (Security, WebSocket, Redis, etc.)
├── controllers/     # REST controllers
├── websocket/       # WebSocket handlers and publishers
├── services/        # Business logic interfaces
│   └── impl/        # Service implementations
├── models/          # JPA entities
├── repositories/    # Spring Data JPA repositories
├── dto/             # Request DTOs (input)
├── responses/       # Response DTOs (output)
├── enums/           # Enums
├── exceptions/      # Custom exceptions + GlobalExceptionHandler
├── filter/          # JWT filter
└── utils/           # Utility classes
```

## API Response Format

Always wrap responses in `ApiResponse<T>`:

```java
// Success
return ResponseEntity.ok(ApiResponse.success("Message", data));
return ResponseEntity.ok(ApiResponse.success(data));

// Created
return ResponseEntity.status(201).body(ApiResponse.success("Created", data));
```

```java
// ApiResponse structure
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String timestamp; // ISO8601 UTC+7
}
```

## Error Response Format

```json
{
  "timestamp": "2025-01-01T10:00:00+07:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/meetings",
  "details": {}
}
```

## Exception Hierarchy

```java
ResourceNotFoundException  → 404
BadRequestException        → 400
ForbiddenException         → 403
UnauthorizedException      → 401
SchedulingConflictException → 409
```

All handled by `GlobalExceptionHandler` (`@ControllerAdvice`).

## Controller Pattern

```java
@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
@Tag(name = "Meeting Management")
public class MeetingController {

    private final MeetingService meetingService;

    @GetMapping("/{id}")
    @Operation(summary = "Get meeting by ID")
    public ResponseEntity<ApiResponse<MeetingResponse>> getMeeting(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(meetingService.getMeetingById(id)));
    }
}
```

## Service Pattern

```java
// Interface
public interface MeetingService {
    MeetingResponse getMeetingById(Long id);
    PageResponse<MeetingResponse> getMeetings(int page, int size, SearchCriteria criteria);
}

// Implementation
@Service
@RequiredArgsConstructor
@Transactional
public class MeetingServiceImpl implements MeetingService {
    private final MeetingRepository meetingRepository;
    // ...
}
```

## JPA Entity Conventions

```java
@Entity
@Table(name = "meeting")
@Data
@EntityListeners(AuditingEntityListener.class)
public class Meeting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

- Use `@Data` (Lombok) for all entities
- Column names: `snake_case`
- All datetimes: `LocalDateTime` (stored as UTC+7)
- Enums: `@Enumerated(EnumType.STRING)`

## Security & JWT

```java
// Get current user in controller
@AuthenticationPrincipal UserDetails userDetails
Long userId = Long.parseLong(userDetails.getUsername());

// Role check annotation
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('ADMIN', 'SECRETARY')")

// Custom check (Host of meeting)
// Use SecurityContextUtil.getCurrentUserId() in service layer
```

JWT token contains: `userId` as subject, `role` as claim.

## WebSocket Events

All meeting events use this format:

```java
// Publish via MeetingEventPublisher
meetingEventPublisher.publish(meetingId, MeetingEvent.builder()
    .type(MeetingEventType.MODE_CHANGED)
    .meetingId(meetingId)
    .timestamp(LocalDateTime.now())
    .payload(Map.of("mode", "MEETING_MODE"))
    .build());
```

STOMP topic: `/topic/meeting/{meetingId}`

## Redis Operations

```java
// TranscriptionQueueService
// HIGH_PRIORITY score: 1_000_000_000 - unix_ms (higher = processed first)
// NORMAL_PRIORITY score: unix_ms inverted

redisTemplate.opsForZSet().add("transcription:queue", jobId, score);
redisTemplate.opsForZSet().popMax("transcription:queue"); // Gipformer polls this
redisTemplate.opsForHash().putAll("transcription:job:" + jobId, jobDetails);
```

## Pagination

```java
// Always use PageResponse<T> for paginated results
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
```

## Datetime Handling

```java
// DateTimeConverter config ensures UTC+7 for all responses
// Use LocalDateTime everywhere internally
// Jackson serializes to ISO8601 with +07:00 offset
```

## File Storage

```java
// FileStorageService
String path = fileStorageService.save(file, FileType.RECORDING, meetingId);
Resource resource = fileStorageService.load(path);
fileStorageService.delete(path);
```

Base path from config: `${file.storage.path:/app/storage}`

## Flyway Migrations

- Location: `src/main/resources/db/migration/`
- Naming: `V{version}__{description}.sql` (e.g., `V1__initial_schema.sql`)
- Never modify existing migration files
