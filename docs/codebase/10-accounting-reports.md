# Accounting Reports Module

## 1. Overview

The Accounting Reports module (spec sections 9–23 and 32, per the class-level Javadoc comments in the source) is a **pure read-only reporting/aggregation layer**. It owns **no database tables and no entities of its own** — every figure it returns is computed live, on each request, from data owned by other modules: the posted accounting journal (`JournalHeader`/`JournalDetail`, owned by the Accounting Core module), `Sale`/`Purchase` (Sales/Purchase modules), and the `Account` chart of accounts. Nothing in this module ever writes to those tables; every service method is `@Transactional(readOnly = true)`.

The controller's own class Javadoc states the design principle explicitly: "Every endpoint reads the posted accounting journal (or, for Outstanding/Ageing, the `Sale.dueAmount`/`Purchase.payableAmount` fields `ReceiptService`/`PaymentService` keep in exact sync with `ReceiptAllocation`/`PaymentAllocation`) — never a second, independently calculated source of truth." In other words, this module never re-derives figures Sale/Purchase/Accounting Core already maintain; it only reshapes and aggregates them for display.

There are 13 reports in total: Day Book, Account Ledger, Trial Balance, Cash Book, Bank Book, Party Ledger, Receivable, Payable, Outstanding (Customers/Suppliers, with ageing), Profit & Loss, Balance Sheet, Account Summary, Expense Summary, Income Summary, and Accounting Health Check — all served from one controller (`AccountingReportController`) backed by 9 focused services. (An Accounting Dashboard summary and Expense/Income Summary are also served from this controller/these services but are not part of the 12 named frontend report pages covered here in detail — see the note at the end of Section 2.)

## 2. Frontend

### Files and Components

| File | Purpose |
|---|---|
| `frontend/src/pages/accounting/reports/DayBook.tsx` | Every POSTED voucher for a date range/voucher-type filter; clicking a row opens a dialog with the full journal (via `journalApi.getById`). |
| `frontend/src/pages/accounting/reports/AccountLedger.tsx` | Running-balance statement for one selected `Account`, date-ranged. |
| `frontend/src/pages/accounting/TrialBalance.tsx` | Account-wise debit/credit balances as of a date, with a Balanced/Out-of-Balance badge. (Lives under `pages/accounting/`, not `pages/accounting/reports/`, but is the same report served by `AccountingReportService.trialBalance`.) |
| `frontend/src/pages/accounting/reports/CashBook.tsx` | Receipts/payments through the system `CASH` account, date-ranged. |
| `frontend/src/pages/accounting/reports/BankBook.tsx` | Receipts/payments through the system `BANK` account (or another bank `Account` once one exists), date-ranged. |
| `frontend/src/pages/accounting/reports/PartyLedger.tsx` | Running-balance statement for one Customer or Supplier; reads/writes `partyType`/`partyId` to the URL query string via `useSearchParams`. |
| `frontend/src/pages/accounting/reports/Receivable.tsx` | Per-customer opening/credit-sales/receipts/notes/closing table; each row links to that customer's Party Ledger. |
| `frontend/src/pages/accounting/reports/Payable.tsx` | Per-supplier opening/purchases/payments/notes/closing table; each row links to that supplier's Party Ledger. |
| `frontend/src/pages/accounting/reports/Outstanding.tsx` | Tabbed Customers/Suppliers bill-wise outstanding with ageing-bucket summary cards; loads both reports in parallel via `Promise.all`. |
| `frontend/src/pages/accounting/reports/ProfitLoss.tsx` | Income vs. Expense account lines for a date range, with a Net Profit/Loss card. |
| `frontend/src/pages/accounting/reports/BalanceSheet.tsx` | Assets vs. Liabilities+Equity as of a date, with a Balanced/Out-of-Balance badge. |
| `frontend/src/pages/accounting/reports/AccountSummary.tsx` | Opening/debit/credit/closing per account, filterable by `AccountType`. |
| `frontend/src/pages/accounting/reports/HealthCheck.tsx` | Overall PASS/WARNING/ERROR status plus one card per check, each with a "Re-run" button. |
| `frontend/src/pages/accounting/reports/reportFormat.ts` | Shared helpers used by 11 of the 12 pages above: `money()` (₹, `en-IN`, 2 decimals), `todayIso()`, `firstDayOfMonthIso()`, `firstDayOfFinancialYearIso()` (defined but not currently called by any of these 12 pages). |

**Note on `TrialBalance.tsx`**: it does **not** import from `reportFormat.ts` — it defines its own local `money`/`todayIso` functions (lines 16–17) that are functionally equivalent (same `en-IN` 2-decimal formatting) but duplicated rather than shared. This is the one inconsistency across the 12 pages.

**Out of scope siblings** (present in `pages/accounting/reports/` but not part of this module's 12 named reports per the task's ground-truth list): `ExpenseIncomeSummaryView.tsx`, `ExpenseSummary.tsx`, `IncomeSummary.tsx` (wrap the `expense-summary`/`income-summary` endpoints also served by `AccountSummaryService`), `ExpenseAnalysis.tsx`, and `StoreComparison.tsx` (a Multi-Store report, documented in the Store module doc).

### Frontend Flow

All 12 pages follow the same generic shape:
1. **Filter state** — `fromDate`/`toDate` (defaulted via `firstDayOfMonthIso()`/`todayIso()`) for period reports, or a single `asOfDate` (defaulted via `todayIso()`) for point-in-time reports (Trial Balance, Balance Sheet, Outstanding). Day Book adds an optional `voucherType` `<Select>`; Account Ledger and Party Ledger add an account/party `<Select>` (Party Ledger also supports a `partyType` toggle between Customer/Supplier); Account Summary adds an optional `accountType` `<Select>`. Health Check has no filters at all — it is a re-runnable check, not a range query.
2. **Load** — an `async load()` function calls the matching `accountingReportApi.*` function inside a `try/catch`, showing a `toast.error(parseApiError(err, '...').message)` on failure and toggling a `loading` boolean in a `finally` block. Every page calls `load()` once on mount via `useEffect(() => { load(); }, [])`; Account Ledger and Party Ledger additionally reload whenever the selected account/party id changes. There is also an explicit "Apply" button (or, for Trial Balance, `onChange` fires the load directly) so users can re-run without changing the mount-time defaults.
3. **Render** — a `PageHeader` with the filter controls in its `actions` slot, a row of summary `Card`s (opening/closing balances, totals, or ageing-bucket cards), then a `Table` of rows (or, for Profit & Loss/Balance Sheet, two side-by-side tables) with a `TableSkeleton` while loading and an `EmptyState` when the row list is empty. Day Book additionally opens a `Dialog` with full journal-line detail on row click.

