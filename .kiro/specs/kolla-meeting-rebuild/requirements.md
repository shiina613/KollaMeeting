# Requirements Document

## Introduction

This document specifies the requirements for rebuilding the Kolla Meeting system with a modern technology stack. The rebuild replaces Angular with React, removes Google Drive dependency in favor of local filesystem storage, replaces Mediasoup with self-hosted Jitsi Meet, and integrates Gipformer for AI-powered transcription. The system maintains existing Spring Boot backend infrastructure while modernizing the frontend and video conferencing capabilities.

The system supports 2-30 participants per meeting, handles 5-7 concurrent meetings, and provides recording and transcription capabilities for each meeting session.

## Glossary

- **Frontend_Application**: The React-based web application built with Vite and Tailwind CSS
- **Backend_API**: The Spring Boot 3.2 REST API server
- **Database**: The MySQL database storing all application data
- **File_Storage**: The local filesystem storage for recordings, documents, and transcriptions
- **Jitsi_Server**: The self-hosted Jitsi Meet video conferencing server
- **Jitsi_Iframe**: The embedded iframe component displaying Jitsi Meet interface
- **Gipformer_Service**: The Python-based Vietnamese ASR (Automatic Speech Recognition) service, specialized exclusively for Vietnamese-language transcription using the Gipformer model
- **Gipformer_ONNX**: The ONNX-based inference mode of Gipformer_Service using sherpa-onnx, recommended for production deployment due to lower resource requirements and CPU compatibility
- **WebSocket_Server**: The Spring WebSocket server for real-time communication
- **Auth_System**: The JWT-based authentication and authorization system
- **Meeting**: A scheduled or active video conference session
- **Recording**: A video/audio file captured during a meeting
- **Transcription**: An AI-generated text transcript of meeting audio
- **User**: An authenticated person with role-based permissions (ADMIN, SECRETARY, USER)
- **Host**: The user with meeting control authority, typically a SECRETARY or ADMIN role
- **Participant**: A user who has joined an active meeting session
- **Room**: A virtual or physical meeting space associated with a department
- **Department**: An organizational unit grouping users and rooms
- **Member**: A user assigned to participate in a specific meeting
- **Attendance_Log**: A record of user join/leave times and presence status
- **Document**: A file uploaded and associated with a meeting
- **Notification**: A system message sent to users about meeting events
- **Free_Mode**: A meeting mode where all participants may activate their microphone simultaneously without restriction
- **Meeting_Mode**: A formal meeting mode where only one participant may speak at a time, controlled via a raise-hand mechanism
- **Raise_Hand_Request**: A participant's request to be granted speaking permission in Meeting_Mode
- **Speaking_Permission**: The exclusive right granted by the Host to one Participant to activate their microphone in Meeting_Mode
- **Transcription_Queue**: The priority-ordered queue managing which meetings receive real-time transcription from Gipformer_Service
- **High_Priority_Meeting**: A meeting assigned the highest transcription priority, receiving near-real-time transcription display and minutes recording
- **Normal_Priority_Meeting**: A meeting receiving background transcription (minutes recording only), without real-time display
- **Minutes**: The saved text record of a meeting's transcription output
- **Audio_Chunk**: Một đoạn audio được cắt từ luồng thu âm của một Participant tại điểm im lặng tự nhiên do VAD phát hiện, hoặc tại hard cap 30 giây nếu không có im lặng đủ ngưỡng; mỗi chunk mang một sequence_number tăng dần trong phạm vi một speaker turn, bắt đầu từ 1
- **VAD (Voice Activity Detection)**: Kỹ thuật phát hiện khoảng im lặng trong audio stream để xác định điểm cắt chunk tự nhiên, chạy trong Gipformer_Service
- **Adaptive_VAD_Threshold**: Ngưỡng thời gian im lặng để cắt chunk, thay đổi theo thời gian thu âm: ≥2–3s khi dưới 15s, ≥0.5–1s khi từ 15s trở lên
- **Transcription_Job**: Một đơn vị công việc trong Redis_Queue, chứa một Audio_Chunk cần được xử lý bởi Gipformer_Service, kèm theo meetingId, speakerId, speakerName, timestamp, priority, sequence_number, và audioData/audioPath
- **Redis_Queue**: Redis Sorted Set dùng để quản lý Transcription_Job theo priority score; job có score cao hơn được xử lý trước
- **Audio_Stream**: Luồng audio thô được capture từ microphone của Participant đang giữ Speaking_Permission, sử dụng Web Audio API `getUserMedia()` trong Frontend_Application
- **Secretary**: The designated SECRETARY-role user assigned to a specific meeting, responsible for editing Minutes and acting as backup Host
- **Meeting_Lifecycle**: The sequence of states a Meeting passes through: SCHEDULED → ACTIVE → ENDED
- **Waiting_Timeout**: A 10-minute countdown that begins when no Host or Secretary is present in an active meeting, after which the meeting automatically ends

## Requirements

### Requirement 1: Frontend Technology Migration

**User Story:** As a developer, I want to rebuild the frontend using React with Vite and Tailwind CSS, so that the application uses modern, maintainable frontend technologies.

#### Acceptance Criteria

