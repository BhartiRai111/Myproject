package com.storehub.controller;

import com.storehub.dto.HsnRequest;
import com.storehub.dto.HsnResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.HsnStatus;
import com.storehub.service.HsnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/hsn")
@RequiredArgsConstructor
public class HsnController {

    private final HsnService hsnService;

    @GetMapping
    public ResponseEntity<PagedResponse<HsnResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) HsnStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(hsnService.search(search, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<HsnResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(hsnService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<HsnResponse> create(@Valid @RequestBody HsnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hsnService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<HsnResponse> update(@PathVariable Long id, @Valid @RequestBody HsnRequest request) {
        return ResponseEntity.ok(hsnService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<HsnResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(hsnService.setStatus(id, HsnStatus.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<HsnResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(hsnService.setStatus(id, HsnStatus.INACTIVE));
    }
}
