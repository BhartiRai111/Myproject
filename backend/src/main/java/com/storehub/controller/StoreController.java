package com.storehub.controller;

import com.storehub.dto.GenerateStoreCodeResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.dto.StoreGstContextResponse;
import com.storehub.dto.StoreRequest;
import com.storehub.dto.StoreResponse;
import com.storehub.entity.StoreStatus;
import com.storehub.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Store/Branch master (Multi-Store spec section 4/68) — lives under Master, not as a new
 * top-level module. GET endpoints are left unguarded, matching the precedent set by
 * EmployeeController/PartyController etc. (every authenticated user needs to read the
 * store list to populate a store picker), while mutations require STORE_* permissions.
 */
@RestController
@RequestMapping("/api/masters/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    public ResponseEntity<PagedResponse<StoreResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) StoreStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(storeService.search(search, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StoreResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(storeService.getById(id));
    }

    /** The effective seller GSTIN/state a billing form should use for this store (Multi-Store spec section 32). */
    @GetMapping("/{id}/gst-context")
    public ResponseEntity<StoreGstContextResponse> gstContext(@PathVariable Long id) {
        return ResponseEntity.ok(storeService.resolveGstContext(id));
    }

    @PostMapping("/generate-code")
    @PreAuthorize("hasAuthority('PERM_STORE_CREATE')")
    public ResponseEntity<GenerateStoreCodeResponse> generateCode() {
        return ResponseEntity.ok(new GenerateStoreCodeResponse(storeService.generateCode()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_STORE_CREATE')")
    public ResponseEntity<StoreResponse> create(@Valid @RequestBody StoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(storeService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_STORE_EDIT')")
    public ResponseEntity<StoreResponse> update(@PathVariable Long id, @Valid @RequestBody StoreRequest request) {
        return ResponseEntity.ok(storeService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PERM_STORE_EDIT')")
    public ResponseEntity<StoreResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(storeService.setStatus(id, StoreStatus.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PERM_STORE_EDIT')")
    public ResponseEntity<StoreResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(storeService.setStatus(id, StoreStatus.INACTIVE));
    }
}
