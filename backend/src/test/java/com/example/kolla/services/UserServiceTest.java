package com.example.kolla.services;

import com.example.kolla.dto.CreateUserRequest;
import com.example.kolla.dto.ResetPasswordRequest;
import com.example.kolla.dto.UpdateUserRequest;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.User;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.responses.UserResponse;
import com.example.kolla.services.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserServiceImpl.
 * Covers: role validation, password reset logic, CRUD constraints.
 * Requirements: 20.1, 11.1–11.10
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private UserServiceImpl userService;

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private User adminUser;
    private User secretaryUser;
    private User regularUser;
    private User targetUser;

    @BeforeEach
    void setUp() {
        adminUser = buildUser(1L, "admin", Role.ADMIN);
        secretaryUser = buildUser(2L, "secretary", Role.SECRETARY);
        regularUser = buildUser(3L, "user1", Role.USER);
        targetUser = buildUser(4L, "target", Role.USER);

        // Default stub for Redis value ops
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── listUsers ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listUsers()")
    class ListUsers {

        @Test
        @DisplayName("Returns paginated user list")
        void listUsers_returnsPaginatedList() {
            Page<User> page = new PageImpl<>(List.of(adminUser, regularUser));
            when(userRepository.findAll(any(PageRequest.class))).thenReturn(page);

            Page<UserResponse> result = userService.listUsers(PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).extracting(UserResponse::getUsername)
                    .containsExactly("admin", "user1");
        }
    }

    // ── getUserById ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getUserById()")
    class GetUserById {

        @Test
        @DisplayName("ADMIN can fetch any user")
        void getUserById_adminCanFetchAnyUser() {
            when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));

            UserResponse response = userService.getUserById(4L, adminUser);

            assertThat(response.getId()).isEqualTo(4L);
        }

        @Test
        @DisplayName("User can fetch their own profile")
        void getUserById_userCanFetchOwnProfile() {
            when(userRepository.findById(3L)).thenReturn(Optional.of(regularUser));

            UserResponse response = userService.getUserById(3L, regularUser);

            assertThat(response.getId()).isEqualTo(3L);
        }

        @Test
        @DisplayName("Non-admin cannot fetch another user's profile")
        void getUserById_nonAdminCannotFetchOtherUser() {
            assertThatThrownBy(() -> userService.getUserById(4L, regularUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("not allowed");
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when user not found")
        void getUserById_throwsWhenNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserById(99L, adminUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ── createUser ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createUser()")
    class CreateUser {

        @Test
        @DisplayName("Creates user with encoded password")
        void createUser_encodesPassword() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("newuser")
                    .password("plainpassword")
                    .fullName("New User")
                    .role(Role.USER)
                    .build();

            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(passwordEncoder.encode("plainpassword")).thenReturn("$2a$12$hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(10L);
                return u;
            });

            UserResponse response = userService.createUser(request);

            assertThat(response.getUsername()).isEqualTo("newuser");
            verify(passwordEncoder).encode("plainpassword");
        }

        @Test
        @DisplayName("Throws BadRequestException when username already taken")
        void createUser_throwsOnDuplicateUsername() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("admin")
                    .password("pass12345")
                    .fullName("Duplicate")
                    .role(Role.USER)
                    .build();

            when(userRepository.existsByUsername("admin")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already taken");
        }

        @Test
        @DisplayName("Throws BadRequestException when email already in use")
        void createUser_throwsOnDuplicateEmail() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("newuser2")
                    .password("pass12345")
                    .fullName("New User 2")
                    .email("existing@example.com")
                    .role(Role.USER)
                    .build();

            when(userRepository.existsByUsername("newuser2")).thenReturn(false);
            when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already in use");
        }
    }

    // ── updateUser ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateUser()")
    class UpdateUser {

        @Test
        @DisplayName("ADMIN can change any user's role")
        void updateUser_adminCanChangeRole() {
            when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateUserRequest request = UpdateUserRequest.builder()
                    .role(Role.SECRETARY)
                    .build();

            UserResponse response = userService.updateUser(4L, request, adminUser);

            assertThat(response.getRole()).isEqualTo(Role.SECRETARY);
            // Token invalidation should be triggered
            verify(valueOps).set(contains("jwt:blacklist:user:4"), anyString());
        }

        @Test
        @DisplayName("Non-admin cannot change role")
        void updateUser_nonAdminCannotChangeRole() {
            when(userRepository.findById(3L)).thenReturn(Optional.of(regularUser));

            UpdateUserRequest request = UpdateUserRequest.builder()
                    .role(Role.ADMIN)
                    .build();

            assertThatThrownBy(() -> userService.updateUser(3L, request, regularUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Only ADMIN may change user roles");
        }

        @Test
        @DisplayName("Non-admin cannot update another user")
        void updateUser_nonAdminCannotUpdateOtherUser() {
            when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));

            UpdateUserRequest request = UpdateUserRequest.builder()
                    .fullName("Hacker")
                    .build();

            assertThatThrownBy(() -> userService.updateUser(4L, request, regularUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("not allowed");
        }

        @Test
        @DisplayName("User can update their own full name")
        void updateUser_userCanUpdateOwnFullName() {
            when(userRepository.findById(3L)).thenReturn(Optional.of(regularUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateUserRequest request = UpdateUserRequest.builder()
                    .fullName("Updated Name")
                    .build();

            UserResponse response = userService.updateUser(3L, request, regularUser);

            assertThat(response.getFullName()).isEqualTo("Updated Name");
        }

        @Test
        @DisplayName("Role change does not invalidate tokens when role is unchanged")
        void updateUser_noTokenInvalidationWhenRoleUnchanged() {
            when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // Same role as current (USER)
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .role(Role.USER)
                    .build();

            userService.updateUser(4L, request, adminUser);

            // No token invalidation since role didn't change
            verify(valueOps, never()).set(anyString(), anyString());
        }
    }

    // ── deleteUser ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteUser()")
    class DeleteUser {

        @Test
        @DisplayName("ADMIN can delete a user without active memberships")
        void deleteUser_adminCanDeleteUser() {
            when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));
            when(userRepository.hasActiveMeetingMemberships(4L)).thenReturn(false);

            userService.deleteUser(4L, adminUser);

            verify(userRepository).delete(targetUser);
        }

        @Test
        @DisplayName("Throws BadRequestException when user has active meeting memberships")
        void deleteUser_throwsWhenHasActiveMemberships() {
            when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));
            when(userRepository.hasActiveMeetingMemberships(4L)).thenReturn(true);

            assertThatThrownBy(() -> userService.deleteUser(4L, adminUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("active or scheduled meeting memberships");
        }

        @Test
        @DisplayName("Throws BadRequestException when admin tries to delete themselves")
        void deleteUser_throwsOnSelfDeletion() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

            assertThatThrownBy(() -> userService.deleteUser(1L, adminUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("cannot delete your own account");
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when user not found")
        void deleteUser_throwsWhenNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteUser(99L, adminUser))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resetPassword()")
    class ResetPassword {

        @Test
        @DisplayName("ADMIN can reset another user's password")
        void resetPassword_adminCanResetPassword() {
            when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$newhash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            String tempPassword = userService.resetPassword(4L, null, adminUser);

            assertThat(tempPassword).isNotBlank().hasSizeGreaterThanOrEqualTo(8);
            verify(passwordEncoder).encode(tempPassword);
            verify(userRepository).save(targetUser);
            // Tokens should be invalidated
            verify(valueOps).set(contains("jwt:blacklist:user:4"), anyString());
        }

        @Test
        @DisplayName("Uses provided password when explicitly given")
        void resetPassword_usesProvidedPassword() {
            when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));
            when(passwordEncoder.encode("MyNewPass1!")).thenReturn("$2a$12$newhash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            ResetPasswordRequest request = new ResetPasswordRequest("MyNewPass1!");
            String result = userService.resetPassword(4L, request, adminUser);

            assertThat(result).isEqualTo("MyNewPass1!");
            verify(passwordEncoder).encode("MyNewPass1!");
        }

        @Test
        @DisplayName("Non-ADMIN cannot reset another user's password")
        void resetPassword_nonAdminForbidden() {
            assertThatThrownBy(() -> userService.resetPassword(4L, null, regularUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Only ADMIN may reset");
        }

        @Test
        @DisplayName("Non-ADMIN SECRETARY cannot reset another user's password")
        void resetPassword_secretaryForbidden() {
            assertThatThrownBy(() -> userService.resetPassword(4L, null, secretaryUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Only ADMIN may reset");
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when target user not found")
        void resetPassword_throwsWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.resetPassword(99L, null, adminUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("Generated temporary password has minimum length of 8")
        void resetPassword_generatedPasswordHasMinLength() {
            when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$newhash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // Run multiple times to verify consistency
            for (int i = 0; i < 10; i++) {
                String tempPassword = userService.resetPassword(4L, null, adminUser);
                assertThat(tempPassword).hasSizeGreaterThanOrEqualTo(8);
            }
        }

        @Test
        @DisplayName("Token invalidation is called after password reset")
        void resetPassword_invalidatesTokens() {
            when(userRepository.findById(4L)).thenReturn(Optional.of(targetUser));
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$newhash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.resetPassword(4L, null, adminUser);

            verify(valueOps).set(eq("jwt:blacklist:user:4"), anyString());
        }
    }

    // ── getCurrentUser ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCurrentUser()")
    class GetCurrentUser {

        @Test
        @DisplayName("Returns the current user's profile")
        void getCurrentUser_returnsProfile() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

            UserResponse response = userService.getCurrentUser(adminUser);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getUsername()).isEqualTo("admin");
            assertThat(response.getRole()).isEqualTo(Role.ADMIN);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private User buildUser(Long id, String username, Role role) {
        return User.builder()
                .id(id)
                .username(username)
                .passwordHash("$2a$12$hashed")
                .fullName(username + " Full Name")
                .email(username + "@example.com")
                .role(role)
                .isActive(true)
                .build();
    }
}
