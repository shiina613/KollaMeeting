package com.example.kolla.controllers;

import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.models.User;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.responses.UserResponse;
import com.example.kolla.services.UserService;
import com.example.kolla.utils.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
                org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
                org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class
        }
)
@Import(UserControllerTest.TestSecurityConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"
})
class UserControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @org.springframework.context.annotation.Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session ->
                            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());

            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private UserRepository userRepository;

    @Test
    @DisplayName("DELETE /users/{id} calls UserService.deleteUser for ADMIN")
    void deleteUser_admin_callsServiceAndReturnsSuccess() throws Exception {
        User admin = User.builder()
                .id(1L)
                .username("admin")
                .passwordHash("hash")
                .fullName("Admin User")
                .email("admin@example.com")
                .role(Role.ADMIN)
                .isActive(true)
                .build();

        mockMvc.perform(delete("/users/{id}", 4L)
                        .with(user(admin))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("User deleted successfully")));

        verify(userService).deleteUser(4L, admin);
    }

    @Test
    @DisplayName("POST /users/me/avatar uploads current user's avatar")
    void uploadCurrentUserAvatar_callsServiceAndReturnsUpdatedUser() throws Exception {
        User currentUser = User.builder()
                .id(1L)
                .username("tungnq")
                .passwordHash("hash")
                .fullName("Nguyen Quang Tung")
                .email("tungnq@kolla.local")
                .role(Role.USER)
                .isActive(true)
                .build();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "avatar".getBytes());
        UserResponse response = UserResponse.builder()
                .id(1L)
                .username("tungnq")
                .fullName("Nguyen Quang Tung")
                .email("tungnq@kolla.local")
                .role(Role.USER)
                .img("/api/v1/users/1/avatar")
                .isActive(true)
                .build();

        org.mockito.Mockito.when(userService.uploadCurrentUserAvatar(any(), eq(currentUser)))
                .thenReturn(response);

        mockMvc.perform(multipart("/users/me/avatar")
                        .file(file)
                        .with(user(currentUser))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.img", is("/api/v1/users/1/avatar")));

        verify(userService).uploadCurrentUserAvatar(any(), eq(currentUser));
    }

    @Test
    @DisplayName("POST /users/me/avatar rejects unsupported image type")
    void uploadCurrentUserAvatar_unsupportedType_returnsBadRequest() throws Exception {
        User currentUser = User.builder()
                .id(1L)
                .username("tungnq")
                .passwordHash("hash")
                .fullName("Nguyen Quang Tung")
                .email("tungnq@kolla.local")
                .role(Role.USER)
                .isActive(true)
                .build();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not-image".getBytes());

        doThrow(new BadRequestException("Unsupported avatar type: text/plain"))
                .when(userService).uploadCurrentUserAvatar(any(), eq(currentUser));

        mockMvc.perform(multipart("/users/me/avatar")
                        .file(file)
                        .with(user(currentUser))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
