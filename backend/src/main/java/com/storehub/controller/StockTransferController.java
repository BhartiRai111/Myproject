package com.storehub.controller;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.StockTransferRequest;
import com.storehub.dto.StockTransferResponse;
import com.storehub.entity.StockTransferStatus;
import com.storehub.service.StockTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_STOCK_TRANSFER_VIEW')")
    public ResponseEntity<PagedResponse<StockTransferResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) StockTransferStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(stockTransferService.search(search, status, fromDate, toDate, storeId, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_STOCK_TRANSFER_VIEW')")
    public ResponseEntity<StockTransferResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(stockTransferService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_STOCK_TRANSFER_CREATE')")
    public ResponseEntity<StockTransferResponse> create(@Valid @RequestBody StockTransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockTransferService.create(request));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('PERM_STOCK_TRANSFER_APPROVE')")
    public ResponseEntity<StockTransferResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(stockTransferService.approve(id));
    }

    @PatchMapping("/{id}/dispatch")
    @PreAuthorize("hasAuthority('PERM_STOCK_TRANSFER_DISPATCH')")
    public ResponseEntity<StockTransferResponse> dispatch(@PathVariable Long id) {
        return ResponseEntity.ok(stockTransferService.dispatch(id));
    }

    @PatchMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('PERM_STOCK_TRANSFER_RECEIVE')")
    public ResponseEntity<StockTransferResponse> receive(@PathVariable Long id) {
        return ResponseEntity.ok(stockTransferService.receive(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PERM_STOCK_TRANSFER_CANCEL')")
    public ResponseEntity<StockTransferResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(stockTransferService.cancel(id));
    }
}
