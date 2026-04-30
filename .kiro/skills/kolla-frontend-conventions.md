# Kolla Frontend — React + Vite Conventions

## Project Structure

```
frontend/src/
├── main.tsx
├── App.tsx
├── router/
│   └── AppRouter.tsx          # React Router v6, protected routes
├── store/
│   ├── authStore.ts           # Zustand: JWT, user info
│   ├── meetingStore.ts        # Zustand: active meeting state
│   └── notificationStore.ts  # Zustand: notifications
├── hooks/
│   ├── useWebSocket.ts        # STOMP/SockJS
│   ├── useAudioCapture.ts     # getUserMedia + PCM streaming
│   ├── useJitsiApi.ts         # Jitsi IFrame API
│   └── useTranscription.ts   # Transcription buffer
├── services/
│   ├── api.ts                 # Axios instance
│   ├── meetingService.ts
│   ├── userService.ts
│   └── ...
├── components/
│   ├── layout/
│   ├── meeting/
│   ├── minutes/
│   ├── admin/
│   └── common/
└── pages/
    ├── LoginPage.tsx
    ├── DashboardPage.tsx
    ├── MeetingRoomPage.tsx
    └── ...
```

## Naming Conventions

- Components: `PascalCase.tsx` (e.g., `MeetingRoom.tsx`)
- Hooks: `camelCase.ts` prefixed with `use` (e.g., `useWebSocket.ts`)
- Services: `camelCase.ts` suffixed with `Service` (e.g., `meetingService.ts`)
- Stores: `camelCase.ts` suffixed with `Store` (e.g., `authStore.ts`)
- Types/interfaces: `PascalCase` (e.g., `MeetingResponse`, `UserDTO`)

## Zustand Store Pattern

```typescript
// store/authStore.ts
interface AuthState {
  token: string | null;
  user: UserResponse | null;
  login: (token: string, user: UserResponse) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  user: null,
  login: (token, user) => set({ token, user }),
  logout: () => set({ token: null, user: null }),
}));
```

**Important:** Never store JWT in localStorage. Keep in Zustand memory only.

## Axios API Service

```typescript
// services/api.ts
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

// Request interceptor: attach JWT
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Response interceptor: handle 401 → redirect login
api.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

## Service Layer Pattern

```typescript
// services/meetingService.ts
export const meetingService = {
  getMeetings: (params: MeetingSearchParams) =>
    api.get<ApiResponse<PageResponse<MeetingResponse>>>('/meetings', { params }),

  createMeeting: (data: MeetingCreateDTO) =>
    api.post<ApiResponse<MeetingResponse>>('/meetings', data),

  activateMeeting: (id: number) =>
    api.post<ApiResponse<MeetingResponse>>(`/meetings/${id}/activate`),
};
```

## WebSocket Hook Pattern

```typescript
// hooks/useWebSocket.ts
export function useWebSocket(meetingId: number) {
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(import.meta.env.VITE_WS_URL),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 1000, // exponential backoff: 1s, 2s, 4s, max 30s
    });

    client.onConnect = () => {
      client.subscribe(`/topic/meeting/${meetingId}`, (msg) => {
        const event: MeetingEvent = JSON.parse(msg.body);
        handleMeetingEvent(event);
      });
    };

    client.activate();
    clientRef.current = client;
    return () => client.deactivate();
  }, [meetingId]);
}
```

## Audio Capture Pattern

```typescript
// hooks/useAudioCapture.ts
// getUserMedia → AudioContext (16kHz) → ScriptProcessorNode → Int16 → WebSocket binary
const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
const audioContext = new AudioContext({ sampleRate: 16000 });
const processor = audioContext.createScriptProcessor(4096, 1, 1);

processor.onaudioprocess = (e) => {
  const float32 = e.inputBuffer.getChannelData(0);
  const int16 = float32ToInt16(float32); // utility function
  wsClient.publish({ destination: `/app/meeting/${meetingId}/audio`,
                     binaryBody: int16.buffer });
};
```

## Jitsi IFrame Pattern

```typescript
// hooks/useJitsiApi.ts
const api = new JitsiMeetExternalAPI(jitsiDomain, {
  roomName: meetingCode,
  parentNode: containerRef.current,
  userInfo: { displayName: user.name },
  jwt: jitsiToken,
  configOverwrite: { startWithAudioMuted: true },
});

// Control mic
api.executeCommand('toggleAudio');
api.executeCommand('muteEveryone', 'audio');
```

## Meeting Event Handling

```typescript
// MeetingEvent types from WebSocket
type MeetingEventType =
  | 'MEETING_STARTED' | 'MEETING_ENDED'
  | 'MODE_CHANGED'
  | 'RAISE_HAND' | 'HAND_LOWERED'
  | 'SPEAKING_PERMISSION_GRANTED' | 'SPEAKING_PERMISSION_REVOKED'
  | 'PARTICIPANT_JOINED' | 'PARTICIPANT_LEFT'
  | 'HOST_TRANSFERRED' | 'HOST_RESTORED'
  | 'WAITING_TIMEOUT_STARTED' | 'WAITING_TIMEOUT_CANCELLED'
  | 'TRANSCRIPTION_SEGMENT'
  | 'PRIORITY_CHANGED'
  | 'TRANSCRIPTION_UNAVAILABLE' | 'TRANSCRIPTION_RECOVERED'
  | 'DOCUMENT_UPLOADED'
  | 'MINUTES_READY' | 'MINUTES_CONFIRMED' | 'MINUTES_PUBLISHED';

interface MeetingEvent {
  type: MeetingEventType;
  meetingId: number;
  timestamp: string; // ISO8601 +07:00
  payload: Record<string, unknown>;
}
```

## Tailwind Conventions

- Use Tailwind utility classes directly, no custom CSS unless necessary
- Color palette: use design system colors (configure in `tailwind.config.ts`)
- Responsive: `sm:`, `md:`, `lg:` breakpoints
- Dark mode: not required (system is light mode only)

## Component Pattern

```tsx
// components/meeting/MeetingModeToggle.tsx
interface MeetingModeToggleProps {
  meetingId: number;
  currentMode: 'FREE_MODE' | 'MEETING_MODE';
  isHost: boolean;
}

export function MeetingModeToggle({ meetingId, currentMode, isHost }: MeetingModeToggleProps) {
  // ...
}
```

- Always define props interface
- Export named (not default) for easier refactoring
- Keep components focused — split if >150 lines

## Environment Variables

Access via `import.meta.env.VITE_*`:
- `VITE_API_BASE_URL` — Spring Boot REST API
- `VITE_WS_URL` — WebSocket endpoint
- `VITE_JITSI_URL` — Jitsi server URL

## Testing (Vitest + React Testing Library)

```typescript
// Component test pattern
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';

describe('RaiseHandPanel', () => {
  it('renders requests in chronological order', () => {
    render(<RaiseHandPanel requests={mockRequests} />);
    // assertions...
  });
});
```

Property tests use `fast-check`:
```typescript
import fc from 'fast-check';
test('segments display in sequence order', () => {
  fc.assert(fc.property(fc.array(arbitrarySegment()), (segments) => {
    // property assertion
  }), { numRuns: 500 });
});
```
