package com.example.kolla.repositories;

import com.example.kolla.models.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for Department entities.
 * Requirements: 12.1–12.8
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    boolean existsByName(String name);
}
