package com.storehub.controller;

import com.storehub.dto.JournalCreateRequest;
import com.storehub.dto.JournalHeaderResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.VoucherType;
import com.storehub.service.AccountingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounting/journals")
@RequiredArgsConstructor
public class AccountingController {

    private final AccountingService accountingService;

    @GetMapping
    public ResponseEntity<PagedResponse<JournalHeaderResponse>> search(
            @RequestParam(required = false) VoucherType voucherType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(accountingService.searchJournals(voucherType, fromDate, toDate, search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JournalHeaderResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(accountingService.getJournalById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JournalHeaderResponse> create(@Valid @RequestBody JournalCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountingService.postManualJournal(request));
    }
}
