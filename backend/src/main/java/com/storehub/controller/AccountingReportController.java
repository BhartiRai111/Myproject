package com.storehub.controller;

import com.storehub.dto.AccountLedgerResponse;
import com.storehub.dto.AccountingDashboardResponse;
import com.storehub.dto.DayBookResponse;
import com.storehub.dto.TrialBalanceResponse;
import com.storehub.service.AccountingReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounting/reports")
@RequiredArgsConstructor
public class AccountingReportController {

    private final AccountingReportService accountingReportService;

    @GetMapping("/dashboard")
    public ResponseEntity<AccountingDashboardResponse> dashboard() {
        return ResponseEntity.ok(accountingReportService.getDashboardSummary());
    }

    @GetMapping("/day-book")
    public ResponseEntity<DayBookResponse> dayBook(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(accountingReportService.dayBook(fromDate, toDate));
    }

    @GetMapping("/account-ledger/{accountId}")
    public ResponseEntity<AccountLedgerResponse> accountLedger(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(accountingReportService.accountLedger(accountId, fromDate, toDate));
    }

    @GetMapping("/trial-balance")
    public ResponseEntity<TrialBalanceResponse> trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(accountingReportService.trialBalance(asOfDate));
    }
}
