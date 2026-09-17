package com.storehub.controller;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.PaymentRequest;
import com.storehub.dto.PaymentResponse;
import com.storehub.dto.SupplierOutstandingResponse;
import com.storehub.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_PAYMENT_VIEW')")
    public ResponseEntity<PagedResponse<PaymentResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(paymentService.search(search, supplierId, fromDate, toDate, storeId, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_VIEW')")
    public ResponseEntity<PaymentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getById(id));
    }

    @GetMapping("/outstanding/{supplierId}")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_VIEW')")
    public ResponseEntity<SupplierOutstandingResponse> getOutstanding(@PathVariable Long supplierId) {
        return ResponseEntity.ok(paymentService.getOutstandingForSupplier(supplierId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_PAYMENT_CREATE')")
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_POST')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        paymentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
