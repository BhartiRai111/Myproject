package com.storehub.controller;

import com.storehub.dto.ExpenseCategoryRequest;
import com.storehub.dto.ExpenseCategoryResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.service.ExpenseCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/masters/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseCategoryService expenseCategoryService;

    @GetMapping
    public ResponseEntity<PagedResponse<ExpenseCategoryResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(expenseCategoryService.search(search, active, page, size));
    }

    @GetMapping("/active")
    public ResponseEntity<List<ExpenseCategoryResponse>> listActive() {
        return ResponseEntity.ok(expenseCategoryService.listActive());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseCategoryResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(expenseCategoryService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<ExpenseCategoryResponse> create(@Valid @RequestBody ExpenseCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseCategoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<ExpenseCategoryResponse> update(@PathVariable Long id, @Valid @RequestBody ExpenseCategoryRequest request) {
        return ResponseEntity.ok(expenseCategoryService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<ExpenseCategoryResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(expenseCategoryService.setActive(id, true));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<ExpenseCategoryResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(expenseCategoryService.setActive(id, false));
    }
}
