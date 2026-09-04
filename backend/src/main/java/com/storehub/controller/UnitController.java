package com.storehub.controller;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.UnitRequest;
import com.storehub.dto.UnitResponse;
import com.storehub.entity.UnitStatus;
import com.storehub.service.UnitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/units")
@RequiredArgsConstructor
public class UnitController {

    private final UnitService unitService;

    @GetMapping
    public ResponseEntity<PagedResponse<UnitResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UnitStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(unitService.search(search, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UnitResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(unitService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<UnitResponse> create(@Valid @RequestBody UnitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(unitService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<UnitResponse> update(@PathVariable Long id, @Valid @RequestBody UnitRequest request) {
        return ResponseEntity.ok(unitService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<UnitResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(unitService.setStatus(id, UnitStatus.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<UnitResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(unitService.setStatus(id, UnitStatus.INACTIVE));
    }
}
