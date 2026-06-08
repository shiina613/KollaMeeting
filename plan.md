# DOCX 3.8 Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the accepted decisions from `review.md`: fix member role selection, align document permissions, hide out-of-scope UI, keep transcription priority, and repair Vietnamese text encoding.

**Architecture:** Keep current routes and service contracts unless a finding requires permission changes. Put UI scope fixes in frontend pages/components; put document access rules in `DocumentServiceImpl`; prove each change with focused tests plus full verification.

**Tech Stack:** React 18, TypeScript, Vite, Vitest, React Testing Library, Spring Boot, JUnit 5, Mockito, Maven.

---

## Scope Decisions

- Finding 1: Fix. `MeetingDetailPage` must choose `MeetingRole` when adding members.
- Finding 2: Fix. Document access must be meeting-scoped.
- Finding 3: Fix. Hide admin `Lưu trữ` UI from submitted frontend.
- Finding 4: Fix. Hide sidebar `Ghi âm`; keep `/recordings` route for direct debug. Keep `/search` but label/present it as `Tra cứu biên bản`.
- Finding 5: Keep current behavior. Do not hide `Ưu tiên phiên âm`.
- Finding 6: Fix. Normalize frontend Vietnamese text to UTF-8 and add a mojibake guard.

## Completion Criteria

- `MeetingDetailPage` add-member flow passes selected `MeetingRole`; default remains `MEMBER`.
- Document upload/delete is limited to assigned meeting secretary; document list/download is limited to meeting members.
- Admin UI no longer shows `Lưu trữ`; backend storage endpoints may remain.
- Sidebar no longer shows `Ghi âm`; `/search` appears as `Tra cứu biên bản`.
- `Ưu tiên phiên âm` remains visible in create/edit meeting and detail view.
- Frontend source has no obvious mojibake patterns from the agreed scan.
- Required automated checks pass: focused tests, full frontend tests, frontend build, backend document tests, whitespace check.

---

## Task 1: Meeting Detail Member Role

**Files**
- Modify: `frontend/src/pages/MeetingDetailPage.tsx`
- Create/modify: `frontend/src/pages/MeetingDetailPage.test.tsx`
- Read: `frontend/src/services/meetingService.ts`

**Implementation**
- Add state near member picker state:
  - `const [memberRole, setMemberRole] = useState<MeetingRole>('MEMBER')`
- In the add-member branch, change call to:
  - `await addMeetingMember(meetingId, u.id, memberRole)`
- Add a `Vai trò khi thêm` `<select>` inside the detail page member picker.
- Options must match edit form practical roles:
  - `MEMBER`
  - `COMMITTEE_MEMBER`
  - `REVIEWER`
  - `GUEST`
- Do not add `HOST` or `SECRETARY` to this picker; those are meeting-level fields.

**Tests**
- `MeetingDetailPage.test.tsx`: adding a user without changing dropdown calls `addMeetingMember(meetingId, userId, 'MEMBER')`.
- `MeetingDetailPage.test.tsx`: selecting `REVIEWER` then adding a user calls `addMeetingMember(meetingId, userId, 'REVIEWER')`.
- Test must open tab `Thành viên`, open `Thêm / bỏ thành viên`, interact with the role select, then click a non-member user.

**Verification**
- [ ] Run: `cd frontend; npm run test -- src/pages/MeetingDetailPage.test.tsx --run`
- [ ] Expected: role picker tests pass.

---

## Task 2: Document Permission Alignment

**Files**
- Modify: `backend/src/main/java/com/example/kolla/services/impl/DocumentServiceImpl.java`
- Modify: `backend/src/test/java/com/example/kolla/services/DocumentServiceImplTest.java`
- Modify: `frontend/src/pages/MeetingDetailPage.tsx`
- Modify: `frontend/src/pages/MeetingDetailPage.test.tsx`

**Backend Implementation**
- Keep upload behavior: `checkAssignedSecretary(meeting, currentUser)`.
- Change `checkMembership` so `ADMIN` and system `SECRETARY` no longer bypass meeting membership.
- `listDocuments`, `getDocumentById`, and `downloadDocument` must call meeting membership check.
- Change `deleteDocument` flow:
  - find document first;
  - check current user is assigned secretary of `document.getMeeting()`;
  - delete file;
  - delete repository record.
- Add helper for delete, e.g. `checkAssignedSecretaryForDelete(Meeting meeting, User currentUser)`.