**Store-filter usage (Multi-Store)**: NOT FOUND IN CURRENT CODEBASE. None of the 12 report pages, `accountingReportApi` functions, `AccountingReportController` endpoints, or the underlying repository queries (`JournalDetailRepository`, `JournalHeaderRepository`, `SaleRepository.findAllOutstanding`, `PurchaseRepository.findAllOutstanding`) take or filter by a `storeId`/`store` parameter, even though `Sale`, `Purchase`, and `JournalHeader` entities do carry a `store` field elsewhere in the codebase. Every report in this module aggregates across **all stores** — the only store-scoped accounting/sales report is the separate `StoreComparison.tsx` page (Store module, out of scope here).

## 3. API Calls

All defined in `frontend/src/api/accountingApi.ts` under `accountingReportApi`, wrapping the shared `api` (axios) client.

| Function | HTTP Method | URL | Request shape (query params) | Response shape |
|---|---|---|---|---|
| `accountingReportApi.dashboard` | GET | `/accounting/reports/dashboard` | — | `AccountingDashboardSummary` |
| `accountingReportApi.dayBook` | GET | `/accounting/reports/day-book` | `fromDate?, toDate?, voucherType?` | `DayBookResponse` |
| `accountingReportApi.accountLedger` | GET | `/accounting/reports/account-ledger/{accountId}` | `fromDate?, toDate?` | `AccountLedgerResponse` |
| `accountingReportApi.trialBalance` | GET | `/accounting/reports/trial-balance` | `asOfDate?` | `TrialBalanceResponse` |
| `accountingReportApi.cashBook` | GET | `/accounting/reports/cash-book` | `fromDate?, toDate?` | `CashBankBookResponse` |
| `accountingReportApi.bankBook` | GET | `/accounting/reports/bank-book` | `fromDate?, toDate?, accountId?` | `CashBankBookResponse` |
| `accountingReportApi.partyLedger` | GET | `/accounting/reports/party-ledger` | `partyType, partyId, fromDate?, toDate?` | `PartyLedgerResponse` |
| `accountingReportApi.receivable` | GET | `/accounting/reports/receivable` | `fromDate?, toDate?` | `ReceivablePayableResponse` |
| `accountingReportApi.payable` | GET | `/accounting/reports/payable` | `fromDate?, toDate?` | `ReceivablePayableResponse` |
| `accountingReportApi.outstandingCustomers` | GET | `/accounting/reports/outstanding/customers` | `asOfDate?` | `OutstandingBillReportResponse` |
| `accountingReportApi.outstandingSuppliers` | GET | `/accounting/reports/outstanding/suppliers` | `asOfDate?` | `OutstandingBillReportResponse` |
| `accountingReportApi.profitLoss` | GET | `/accounting/reports/profit-loss` | `fromDate?, toDate?` | `ProfitLossResponse` |
| `accountingReportApi.balanceSheet` | GET | `/accounting/reports/balance-sheet` | `asOfDate?` | `BalanceSheetResponse` |
| `accountingReportApi.accountSummary` | GET | `/accounting/reports/account-summary` | `accountType?, fromDate?, toDate?` | `AccountSummaryResponse` |
| `accountingReportApi.expenseSummary` | GET | `/accounting/reports/expense-summary` | `fromDate?, toDate?` | `ExpenseIncomeSummaryResponse` |
| `accountingReportApi.incomeSummary` | GET | `/accounting/reports/income-summary` | `fromDate?, toDate?` | `ExpenseIncomeSummaryResponse` |
| `accountingReportApi.healthCheck` | GET | `/accounting/reports/health-check` | — | `AccountingHealthCheckResponse` |

All dates are ISO `yyyy-MM-dd` strings (Java-side bound with `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)`). The base path prefix `/api` is added by the shared axios client (`frontend/src/api/axios.ts`) — the controller's `@RequestMapping` is `/api/accounting/reports`.

## 4. Backend

### Controllers

Single controller: `AccountingReportController` (`@RequestMapping("/api/accounting/reports")`), 17 endpoints, all `@GetMapping`, delegating to 8 injected services (`AccountingReportService` + 7 focused services). No endpoint has a request body; every parameter is a `@RequestParam` (or `@PathVariable Long accountId` for `account-ledger`), all optional except `party-ledger`'s `partyType`/`partyId`.

| Endpoint | Delegates to |
|---|---|
| `GET /dashboard` | `AccountingReportService.getDashboardSummary()` |
| `GET /day-book` | `AccountingReportService.dayBook(voucherType, fromDate, toDate)` |
| `GET /account-ledger/{accountId}` | `AccountingReportService.accountLedger(accountId, fromDate, toDate)` |
| `GET /trial-balance` | `AccountingReportService.trialBalance(asOfDate)` |
| `GET /cash-book` | `CashBankBookService.cashBook(fromDate, toDate)` |
| `GET /bank-book` | `CashBankBookService.bankBook(accountId, fromDate, toDate)` |
| `GET /party-ledger` | `PartyLedgerService.partyLedger(partyType, partyId, fromDate, toDate)` |
| `GET /receivable` | `ReceivablePayableService.receivable(fromDate, toDate)` |
| `GET /payable` | `ReceivablePayableService.payable(fromDate, toDate)` |
| `GET /outstanding/customers` | `OutstandingBillService.customerOutstanding(asOfDate)` |
| `GET /outstanding/suppliers` | `OutstandingBillService.supplierOutstanding(asOfDate)` |
| `GET /profit-loss` | `ProfitLossService.profitAndLoss(fromDate, toDate)` |
| `GET /balance-sheet` | `BalanceSheetService.balanceSheet(asOfDate)` |
| `GET /account-summary` | `AccountSummaryService.accountSummary(accountType, fromDate, toDate)` |
| `GET /expense-summary` | `AccountSummaryService.expenseSummary(fromDate, toDate)` |
| `GET /income-summary` | `AccountSummaryService.incomeSummary(fromDate, toDate)` |
| `GET /health-check` | `AccountingHealthCheckService.runHealthCheck()` |

### DTOs

All under `backend/src/main/java/com/storehub/dto/`, plain Lombok `@Getter @Builder @AllArgsConstructor` classes (no `@Setter`, no persistence annotations — confirming they are pure response shapes, not entities).