1. THE Frontend_Application SHALL be built using React 18 or later
2. THE Frontend_Application SHALL use Vite as the build tool and development server
3. THE Frontend_Application SHALL use Tailwind CSS for styling
4. THE Frontend_Application SHALL implement all existing Angular features with equivalent React components
5. THE Frontend_Application SHALL maintain responsive design for desktop and mobile devices
6. THE Frontend_Application SHALL use React Router for client-side routing
7. THE Frontend_Application SHALL use a state management solution (Context API or Zustand) for global state

### Requirement 2: Authentication and Authorization

**User Story:** As a user, I want to securely log in and access features based on my role, so that the system maintains proper access control.

#### Acceptance Criteria

1. WHEN a user submits valid credentials, THE Frontend_Application SHALL receive a JWT token from the Backend_API
2. THE Frontend_Application SHALL store the JWT token securely in memory or httpOnly cookies
3. THE Frontend_Application SHALL include the JWT token in all authenticated API requests
4. WHEN a JWT token expires, THE Frontend_Application SHALL redirect the user to the login page
5. THE Frontend_Application SHALL display UI elements based on user role (ADMIN, SECRETARY, USER)
6. THE Backend_API SHALL validate JWT tokens on all protected endpoints
7. THE Backend_API SHALL enforce role-based access control using Spring Security annotations

### Requirement 3: Meeting Management

**User Story:** As a secretary or admin, I want to create, schedule, and manage meetings, so that I can organize video conferences for my team.

#### Acceptance Criteria

1. WHEN an authorized user creates a meeting, THE Backend_API SHALL generate a unique meeting code
2. THE Backend_API SHALL store meeting details (title, description, start time, end time, room, creator) in the Database
3. THE Frontend_Application SHALL display a meeting creation form with validation
4. THE Frontend_Application SHALL display a list of scheduled meetings with filtering and sorting
5. WHEN a user updates a meeting, THE Backend_API SHALL validate permissions and update the Database
6. WHEN a user deletes a meeting, THE Backend_API SHALL remove the meeting and cascade delete related records
7. THE Frontend_Application SHALL display meeting details including participants, documents, and recordings
8. WHEN an Admin creates a meeting, THE Backend_API SHALL require designation of one Host (SECRETARY or ADMIN role) and one Secretary (SECRETARY role) before the meeting record is saved
9. THE Backend_API SHALL enforce that only Members explicitly added to the meeting may join; non-Members SHALL receive a 403 Forbidden response when attempting to join
10. WHEN the Host activates the meeting room, THE Backend_API SHALL transition the Meeting from SCHEDULED to ACTIVE state; this action opens the room for participants to join and does not switch the meeting to Meeting_Mode
11. THE Backend_API SHALL track each Meeting through the following states: SCHEDULED, ACTIVE, and ENDED
12. WHEN an authorized user creates or updates a meeting with a Room and time range, THE Backend_API SHALL check for scheduling conflicts; IF another ACTIVE or SCHEDULED meeting occupies the same Room during the overlapping time period, THEN THE Backend_API SHALL reject the request with a 409 Conflict response and return the conflicting meeting details

### Requirement 4: Jitsi Meet Integration

**User Story:** As a meeting participant, I want to join video conferences through an embedded Jitsi interface, so that I can communicate with other participants.

#### Acceptance Criteria

1. THE Frontend_Application SHALL embed Jitsi Meet using an iframe component
2. WHEN a user joins a meeting, THE Frontend_Application SHALL load the Jitsi_Iframe with the meeting code as the room identifier
3. THE Jitsi_Iframe SHALL connect to the self-hosted Jitsi_Server
4. THE Frontend_Application SHALL pass user display name and avatar to the Jitsi_Iframe
5. THE Jitsi_Iframe SHALL support 2-30 concurrent participants per meeting
6. THE Frontend_Application SHALL handle Jitsi API events (user joined, user left, meeting ended)
7. THE Frontend_Application SHALL provide controls to mute/unmute audio and enable/disable video through Jitsi API
8. WHILE a meeting is in Free_Mode, THE Frontend_Application SHALL allow all Participants to activate their microphone simultaneously via Jitsi API
9. WHILE a meeting is in Meeting_Mode, THE Frontend_Application SHALL mute all Participants who do not hold Speaking_Permission via Jitsi API
10. WHEN the Host grants Speaking_Permission to a Participant in Meeting_Mode, THE Frontend_Application SHALL unmute that Participant and mute all other Participants via Jitsi API
11. THE Frontend_Application SHALL use Jitsi's built-in chat feature for in-meeting text communication; chat messages SHALL NOT be stored in the Database or File_Storage

### Requirement 5: Attendance Tracking

**User Story:** As a system, I want to automatically track when users join and leave meetings, so that attendance records are accurate.

#### Acceptance Criteria

1. WHEN a user joins a meeting through the Jitsi_Iframe, THE Frontend_Application SHALL notify the Backend_API
2. THE Backend_API SHALL create an Attendance_Log record with join time, user ID, meeting ID, IP address, and device info
3. WHEN a user leaves a meeting, THE Frontend_Application SHALL notify the Backend_API
4. THE Backend_API SHALL update the Attendance_Log record with leave time
5. THE Backend_API SHALL calculate total attendance duration from join and leave times
6. THE Frontend_Application SHALL display real-time participant list during active meetings
7. THE Frontend_Application SHALL display attendance history for completed meetings
8. WHILE a meeting is in Meeting_Mode, THE Backend_API SHALL automatically create an Attendance_Log record for each Participant upon joining without requiring manual check-in

