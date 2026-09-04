package com.storehub.controller;

import com.storehub.dto.CustomerOutstandingResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.dto.ReceiptRequest;
import com.storehub.dto.ReceiptResponse;
import com.storehub.service.ReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping
    public ResponseEntity<PagedResponse<ReceiptResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(receiptService.search(search, customerId, fromDate, toDate, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReceiptResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(receiptService.getById(id));
    }

    @GetMapping("/outstanding/{customerId}")
    public ResponseEntity<CustomerOutstandingResponse> getOutstanding(@PathVariable Long customerId) {
        return ResponseEntity.ok(receiptService.getOutstandingForCustomer(customerId));
    }

    @PostMapping
    public ResponseEntity<ReceiptResponse> create(@Valid @RequestBody ReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(receiptService.create(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        receiptService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
