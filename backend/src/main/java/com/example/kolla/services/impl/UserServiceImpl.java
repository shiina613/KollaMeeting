package com.example.kolla.services.impl;

import com.example.kolla.dto.ChangePasswordRequest;
import com.example.kolla.dto.CreateUserRequest;
import com.example.kolla.dto.ResetPasswordRequest;
import com.example.kolla.dto.UpdateUserRequest;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.Department;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DepartmentRepository;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.responses.UserResponse;
import com.example.kolla.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UserService implementation.
 * Requirements: 11.1–11.10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String JWT_BLACKLIST_PREFIX = "jwt:blacklist:user:";
    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
    private static final int TEMP_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    // ── List ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        Page<User> page = userRepository.findAll(pageable);

        Set<Long> deptIds = page.getContent().stream()
                .map(User::getDepartmentId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : departmentRepository.findAllById(deptIds).stream()
                        .collect(Collectors.toMap(Department::getId, Department::getName));

        return page.map(u -> UserResponse.from(u,
                u.getDepartmentId() != null ? deptNames.get(u.getDepartmentId()) : null));
    }

    // ── Search users ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> searchUsers(String query) {
        if (query == null || query.isBlank()) return List.of();
        org.springframework.data.domain.Pageable limit =
                org.springframework.data.domain.PageRequest.of(0, 20);
        return userRepository.searchByNameOrUsername(query.trim(), limit)
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> listAllActiveUsers() {
        return userRepository.findByIsActiveTrueOrderByFullNameAsc()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> listMeetingCandidates() {
        List<User> users = userRepository.findByRoleAndIsActiveTrueOrderByFullNameAsc(Role.SECRETARY);

        // Batch-load departments to avoid N+1
        Set<Long> deptIds = users.stream()
                .map(User::getDepartmentId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> deptNames = departmentRepository.findAllById(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));

        return users.stream()
                .map(u -> UserResponse.from(u, u.getDepartmentId() != null
                        ? deptNames.get(u.getDepartmentId())
                        : null))
                .toList();
    }

    // ── Get by ID ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id, User requester) {
        // Non-admin users may only view their own profile
        if (requester.getRole() != Role.ADMIN && !requester.getId().equals(id)) {
            throw new ForbiddenException("You are not allowed to view other users' profiles");
        }
        User user = findUserOrThrow(id);
        return UserResponse.from(user);
    }

    // ── Get current user ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(User requester) {
        // Reload from DB to get the latest data
        User user = findUserOrThrow(requester.getId());
        return UserResponse.from(user);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String employeeCode = resolveEmployeeCode(request.getEmployeeCode(), request.getUsername());
        if (userRepository.existsByUsername(employeeCode)
                || userRepository.existsByEmployeeCode(employeeCode)) {
            throw new BadRequestException("Employee code '" + employeeCode + "' is already taken");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email '" + request.getEmail() + "' is already in use");
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new BadRequestException("Phone number '" + request.getPhoneNumber() + "' is already in use");
        }
        if (request.getIdentification() != null && !request.getIdentification().isBlank()
                && userRepository.existsByIdentification(request.getIdentification())) {
            throw new BadRequestException("Identification '" + request.getIdentification() + "' is already in use");
        }
        if (request.getDepartmentId() != null && !departmentRepository.existsById(request.getDepartmentId())) {
            throw new BadRequestException("Department not found with id: " + request.getDepartmentId());
        }

        User user = User.builder()
                .username(employeeCode)
                .employeeCode(employeeCode)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .dob(request.getDob())
                .phoneNumber(blankToNull(request.getPhoneNumber()))
                .degree(blankToNull(request.getDegree()))
                .identification(blankToNull(request.getIdentification()))
                .address(blankToNull(request.getAddress()))
                .bankName(blankToNull(request.getBankName()))
                .bankNumber(blankToNull(request.getBankNumber()))
                .img(blankToNull(request.getImg()))
                .role(request.getRole())
                .departmentId(request.getDepartmentId())
                .isActive(true)
                .build();

        User saved = userRepository.save(user);
        log.info("Created user '{}' with role {}", saved.getUsername(), saved.getRole());
        return UserResponse.from(saved);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request, User requester) {
        User target = findUserOrThrow(id);

        boolean isSelf = requester.getId().equals(id);
        boolean isAdmin = requester.getRole() == Role.ADMIN;

        // Non-admin users may only update themselves
        if (!isAdmin && !isSelf) {
            throw new ForbiddenException("You are not allowed to update other users");
        }

        // Only ADMIN may change roles (Requirement 11.6)
        if (request.getRole() != null && !isAdmin) {
            throw new ForbiddenException("Only ADMIN may change user roles");
        }
        if (request.getEmployeeCode() != null && !isAdmin) {
            throw new ForbiddenException("Only ADMIN may change employee codes");
        }

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            target.setFullName(request.getFullName());
        }
        if (request.getEmployeeCode() != null && isAdmin) {
            String employeeCode = resolveEmployeeCode(request.getEmployeeCode(), null);
            if (!employeeCode.equals(target.getEmployeeCode())
                    && (userRepository.existsByEmployeeCode(employeeCode)
                    || userRepository.existsByUsername(employeeCode))) {
                throw new BadRequestException("Employee code '" + employeeCode + "' is already taken");
            }
            String oldUsername = target.getUsername();
            target.setUsername(employeeCode);
            target.setEmployeeCode(employeeCode);
            if (!employeeCode.equals(oldUsername)) {
                invalidateUserTokens(id);
            }
        }
        if (request.getEmail() != null) {
            if (!request.getEmail().isBlank()
                    && !request.getEmail().equals(target.getEmail())
                    && userRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email '" + request.getEmail() + "' is already in use");
            }
            target.setEmail(request.getEmail().isBlank() ? null : request.getEmail());
        }
        if (request.getDob() != null) {
            target.setDob(request.getDob());
        }
        if (request.getPhoneNumber() != null) {
            String phone = blankToNull(request.getPhoneNumber());
            if (phone != null
                    && !phone.equals(target.getPhoneNumber())
                    && userRepository.existsByPhoneNumber(phone)) {
                throw new BadRequestException("Phone number '" + phone + "' is already in use");
            }
            target.setPhoneNumber(phone);
        }
        if (request.getDegree() != null) {
            target.setDegree(blankToNull(request.getDegree()));
        }
        if (request.getIdentification() != null) {
            String identification = blankToNull(request.getIdentification());
            if (identification != null
                    && !identification.equals(target.getIdentification())
                    && userRepository.existsByIdentification(identification)) {
                throw new BadRequestException("Identification '" + identification + "' is already in use");
            }
            target.setIdentification(identification);
        }
        if (request.getAddress() != null) {
            target.setAddress(blankToNull(request.getAddress()));
        }
        if (request.getBankName() != null) {
            target.setBankName(blankToNull(request.getBankName()));
        }
        if (request.getBankNumber() != null) {
            target.setBankNumber(blankToNull(request.getBankNumber()));
        }
        if (request.getImg() != null) {
            target.setImg(blankToNull(request.getImg()));
        }
        if (request.getRole() != null) {
            Role oldRole = target.getRole();
            target.setRole(request.getRole());
            if (oldRole != request.getRole()) {
                // Invalidate all existing tokens for this user (Requirement 11.4)
                invalidateUserTokens(id);
                log.info("Role changed for user '{}': {} → {}; tokens invalidated",
                        target.getUsername(), oldRole, request.getRole());
            }
        }
        if (request.getDepartmentId() != null) {
            if (!departmentRepository.existsById(request.getDepartmentId())) {
                throw new BadRequestException(
                        "Department not found with id: " + request.getDepartmentId());
            }
            target.setDepartmentId(request.getDepartmentId());
        }
        if (request.getIsActive() != null && isAdmin) {
            target.setActive(request.getIsActive());
        }

        User saved = userRepository.save(target);
        log.info("Updated user '{}' by '{}'", saved.getUsername(), requester.getUsername());

        String deptName = null;
        if (saved.getDepartmentId() != null) {
            deptName = departmentRepository.findById(saved.getDepartmentId())
                    .map(Department::getName).orElse(null);
        }
        return UserResponse.from(saved, deptName);
    }

    @Override
    @Transactional
    public void changeOwnPassword(ChangePasswordRequest request, User requester) {
        User target = findUserOrThrow(requester.getId());
        if (!passwordEncoder.matches(request.currentPassword(), target.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        target.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(target);
        invalidateUserTokens(target.getId());
        log.info("Password changed for user '{}'", target.getUsername());
    }

    // ── Toggle active ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserResponse toggleUserActive(Long id, User requester) {
        User target = findUserOrThrow(id);

        // Prevent deactivating own account
        if (requester.getId().equals(id)) {
            throw new BadRequestException("You cannot deactivate your own account");
        }

        boolean newStatus = !target.isActive();
        target.setActive(newStatus);
        User saved = userRepository.save(target);

        // Invalidate all tokens when deactivating so the user is logged out immediately
        if (!newStatus) {
            invalidateUserTokens(id);
            log.info("Deactivated user '{}' (id={}) by '{}'; tokens invalidated",
                    target.getUsername(), id, requester.getUsername());
        } else {
            log.info("Activated user '{}' (id={}) by '{}'",
                    target.getUsername(), id, requester.getUsername());
        }

        String deptName = null;
        if (saved.getDepartmentId() != null) {
            deptName = departmentRepository.findById(saved.getDepartmentId())
                    .map(Department::getName).orElse(null);
        }
        return UserResponse.from(saved, deptName);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteUser(Long id, User requester) {
        User target = findUserOrThrow(id);

        // Prevent self-deletion
        if (requester.getId().equals(id)) {
            throw new BadRequestException("You cannot delete your own account");
        }

        // Prevent deletion of users with active meeting memberships (Requirement 11.7)
        if (userRepository.hasActiveMeetingMemberships(id)) {
            throw new BadRequestException(
                    "Cannot delete user '" + target.getUsername()
                    + "': they have active or scheduled meeting memberships");
        }

        userRepository.delete(target);
        log.info("Deleted user '{}' (id={}) by '{}'",
                target.getUsername(), id, requester.getUsername());
    }

    // ── Reset password ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public String resetPassword(Long id, ResetPasswordRequest request, User requester) {
        // Only ADMIN may reset other users' passwords (Requirement 11.10)
        if (requester.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only ADMIN may reset other users' passwords");
        }

        User target = findUserOrThrow(id);

        String plainPassword = (request != null
                && request.getNewPassword() != null
                && !request.getNewPassword().isBlank())
                ? request.getNewPassword()
                : generateTemporaryPassword();

        target.setPasswordHash(passwordEncoder.encode(plainPassword));
        userRepository.save(target);

        // Invalidate all existing JWT tokens for the target user (Requirement 11.9)
        invalidateUserTokens(id);

        log.info("Password reset for user '{}' (id={}) by admin '{}'",
                target.getUsername(), id, requester.getUsername());

        return plainPassword;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    /**
     * Marks all tokens for a user as invalidated by storing a Redis key.
     * The JWT filter checks this key and rejects tokens issued before the
     * invalidation timestamp.
     *
     * <p>This method throws a RuntimeException if Redis is unavailable, causing
     * the enclosing transaction to roll back. This is intentional: if we cannot
     * invalidate tokens, the role/status change must not be committed (fail-closed).
     * Requirements: 11.4, 11.9
     */
    private void invalidateUserTokens(Long userId) {
        try {
            // Store the invalidation timestamp; JWT filter compares token iat against this
            redisTemplate.opsForValue().set(
                    JWT_BLACKLIST_PREFIX + userId,
                    String.valueOf(System.currentTimeMillis()));
            log.debug("Invalidated all tokens for userId={}", userId);
        } catch (Exception e) {
            log.error("Failed to invalidate tokens for userId={}: {}. "
                    + "Rolling back to prevent stale tokens from remaining valid.",
                    userId, e.getMessage(), e);
            throw new RuntimeException(
                    "Cannot invalidate user tokens — Redis unavailable. Operation aborted.", e);
        }
    }

    private String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(random.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private String resolveEmployeeCode(String employeeCode, String username) {
        String value = employeeCode != null && !employeeCode.isBlank()
                ? employeeCode.trim()
                : username != null ? username.trim() : null;
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Employee code is required");
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