### Requirement 6: Local File Storage

**User Story:** As a system administrator, I want to store recordings and documents on the local filesystem, so that the system does not depend on Google Drive.

#### Acceptance Criteria

1. THE Backend_API SHALL store uploaded files in a configurable local directory
2. THE Backend_API SHALL organize files in subdirectories by meeting ID and file type
3. THE Backend_API SHALL store file metadata (name, size, type, path, upload time) in the Database
4. THE Backend_API SHALL validate file types and sizes before accepting uploads
5. THE Backend_API SHALL serve files for download through authenticated endpoints
6. THE Backend_API SHALL delete files from filesystem when corresponding database records are deleted
7. THE File_Storage SHALL support concurrent read/write operations for 5-7 active meetings

### Requirement 7: Meeting Recording

**User Story:** As a secretary or admin, I want to record meetings and store them locally, so that participants can review meeting content later.

#### Acceptance Criteria

1. WHEN an authorized user starts recording, THE Backend_API SHALL create a Recording record with start time
2. THE Jitsi_Server SHALL capture audio and video streams to a file
3. THE Backend_API SHALL store the recording file in File_Storage
4. WHEN recording stops, THE Backend_API SHALL update the Recording record with end time, file name, and file size
5. THE Frontend_Application SHALL display recording controls (start/stop) to authorized users
6. THE Frontend_Application SHALL display a list of recordings for each meeting
7. THE Frontend_Application SHALL provide download functionality for recordings

### Requirement 8: Gipformer Transcription Integration

**User Story:** As a meeting participant, I want automatic transcription of meeting audio, so that I can search and review meeting content as text.

#### Acceptance Criteria

1. WHEN a recording completes, THE Backend_API SHALL send the recording file to the Gipformer_Service for post-meeting transcription
2. THE Gipformer_Service SHALL process the audio and return a text transcription
3. THE Backend_API SHALL store the transcription text in the Database linked to the Recording
4. THE Backend_API SHALL store transcription metadata (language, confidence score, processing time)
5. THE Frontend_Application SHALL display transcriptions alongside recordings
6. THE Frontend_Application SHALL provide search functionality within transcriptions
7. IF transcription fails, THEN THE Backend_API SHALL log the error and mark the transcription as failed
8. WHEN a Participant is granted Speaking_Permission in Meeting_Mode, THE Frontend_Application SHALL capture that Participant's Audio_Stream using Web Audio API `getUserMedia()` and stream the audio to the Backend_API via WebSocket_Server, which SHALL forward the stream to the Gipformer_Service for transcription
9. WHEN Speaking_Permission is revoked or the Participant lowers their hand in Meeting_Mode, THE Backend_API SHALL finalize the current Audio_Chunk and push it to the Redis_Queue
10. THE Gipformer_Service SHALL continuously poll the Redis_Queue and process the Transcription_Job with the highest priority score first
11. WHEN the Gipformer_Service completes processing a Transcription_Job, THE Gipformer_Service SHALL send the transcription result back to the Backend_API via HTTP callback
12. WHILE a Meeting holds High_Priority_Meeting status, THE Backend_API SHALL broadcast received transcription results to all Participants via WebSocket_Server and persist the results to the Database as Minutes
13. WHILE a Meeting holds Normal_Priority_Meeting status, THE Backend_API SHALL persist received transcription results to the Database as Minutes only, without broadcasting via WebSocket_Server
14. THE Frontend_Application SHALL capture raw PCM audio from the Participant's microphone via Web Audio API `getUserMedia()` and stream it to the Backend_API via WebSocket_Server
15. THE Backend_API SHALL forward the raw PCM audio stream to the Gipformer_Service
16. THE Gipformer_Service SHALL convert received audio to WAV format at 16kHz mono sample rate before running inference
17. THE Gipformer_Service SHALL use ONNX inference mode (sherpa-onnx) for production deployment
18. THE Gipformer_Service SHALL transcribe Vietnamese speech only; the system is designed for Vietnamese-language meetings

### Requirement 9: Document Management

**User Story:** As a meeting participant, I want to upload and share documents related to meetings, so that all participants have access to relevant materials.

#### Acceptance Criteria

1. WHEN a user uploads a document, THE Backend_API SHALL validate file type and size
2. THE Backend_API SHALL store the document in File_Storage
3. THE Backend_API SHALL create a Document record in the Database with metadata
4. THE Frontend_Application SHALL display uploaded documents for each meeting
5. THE Frontend_Application SHALL provide download functionality for documents
6. WHEN a user deletes a document, THE Backend_API SHALL remove it from File_Storage and Database
7. THE Backend_API SHALL support concurrent document uploads during active meetings

### Requirement 10: Real-Time Notifications

**User Story:** As a user, I want to receive real-time notifications about meeting events, so that I stay informed about important updates.

#### Acceptance Criteria

1. THE WebSocket_Server SHALL maintain persistent connections with authenticated clients
2. WHEN a meeting starts, THE Backend_API SHALL send notifications to all meeting members via WebSocket_Server
3. WHEN a user is added to a meeting, THE Backend_API SHALL send a notification to that user
4. WHEN a document is uploaded, THE Backend_API SHALL notify all meeting participants
5. THE Frontend_Application SHALL display notifications in a notification panel
6. THE Frontend_Application SHALL mark notifications as read when viewed
7. THE Backend_API SHALL store notification history in the Database