| Response DTO | Fields | Row DTO | Row fields |
|---|---|---|---|
| `DayBookResponse` | `rows: List<DayBookRow>`, `totalDebit`, `totalCredit` | `DayBookRow` | `journalDate, journalId, journalNumber, voucherType, voucherNumber, narration, debit, credit, status` |
| `AccountLedgerResponse` | `accountId, accountCode, accountName, openingBalance, openingBalanceType, rows: List<AccountLedgerRow>, closingBalance, closingBalanceType` | `AccountLedgerRow` | `journalDate, voucherType, voucherNumber, particulars, debit, credit, balance, balanceType` |
| `TrialBalanceResponse` | `asOfDate, rows: List<TrialBalanceRow>, totalDebit, totalCredit, difference, balanced` | `TrialBalanceRow` | `accountId, accountCode, accountName, accountType, debit, credit` |
| `CashBankBookResponse` | `accountId, accountCode, accountName, fromDate, toDate, openingBalance, rows: List<CashBankBookRow>, totalReceipts, totalPayments, closingBalance` | `CashBankBookRow` | `voucherDate, voucherType, voucherNumber, particulars, receipt, payment, runningBalance` |
| `PartyLedgerResponse` | `partyType, partyId, partyName, fromDate, toDate, openingBalance, openingBalanceType, rows: List<PartyLedgerRow>, closingBalance, closingBalanceType` | `PartyLedgerRow` | `voucherDate, voucherType, voucherNumber, particulars, debit, credit, balance, balanceType` |
| `ReceivablePayableResponse` | `partyType, fromDate, toDate, rows: List<ReceivablePayableRow>, totalOpening, totalTransactions, totalPayments, totalOutstanding` | `ReceivablePayableRow` | `partyId, partyName, openingBalance, transactionAmount, paymentAmount, creditNoteAmount, debitNoteAmount, closingOutstanding` |
| `OutstandingBillReportResponse` | `partyType, asOfDate, ageingBasis, rows: List<OutstandingBillDetailRow>, ageingSummary: List<AgeingBucketSummary>, totalInvoiceAmount, totalReceivedOrPaid, totalOutstanding` | `OutstandingBillDetailRow` | `partyId, partyName, billId, invoiceNumber, invoiceDate, invoiceAmount, receivedOrPaidAmount, outstanding, daysOutstanding, ageingBucket`; `AgeingBucketSummary`: `bucket, count, amount` |
| `ProfitLossResponse` | `fromDate, toDate, incomeLines: List<PnlAccountLine>, expenseLines: List<PnlAccountLine>, totalIncome, totalExpense, netProfitOrLoss` | `PnlAccountLine` | `accountId, accountCode, accountName, amount` |
| `BalanceSheetResponse` | `asOfDate, assetLines: List<BalanceSheetLine>, liabilityLines: List<BalanceSheetLine>, equityLines: List<BalanceSheetLine>, totalAssets, totalLiabilities, totalEquity, currentYearProfit, currentFinancialYear, difference, balanced` | `BalanceSheetLine` | `accountId (nullable), accountCode (nullable), accountName, amount` |
| `AccountSummaryResponse` | `fromDate, toDate, rows: List<AccountSummaryRow>, totalOpening, totalDebit, totalCredit, totalClosing` | `AccountSummaryRow` | `accountId, accountCode, accountName, accountType, openingBalance, debit, credit, closingBalance` |
| `ExpenseIncomeSummaryResponse` | `fromDate, toDate, accounts: List<ExpenseIncomeAccountGroup>, totalAmount` | `ExpenseIncomeAccountGroup` → `ExpenseIncomeVoucherRow` | Group: `accountId, accountCode, accountName, total, vouchers`. Voucher row: `voucherDate, journalId, journalNumber, voucherType, voucherNumber, narration, amount` |
| `AccountingHealthCheckResponse` | `generatedAt, overallStatus: HealthCheckStatus, findings: List<HealthCheckFinding>` | `HealthCheckFinding` | `checkName, status: HealthCheckStatus, message, details: List<String>` |
| `AccountingDashboardResponse` | `cashBalance, bankBalance, receivableBalance, payableBalance` | — | — |

`HealthCheckStatus` (`backend/src/main/java/com/storehub/entity/HealthCheckStatus.java`) is a 3-value enum: `PASS, WARNING, ERROR`.

### Services

All 9 services are `@Service @RequiredArgsConstructor`, and every public method is `@Transactional(readOnly = true)`.

#### 1. `AccountingReportService` — Day Book, Account Ledger, Trial Balance, Dashboard

- **Day Book** (`dayBook(voucherType, fromDate, toDate)`): calls `JournalDetailRepository.dayBookRows(voucherType, fromDate, toDate)`, which returns one row per `JournalDetail` line (columns: journal date, id, number, voucher type, voucher number, narration, debit, credit, status). Sums `totalDebit`/`totalCredit` by simple accumulation across all returned rows.
- **Account Ledger** (`accountLedger(accountId, fromDate, toDate)`): loads the `Account` (`AccountService.findOrThrow`), computes a *signed* opening balance — `+openingBalance` if `openingBalanceType == DEBIT`, else `-openingBalance` — then, if `fromDate` is given, adds all activity strictly before it via `JournalDetailRepository.sumDebitCreditBefore(accountId, fromDate)` (`openingSigned += priorDebit - priorCredit`). It then iterates `JournalDetailRepository.findLedgerLines(accountId, fromDate, toDate)` in order, maintaining a running signed balance (`running = running + debitAmount - creditAmount` per line) and emitting each row's `balance` as `running.abs()` with `balanceType` = `DEBIT` if `running.signum() >= 0` else `CREDIT`.
- **Trial Balance** (`trialBalance(asOfDate)`): for every active `Account` (`AccountRepository.findByActiveTrueOrderByAccountNameAsc()`, then re-sorted by `accountCode`), computes the same "opening balance + all activity up to `asOfDate`" signed net (activity from `JournalDetailRepository.sumDebitCreditByAccount(asOfDate)`, a map keyed by account id). **Formula**: `netSigned = openingSigned + activityDebit - activityCredit`; then `debit = netSigned > 0 ? netSigned : 0` and `credit = netSigned < 0 ? -netSigned : 0` (i.e. every account's net balance is placed on exactly one side). `totalDebit`/`totalCredit` are the column sums; `difference = totalDebit - totalCredit`; `balanced = (totalDebit == totalCredit)` (by `BigDecimal.compareTo`, not `equals`, so scale differences don't cause a false "unbalanced").
- **Dashboard** (`getDashboardSummary()`): returns `currentBalance()` (opening + all-time journal activity, same signed formula as above with no date cutoff) for the `CASH`, `BANK`, and `CUSTOMER_RECEIVABLE` system accounts, plus `SUPPLIER_PAYABLE`'s balance negated (so it reads as a positive "amount owed" figure).

#### 2. `CashBankBookService` — Cash Book, Bank Book

Both reuse one private method, `bookForAccount(accountId, fromDate, toDate)`, which is **exactly the same opening-balance-plus-running-balance algorithm as `AccountingReportService.accountLedger`**, reshaped into receipt/payment columns instead of debit/credit: a debit into the account is a `receipt`, a credit out of it is a `payment` (the class Javadoc states this explicitly). `cashBook()` resolves the account via `AccountService.getSystemAccount(SystemAccountCode.CASH)`; `bankBook(accountId, ...)` uses the given `accountId` if supplied, else `SystemAccountCode.BANK`. `totalReceipts`/`totalPayments` are the column sums (not net); `closingBalance` is the final signed running balance (not `.abs()`, unlike Account Ledger — Cash/Bank running balance is always debit-side so no sign-flip display is needed).

#### 3. `PartyLedgerService` — Party Ledger

