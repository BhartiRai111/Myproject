package com.storehub.controller;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.PurchaseCreateRequest;
import com.storehub.dto.PurchaseResponse;
import com.storehub.dto.PurchaseUpdateRequest;
import com.storehub.entity.PaymentStatus;
import com.storehub.entity.PurchaseStatus;
import com.storehub.service.PurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
public class PurchaseController {

    private final PurchaseService purchaseService;

    @GetMapping
    public ResponseEntity<PagedResponse<PurchaseResponse>> getPurchases(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) PurchaseStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(purchaseService.getPurchases(search, paymentStatus, status, fromDate, toDate, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseResponse> getPurchaseById(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseService.getPurchaseById(id));
    }

    @PostMapping
    public ResponseEntity<PurchaseResponse> createPurchase(@Valid @RequestBody PurchaseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseService.createPurchase(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PurchaseResponse> updatePurchase(@PathVariable Long id,
                                                            @Valid @RequestBody PurchaseUpdateRequest request) {
        return ResponseEntity.ok(purchaseService.updatePurchase(id, request));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<PurchaseResponse> cancelPurchase(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseService.cancelPurchase(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePurchase(@PathVariable Long id) {
        purchaseService.deletePurchase(id);
        return ResponseEntity.noContent().build();
    }
}
