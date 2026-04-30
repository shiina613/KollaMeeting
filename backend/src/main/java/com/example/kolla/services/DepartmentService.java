package com.example.kolla.services;

import com.example.kolla.dto.CreateDepartmentRequest;
import com.example.kolla.dto.UpdateDepartmentRequest;
import com.example.kolla.responses.DepartmentResponse;

import java.util.List;

/**
 * Department management service interface.
 * Requirements: 12.1–12.8
 */
public interface DepartmentService {

    List<DepartmentResponse> listDepartments();

    DepartmentResponse getDepartmentById(Long id);

    DepartmentResponse createDepartment(CreateDepartmentRequest request);

    DepartmentResponse updateDepartment(Long id, UpdateDepartmentRequest request);

    void deleteDepartment(Long id);
}