### Requirement 11: User and Role Management

**User Story:** As an admin, I want to manage users and their roles, so that I can control access to system features.

#### Acceptance Criteria

1. THE Backend_API SHALL support CRUD operations for User records
2. THE Backend_API SHALL assign one role (ADMIN, SECRETARY, USER) to each user
3. THE Frontend_Application SHALL display a user management interface for admins
4. WHEN an admin updates a user role, THE Backend_API SHALL update the Database and invalidate existing JWT tokens
5. THE Frontend_Application SHALL display user profiles with department and role information
6. THE Backend_API SHALL validate that only admins can modify user roles
7. THE Backend_API SHALL prevent deletion of users with active meeting memberships
8. THE Frontend_Application SHALL provide an Admin interface to reset any user's password
9. WHEN an Admin resets a user's password, THE Backend_API SHALL generate a temporary password, update the Database, and invalidate all existing JWT tokens for that user
10. THE Backend_API SHALL enforce that only ADMIN role users may reset other users' passwords

### Requirement 12: Room and Department Management

**User Story:** As an admin, I want to organize meetings by rooms and departments, so that the system reflects organizational structure.

#### Acceptance Criteria

1. THE Backend_API SHALL support CRUD operations for Room and Department records
2. THE Backend_API SHALL associate each Room with one Department
3. THE Backend_API SHALL associate each Meeting with one Room
4. THE Frontend_Application SHALL display room and department selection when creating meetings
5. THE Frontend_Application SHALL filter meetings by room or department
6. THE Backend_API SHALL validate that only admins can create or modify rooms and departments
7. THE Backend_API SHALL prevent deletion of rooms with scheduled meetings
8. THE Frontend_Application SHALL display a room availability calendar or indicator showing which time slots are already booked when creating or editing a meeting

### Requirement 13: Search and Filtering

**User Story:** As a user, I want to search and filter meetings, recordings, and documents, so that I can quickly find relevant information.

#### Acceptance Criteria

1. THE Backend_API SHALL provide search endpoints with pagination support
2. THE Backend_API SHALL support filtering meetings by date range, room, department, and creator
3. THE Backend_API SHALL support searching recordings by meeting title and date
4. THE Backend_API SHALL support searching transcriptions by text content
5. THE Frontend_Application SHALL display search interfaces with filter controls
6. THE Frontend_Application SHALL display search results with pagination
7. THE Backend_API SHALL return search results within 500ms for typical queries

### Requirement 14: System Configuration

**User Story:** As a system administrator, I want to configure system settings through environment variables, so that deployment is flexible and secure.

#### Acceptance Criteria

1. THE Backend_API SHALL read database connection settings from environment variables or application.yml
2. THE Backend_API SHALL read JWT secret and expiration from configuration
3. THE Backend_API SHALL read file storage path from configuration
4. THE Backend_API SHALL read Jitsi server URL from configuration
5. THE Backend_API SHALL read Gipformer service URL from configuration
6. THE Frontend_Application SHALL read API base URL from environment variables
7. THE Frontend_Application SHALL read Jitsi server URL from environment variables
8. THE Backend_API SHALL store all datetime values in UTC+7 (Asia/Ho_Chi_Minh) timezone
9. THE Frontend_Application SHALL display all datetime values in UTC+7 timezone
10. THE Backend_API SHALL include timezone information in all datetime fields returned by API responses

### Requirement 15: Error Handling and Logging

**User Story:** As a developer, I want comprehensive error handling and logging, so that I can diagnose and fix issues quickly.

#### Acceptance Criteria

1. THE Backend_API SHALL log all errors with stack traces and context information
2. THE Backend_API SHALL return structured error responses with HTTP status codes and error messages
3. THE Frontend_Application SHALL display user-friendly error messages for API failures
4. THE Frontend_Application SHALL log JavaScript errors to the console
5. WHEN file upload fails, THE Backend_API SHALL return a descriptive error message
6. WHEN transcription fails, THE Backend_API SHALL log the failure and continue operation
7. THE Backend_API SHALL implement global exception handling for all controllers

### Requirement 16: Performance and Scalability

**User Story:** As a system administrator, I want the system to handle concurrent meetings efficiently, so that performance remains acceptable under load.

#### Acceptance Criteria

1. THE Backend_API SHALL support 5-7 concurrent active meetings
2. THE Backend_API SHALL handle 30 concurrent users per meeting
3. THE Backend_API SHALL respond to API requests within 200ms for 95% of requests
4. THE File_Storage SHALL support concurrent file uploads and downloads
5. THE WebSocket_Server SHALL maintain 150+ concurrent WebSocket connections (30 users × 5 meetings)
6. THE Database SHALL use connection pooling with appropriate pool size
7. THE Frontend_Application SHALL implement lazy loading for large lists and images

### Requirement 17: Data Migration

**User Story:** As a system administrator, I want to migrate existing data from the old system, so that historical data is preserved.

#### Acceptance Criteria

1. THE Backend_API SHALL provide migration scripts for existing database schema
2. THE migration scripts SHALL preserve all User, Meeting, Room, Department, and Role records
3. THE migration scripts SHALL migrate Recording metadata while updating file paths
4. THE migration scripts SHALL migrate Document metadata while updating file paths
5. THE migration scripts SHALL migrate Attendance_Log and Notification records
6. THE migration scripts SHALL validate data integrity after migration
7. THE migration scripts SHALL generate a migration report with success/failure counts

