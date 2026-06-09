# Delete User Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace admin user deactivation with delete behavior.

**Architecture:** Backend owns delete semantics; frontend calls `deleteUser(id)` while keeping icon `block`.

**Tech Stack:** Spring Boot 3, Spring Data JPA, JUnit 5/Mockito, React 18, Vitest, Testing Library.

---

### Task 1: Backend Service Delete Semantics

- [ ] Write failing tests for hard-delete and anonymize-on-history.
- [ ] Run `cd backend && .\mvnw.cmd -Dtest=UserServiceTest test`; expect failure before implementation.
- [ ] Add member/document history checks, visible-user repository queries, and safe anonymize logic.
- [ ] Run `cd backend && .\mvnw.cmd -Dtest=UserServiceTest test`; expect `BUILD SUCCESS`.
- [ ] Commit `feat: safely delete users`.

### Task 2: Backend Delete Endpoint

**Files:** `backend/src/main/java/com/example/kolla/controllers/UserController.java`, `backend/src/test/java/com/example/kolla/controllers/UserControllerTest.java`

- [ ] Write failing controller test for `deleteUser(Long, User)` and `@DeleteMapping(/{id})`.
- [ ] Run `cd backend && .\mvnw.cmd -Dtest=UserControllerTest test`; expect failure before endpoint exists.
- [ ] Add `DELETE /users/{id}` with `@PreAuthorize(hasRole('ADMIN'))`, service call, and success response.
- [ ] Run `cd backend && .\mvnw.cmd -Dtest=UserControllerTest test`; expect `BUILD SUCCESS`.
- [ ] Commit `feat: expose delete user endpoint`.

### Task 3: Frontend Admin Delete Flow

**Files:** `frontend/src/components/admin/UserManagement.tsx`, `frontend/src/components/admin/UserManagement.test.tsx`

- [ ] Write failing tests for delete dialog, kept `block` icon, `deleteUser(id)` call, and delete failure error.
- [ ] Run `cd frontend && npm test -- UserManagement.test.tsx --run`; expect failure before UI change.
- [ ] Replace toggle-active action with delete dialog and `deleteUser` call while keeping `block` icon.
- [ ] Run `cd frontend && npm test -- UserManagement.test.tsx --run`; expect pass.
- [ ] Commit `feat: replace user deactivate action with delete`.

### Task 4: Full Verification, Push, Clean Worktree

- [ ] Run `cd backend && .\mvnw.cmd test`; expect `BUILD SUCCESS`.
- [ ] Run `cd frontend && npm test -- --run`; expect all tests pass.
- [ ] Run `git push origin v2`, `git fetch origin`, compare `git rev-parse HEAD` with `git rev-parse origin/v2`, and confirm `git status -sb` is clean.