**Backend Tests**
- `DocumentServiceImplTest`: `listDocuments` rejects system `SECRETARY` who is not a meeting member.
- `DocumentServiceImplTest`: `listDocuments` allows meeting member.
- `DocumentServiceImplTest`: `downloadDocument` rejects `ADMIN` who is not a meeting member.
- `DocumentServiceImplTest`: `deleteDocument` rejects `SECRETARY` who is not assigned secretary of that meeting.
- `DocumentServiceImplTest`: `deleteDocument` allows assigned secretary and calls `fileStorageService.deleteFile(...)` and `documentRepository.delete(...)`.

**Frontend Implementation**
- In document delete button rendering only, replace system-secretary condition:
  - from `{isSecretary && (...)}`
  - to `{isAssignedSecretary && (...)}`
- Keep `isSecretary` checks for minutes editor unchanged.

**Frontend Tests**
- `MeetingDetailPage.test.tsx`: assigned secretary sees delete button for document.
- `MeetingDetailPage.test.tsx`: other system secretary does not see delete button for same document.

**Verification**
- [ ] Run: `cd backend; .\mvnw.cmd test -Dtest=DocumentServiceImplTest`
- [ ] Expected: document service tests pass.
- [ ] Run: `cd frontend; npm run test -- src/pages/MeetingDetailPage.test.tsx --run`
- [ ] Expected: document permission UI tests pass.

---

## Task 3: Admin Storage UI Removal

**Files**
- Modify: `frontend/src/pages/AdminPage.tsx`
- Create/modify: `frontend/src/pages/AdminPage.test.tsx`

**Implementation**
- Remove `StorageDashboard` import.
- Change `TabId` from `users | storage | departments` to `users | departments`.
- Remove `storage` entry from `TABS`.
- Remove storage tab panel render block.
- Update admin subtitle so it no longer mentions storage.
- Do not delete `StorageDashboard` component or backend storage endpoints in this plan.

**Tests**
- `AdminPage.test.tsx`: `Lưu trữ` tab is absent.
- `AdminPage.test.tsx`: storage dashboard content is not rendered.
- `AdminPage.test.tsx`: `Quản lý người dùng` tab remains.
- `AdminPage.test.tsx`: `Phòng ban & Phòng họp` tab remains.

**Verification**
- [ ] Run: `cd frontend; npm run test -- src/pages/AdminPage.test.tsx --run`
- [ ] Expected: admin page tests pass.

---

## Task 4: Sidebar Demo Scope

**Files**
- Modify: `frontend/src/components/layout/Sidebar.tsx`
- Create/modify: `frontend/src/components/layout/Sidebar.test.tsx`
- Read: `frontend/src/router/AppRouter.tsx`

**Implementation**
- Remove nav item `{ label: 'Ghi âm', path: '/recordings' }` from sidebar.
- Keep `/recordings` route in router for direct debug access.
- Rename search nav label from `Tìm kiếm` to `Tra cứu biên bản`.
- Keep search path as `/search`.

**Tests**
- `Sidebar.test.tsx`: no `Ghi âm` link exists.
- `Sidebar.test.tsx`: no `Tìm kiếm` link exists.
- `Sidebar.test.tsx`: `Tra cứu biên bản` link exists and has `href="/search"`.
- `AppRouter` route `/recordings` does not need a component test here; verify by code read and manual QA.

**Verification**
- [ ] Run: `cd frontend; npm run test -- src/components/layout/Sidebar.test.tsx --run`
- [ ] Expected: sidebar tests pass.

---

## Task 5: UTF-8 Text Normalization

**Files**
- Modify: `frontend/src/**/*.ts`
- Modify: `frontend/src/**/*.tsx`
- Create: `frontend/scripts/check-mojibake.mjs`
- Modify: `frontend/package.json`

**Implementation**
- Normalize visible Vietnamese strings across frontend source to valid UTF-8.
- Prioritize screens used in demo:
  - sidebar;
  - login/profile;
  - admin;
  - meeting list;
  - meeting create/edit;
  - meeting detail;
  - documents;
  - minutes.
- Update tests that assert old mojibake text to assert proper Vietnamese text.
- Add npm script:
  - `"check:mojibake": "node scripts/check-mojibake.mjs"`