### Requirement 18: API Documentation

**User Story:** As a frontend developer, I want comprehensive API documentation, so that I can integrate with the backend efficiently.

#### Acceptance Criteria

1. THE Backend_API SHALL expose OpenAPI/Swagger documentation at /swagger-ui.html
2. THE API documentation SHALL include all endpoints with request/response schemas
3. THE API documentation SHALL include authentication requirements for each endpoint
4. THE API documentation SHALL include example requests and responses
5. THE API documentation SHALL document error response formats
6. THE API documentation SHALL be automatically generated from code annotations
7. THE API documentation SHALL be accessible without authentication for development environments

### Requirement 19: Security

**User Story:** As a security-conscious user, I want the system to protect sensitive data and prevent unauthorized access, so that my information remains secure.

#### Acceptance Criteria

1. THE Backend_API SHALL use HTTPS for all production deployments
2. THE Backend_API SHALL validate and sanitize all user inputs
3. THE Backend_API SHALL implement CORS policies to restrict cross-origin requests
4. THE Backend_API SHALL hash passwords using bcrypt before storing in Database
5. THE Backend_API SHALL implement rate limiting on authentication endpoints
6. THE Frontend_Application SHALL not store sensitive data in localStorage
7. THE Backend_API SHALL log all authentication failures for security monitoring

### Requirement 20: Testing and Quality Assurance

**User Story:** As a developer, I want comprehensive test coverage, so that the system is reliable and maintainable.

#### Acceptance Criteria

1. THE Backend_API SHALL include unit tests for service layer with 80%+ coverage
2. THE Backend_API SHALL include integration tests for API endpoints
3. THE Frontend_Application SHALL include unit tests for utility functions and hooks
4. THE Frontend_Application SHALL include component tests for critical UI components
5. THE Backend_API SHALL include tests for authentication and authorization logic
6. THE Backend_API SHALL include tests for file upload and download functionality
7. THE project SHALL include end-to-end tests for critical user workflows (login, create meeting, join meeting)

### Requirement 21: Meeting Mode Switching

**User Story:** As a host, I want to switch between Free Mode and Meeting Mode during an active meeting, so that I can control the level of formality and structure appropriate for the session.

#### Acceptance Criteria

1. THE Backend_API SHALL store the current mode (FREE_MODE or MEETING_MODE) for each active Meeting
2. WHEN a Host switches a meeting from Free_Mode to Meeting_Mode, THE Backend_API SHALL update the meeting mode in the Database and notify all Participants via WebSocket_Server
3. WHEN a Host switches a meeting from Meeting_Mode to Free_Mode, THE Backend_API SHALL update the meeting mode, release any active Speaking_Permission, and notify all Participants via WebSocket_Server
4. WHEN a meeting transitions to Meeting_Mode, THE Frontend_Application SHALL mute all Participants via Jitsi API and display the raise-hand control to all Participants
5. WHEN a meeting transitions to Free_Mode, THE Frontend_Application SHALL restore microphone control to all Participants via Jitsi API and hide the raise-hand control
6. THE Frontend_Application SHALL display the current meeting mode (Free_Mode or Meeting_Mode) visibly to all Participants at all times
7. THE Backend_API SHALL enforce that only users with Host authority (SECRETARY or ADMIN role) may switch meeting modes
8. IF a non-Host user attempts to switch meeting mode, THEN THE Backend_API SHALL return a 403 Forbidden response
9. WHEN the Host switches a meeting from Meeting_Mode to Free_Mode while a Participant holds Speaking_Permission, THE Backend_API SHALL first finalize the current Audio_Chunk, push it to the Redis_Queue, and revoke Speaking_Permission before completing the mode transition
10. THE Backend_API SHALL ensure the mode transition to Free_Mode is not visible to Participants until the Audio_Chunk finalization is complete

### Requirement 22: Raise Hand Mechanism

**User Story:** As a participant in Meeting Mode, I want to raise my hand to request speaking permission, so that I can contribute to the discussion in an orderly manner.

#### Acceptance Criteria

1. WHILE a meeting is in Meeting_Mode, THE Frontend_Application SHALL display a "Raise Hand" button to all Participants who do not currently hold Speaking_Permission
2. WHEN a Participant submits a Raise_Hand_Request, THE Backend_API SHALL record the request with a timestamp and notify the Host via WebSocket_Server
3. WHEN a Raise_Hand_Request is received, THE Frontend_Application SHALL display a visual indicator and notification to the Host identifying the requesting Participant
4. WHEN the Host grants Speaking_Permission to a Participant, THE Backend_API SHALL record the permission grant, revoke any existing Speaking_Permission from another Participant, and notify all Participants via WebSocket_Server
5. WHEN Speaking_Permission is granted to a Participant, THE Frontend_Application SHALL unmute that Participant and mute all other Participants via Jitsi API
6. WHEN the Host revokes Speaking_Permission from a Participant, THE Backend_API SHALL remove the Speaking_Permission record and notify all Participants via WebSocket_Server
7. WHEN a Participant lowers their hand voluntarily, THE Backend_API SHALL remove the Raise_Hand_Request and Speaking_Permission if held, and notify the Host via WebSocket_Server
8. THE Backend_API SHALL enforce that only one Participant holds Speaking_Permission at any given time within a single Meeting
9. THE Frontend_Application SHALL display the list of pending Raise_Hand_Requests to the Host in chronological order
10. IF a Participant with Speaking_Permission leaves the meeting, THEN THE Backend_API SHALL automatically revoke Speaking_Permission and notify the Host
11. WHEN a Participant holding Speaking_Permission mutes their microphone in Jitsi, THE Backend_API SHALL automatically revoke that Participant's Speaking_Permission, finalize the current Audio_Chunk and push it to the Redis_Queue, notify the Host via WebSocket_Server, and allow the next Participant in the Raise_Hand_Request queue to be granted Speaking_Permission
12. THE Backend_API SHALL use database-level locking (SELECT FOR UPDATE) when granting Speaking_Permission to prevent race conditions where two concurrent grant requests could result in more than one Participant holding Speaking_Permission simultaneously

