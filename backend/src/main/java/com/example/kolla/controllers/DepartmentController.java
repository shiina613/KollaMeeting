package com.example.kolla.controllers;

import com.example.kolla.dto.CreateDepartmentRequest;
import com.example.kolla.dto.UpdateDepartmentRequest;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.DepartmentResponse;
import com.example.kolla.services.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Department management endpoints.
 * Context-path: /api/v1
 * Requirements: 12.1–12.8
 */
@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
@Tag(name = "Departments", description = "Department CRUD")
@SecurityRequirement(name = "bearerAuth")
public class DepartmentController {

    private final DepartmentService departmentService;

    /**
     * GET /api/v1/departments
     * List all departments. Any authenticated user.
     */
    @GetMapping
    @Operation(summary = "List all departments")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of departments"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> listDepartments() {
        return ResponseEntity.ok(ApiResponse.success(departmentService.listDepartments()));
    }

    /**
     * GET /api/v1/departments/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get department by ID")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Department details"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Department not found")
    })
    public ResponseEntity<ApiResponse<DepartmentResponse>> getDepartmentById(@Parameter(description = "Department ID") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(departmentService.getDepartmentById(id)));
    }

    /**
     * POST /api/v1/departments
     * Create a department. ADMIN only.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create department (ADMIN only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Department created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Department name already exists")
    })
    public ResponseEntity<ApiResponse<DepartmentResponse>> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request) {

        DepartmentResponse response = departmentService.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Department created successfully", response));
    }

    /**
     * PUT /api/v1/departments/{id}
     * Update a department. ADMIN only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update department (ADMIN only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Department updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Department not found")
    })
    public ResponseEntity<ApiResponse<DepartmentResponse>> updateDepartment(
            @Parameter(description = "Department ID") @PathVariable Long id,
            @Valid @RequestBody UpdateDepartmentRequest request) {

        DepartmentResponse response = departmentService.updateDepartment(id, request);
        return ResponseEntity.ok(ApiResponse.success("Department updated successfully", response));
    }

    /**
     * DELETE /api/v1/departments/{id}
     * Delete a department. ADMIN only.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete department (ADMIN only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Department deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Department not found")
    })
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(@Parameter(description = "Department ID") @PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.ok(ApiResponse.success("Department deleted successfully", null));
    }
}
