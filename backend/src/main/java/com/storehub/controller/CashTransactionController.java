package com.storehub.controller;

import com.storehub.dto.CashTransactionCreateRequest;
import com.storehub.dto.CashTransactionResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.CashTransactionStatus;
import com.storehub.entity.CashTransactionType;
import com.storehub.service.CashTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/cash-transactions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_CASH_MANAGE')")
public class CashTransactionController {

    private final CashTransactionService cashTransactionService;

    @GetMapping
    public ResponseEntity<PagedResponse<CashTransactionResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CashTransactionType transactionType,
            @RequestParam(required = false) CashTransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cashTransactionService.search(search, transactionType, status, fromDate, toDate, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CashTransactionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(cashTransactionService.getById(id));
    }

    @PostMapping
    public ResponseEntity<CashTransactionResponse> create(@Valid @RequestBody CashTransactionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cashTransactionService.create(request));
    }

    @PostMapping("/{id}/post")
    public ResponseEntity<CashTransactionResponse> post(@PathVariable Long id) {
        return ResponseEntity.ok(cashTransactionService.post(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<CashTransactionResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(cashTransactionService.cancel(id));
    }
}