### Requirement 23: Priority-Based Transcription Queue

**User Story:** As an admin or secretary, I want to assign transcription priority to meetings, so that the most important meeting receives near-real-time transcription resources.

#### Acceptance Criteria

1. THE Backend_API SHALL maintain a Redis_Queue (Redis Sorted Set) that assigns each Transcription_Job a priority score; jobs from High_Priority_Meeting SHALL receive a higher score than jobs from Normal_Priority_Meeting
2. THE Backend_API SHALL enforce that at most one Meeting holds HIGH_PRIORITY status at any given time across all concurrent meetings
3. WHEN an Admin or Secretary assigns HIGH_PRIORITY to a Meeting, THE Backend_API SHALL demote any existing HIGH_PRIORITY meeting to NORMAL_PRIORITY and promote the selected Meeting to HIGH_PRIORITY
4. THE Backend_API SHALL notify affected meetings of priority changes via WebSocket_Server
5. THE Frontend_Application SHALL display the current transcription priority level of the meeting to the Host
6. THE Frontend_Application SHALL provide a priority assignment control visible only to users with ADMIN or SECRETARY role
7. WHILE a Participant holds Speaking_Permission and the recording duration is less than 15 seconds, THE Gipformer_Service SHALL apply an Adaptive_VAD_Threshold of ≥2–3 seconds of silence to determine the Audio_Chunk cut point
8. WHEN the recording duration reaches 15 seconds or more, THE Gipformer_Service SHALL reduce the Adaptive_VAD_Threshold to ≥0.5–1 second of silence to determine the Audio_Chunk cut point, enabling more frequent chunking
9. WHEN the recording duration exceeds 30 seconds without detecting silence meeting the Adaptive_VAD_Threshold, THE Gipformer_Service SHALL apply a hard cap and cut the Audio_Chunk immediately at 30 seconds, then push it as a Transcription_Job to the Redis_Queue
10. WHEN Speaking_Permission is revoked or the Participant lowers their hand, THE Backend_API SHALL signal the Gipformer_Service to finalize the current Audio_Chunk at the next available VAD silence point or immediately if no silence is detected, and push it as a Transcription_Job to the Redis_Queue
11. Each Audio_Chunk SHALL end at a natural silence point detected by VAD, except when the hard cap at 30 seconds is applied
12. Each Transcription_Job pushed to the Redis_Queue SHALL carry meetingId, speakerId, speakerName, timestamp, priority score, and audioData or audioPath
13. Each Transcription_Job pushed to the Redis_Queue SHALL include a sequence_number field indicating the chunk's position within the speaker's current speaking turn, starting from 1 and incrementing for each subsequent chunk
14. THE Backend_API SHALL sort transcription results by (speakerId, speakerTurnId, sequence_number) before assembling them into Minutes to ensure correct ordering regardless of processing completion order
15. THE Frontend_Application SHALL buffer incoming transcription segments for a HIGH_PRIORITY meeting and display them in sequence_number order within each speaker turn
16. THE Gipformer_Service SHALL continuously poll the Redis_Queue and always process the Transcription_Job with the highest priority score first
17. WHEN the Gipformer_Service completes a Transcription_Job, THE Gipformer_Service SHALL deliver the result to the Backend_API via HTTP callback
18. WHILE a Meeting holds NORMAL_PRIORITY status, THE Backend_API SHALL push Transcription_Jobs to the Redis_Queue with a lower priority score; these jobs SHALL be processed by the Gipformer_Service after all HIGH_PRIORITY jobs are complete
19. A near-real-time delay of a few seconds between speech and transcription display is acceptable for HIGH_PRIORITY meetings

### Requirement 24: Real-Time Transcription Display

**User Story:** As a meeting participant in a high-priority meeting, I want to see near-real-time transcription on screen as people speak, so that I can follow the discussion with minimal delay.

#### Acceptance Criteria