A "second VIEW over the existing journal" (class Javadoc) — reads `JournalDetail` lines tagged with a `partyType`/`partyId` (the `CUSTOMER_RECEIVABLE`/`SUPPLIER_PAYABLE` control-account lines), via `JournalDetailRepository.sumByPartyBefore` (opening) and `findLedgerLinesByParty` (period rows). Same running-balance algorithm as Account Ledger, but starting opening balance is **unsigned zero** unless `fromDate` is given (no account-level `openingBalance` field exists for a party, only journal activity). Explicitly documented as *not* replacing the operational `CustomerLedgerEntry`/`SupplierLedgerEntry` sub-ledger tables from the Sales/Purchase modules — those "remain untouched and keep reconciling against this" (checked by the Health Check's "Party Ledger Match").

#### 4. `ReceivablePayableService` — Receivable, Payable

Both call one shared `build(partyType, transactionRef, paymentRef, noteRef, creditIncreases, fromDate, toDate)` with polarity flipped: Receivable is `build(CUSTOMER, SALE, RECEIPT, CREDIT_NOTE, creditIncreases=false, ...)`, Payable is `build(SUPPLIER, PURCHASE, PAYMENT, DEBIT_NOTE, creditIncreases=true, ...)`.

**Formula** per party: `closingOutstanding = openingBalance + transactionAmount − paymentAmount − noteAmount`, where:
- `openingBalance` = every posted/reversed control-account line strictly before `fromDate` (via `sumByPartyBefore`), net-signed by polarity (`creditIncreases ? credit-debit : debit-credit`).
- `transactionAmount` (credit sales / purchases) and `paymentAmount` (receipts / payments) and `noteAmount` (credit/debit notes) are each read from `JournalDetailRepository.sumByPartyAndReferenceTypeRange`, one bucket per `VoucherType`, and **both sides of each bucket are netted, not just the increasing side** — a reversed Sale/Purchase posts its offsetting entry as the opposite side of the *same* `referenceType` bucket, so reading only the increasing side would double-count a cancelled voucher forever (explicit in the source comment).
- A Credit Note (Receivable) or Debit Note (Payable) always *reduces* outstanding — same polarity as a payment — so it nets the same way `paymentAmount` does; `creditNoteAmount`/`debitNoteAmount` in the row are just the note amount routed into whichever of the two output fields matches the report (the other stays zero, since a Sales Credit Note only ever affects Receivable and a Purchase Debit Note only ever affects Payable).

Rows are sorted by `partyName` (case-insensitive); totals are simple column sums across all parties with any opening balance or activity.

#### 5. `OutstandingBillService` — Outstanding (Customers/Suppliers) with Ageing

Built directly from `Sale.dueAmount`/`Purchase.payableAmount` — fields `ReceiptService`/`PaymentService` keep in exact sync with `ReceiptAllocation`/`PaymentAllocation` — rather than re-deriving outstanding from the journal. `customerOutstanding(asOfDate)` reads `SaleRepository.findAllOutstanding()` (every `Sale` with a non-null customer, `dueAmount > 0`, `status = COMPLETED`); `supplierOutstanding(asOfDate)` reads `PurchaseRepository.findAllOutstanding()` (every `Purchase` with `payableAmount > 0`, `status = COMPLETED`).

**Ageing formula**: `daysOutstanding = ChronoUnit.DAYS.between(invoiceDate, asOfDate)` (defaulting `asOfDate` to today if not given); **ageing basis is always `INVOICE_DATE`**, stated explicitly in the `OutstandingBillReportResponse` Javadoc because no due-date/credit-period/credit-terms field exists anywhere on Sale/Purchase/Receipt/Payment/allocation entities in this codebase (confirmed absent by inspection). **Bucket boundaries** (`bucketFor`): `0–30 Days` (≤30), `31–60 Days` (≤60), `61–90 Days` (≤90), `91–180 Days` (≤180), `180+ Days` (>180). `ageingSummary` is a per-bucket count+amount rollup (`AgeingBucketSummary`), built with all 5 buckets pre-seeded at zero so empty buckets still appear. `totalInvoiceAmount`/`totalReceivedOrPaid`/`totalOutstanding` are simple sums over all rows.

#### 6. `ProfitLossService` — Profit & Loss

Built **only** from accounts classified `AccountType.INCOME`/`AccountType.EXPENSE` in the Chart of Accounts — never from Sale/Purchase tables directly, and never special-cased by account name. For every active account with any activity in `[fromDate, toDate]` (via `JournalDetailRepository.sumDebitCreditByAccountRange`): an `INCOME` account's line amount is `credit − debit`; an `EXPENSE` account's line amount is `debit − credit`. `netProfitOrLoss = totalIncome − totalExpense`. A `netProfit(fromDate, toDate)` convenience method (just the net figure) is used by Balance Sheet.

**Documented simplification** (class Javadoc): `SystemAccountCode.PURCHASE` is itself classified `EXPENSE` and is debited for the taxable amount on every posted Purchase, so every purchase is expensed immediately in the period posted — there is a Chart-of-Accounts `INVENTORY` account, but nothing in the codebase ever posts to it (confirmed by inspection), so there is **no perpetual-inventory/COGS-matching treatment**. This is called out as "a genuine simplification of the existing... accounting design, not something invented here — P&L simply reports what the ledger actually contains."

#### 7. `BalanceSheetService` — Balance Sheet

Reuses the *exact same* per-account "opening balance + all activity up to a date" math as `AccountingReportService.trialBalance` (via `sumDebitCreditByAccount`), so Assets/Liabilities here are guaranteed consistent with the Trial Balance, but bucketed by `AccountType` instead of listed flat:
- `ASSET`: net-signed balance (`netSigned`) added directly to `assetLines`/`totalAssets` when non-zero.
- `LIABILITY`: `amount = netSigned.negate()` (liabilities are naturally credit-balance) added to `liabilityLines`/`totalLiabilities`.
- `INCOME`/`EXPENSE`: **not** shown as lines — instead accumulated into `cumulativeIncome`/`cumulativeExpense` (`INCOME: cumulativeIncome += netSigned.negate()`; `EXPENSE: cumulativeExpense += netSigned`) across **all-time**, not just the period.

**Equity**: there is no `EQUITY` `AccountType` in this app (confirmed — Chart of Accounts only classifies `ASSET/LIABILITY/INCOME/EXPENSE`). `equityLines` is therefore always exactly one synthetic line, `"Retained Earnings (Accumulated Profit/Loss)"`, computed as `retainedEarnings = cumulativeIncome − cumulativeExpense` — a report-level figure, never a real `Account` row and never a posted journal entry. `totalEquity = retainedEarnings`.

**Balance check formula**: `difference = totalAssets − (totalLiabilities + totalEquity)`; `balanced = (difference == 0)` (`BigDecimal.compareTo`). This is the literal "Assets = Liabilities + Equity" identity.

`currentYearProfit` is a separate, informational-only figure: `ProfitLossService.netProfit(FinancialYearUtil.startOf(asOfDate), asOfDate)` — the financial-year-to-date slice of the same P&L, distinct from the all-time `totalEquity` figure the balance check uses. `currentFinancialYear` is a display label from `FinancialYearUtil.label(asOfDate)`.

#### 8. `AccountSummaryService` — Account Summary, Expense Summary, Income Summary

- **Account Summary** (`accountSummary(accountType, fromDate, toDate)`): for every active account (optionally filtered by `accountType`), computes `openingBalance` (opening + activity strictly before `fromDate`, same formula as elsewhere), `debit`/`credit` (activity within `[fromDate, toDate]` via `sumDebitCreditByAccountRange`), and `closingBalance = openingBalance + debit − credit`. Accounts with zero opening, debit, and credit are skipped. Rows sorted by `accountCode`; four running totals (`totalOpening/Debit/Credit/Closing`) are simple sums.
- **Expense Summary / Income Summary** (`expenseSummary`/`incomeSummary`, both delegate to `voucherGroupedSummary(accountType, ...)`): for every active account of the given type with any activity, lists individual vouchers via `JournalDetailRepository.accountVoucherRows(accountId, fromDate, toDate)`. Per-voucher `amount = debitIsAmount ? (debit − credit) : (credit − debit)`, where `debitIsAmount = (accountType == EXPENSE)` — i.e. Expense accounts read their debit side as the "amount", Income accounts read their credit side. Zero-amount vouchers are dropped. Grouped into `ExpenseIncomeAccountGroup` per account (with an `accountTotal`), groups sorted by `accountCode`, and an overall `grandTotal` summed across all groups.

#### 9. `AccountingHealthCheckService` — Accounting Health Check

Runs 7 independent checks in a fixed sequence, each returning a `HealthCheckFinding` with an explicit status — "every check reports PASS/WARNING/ERROR explicitly rather than hiding a mismatch" (class Javadoc). `overallStatus` is the worst of the 7 (`ERROR` beats `WARNING` beats `PASS`).

| # | Check | Status if failing | Logic |
|---|---|---|---|
| 1 | Journal Balance | **ERROR** | `JournalHeaderRepository`... `JournalDetailRepository.findUnbalancedJournals()` — any journal whose own lines don't sum `debit == credit`. Documented as "should be impossible" given posting-time validation. |
| 2 | Duplicate Posting | **ERROR** | `JournalHeaderRepository.findDuplicateActivePostings()` — any `(voucherType, voucherId)` pair with more than one currently-active posted journal. "Should be impossible given the duplicate-post guard." |
| 3 | Source Posting | **WARNING** | For every `COMPLETED`/`POSTED` Sale, Purchase, Receipt, and Payment (via each module's repository `findCompletedIds()`/`findAllIds()`), checks it has a matching active journal via `JournalHeaderRepository.findActivePostedVoucherIds(voucherType)`. Flags any source transaction with no journal. |
| 4 | Orphan Journal | **WARNING** | For SALE/PURCHASE/RECEIPT/PAYMENT/CREDIT_NOTE/DEBIT_NOTE, checks every journal's `voucherId` (via `JournalHeaderRepository.findDistinctVoucherIds(voucherType)`) still points at a row that exists in the owning module's repository. Documented as reachable *legitimately* — `SaleService.deleteSale`/`PurchaseService.deletePurchase` reverse the journal (kept for audit) and then hard-delete the source row, so a hard "delete" (not "cancel") of an old bill always surfaces here; "that is expected, not a bug." |
| 5 | Party Ledger Match | **WARNING** | Compares, per customer/supplier, the accounting journal's control-account net balance (`JournalDetailRepository.sumByParty(partyType)`) against the operational sub-ledger balance (`CustomerLedgerEntryRepository.sumByCustomer()` / `SupplierLedgerEntryRepository.sumBySupplier()`, from the Sales/Purchase modules' own parallel ledger tables). Any mismatch (`compareTo != 0`) is flagged. |
| 6 | Financial Year Coverage | **WARNING** | `JournalHeaderRepository.findPostedDatesWithoutFinancialYear()` — any POSTED journal dated outside every defined `FinancialYear`. "Should not occur going forward" since posting always resolves a FY first; a non-empty result means pre-FY-feature data, or a later-deleted FY. |
| 7 | Note Posting | **WARNING** | Mirrors check 3 for `CreditNoteRepository.findPostedIds()`/`DebitNoteRepository.findPostedIds()` against `JournalHeaderRepository.findActivePostedVoucherIds(CREDIT_NOTE/DEBIT_NOTE)`. |

Each `pass()`/`warning()`/`error()` builder sets `checkName`, `status`, a human-readable `message`, and a `details: List<String>` (empty on PASS, one line per offending record otherwise).

### Repository

This module has no repositories of its own. Every service injects and calls repositories owned by other modules:

| Repository (owning module) | Used by | Key methods called |
|---|---|---|
| `JournalDetailRepository` (Accounting Core) | `AccountingReportService`, `CashBankBookService`, `PartyLedgerService`, `ReceivablePayableService`, `ProfitLossService`, `BalanceSheetService`, `AccountSummaryService`, `AccountingHealthCheckService` | `dayBookRows`, `findLedgerLines`, `findLedgerLinesByParty`, `sumDebitCreditBefore`, `sumDebitCreditByAccount`, `sumDebitCreditByAccountRange`, `sumDebitCreditForAccount`, `sumByPartyBefore`, `sumByPartyAndReferenceTypeRange`, `sumByParty`, `accountVoucherRows`, `findUnbalancedJournals` |
| `JournalHeaderRepository` (Accounting Core) | `AccountingHealthCheckService` | `findDuplicateActivePostings`, `findActivePostedVoucherIds`, `findDistinctVoucherIds`, `findPostedDatesWithoutFinancialYear` |
| `AccountRepository` (Accounting Core) | `AccountingReportService`, `BalanceSheetService`, `AccountSummaryService`, `ProfitLossService` | `findByActiveTrueOrderByAccountNameAsc` |
| `SaleRepository` (Sales module) | `OutstandingBillService`, `AccountingHealthCheckService` | `findAllOutstanding`, `findCompletedIds`, `findAllIds` |
| `PurchaseRepository` (Purchase module) | `OutstandingBillService`, `AccountingHealthCheckService` | `findAllOutstanding`, `findCompletedIds`, `findAllIds` |
| `ReceiptRepository` (Sales/Receipt module) | `AccountingHealthCheckService` | `findAllIds` |
| `PaymentRepository` (Purchase/Payment module) | `AccountingHealthCheckService` | `findAllIds` |
| `CustomerLedgerEntryRepository` (Customer module) | `AccountingHealthCheckService` | `sumByCustomer` |
| `SupplierLedgerEntryRepository` (Supplier module) | `AccountingHealthCheckService` | `sumBySupplier` |
| `CreditNoteRepository` (Sales module) | `AccountingHealthCheckService` | `findAllIds`, `findPostedIds` |
| `DebitNoteRepository` (Purchase module) | `AccountingHealthCheckService` | `findAllIds`, `findPostedIds` |
| `CustomerRepository` (Customer module) | `PartyLedgerService`, `ReceivablePayableService` | `findById`, `findAllById` |
| `SupplierRepository` (Supplier module) | `PartyLedgerService`, `ReceivablePayableService` | `findById`, `findAllById` |

`AccountService` and `AccountingReportService` itself (self-injected into `AccountingReportController`) are the only same-package service dependencies; `AccountService.findOrThrow`/`getSystemAccount` are used to resolve `Account` rows and system accounts (`CASH`, `BANK`, `CUSTOMER_RECEIVABLE`, `SUPPLIER_PAYABLE`).

## 5. Database

### Tables

**This module owns no database tables.** Every report reads from tables owned by other modules:

| Table (via entity) | Read by |
|---|---|
| `journal_header` (`JournalHeader`) | Day Book, Account Ledger, Trial Balance, Cash Book, Bank Book, Party Ledger, Receivable, Payable, Profit & Loss, Balance Sheet, Account Summary, Expense Summary, Income Summary, Health Check |
| `journal_detail` (`JournalDetail`) | Same set as above (every report except Outstanding reads `JournalDetail` rows/aggregates) |
| `account` (`Account`) | Account Ledger, Trial Balance, Cash Book, Bank Book, Profit & Loss, Balance Sheet, Account Summary, Expense Summary, Income Summary |
| `sale` (`Sale`) | Outstanding (customers), Health Check (Source/Orphan Posting) |
| `purchase` (`Purchase`) | Outstanding (suppliers), Health Check (Source/Orphan Posting) |
| `receipt` (`Receipt`) | Health Check (Source/Orphan Posting) |
| `payment` (`Payment`) | Health Check (Source/Orphan Posting) |
| `credit_note` (`CreditNote`) | Health Check (Note/Orphan Posting) |
| `debit_note` (`DebitNote`) | Health Check (Note/Orphan Posting) |
| `customer_ledger_entry` (`CustomerLedgerEntry`) | Health Check (Party Ledger Match) |
| `supplier_ledger_entry` (`SupplierLedgerEntry`) | Health Check (Party Ledger Match) |
| `customer` (`Customer`) | Party Ledger, Receivable, Payable (name resolution) |
| `supplier` (`Supplier`) | Party Ledger, Payable, Receivable (name resolution) |
| `financial_year` (`FinancialYear`, via `FinancialYearUtil`/`findPostedDatesWithoutFinancialYear`) | Balance Sheet (current FY label/window), Health Check (FY coverage) |

### Relationships

N/A — this module defines no foreign keys or JPA relationships of its own. Its only "relationship" is a **read dependency graph**: every report service reads live from `JournalHeader`/`JournalDetail` (which itself carries FKs to `Account`, `Customer`/`Supplier` via `partyType`/`partyId`, and back to its source voucher via `voucherType`/`voucherId`), plus, for Outstanding, directly from `Sale`/`Purchase`. None of these reads are cached or materialized — every request re-aggregates from the current table state, so this module has zero data of its own to go stale.

## 6. Validation

There are no `@Valid`/DTO-annotated request bodies in this module (every endpoint is a `GET` with `@RequestParam`/`@PathVariable`), so there is no `MethodArgumentNotValidException`/`fieldErrors` path for these endpoints specifically (that mechanism, per `GlobalExceptionHandler`, only fires for `@Valid` request-body DTOs elsewhere in the codebase). Date parameters are bound with `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)`; a malformed date string fails Spring's own parameter-conversion and surfaces as a generic `400`/`500` via `GlobalExceptionHandler`'s catch-all `Exception.class` handler (not a field-level error). All date-range/`accountId`/`partyId` parameters are optional or defaulted server-side (e.g. `asOfDate` defaults to `LocalDate.now()` in `BalanceSheetService`/`OutstandingBillService`) — **there is no explicit "fromDate must be before toDate" or similar cross-field validation anywhere in this module** (NOT FOUND IN CURRENT CODEBASE). `party-ledger`'s required `partyType`/`partyId` missing a matching Customer/Supplier row throws `BadRequestException` (`PartyLedgerService.resolvePartyName`), mapped by `GlobalExceptionHandler.handleBadRequest` to a plain `400` with the exception message (no `fieldErrors` map).

## 7. Authentication / Authorization

Every endpoint requires an authenticated request: `SecurityConfig.filterChain` permits only `/api/auth/**` and `/actuator/health`, and applies `.anyRequest().authenticated()` to everything else, including `/api/accounting/reports/**`. Requests are authenticated via `JwtAuthenticationFilter` (JWT bearer token → `CustomUserDetailsService`), the same mechanism used app-wide.

**No endpoint-level authorization**: unlike most other controllers in the codebase (e.g. `ExpenseController`, which annotates every method with `@PreAuthorize("hasAuthority('PERM_EXPENSE_VIEW')")` etc.), `AccountingReportController` has **zero `@PreAuthorize` annotations** — confirmed by reading the full controller source. Authorization for these 17 endpoints is therefore reduced to "any authenticated user of any role" at the backend. The only gate that exists is on the **frontend**: every one of these routes in `App.tsx` is nested inside `<Route element={<ManagerRoute extraRoles={['ACCOUNTANT']} />}>`, and `ManagerRoute` (`frontend/src/components/ManagerRoute.tsx`) redirects to `/unauthorized` unless `user.role` is one of `ADMIN`, `STORE_MANAGER`, or `ACCOUNTANT`. This is a client-side-only check; a `SALES_USER`/`PURCHASE_USER`/`INVENTORY_USER`/`STAFF` with a valid JWT could call any of these 17 endpoints directly (e.g. via curl) and receive a `200` with full accounting data.

## 8. Permissions

`Permission.REPORT_VIEW` and `Permission.REPORT_EXPORT` (both grouped under category `"Account"` in `entity/Permission.java`) exist and are assigned via `RolePermissions.java`:
- **ADMIN**: holds every `Permission` (`EnumSet.allOf(Permission.class)`), including `REPORT_VIEW`/`REPORT_EXPORT`.
- **STORE_MANAGER**: holds every permission ADMIN does *except* the `USER_*`/`ROLE_*`/`PERMISSION_VIEW` security-admin tier and `FY_MANAGE` — so it retains `REPORT_VIEW`/`REPORT_EXPORT`.
- **ACCOUNTANT**: explicitly granted `Permission.REPORT_VIEW, Permission.REPORT_EXPORT` (line 73) alongside its other accounting permissions.
- SALES_USER / PURCHASE_USER / INVENTORY_USER / STAFF: not granted `REPORT_VIEW`/`REPORT_EXPORT` (not present in their `EnumSet.of(...)` lists).

However, as noted in Section 7, **these permissions are never actually checked against `AccountingReportController`** — no `@PreAuthorize("hasAuthority('PERM_REPORT_VIEW')")` (or similar) exists on any of its 17 methods, so `REPORT_VIEW`/`REPORT_EXPORT` are defined in the permission model but not wired to backend enforcement for this module. There is no dedicated "export" action (CSV/PDF/etc.) implemented anywhere in this module's controller or services — `REPORT_EXPORT` has no corresponding endpoint (NOT FOUND IN CURRENT CODEBASE).

## 9. Transaction Handling

Every public method across all 9 services is annotated `@Transactional(readOnly = true)`. This is purely a performance/consistency optimization for the read (single-connection snapshot across multiple repository calls within one report build, e.g. Trial Balance's per-account loop) — there is no write transaction anywhere in this module, consistent with it owning no tables.

## 10. Error Handling

Handled entirely by the shared `GlobalExceptionHandler` (`@RestControllerAdvice`), no module-specific exception handlers:
- `BadRequestException` (thrown by `PartyLedgerService.resolvePartyName` for an unknown customer/supplier id, or an unsupported `partyType`) → `400 Bad Request`, `ApiError.message` set to the exception message, no `fieldErrors`.
- Any other unhandled exception (e.g. a malformed date query param failing Spring's binder) → falls through to the catch-all `@ExceptionHandler(Exception.class)` → `500 Internal Server Error`, generic message `"An unexpected error occurred"`.
- On the frontend, every one of the 12 pages wraps its `load()` call in `try { ... } catch (err) { toast.error(parseApiError(err, '<Report Name>-specific fallback message').message); }` — a toast notification, not an inline per-field error (there are no form fields to attach errors to; these are all read-only GET reports).

## 11. Audit Flow

NOT FOUND IN CURRENT CODEBASE. None of the 9 services or the controller write to any audit-log table, call an audit-logging service, or reference `AuditLogController`/an audit entity. This is expected for a pure read-only reporting module — there is nothing here to audit (no create/update/delete actions), and the module's only self-check mechanism is the Accounting Health Check (Section 4.9), which is a data-integrity check, not an audit trail of user actions.

## 12. Important Side Effects

**None** — every service method is `@Transactional(readOnly = true)` and every controller method is a `@GetMapping`; no service in this module calls `.save()`/`.delete()`/`.update()` on any repository, confirmed by inspection of all 9 service files. The only "write-adjacent" behavior is `PartyLedgerService`'s frontend companion writing `partyType`/`partyId` into the browser's URL query string (`useSearchParams`) for deep-linking, which is a client-side navigation convenience, not a data side effect.

**CSV-injection / export protections**: NOT FOUND IN CURRENT CODEBASE. There is no CSV, Excel, or PDF export endpoint implemented anywhere in this module (see Section 8 — `REPORT_EXPORT` has no wired action), so there is no export-formatting code to review for CSV-injection sanitization.

## 13. Dependencies on Other Modules

| Report | Modules/data it depends on |
|---|---|
| Day Book | Accounting Core (`JournalHeader`/`JournalDetail`) |
| Account Ledger | Accounting Core (`JournalHeader`/`JournalDetail`, `Account`) |
| Trial Balance | Accounting Core (`JournalHeader`/`JournalDetail`, `Account`) |
| Cash Book | Accounting Core (`JournalHeader`/`JournalDetail`, `Account` — system `CASH` account) |
| Bank Book | Accounting Core (`JournalHeader`/`JournalDetail`, `Account` — system `BANK` account or any manually added bank `Account`) |
| Party Ledger | Accounting Core (`JournalHeader`/`JournalDetail`), Customer module, Supplier module |
| Receivable | Accounting Core (journal control-account activity: SALE/RECEIPT/CREDIT_NOTE reference types), Customer module |
| Payable | Accounting Core (journal control-account activity: PURCHASE/PAYMENT/DEBIT_NOTE reference types), Supplier module |
| Outstanding (Customers/Suppliers) | Sales module (`Sale.dueAmount`), Purchase module (`Purchase.payableAmount`) — indirectly, Receipt/Payment modules via `ReceiptAllocation`/`PaymentAllocation` (which keep those fields in sync, but are not queried directly here) |
| Profit & Loss | Accounting Core (`JournalHeader`/`JournalDetail`, `Account` filtered to `INCOME`/`EXPENSE`) |
| Balance Sheet | Accounting Core (`JournalHeader`/`JournalDetail`, `Account` filtered to `ASSET`/`LIABILITY`), Profit & Loss (for `currentYearProfit`), Financial Year module (`FinancialYearUtil`) |
| Account Summary | Accounting Core (`JournalHeader`/`JournalDetail`, `Account`) |
| Expense Summary / Income Summary | Accounting Core (`JournalHeader`/`JournalDetail`, `Account` filtered to `EXPENSE`/`INCOME`) |
| Accounting Health Check | Accounting Core (`JournalHeader`/`JournalDetail`), Sales module (`Sale`, `CreditNote`), Purchase module (`Purchase`, `DebitNote`), Receipt module (`Receipt`), Payment module (`Payment`), Customer module (`CustomerLedgerEntry`), Supplier module (`SupplierLedgerEntry`), Financial Year module |

No report in this module depends on Store/Multi-Store data (confirmed — see Section 2's "Store-filter usage" note) or on Inventory/Product data.

## 14. Key Operation Flows

### Flow A — Trial Balance

1. **UI**: `frontend/src/pages/accounting/TrialBalance.tsx` mounts, defaults `asOfDate = todayIso()`, calls `load(asOfDate)` in a `useEffect`. Changing the date `<Input>` calls `handleDateChange(value)` which both sets state and immediately calls `load(value)`.
2. **Function**: `load()` calls `accountingReportApi.trialBalance(date)`.
3. **API**: `GET /api/accounting/reports/trial-balance?asOfDate=<date>` (`frontend/src/api/accountingApi.ts`).
4. **Controller**: `AccountingReportController.trialBalance(@RequestParam LocalDate asOfDate)` → `accountingReportService.trialBalance(asOfDate)`.
5. **DTO in**: none (query param only).
6. **Service**: `AccountingReportService.trialBalance(asOfDate)` —
   a. `journalDetailRepository.sumDebitCreditByAccount(asOfDate)` → `Map<accountId, [debit, credit]>` of all activity up to `asOfDate`.
   b. `accountRepository.findByActiveTrueOrderByAccountNameAsc()`, re-sorted by `accountCode`.
   c. For each account: `openingSigned` from `Account.openingBalance`/`openingBalanceType`; `netSigned = openingSigned + activityDebit − activityCredit`; split into `debit`/`credit` columns by sign.
   d. Accumulate `totalDebit`/`totalCredit`; compute `difference` and `balanced`.
7. **Repository (multiple)**: `JournalDetailRepository` (journal activity aggregate query), `AccountRepository` (active accounts list) — both owned by the Accounting Core module, not this one.
8. **Database**: `journal_detail` (joined to `journal_header` inside the JPQL aggregate, filtered to `POSTED`/`REVERSED` status and `journalDate <= asOfDate`), `account`.
9. **Response**: `TrialBalanceResponse { asOfDate, rows: TrialBalanceRow[], totalDebit, totalCredit, difference, balanced }` serialized as JSON.
10. **UI**: `report` state updates; the table renders one row per account (`accountCode, accountName, accountType, debit, credit`), and a footer shows `Total Debit`, `Total Credit`, `Difference`, and a `Balanced`/`Out of Balance` `Badge` keyed off `report.balanced`.

### Flow B — Accounting Health Check

1. **UI**: `frontend/src/pages/accounting/reports/HealthCheck.tsx` mounts, calls `load()` in a `useEffect` (no filters — this report always runs against current data); a "Re-run" button re-triggers the same `load()`.
2. **Function**: `load()` calls `accountingReportApi.healthCheck()`.
3. **API**: `GET /api/accounting/reports/health-check` (no params).
4. **Controller**: `AccountingReportController.healthCheck()` → `accountingHealthCheckService.runHealthCheck()`.
5. **DTO in**: none.
6. **Service**: `AccountingHealthCheckService.runHealthCheck()` runs 7 private check methods in sequence (`checkJournalBalance`, `checkDuplicatePosting`, `checkSourcePosting`, `checkOrphanJournals`, `checkLedgerMismatch`, `checkFinancialYearCoverage`, `checkNotePosting` — see Section 4.9 for each one's exact logic), each producing a `HealthCheckFinding`. `overallStatus` is derived as the worst status among all 7 (`ERROR` > `WARNING` > `PASS`).
7. **Repository (multiple)**: `JournalHeaderRepository`, `JournalDetailRepository` (Accounting Core); `SaleRepository`, `CreditNoteRepository` (Sales module); `PurchaseRepository`, `DebitNoteRepository` (Purchase module); `ReceiptRepository` (Receipt module); `PaymentRepository` (Payment module); `CustomerLedgerEntryRepository` (Customer module); `SupplierLedgerEntryRepository` (Supplier module) — 10 repositories across 7 modules, none owned by this one.
8. **Database**: `journal_header`, `journal_detail`, `sale`, `purchase`, `receipt`, `payment`, `credit_note`, `debit_note`, `customer_ledger_entry`, `supplier_ledger_entry`, plus `financial_year` (via `findPostedDatesWithoutFinancialYear`).
9. **Response**: `AccountingHealthCheckResponse { generatedAt, overallStatus, findings: HealthCheckFinding[] }` serialized as JSON.
10. **UI**: an overall-status `Card` (icon + `Badge` colored by `PASS`/`WARNING`/`ERROR`) followed by one `Card` per finding, each showing its `checkName`, status `Badge`, `message`, and — if non-empty — a scrollable `details` list of the specific offending records (e.g. `"Journal JRN-000123 (id=45): debit=500.00 credit=450.00"`).

## 15. Manual Changes

**To add a new report**, touch every one of these files:
1. **DTO(s)** — one or more new classes in `backend/src/main/java/com/storehub/dto/` (a `<Name>Response` and, if tabular, a `<Name>Row`), following the existing `@Getter @Builder @AllArgsConstructor` pattern (see Section 4's DTO table for the template).
2. **Service method** — add a method to the most relevant of the 9 existing services (e.g. a new journal-based statement belongs in `AccountingReportService` or a new focused service if it doesn't fit an existing one's theme), annotated `@Transactional(readOnly = true)`, reusing an existing repository method where possible rather than writing a new custom query (per this module's own "never a second, independently calculated source of truth" principle).
3. **Controller endpoint** — a new `@GetMapping` in `AccountingReportController`, injecting the owning service if it's a new one, with `@RequestParam`/`@DateTimeFormat` for any filters.
4. **Frontend API export** — a new function on `accountingReportApi` in `frontend/src/api/accountingApi.ts`, and the matching request/response TypeScript interfaces in `frontend/src/types/accounting.ts`.
5. **Frontend page** — a new `.tsx` file in `frontend/src/pages/accounting/reports/`, following the established pattern (Section 2's "Frontend Flow"): filter state → `load()` with try/catch/toast → summary cards → table, importing `money`/date helpers from `./reportFormat`.
6. **Route** — a new `<Route path="/accounting/reports/<slug>" element={<NewReport />} />` inside the existing `<Route element={<ManagerRoute extraRoles={['ACCOUNTANT']} />}>` block in `frontend/src/App.tsx`, plus the matching `import`.
7. **Nav entry** — a new report card entry in `frontend/src/pages/accounting/AccountingHub.tsx` (the array of `{ title, description, icon, path }` cards rendered on the Accounting hub page).

**To change an existing report's formula/logic**: edit only the relevant private/public method inside its owning service (Section 4's per-service breakdown gives the exact formula for each) — the DTO shape and controller signature typically stay unchanged unless the change adds a new field. Because every formula here is a live re-derivation (nothing cached), a formula fix takes effect immediately on next load with no migration or backfill needed.

**Fragility — this module depends on other modules' schemas without owning them.** Since none of these 9 services own their data, a schema/behavior change in another module can silently break a report here without that module's own tests catching it:
- Changing `JournalDetail`'s `debitAmount`/`creditAmount`/`partyType`/`partyId`/`referenceType` columns, or `JournalHeader`'s `status`/`voucherType`/`voucherId`, breaks **8 of the 9 services** (every one except `OutstandingBillService`) — this is the highest-fragility dependency in the module.
- Changing `Account`'s `accountType`, `openingBalance`/`openingBalanceType`, or `active` flag changes Trial Balance, Balance Sheet, Profit & Loss, and Account/Expense/Income Summary results, since all of them branch or filter on those exact fields.
- Changing `Sale.dueAmount`/`Purchase.payableAmount` semantics (e.g. how `ReceiptAllocation`/`PaymentAllocation` keep them in sync) directly changes Outstanding's figures, since this report deliberately reuses those fields instead of re-deriving them from receipts/payments itself.
- Renaming/removing a `SystemAccountCode` (`CASH`, `BANK`, `CUSTOMER_RECEIVABLE`, `SUPPLIER_PAYABLE`, `PURCHASE`, `INVENTORY`) breaks whichever service calls `AccountService.getSystemAccount(...)` for it (Cash/Bank Book, Dashboard) or whichever service's Javadoc assumes its accounting treatment (`ProfitLossService`'s Purchase-as-expense assumption).
- Adding a due-date/credit-terms field to `Sale`/`Purchase` would **not** automatically change Outstanding's ageing basis — `OutstandingBillService.AGEING_BASIS` is a hardcoded string and `bucketFor()` always computes from `invoiceDate`; that would need an explicit code change to pick it up.
- Any new `VoucherType` or `AccountingPartyType` enum value needs to be added to the relevant `switch`/`EnumSet` in this module's services (e.g. `ReceivablePayableService`'s reference-type buckets, `AccountingHealthCheckService`'s per-voucher-type checks) or it will silently be excluded from these reports rather than erroring.
