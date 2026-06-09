# Delete User Design

## Context

Admin user management currently presents a deactivate/reactivate action. The requested behavior is to turn that action into user deletion while keeping the existing `block` icon. The project schema is aligned with the submitted Word document, and the `user` table is referenced by `member.User_id` and `document.User_id`. Deleting a referenced user can break meeting history, documents, minutes, or database constraints.

## Goal

Replace the admin-facing deactivate flow with a delete flow that removes users completely when it is safe and preserves meeting/document history when a hard delete would break other data.

## Recommended Behavior

The delete action uses a two-path backend operation:

1. If the target user has no historical references, hard-delete the `user` row.
2. If the target user is referenced by meeting members, documents, or other persisted history, perform a safe delete by anonymizing the existing user row instead of removing it.

Safe delete means:

- Change the employee code/username to a sentinel value such as `deleted-user-<id>`.
- Replace full name with `Nguoi dung da xoa`.
- Clear personal fields where nullable: email, phone number, date of birth, degree, identification, address, bank name, bank number, image.
- Change password to an unguessable generated value and invalidate existing tokens.
- Keep `id`, `Department_id`, and referenced rows intact so historical meeting and document data still resolves.
- Exclude sentinel users from user management lists, active user lists, meeting candidate lists, and user search results.

## Frontend Design

In `frontend/src/components/admin/UserManagement.tsx`:

- Replace `toggleUserActive` import and calls with `deleteUser`.
- Rename modal state from toggle/deactivate naming to delete naming.
- Keep the material icon text `block` on the row action button.
- Change labels to `Xoa nguoi dung`, `Xoa`, and a permanent-deletion warning.
- After successful deletion, close the dialog and reload users.
- On failure, show the backend error message when present; fall back to `Khong the xoa nguoi dung. Vui long thu lai.`

In `frontend/src/services/userService.ts`:

- Keep `deleteUser(id)` using `DELETE /users/{id}`.
- `toggleUserActive` may remain for backward compatibility unless unused cleanup is low risk.

## Backend Design

In `backend/src/main/java/com/example/kolla/controllers/UserController.java`:

- Add `DELETE /users/{id}`.
- Restrict it to `ADMIN` with a Spring Security pre-authorization expression for `hasRole('ADMIN')`.
- Call `userService.deleteUser(id, currentUser)`.
- Return a successful `ApiResponse<Void>`.

In `UserServiceImpl`:

- Keep self-delete protection.
- Determine whether the user has any persisted references, not only active/scheduled memberships.
- Hard-delete only when no references exist.
- Safe-delete/anonymize when references exist.
- Invalidate tokens for both hard delete and safe delete.

In `UserRepository` and related repositories:

- Add reference checks for any membership/document history needed by the current schema.
- Exclude sentinel deleted users from list/search/candidate queries.

## Error Handling

- Self-delete returns `BadRequestException` with a clear message.
- Missing target user returns `ResourceNotFoundException`.
- Unexpected database integrity failures should not leak raw SQL details to the UI.

## Testing

Backend tests should cover:

- `DELETE /users/{id}` calls service for ADMIN.
- Hard delete removes unreferenced users.
- Referenced users are anonymized, not removed.
- Self-delete is rejected.
- Deleted/anonymized users are excluded from list/search/candidate results.

Frontend tests should cover:

- Row action keeps `block` icon but opens delete dialog.
- Confirm calls `deleteUser(id)`.
- Successful delete reloads users and closes dialog.
- Failure shows an error message.

## Non-Goals

- Do not add new database columns such as `deleted_at` or `is_deleted`, to avoid drifting from the Word-aligned schema.
- Do not cascade-delete meetings, members, documents, minutes, recordings, or transcripts.
- Do not change the icon requested by the user.