1. WHILE a Meeting holds HIGH_PRIORITY status and is in Meeting_Mode, THE Frontend_Application SHALL display a transcription panel showing the current speaker's transcribed text as results arrive from the Gipformer_Service
2. WHEN the Backend_API receives a transcription result via HTTP callback from the Gipformer_Service for a HIGH_PRIORITY meeting, THE Backend_API SHALL broadcast the transcription segment to all Participants of that meeting via WebSocket_Server
3. THE Frontend_Application SHALL append each received transcription segment to the live display panel without requiring a page refresh
4. THE Frontend_Application SHALL label each transcription segment with the speaker's display name and timestamp
5. THE Backend_API SHALL persist all transcription segments to the Database as Minutes, regardless of whether real-time display is active
6. WHILE a Meeting holds NORMAL_PRIORITY status, THE Frontend_Application SHALL NOT display a real-time transcription panel; transcription SHALL be saved to Minutes only
7. THE Frontend_Application SHALL display a visual indicator showing whether near-real-time transcription is active for the current meeting
8. WHEN a Meeting transitions from HIGH_PRIORITY to NORMAL_PRIORITY, THE Frontend_Application SHALL hide the transcription panel and display a notification to Participants that live transcription has ended
9. THE Frontend_Application SHALL allow Participants to scroll through the transcription history within the current session's live display panel

### Requirement 25: Meeting Minutes Workflow

**User Story:** As a meeting host and secretary, I want the meeting minutes to be automatically generated, confirmed, and editable, so that an accurate and verified record of the meeting exists.

#### Acceptance Criteria

1. WHEN a meeting ends, THE Backend_API SHALL automatically compile all transcription segments into a draft Minutes document in PDF format, ordered chronologically with speaker name and timestamp for each segment
2. THE Backend_API SHALL store the draft Minutes PDF in File_Storage and create a Minutes record in the Database with status DRAFT
3. WHEN the draft Minutes is created, THE Backend_API SHALL notify the Host via WebSocket_Server to review and confirm
4. WHEN the Host confirms the Minutes, THE Backend_API SHALL embed a digital confirmation stamp into the PDF containing: Host's full name, confirmation timestamp, and a hash derived from the JWT token and document content
5. THE Backend_API SHALL update the Minutes record status to HOST_CONFIRMED and store the confirmed PDF in File_Storage
6. AFTER the Host confirms, THE Backend_API SHALL notify the assigned Secretary (SECRETARY role) to review and edit the Minutes
7. THE Frontend_Application SHALL provide a text editor interface for the Secretary to correct speech-to-text errors in the Minutes content
8. WHEN the Secretary submits edits, THE Backend_API SHALL generate a new PDF with the corrected content and update the Minutes record status to SECRETARY_CONFIRMED
9. AFTER Secretary confirmation, THE Frontend_Application SHALL make the Minutes available to all meeting Members for viewing and download
10. THE Frontend_Application SHALL allow Members to download both the original Host-confirmed PDF (with digital stamp) and the Secretary-edited PDF
11. THE Backend_API SHALL maintain both PDF versions in File_Storage permanently linked to the Meeting record
12. IF the Host has not confirmed within 24 hours, THEN THE Backend_API SHALL send a reminder notification to the Host
13. THE Frontend_Application SHALL provide a rich text editor for the Secretary supporting basic formatting: bold, italic, bullet lists, and numbered lists
14. THE Backend_API SHALL accept rich text content in HTML format from the Secretary's editor and render it correctly in the generated PDF

### Requirement 26: Meeting Lifecycle and Host Management

**User Story:** As a system, I want to manage meeting states and host authority automatically, so that meetings run smoothly even when participants disconnect.

#### Acceptance Criteria

1. THE Backend_API SHALL track each Meeting through states: SCHEDULED, ACTIVE, and ENDED
2. WHEN the Host opens the meeting room, THE Backend_API SHALL transition the Meeting from SCHEDULED to ACTIVE and notify all Members via WebSocket_Server
3. THE Backend_API SHALL designate the assigned Host as the primary authority for meeting control; the assigned Secretary SHALL serve as backup Host
4. WHEN the Host disconnects or leaves the meeting, THE Backend_API SHALL automatically transfer Host authority to the Secretary and notify all Participants via WebSocket_Server
5. WHEN the Host reconnects to an ACTIVE meeting, THE Backend_API SHALL automatically restore Host authority from the Secretary and notify all Participants via WebSocket_Server
6. WHEN both the Host and Secretary are absent from an ACTIVE meeting, THE Backend_API SHALL start a 10-minute Waiting_Timeout countdown and notify all remaining Participants via WebSocket_Server
7. IF the Host or Secretary reconnects before the Waiting_Timeout expires, THE Backend_API SHALL cancel the countdown and restore normal meeting operation
8. WHEN the Waiting_Timeout expires with neither Host nor Secretary present, THE Backend_API SHALL automatically transition the Meeting to ENDED state
9. WHEN the Host or Secretary explicitly ends the meeting, THE Backend_API SHALL transition the Meeting to ENDED state and notify all Participants via WebSocket_Server
10. WHEN a Meeting room has been empty (zero Participants) for 10 minutes, THE Backend_API SHALL automatically transition the Meeting to ENDED state
11. THE Backend_API SHALL enforce that only Members explicitly added to the meeting may join; non-Members SHALL receive a 403 Forbidden response when attempting to join

### Requirement 27: Disconnection and Reconnection Handling

**User Story:** As a participant, I want the system to handle network interruptions gracefully, so that temporary disconnections do not disrupt the meeting flow.

#### Acceptance Criteria

