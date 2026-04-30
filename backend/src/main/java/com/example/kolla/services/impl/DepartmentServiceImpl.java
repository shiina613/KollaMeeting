package com.example.kolla.services.impl;

import com.example.kolla.dto.CreateDepartmentRequest;
import com.example.kolla.dto.UpdateDepartmentRequest;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.Department;
import com.example.kolla.repositories.DepartmentRepository;
import com.example.kolla.responses.DepartmentResponse;
import com.example.kolla.services.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DepartmentService implementation.
 * Requirements: 12.1–12.8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> listDepartments() {
        return departmentRepository.findAll().stream()
                .map(DepartmentResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentById(Long id) {
        return DepartmentResponse.from(findOrThrow(id));
    }

    @Override
    @Transactional
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        if (departmentRepository.existsByName(request.getName())) {
            throw new BadRequestException("Department with name '" + request.getName() + "' already exists");
        }

        Department department = Department.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        Department saved = departmentRepository.save(department);
        log.info("Created department '{}' (id={})", saved.getName(), saved.getId());
        return DepartmentResponse.from(saved);
    }

    @Override
    @Transactional
    public DepartmentResponse updateDepartment(Long id, UpdateDepartmentRequest request) {
        Department department = findOrThrow(id);

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!request.getName().equals(department.getName())
                    && departmentRepository.existsByName(request.getName())) {
                throw new BadRequestException("Department with name '" + request.getName() + "' already exists");
            }
            department.setName(request.getName());
        }
        if (request.getDescription() != null) {
            department.setDescription(request.getDescription());
        }

        Department saved = departmentRepository.save(department);
        log.info("Updated department '{}' (id={})", saved.getName(), saved.getId());
        return DepartmentResponse.from(saved);
    }

    @Override
    @Transactional
    public void deleteDepartment(Long id) {
        Department department = findOrThrow(id);
        departmentRepository.delete(department);
        log.info("Deleted department '{}' (id={})", department.getName(), id);
    }

    private Department findOrThrow(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));
    }
}
