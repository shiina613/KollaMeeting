package com.example.kolla.models;

import com.example.kolla.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * JPA entity for the `user` table.
 * Implements UserDetails so it can be used directly with Spring Security.
 * Requirements: 2.6, 11.1–11.2
 */
@Entity
@Table(name = "user")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EmployeeCode", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "EmployeeCode", insertable = false, updatable = false, length = 100)
    private String employeeCode;

    @Column(name = "Password", nullable = false)
    private String passwordHash;

    @Column(name = "Name", nullable = false)
    private String fullName;

    @Column(name = "Email", unique = true)
    private String email;

    @Column(name = "Dob")
    private LocalDate dob;

    @Column(name = "PhoneNumber", unique = true, length = 30)
    private String phoneNumber;

    @Column(name = "Degree", length = 255)
    private String degree;

    @Column(name = "Identification", unique = true, length = 100)
    private String identification;

    @Column(name = "Address", length = 1000)
    private String address;

    @Column(name = "BankName", length = 255)
    private String bankName;

    @Column(name = "BankNumber", length = 100)
    private String bankNumber;

    @Column(name = "Img", length = 1000)
    private String img;

    @Enumerated(EnumType.STRING)
    @Column(name = "Role", nullable = false)
    private Role role;

    @Column(name = "Department_id")
    private Long departmentId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── UserDetails implementation ──────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** Returns the hashed password stored in the database. */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return isActive;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive;
    }
}
