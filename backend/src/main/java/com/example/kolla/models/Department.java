package com.example.kolla.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for the `department` table.
 * Requirements: 12.1–12.8
 */
@Entity
@Table(name = "department")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "Name", nullable = false)
    private String name;

    @Column(name = "DepartmentCode", nullable = false, unique = true, length = 100)
    private String departmentCode;

    @Transient
    private String description;

    @Transient
    private LocalDateTime createdAt;

    @Transient
    private LocalDateTime updatedAt;
}
