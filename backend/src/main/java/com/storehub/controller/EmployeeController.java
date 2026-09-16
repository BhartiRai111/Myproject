package com.storehub.controller;

import com.storehub.dto.EmployeeRequest;
import com.storehub.dto.EmployeeResponse;
import com.storehub.dto.GenerateEmployeeCodeResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.EmployeeStatus;
import com.storehub.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    public ResponseEntity<PagedResponse<EmployeeResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(employeeService.search(search, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getById(id));
    }

    @PostMapping("/generate-code")
    @PreAuthorize("hasAuthority('PERM_EMPLOYEE_CREATE')")
    public ResponseEntity<GenerateEmployeeCodeResponse> generateCode() {
        return ResponseEntity.ok(new GenerateEmployeeCodeResponse(employeeService.generateCode()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_EMPLOYEE_CREATE')")
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_EMPLOYEE_EDIT')")
    public ResponseEntity<EmployeeResponse> update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.ok(employeeService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PERM_EMPLOYEE_EDIT')")
    public ResponseEntity<EmployeeResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.setStatus(id, EmployeeStatus.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PERM_EMPLOYEE_EDIT')")
    public ResponseEntity<EmployeeResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.setStatus(id, EmployeeStatus.INACTIVE));
    }
}
