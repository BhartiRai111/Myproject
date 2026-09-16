package com.storehub.controller;

import com.storehub.dto.ExpenseCreateRequest;
import com.storehub.dto.ExpenseResponse;
import com.storehub.dto.ExpenseSummaryReportResponse;
import com.storehub.dto.ExpenseUpdateRequest;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.ExpenseStatus;
import com.storehub.entity.PaymentMode;
import com.storehub.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public ResponseEntity<PagedResponse<ExpenseResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ExpenseStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PaymentMode paymentMode,
            @RequestParam(required = false) Boolean gstApplicable,
            @RequestParam(required = false) Boolean itcEligible,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(expenseService.search(search, status, category, categoryId, supplierId, paymentMode,
                gstApplicable, itcEligible, fromDate, toDate, page, size));
    }

    @GetMapping("/reports/summary")
    public ResponseEntity<ExpenseSummaryReportResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(expenseService.summary(fromDate, toDate));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody ExpenseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponse> update(@PathVariable Long id, @Valid @RequestBody ExpenseUpdateRequest request) {
        return ResponseEntity.ok(expenseService.update(id, request));
    }

    @PostMapping("/{id}/post")
    public ResponseEntity<ExpenseResponse> post(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.post(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ExpenseResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.cancel(id));
    }
}
