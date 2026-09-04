package com.storehub.controller;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.SupplierCreateRequest;
import com.storehub.dto.SupplierPurchaseSummary;
import com.storehub.dto.SupplierResponse;
import com.storehub.dto.SupplierUpdateRequest;
import com.storehub.entity.SupplierStatus;
import com.storehub.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public ResponseEntity<PagedResponse<SupplierResponse>> getSuppliers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SupplierStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(supplierService.searchSuppliers(search, status, page, size, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupplierResponse> getSupplierById(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getSupplierById(id));
    }

    @GetMapping("/{id}/purchases")
    public ResponseEntity<List<SupplierPurchaseSummary>> getSupplierPurchaseHistory(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getPurchaseHistory(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<SupplierResponse> createSupplier(@Valid @RequestBody SupplierCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplierService.createSupplier(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<SupplierResponse> updateSupplier(@PathVariable Long id,
                                                             @Valid @RequestBody SupplierUpdateRequest request) {
        return ResponseEntity.ok(supplierService.updateSupplier(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<SupplierResponse> activateSupplier(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.setStatus(id, SupplierStatus.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<SupplierResponse> deactivateSupplier(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.setStatus(id, SupplierStatus.INACTIVE));
    }
}
