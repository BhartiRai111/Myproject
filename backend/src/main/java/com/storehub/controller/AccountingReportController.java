package com.storehub.controller;

import com.storehub.dto.AccountLedgerResponse;
import com.storehub.dto.AccountSummaryResponse;
import com.storehub.dto.AccountingDashboardResponse;
import com.storehub.dto.AccountingHealthCheckResponse;
import com.storehub.dto.BalanceSheetResponse;
import com.storehub.dto.CashBankBookResponse;
import com.storehub.dto.DayBookResponse;
import com.storehub.dto.ExpenseIncomeSummaryResponse;
import com.storehub.dto.OutstandingBillReportResponse;
import com.storehub.dto.PartyLedgerResponse;
import com.storehub.dto.ProfitLossResponse;
import com.storehub.dto.ReceivablePayableResponse;
import com.storehub.dto.TrialBalanceResponse;
import com.storehub.entity.AccountType;
import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.VoucherType;
import com.storehub.service.AccountSummaryService;
import com.storehub.service.AccountingHealthCheckService;
import com.storehub.service.AccountingReportService;
import com.storehub.service.BalanceSheetService;
import com.storehub.service.CashBankBookService;
import com.storehub.service.OutstandingBillService;
import com.storehub.service.PartyLedgerService;
import com.storehub.service.ProfitLossService;
import com.storehub.service.ReceivablePayableService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Phase 4 accounting reports. Every endpoint reads the posted accounting
 * journal (or, for Outstanding/Ageing, the Sale.dueAmount/Purchase.payableAmount
 * fields ReceiptService/PaymentService keep in exact sync with
 * ReceiptAllocation/PaymentAllocation) — never a second, independently
 * calculated source of truth. Kept in one controller (matching the existing
 * Phase 1 convention) with the underlying report LOGIC split across focused
 * services (CashBankBookService, PartyLedgerService, ReceivablePayableService,
 * OutstandingBillService, ProfitLossService, BalanceSheetService,
 * AccountSummaryService, AccountingHealthCheckService).
 */
@RestController
@RequestMapping("/api/accounting/reports")
@RequiredArgsConstructor
public class AccountingReportController {

    private final AccountingReportService accountingReportService;
    private final CashBankBookService cashBankBookService;
    private final PartyLedgerService partyLedgerService;
    private final ReceivablePayableService receivablePayableService;
    private final OutstandingBillService outstandingBillService;
    private final ProfitLossService profitLossService;
    private final BalanceSheetService balanceSheetService;
    private final AccountSummaryService accountSummaryService;
    private final AccountingHealthCheckService accountingHealthCheckService;

    @GetMapping("/dashboard")
    public ResponseEntity<AccountingDashboardResponse> dashboard() {
        return ResponseEntity.ok(accountingReportService.getDashboardSummary());
    }

    @GetMapping("/day-book")
    public ResponseEntity<DayBookResponse> dayBook(
            @RequestParam(required = false) VoucherType voucherType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(accountingReportService.dayBook(voucherType, fromDate, toDate));
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

    @GetMapping("/cash-book")
    public ResponseEntity<CashBankBookResponse> cashBook(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(cashBankBookService.cashBook(fromDate, toDate));
    }

    @GetMapping("/bank-book")
    public ResponseEntity<CashBankBookResponse> bankBook(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(cashBankBookService.bankBook(accountId, fromDate, toDate));
    }

    @GetMapping("/party-ledger")
    public ResponseEntity<PartyLedgerResponse> partyLedger(
            @RequestParam AccountingPartyType partyType,
            @RequestParam Long partyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(partyLedgerService.partyLedger(partyType, partyId, fromDate, toDate));
    }

    @GetMapping("/receivable")
    public ResponseEntity<ReceivablePayableResponse> receivable(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(receivablePayableService.receivable(fromDate, toDate));
    }

    @GetMapping("/payable")
    public ResponseEntity<ReceivablePayableResponse> payable(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(receivablePayableService.payable(fromDate, toDate));
    }

    @GetMapping("/outstanding/customers")
    public ResponseEntity<OutstandingBillReportResponse> outstandingCustomers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(outstandingBillService.customerOutstanding(asOfDate));
    }

    @GetMapping("/outstanding/suppliers")
    public ResponseEntity<OutstandingBillReportResponse> outstandingSuppliers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(outstandingBillService.supplierOutstanding(asOfDate));
    }

    @GetMapping("/profit-loss")
    public ResponseEntity<ProfitLossResponse> profitLoss(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(profitLossService.profitAndLoss(fromDate, toDate));
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<BalanceSheetResponse> balanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(balanceSheetService.balanceSheet(asOfDate));
    }

    @GetMapping("/account-summary")
    public ResponseEntity<AccountSummaryResponse> accountSummary(
            @RequestParam(required = false) AccountType accountType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(accountSummaryService.accountSummary(accountType, fromDate, toDate));
    }

    @GetMapping("/expense-summary")
    public ResponseEntity<ExpenseIncomeSummaryResponse> expenseSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(accountSummaryService.expenseSummary(fromDate, toDate));
    }

    @GetMapping("/income-summary")
    public ResponseEntity<ExpenseIncomeSummaryResponse> incomeSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(accountSummaryService.incomeSummary(fromDate, toDate));
    }

    @GetMapping("/health-check")
    public ResponseEntity<AccountingHealthCheckResponse> healthCheck() {
        return ResponseEntity.ok(accountingHealthCheckService.runHealthCheck());
    }
}