1. WHEN a Participant's connection is lost, THE Backend_API SHALL detect the disconnection within 5–10 seconds via WebSocket heartbeat timeout
2. WHEN a Participant holding Speaking_Permission disconnects, THE Backend_API SHALL automatically revoke Speaking_Permission after the 5–10 second timeout, push any partially captured Audio_Chunk to the Redis_Queue, and notify the Host via WebSocket_Server
3. WHEN a Participant reconnects to an ACTIVE meeting within the session, THE Backend_API SHALL restore their Participant state (but not Speaking_Permission) and notify other Participants via WebSocket_Server
4. WHEN the Host disconnects, THE Backend_API SHALL transfer Host authority to the Secretary within 5–10 seconds and notify all Participants via WebSocket_Server
5. WHEN the Host reconnects, THE Backend_API SHALL restore Host authority and notify all Participants via WebSocket_Server
6. THE Frontend_Application SHALL display a reconnecting indicator when the WebSocket connection is lost and attempt automatic reconnection
7. THE Frontend_Application SHALL display a notification to all Participants when the Host or Secretary disconnects or reconnects

### Requirement 28: Gipformer Service Resilience

**User Story:** As a system administrator, I want the transcription pipeline to be resilient to Gipformer service failures, so that audio data is not lost when the service is temporarily unavailable.

#### Acceptance Criteria

1. THE Backend_API SHALL perform a health check on the Gipformer_Service before forwarding Audio_Streams
2. WHEN the Gipformer_Service is unavailable, THE Backend_API SHALL save incoming Audio_Chunks to File_Storage as pending files and create corresponding Transcription_Job records in the Database with status PENDING
3. WHEN the Gipformer_Service becomes available again, THE Backend_API SHALL automatically requeue all PENDING Transcription_Jobs into the Redis_Queue for processing
4. THE Backend_API SHALL notify the Host via WebSocket_Server when the Gipformer_Service becomes unavailable and when it recovers
5. THE Frontend_Application SHALL display a visual indicator to the Host when transcription is unavailable due to service downtime
6. THE meeting SHALL continue normally (video, audio, raise hand) even when the Gipformer_Service is unavailable; only transcription is affected
7. THE Backend_API SHALL retry failed Transcription_Jobs up to 3 times before marking them as FAILED in the Database

### Requirement 29: Storage Management

**User Story:** As an admin, I want to manage file storage efficiently, so that the system does not run out of disk space.

#### Acceptance Criteria

1. THE Frontend_Application SHALL display a storage dashboard showing total disk usage, usage by category (recordings, documents, audio chunks, minutes PDFs), and usage per meeting
2. THE Frontend_Application SHALL provide Admin users with bulk deletion controls to delete recordings older than a selectable time period: 1 week, 1 month, 3 months, or a custom number of days
3. WHEN an Admin selects a bulk deletion period, THE Frontend_Application SHALL display a confirmation dialog showing the number of files and total size that will be deleted before proceeding
4. WHEN an Admin confirms bulk deletion, THE Backend_API SHALL delete the selected recording files from File_Storage and update the corresponding Database records
5. THE Backend_API SHALL enforce that only ADMIN role users may perform bulk deletion operations
6. THE Backend_API SHALL log all deletion operations with the admin user ID, timestamp, number of files deleted, and total size freed
7. THE Frontend_Application SHALL display a storage usage warning to Admin users when disk usage exceeds 80% of configured storage capacity
8. THE Backend_API SHALL read the configured storage capacity limit from application configuration

### Requirement 30: Gipformer REST API Wrapper

**User Story:** As a system integrator, I want Gipformer to expose a REST API, so that Spring Boot can submit transcription jobs and receive results programmatically.

#### Acceptance Criteria

1. THE Gipformer_Service SHALL expose a REST API wrapping the ONNX inference script, accepting HTTP requests from the Backend_API
2. THE Gipformer_Service SHALL expose a POST endpoint `/transcribe` that accepts a WAV audio file and returns the transcription text as JSON
3. THE Gipformer_Service SHALL expose a GET endpoint `/health` that returns service status, model load status, and current queue depth
4. THE Gipformer_Service SHALL expose a POST endpoint `/jobs` that accepts a Transcription_Job payload (audioPath, meetingId, speakerId, speakerName, timestamp, priority) and enqueues it for processing
5. WHEN a Transcription_Job completes, THE Gipformer_Service SHALL POST the result to the Backend_API callback URL specified in the job payload
6. THE Gipformer_Service SHALL load the Gipformer ONNX model once at startup and reuse it for all subsequent inference requests
7. THE Gipformer_Service SHALL be implemented as a Python FastAPI application
8. THE Gipformer_Service SHALL read its configuration (port, model path, callback URL, Redis connection) from environment variables
9. Each Transcription_Job SHALL carry a globally unique job_id generated by the Backend_API at job creation time
10. WHEN the Gipformer_Service delivers a transcription result via HTTP callback, THE Backend_API SHALL check whether a result for that job_id already exists in the Database; IF a result already exists, THE Backend_API SHALL return 200 OK without processing the duplicate
11. THE Gipformer_Service SHALL include the job_id in all callback retry attempts to enable idempotency checking
12. THE Gipformer_Service SHALL complete model loading and warm-up before accepting any inference requests; the `/health` endpoint SHALL return status "ready" only after the model is fully loaded
13. THE Backend_API SHALL poll the Gipformer_Service `/health` endpoint at startup and wait until status is "ready" before routing any Transcription_Jobs to the service