- `check-mojibake.mjs` should scan `frontend/src/**/*.{ts,tsx}` for obvious mojibake patterns, including:
  - `Ã`
  - `Ä`
  - `Â`
  - `á»`
  - `áº`
  - `Æ`
  - `â€`
- The script should print file:line matches and exit `1` when matches exist.

**Important Constraint**
- Do not rename API fields, enum values, route paths, localStorage keys, test IDs, or CSS classes.
- Do not hide or remove `transcriptionPriority`; only fix its visible Vietnamese label/options if encoded wrong.

**Tests**
- Existing frontend tests that query labels must pass with normalized text.
- `npm run check:mojibake` must fail before cleanup and pass after cleanup.

**Verification**
- [ ] Run: `cd frontend; npm run check:mojibake`
- [ ] Expected before cleanup: fails with mojibake file lines.
- [ ] Expected after cleanup: passes with no mojibake output.

---

## Task 6: Preserve Transcription Priority

**Files**
- Read/modify only for UTF-8: `frontend/src/pages/MeetingFormPage.tsx`
- Read/modify only for UTF-8: `frontend/src/pages/MeetingDetailPage.tsx`
- Read/modify tests only if text assertions change: `frontend/src/pages/MeetingFormPage.test.tsx`

**Implementation**
- Keep `Ưu tiên phiên âm` visible in create/edit meeting form.
- Keep `transcriptionPriority` included in create/update request payload.
- Keep `Ưu tiên phiên âm` visible in meeting detail information.
- Only normalize text encoding if needed.

**Tests**
- Existing `MeetingFormPage.test.tsx` must still pass.
- Add or keep an assertion that the create form renders label `/Ưu tiên phiên âm/i`.

**Verification**
- [ ] Run: `cd frontend; npm run test -- src/pages/MeetingFormPage.test.tsx --run`
- [ ] Expected: pass.

---

## Full Verification

Run these after all tasks finish.

- [ ] Backend focused tests:
  - Command: `cd backend; .\mvnw.cmd test -Dtest=DocumentServiceImplTest`
  - Expected: pass.

- [ ] Frontend focused tests:
  - Command: `cd frontend; npm run test -- src/pages/MeetingDetailPage.test.tsx src/pages/AdminPage.test.tsx src/components/layout/Sidebar.test.tsx src/pages/MeetingFormPage.test.tsx --run`
  - Expected: pass.

- [ ] Mojibake guard:
  - Command: `cd frontend; npm run check:mojibake`
  - Expected: pass.

- [ ] Full frontend tests:
  - Command: `cd frontend; npm run test -- --run`
  - Expected: pass. Existing non-failing warnings are acceptable.

- [ ] Frontend build:
  - Command: `cd frontend; npm run build`
  - Expected: TypeScript and Vite build pass.

- [ ] Backend full tests:
  - Command: `cd backend; .\mvnw.cmd test`
  - Expected: pass.

- [ ] Whitespace check:
  - Command: `git diff --check -- frontend backend plan.md review.md`
  - Expected: no output.

## Manual QA

- Login as assigned `SECRETARY`.
- Open scheduled meeting detail page.
- In `Thành viên`, choose role `Phản biện`, add a user, confirm member list shows `Phản biện`.
- In `Tài liệu`, confirm assigned secretary sees upload/delete controls.
- Login as another `SECRETARY` not assigned to meeting; confirm document delete is hidden.
- Confirm non-member cannot list/download meeting documents by direct API/browser flow.
- Open admin page; confirm `Lưu trữ` tab is gone.
- Check sidebar; confirm `Ghi âm` is gone and `Tra cứu biên bản` points to `/search`.
- Open `/recordings` directly; confirm route still exists for debug if needed.
- Open create/edit meeting; confirm `Ưu tiên phiên âm` still appears.
- Walk demo screens and confirm Vietnamese accents render correctly.

## Done Definition

Work is complete only when:

- All completion criteria above are met.
- All required focused tests pass.
- Full frontend test and build pass.
- Backend document service tests pass.
- `npm run check:mojibake` passes.
- `git diff --check -- frontend backend plan.md review.md` has no output.
- Full backend test passes, or a blocker is explicitly reported with the exact failing command/output summary.

## Self-Review

- Spec coverage: Findings 1, 2, 3, 4, and 6 have tasks. Finding 5 is explicitly preserved.
- Test coverage: each behavior change has focused automated tests plus full verification.
- Ambiguity check: storage backend and `/recordings` route remain; only submitted/demo UI is hidden.
