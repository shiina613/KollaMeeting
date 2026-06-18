# Avatar Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the profile avatar path text field with a user-facing image upload flow.

**Architecture:** The frontend previews a selected image file and uploads it before saving the profile. The backend exposes `POST /users/me/avatar` for authenticated users, validates image type and size, stores the avatar under the configured file storage root, and returns the updated user profile with the new `img` value.

**Tech Stack:** React, TypeScript, Vitest, Testing Library, Spring Boot, JUnit, MockMvc, MultipartFile.

---

### Task 1: Frontend Avatar Upload Flow

**Files:**
- Modify: `frontend/src/services/userService.ts`
- Modify: `frontend/src/pages/ProfilePage.tsx`
- Test: `frontend/src/pages/ProfilePage.test.tsx`

- [ ] **Step 1: Write the failing test**

Update `ProfilePage.test.tsx` so the avatar test selects a `File`, expects no text path input, and expects `uploadCurrentUserAvatar` to be called before `updateCurrentUser`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- ProfilePage.test.tsx`
Expected: FAIL because `uploadCurrentUserAvatar` and the file upload UI do not exist.

- [ ] **Step 3: Write minimal implementation**

Add `uploadCurrentUserAvatar(file: File)` to `userService.ts`. In `ProfilePage.tsx`, add file state, local object URL preview, an image file input, and submit logic that uploads the avatar first when a new file is selected.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- ProfilePage.test.tsx`
Expected: PASS.

### Task 2: Backend Avatar Upload Endpoint

**Files:**
- Modify: `backend/src/main/java/com/example/kolla/config/FileStorageProperties.java`
- Modify: `backend/src/main/java/com/example/kolla/controllers/UserController.java`
- Modify: `backend/src/main/java/com/example/kolla/services/UserService.java`
- Modify: `backend/src/main/java/com/example/kolla/services/impl/UserServiceImpl.java`
- Test: `backend/src/test/java/com/example/kolla/controllers/UserControllerTest.java`

- [ ] **Step 1: Write the failing test**

Add MockMvc tests for `POST /users/me/avatar`: successful JPEG upload returns updated user response, and unsupported MIME type returns 400.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && .\mvnw.cmd -Dtest=UserControllerTest test`
Expected: FAIL because the endpoint does not exist.

- [ ] **Step 3: Write minimal implementation**

Add avatar properties for allowed image types and max size. Add `uploadCurrentUserAvatar(MultipartFile file, User currentUser)` to `UserService`, implement validation/storage/update in `UserServiceImpl`, and expose it from `UserController`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && .\mvnw.cmd -Dtest=UserControllerTest test`
Expected: PASS.

### Task 3: Integrated Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused frontend tests**

Run: `cd frontend && npm test -- ProfilePage.test.tsx`
Expected: PASS.

- [ ] **Step 2: Run focused backend tests**

Run: `cd backend && .\mvnw.cmd -Dtest=UserControllerTest test`
Expected: PASS.

- [ ] **Step 3: Inspect diff**

Run: `git diff -- frontend/src/pages/ProfilePage.tsx frontend/src/pages/ProfilePage.test.tsx frontend/src/services/userService.ts backend/src/main/java/com/example/kolla/controllers/UserController.java backend/src/main/java/com/example/kolla/services/UserService.java backend/src/main/java/com/example/kolla/services/impl/UserServiceImpl.java backend/src/main/java/com/example/kolla/config/FileStorageProperties.java backend/src/test/java/com/example/kolla/controllers/UserControllerTest.java`
Expected: Diff only contains avatar upload changes.
