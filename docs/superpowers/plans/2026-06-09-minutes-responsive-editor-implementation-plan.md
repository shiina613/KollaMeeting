# Minutes Responsive Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement responsive app shell, PDF zoom controls, simplified minutes artifacts, and structured form editing that renders edited DOCX only.

**Architecture:** Preserve raw STT minutes and host-signed raw PDF. Store structured editable content on `Minutes`, expose it through the existing minutes API, and render edited DOCX with `DocxMinutesRenderer`. Frontend replaces HTML editing with form fields and uses a mobile drawer shell.

**Tech Stack:** Spring Boot 3.2, Java 17, React 18, Vite, TypeScript, Tailwind CSS, Vitest, JUnit/Mockito.

---

### Task 1: Backend Structured Contract

**Files:**
- Create: `backend/src/main/java/com/example/kolla/dto/MinutesContentEntryRequest.java`
- Create: `backend/src/main/java/com/example/kolla/responses/MinutesContentEntryResponse.java`
- Modify: `backend/src/main/java/com/example/kolla/dto/EditMinutesRequest.java`
- Modify: `backend/src/main/java/com/example/kolla/models/Minutes.java`
- Modify: `backend/src/main/java/com/example/kolla/responses/MinutesResponse.java`
- Modify: `backend/src/main/java/com/example/kolla/runtime/RuntimeMeetingStateStore.java`
- Test: `backend/src/test/java/com/example/kolla/services/MinutesServiceImplDocxTest.java`

- [ ] Write failing test `editMinutes_rendersEditedDocxFromStructuredContentOnly` asserting edited DOCX path exists, secretary PDF remains null, confirmed raw PDF remains unchanged, structured JSON contains edited speech, and response reports edited Word availability.
- [ ] Run RED: `cd backend; .\mvnw.cmd -Dtest=MinutesServiceImplDocxTest#editMinutes_rendersEditedDocxFromStructuredContentOnly test`. Expected: compile/test failure for missing structured request fields.
- [ ] Add request/response DTOs and persist `contentEntriesJson` plus `conclusion` on `Minutes` and runtime state.

### Task 2: Backend Structured Editing

**Files:**
- Modify: `backend/src/main/java/com/example/kolla/services/MinutesService.java`
- Modify: `backend/src/main/java/com/example/kolla/controllers/MinutesController.java`
- Modify: `backend/src/main/java/com/example/kolla/services/impl/MinutesServiceImpl.java`
- Test: `backend/src/test/java/com/example/kolla/services/MinutesServiceImplDocxTest.java`

- [ ] Change service signature to accept `EditMinutesRequest` and update controller call.
- [ ] Allow edit when status is `HOST_CONFIRMED` or `SECRETARY_CONFIRMED`; reject other states.
- [ ] Build edited lines from read-only meeting metadata plus request entries/conclusion.
- [ ] Render only DOCX via `DocxMinutesRenderer.renderLines(lines)`, store as `edited_{minutesId}.docx`, and set edited Word fields.
- [ ] Do not generate edited PDF or mutate raw draft/confirmed fields.
- [ ] Make `version=secretary&format=docx` download edited Word; make `version=secretary&format=pdf` fail with a clear error.
- [ ] Run GREEN: `cd backend; .\mvnw.cmd -Dtest=MinutesServiceImplDocxTest test`. Expected: PASS.

### Task 3: Frontend Minutes UI

**Files:**
- Modify: `frontend/src/types/minutes.ts`
- Modify: `frontend/src/services/minutesService.ts`
- Modify: `frontend/src/components/minutes/MinutesEditor.tsx`
- Modify: `frontend/src/components/minutes/MinutesEditor.test.tsx`
- Modify: `frontend/src/components/minutes/MinutesDownloadButtons.tsx`
- Modify: `frontend/src/components/minutes/MinutesDownloadButtons.test.tsx`
- Modify: `frontend/src/components/minutes/MinutesViewer.tsx`
- Modify: `frontend/src/pages/MeetingDetailPage.tsx`

- [ ] Replace HTML textarea tests with structured form tests using `minutes-entry-text-0`, `minutes-conclusion-input`, and structured `editMinutes` payload.
- [ ] Run RED: `cd frontend; npm test -- src/components/minutes/MinutesEditor.test.tsx`. Expected: FAIL with missing structured controls.
- [ ] Add TS types `MinutesContentEntry` and structured `EditMinutesRequest`; change service to PUT structured body.
- [ ] Refactor `MinutesEditor` into form editor with editable entry textareas and conclusion textarea.
- [ ] Simplify download actions: raw signed PDF/raw Word before edited Word; edited Word only after edited Word exists.
- [ ] Add `MinutesViewer` zoom selector and responsive iframe height.
- [ ] Update `MeetingDetailPage` to hide raw PDF when edited Word exists and show structured edit controls.
- [ ] Run GREEN: `cd frontend; npm test -- src/components/minutes/MinutesEditor.test.tsx src/components/minutes/MinutesDownloadButtons.test.tsx`. Expected: PASS.

### Task 4: Responsive App Shell

**Files:**
- Modify: `frontend/src/components/layout/AppLayout.tsx`
- Modify: `frontend/src/components/layout/Header.tsx`
- Modify: `frontend/src/components/layout/Sidebar.tsx`
- Modify: `frontend/src/components/layout/Sidebar.test.tsx`

- [ ] Add failing layout test for mobile menu button and drawer close on backdrop/navigation.
- [ ] Run RED: `cd frontend; npm test -- src/components/layout/Sidebar.test.tsx`. Expected: FAIL because drawer does not exist.
- [ ] In `AppLayout`, keep desktop sidebar as `hidden lg:flex`, add mobile drawer/backdrop state, and change main to responsive margin/padding.
- [ ] In `Header`, accept `onMenuClick`, render `lg:hidden` menu button, and change offset to `left-0 lg:left-64`.
- [ ] In `Sidebar`, accept `onNavigate`, call it on links/logout, preserve existing labels/role filtering.
- [ ] Run GREEN: `cd frontend; npm test -- src/components/layout/Sidebar.test.tsx`. Expected: PASS.

### Task 5: Verification, Commit, Worktree Audit

- [ ] Run backend full tests: `cd backend; .\mvnw.cmd test`. Expected: PASS.
- [ ] Run frontend full tests: `cd frontend; npm test`. Expected: PASS.
- [ ] Run frontend build: `cd frontend; npm run build`. Expected: PASS.
- [ ] Run `git status --short`; stage only intended implementation/plan files.
- [ ] Commit implementation.
- [ ] If pre-existing user-owned files remain, ask before deleting/restoring them.

---

## Self-Review

- Spec coverage: responsive shell Task 4; PDF zoom Task 3; raw signed PDF preserved Task 2; structured editor and edited DOCX-only output Tasks 1-3; verification/cleanup Task 5.
- Placeholder scan: no `TBD` or `TODO`.
- Type consistency: structured request uses `contentEntries` and `conclusion`; edited artifact uses existing `secretaryDocxPath` compatibility field.
