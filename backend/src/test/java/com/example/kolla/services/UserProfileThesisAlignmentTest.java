package com.example.kolla.services;

import com.example.kolla.dto.ChangePasswordRequest;
import com.example.kolla.dto.CreateUserRequest;
import com.example.kolla.dto.UpdateUserRequest;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DepartmentRepository;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.responses.UserResponse;
import com.example.kolla.services.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileThesisAlignmentTest {

    @Mock private UserRepository userRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(
                userRepository,
                departmentRepository,
                passwordEncoder,
                redisTemplate);
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void createUser_acceptsEmployeeCodeAndThesisProfileFields() {
        CreateUserRequest request = CreateUserRequest.builder()
                .employeeCode("NV001")
                .password("StrongPass1!")
                .fullName("Nguyễn Văn A")
                .email("a@example.com")
                .phoneNumber("0909000001")
                .identification("012345678901")
                .dob(LocalDate.of(2000, 1, 2))
                .degree("Thạc sĩ")
                .address("Hà Nội")
                .bankName("VCB")
                .bankNumber("123456789")
                .img("avatars/nv001.png")
                .role(Role.USER)
                .departmentId(10L)
                .build();

        when(userRepository.existsByUsername("NV001")).thenReturn(false);
        when(userRepository.existsByEmployeeCode("NV001")).thenReturn(false);
        when(userRepository.existsByEmail("a@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("0909000001")).thenReturn(false);
        when(userRepository.existsByIdentification("012345678901")).thenReturn(false);
        when(departmentRepository.existsById(10L)).thenReturn(true);
        when(passwordEncoder.encode("StrongPass1!")).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(99L);
            return user;
        });

        UserResponse response = service.createUser(request);

        assertThat(response.getEmployeeCode()).isEqualTo("NV001");
        assertThat(response.getPhoneNumber()).isEqualTo("0909000001");
        assertThat(response.getIdentification()).isEqualTo("012345678901");
        assertThat(response.getDegree()).isEqualTo("Thạc sĩ");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("NV001");
        assertThat(userCaptor.getValue().getEmployeeCode()).isEqualTo("NV001");
    }

    @Test
    void updateOwnProfile_updatesAllowedThesisFields() {
        User requester = user(7L);
        UpdateUserRequest request = UpdateUserRequest.builder()
                .phoneNumber("0909000002")
                .address("TP Hồ Chí Minh")
                .bankName("ACB")
                .bankNumber("987654321")
                .build();

        when(userRepository.findById(7L)).thenReturn(Optional.of(requester));
        when(userRepository.existsByPhoneNumber("0909000002")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = service.updateUser(7L, request, requester);

        assertThat(response.getPhoneNumber()).isEqualTo("0909000002");
        assertThat(response.getAddress()).isEqualTo("TP Hồ Chí Minh");
        assertThat(response.getBankName()).isEqualTo("ACB");
        assertThat(response.getBankNumber()).isEqualTo("987654321");
    }

    @Test
    void changeOwnPassword_requiresCurrentPassword() {
        User requester = user(7L);
        ChangePasswordRequest request = new ChangePasswordRequest("wrong", "NewStrongPass1!");

        when(userRepository.findById(7L)).thenReturn(Optional.of(requester));
        when(passwordEncoder.matches("wrong", requester.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> service.changeOwnPassword(request, requester))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Current password");
    }

    @Test
    void changeOwnPassword_encodesNewPassword() {
        User requester = user(7L);
        ChangePasswordRequest request = new ChangePasswordRequest("CurrentPass1!", "NewStrongPass1!");

        when(userRepository.findById(7L)).thenReturn(Optional.of(requester));
        when(passwordEncoder.matches("CurrentPass1!", requester.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("NewStrongPass1!")).thenReturn("$2a$12$newhash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.changeOwnPassword(request, requester);

        assertThat(requester.getPasswordHash()).isEqualTo("$2a$12$newhash");
        verify(userRepository).save(requester);
    }

    private User user(Long id) {
        return User.builder()
                .id(id)
                .username("NV00" + id)
                .employeeCode("NV00" + id)
                .passwordHash("$2a$12$current")
                .fullName("Nguyễn Văn " + id)
                .email("nv" + id + "@example.com")
                .role(Role.USER)
                .isActive(true)
                .build();
    }
}
