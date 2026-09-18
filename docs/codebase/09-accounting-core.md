# Accounting Core Engine Module

## 1. Overview

StoreHub implements double-entry bookkeeping around three pillars:

- **Chart of Accounts** — `Account` rows (the GL/ledger accounts) optionally grouped under `AccountGroup` nodes (`Assets -> Current Assets -> Cash`, etc.), each `Account` tagged with an `AccountType` (`ASSET`, `LIABILITY`, `INCOME`, `EXPENSE`). A fixed subset of accounts (Cash, Bank, Customer Receivable, Sales, Output CGST/SGST/IGST, Supplier Payable, Purchase, Input CGST/SGST/IGST, Expenses, Discount Allowed/Received, Round Off, Other Income, Inventory) is enumerated in `SystemAccountCode` and seeded idempotently on startup by `AccountService.ensureSystemAccountsExist()` (a `@PostConstruct`). Business logic must never hard-code an account id — it resolves a system account via `AccountService.getSystemAccount(SystemAccountCode)`.
- **Journal posting** — one posted voucher = one `JournalHeader` row plus 2+ `JournalDetail` (line) rows, each line carrying exactly one of `debitAmount`/`creditAmount` (never both, never neither) referencing one `Account`. A journal is never physically deleted once POSTED; reversing it creates a *second* `JournalHeader` (linked back via `reversalOfJournalId`) with every line's debit/credit swapped, and flips the original's `status` to `REVERSED`.
- **Single point of entry** — every other transactional module (Sale, Purchase, Receipt, Payment, Expense, CashTransaction, CreditNote, DebitNote) posts through `AccountingService` (`postSaleJournal`, `postPurchaseJournal`, `postReceiptJournal`, `postPaymentJournal`, or the generic `postJournal`/`postJournalByAccountId`) rather than ever constructing `JournalHeader`/`JournalDetail` rows itself. `AccountingService` is a *parallel* financial ledger alongside the pre-existing operational sub-ledgers (`CustomerLedgerEntry`, `SupplierLedgerEntry`, `CashLedgerEntry`, `GstEntry`, `PurchaseGstEntry`) maintained by `LedgerService` — it does not replace them; both are updated side by side by each calling service, consuming the same already-calculated amounts (GST, discounts, taxable value are never recomputed by the accounting engine).
- **FY-aware voucher numbering** — `VoucherNumberService.next(VoucherDocType, LocalDate)` is documented as "the ONLY place a voucher number is ever generated." Every document-creating service (Sale, Purchase, SalesOrder, PurchaseOrder, Receipt, Payment, CreditNote, DebitNote, Expense, CashTransaction, StockTransfer) calls it instead of formatting its own `"%s-%06d"` string, producing numbers of the form `PREFIX/FY-CODE/000001`.

## 2. Frontend

### Files and Components

| File | Component | Purpose |
|---|---|---|
| `frontend/src/pages/accounting/AccountingHub.tsx` | `AccountingHub` | Landing page for the Accounting module: stat cards (Cash/Bank/Receivable/Payable balances, today's sales/purchases) plus a grid of links into every accounting sub-page/report (Accounts, Journals, Trial Balance, Expenses, Cash Management, Day Book, Account Ledger, P&L, Balance Sheet, Health Check, etc.). |
| `frontend/src/pages/accounting/AccountMaster.tsx` | `AccountMaster` | Chart of Accounts list (search by code/name, filter by `AccountType`, paginated) with an Add/Edit modal restricted to `user.role === 'ADMIN'`. System accounts have `accountCode`/`accountType` disabled for editing. |
| `frontend/src/pages/accounting/Journals.tsx` | `Journals` | Journal Register: paginated list filterable by `voucherType`, `status` (`DRAFT`/`POSTED`/`REVERSED`), and free-text search; a "New Journal Entry" button (ADMIN only) links to `JournalEntryForm`; a View action opens a modal with the full line breakdown. |
| `frontend/src/pages/accounting/JournalEntryForm.tsx` | `JournalEntryForm` | Manual Journal Entry creation form: date, narration, dynamic list of debit/credit lines (min 2), live-computed totals and a "Balanced"/"Not Balanced" indicator; client-side pre-check mirrors the backend's balance rule before submit. |
| `frontend/src/pages/accounting/TrialBalance.tsx` | `TrialBalance` | As-of-date Trial Balance: per-account debit/credit table plus total debit, total credit, difference, and a Balanced/Out of Balance badge. |
| `frontend/src/api/accountingApi.ts` | `accountApi`, `journalApi`, `accountingReportApi` | Axios wrappers for `/api/accounts`, `/api/accounting/journals`, and `/api/accounting/reports/*`. |
| `frontend/src/types/accounting.ts` | — | TypeScript types mirroring the backend DTOs (`Account`, `AccountGroup`, `JournalHeader`, `JournalLine`, `TrialBalanceResponse`, and the Phase-4 report response shapes). |

### Frontend Flow

**Create/Edit Account** (`AccountMaster.tsx`): `openAdd()`/`openEdit(account)` populate the `values` form state (a snapshot is stored in `initialSnapshot` for dirty-checking) → `handleSubmit` calls `accountApi.create(values)` or `accountApi.update(id, values)` → on success, toast + `load()` refetches the page; on error, `parseApiError` splits the response into a top-level `error` message and a `fieldErrors` map, rendered per-field under each `Input` via `invalid={!!fieldErrors.x}` and a `<p>` feedback line (the pattern this codebase standardizes on for surfacing backend `fieldErrors`).

**Manual Journal Entry create** (`JournalEntryForm.tsx`): user picks a `journalDate`, optional `narration`, and adds/removes line rows (`Account` select + debit/credit inputs, mutually exclusive per row in the UI). `totalDebit`/`totalCredit` are derived every render; a client-side guard blocks submit if fewer than 2 non-empty lines or `totalDebit !== totalCredit`, with the same "Journal is not balanced: total debit (...) does not equal total credit (...)" message format the backend also throws. `handleSubmit` calls `journalApi.create({journalDate, narration, lines})` → on success, navigates to `/accounting/journals`.

**Journal list/detail view** (`Journals.tsx`): `load()` calls `journalApi.list(query)` on mount and whenever `page`/`voucherType`/`status` change; `openView(journal)` calls `journalApi.getById(journal.id)` to fetch the full line breakdown (the list response omits lines) and renders it in a `Dialog` with per-line account/narration/debit/credit and header-level total debit/credit.

**Trial Balance view** (`TrialBalance.tsx`): `load(asOfDate)` calls `accountingReportApi.trialBalance(asOfDate)` on mount and whenever the `asOfDate` date input changes; renders `report.rows` (account code/name/type/debit/credit) plus footer totals and a `balanced` badge, all computed server-side from `JournalDetailRepository.sumDebitCreditByAccount`.

## 3. API Calls

| Function | HTTP Method | URL | Request Shape | Response Shape |
|---|---|---|---|---|
| `accountApi.list(query)` | GET | `/api/accounts` | query params `search?, accountType?, active?, page, size` | `PagedResponse<AccountResponse>` |
| `accountApi.groups()` | GET | `/api/accounts/groups` | — | `AccountGroupResponse[]` |
| `accountApi.getById(id)` | GET | `/api/accounts/{id}` | — | `AccountResponse` |
| `accountApi.create(payload)` | POST | `/api/accounts` | `AccountRequest` | `AccountResponse` (201) |
| `accountApi.update(id, payload)` | PUT | `/api/accounts/{id}` | `AccountRequest` | `AccountResponse` |
| `journalApi.list(query)` | GET | `/api/accounting/journals` | query params `voucherType?, status?, fromDate?, toDate?, search?, page, size` | `PagedResponse<JournalHeaderResponse>` |
| `journalApi.getById(id)` | GET | `/api/accounting/journals/{id}` | — | `JournalHeaderResponse` (with `lines`) |
| `journalApi.create(payload)` | POST | `/api/accounting/journals` | `JournalCreateRequest` (`journalDate`, `narration?`, `lines: JournalLineRequest[]`) | `JournalHeaderResponse` (201) |
| `accountingReportApi.trialBalance(asOfDate?)` | GET | `/api/accounting/reports/trial-balance` | query param `asOfDate?` | `TrialBalanceResponse` |
| `accountingReportApi.dashboard()` | GET | `/api/accounting/reports/dashboard` | — | `AccountingDashboardSummary` |
| `accountingReportApi.dayBook/accountLedger/cashBook/bankBook/partyLedger/receivable/payable/outstandingCustomers/outstandingSuppliers/profitLoss/balanceSheet/accountSummary/expenseSummary/incomeSummary/healthCheck` | GET | `/api/accounting/reports/*` | various date/id query params | corresponding report DTO |

Note: `accountingReportApi` (Trial Balance's data source and the wider report suite listed on `AccountingHub`) is served by `backend/src/main/java/com/storehub/controller/AccountingReportController.java`, a controller adjacent to but not in this task's backend ground-truth list; it is included here only because `TrialBalance.tsx` is a ground-truth frontend file. Its full endpoint-by-endpoint documentation (permissions, DTOs) is **NOT FOUND IN CURRENT CODEBASE's ground-truth scope for this doc** — see the Accounting Reports module doc if one exists separately.

## 4. Backend

### Controllers

**`AccountController`** (`@RequestMapping("/api/accounts")`)

| Method | Endpoint | `@PreAuthorize` | Description |
|---|---|---|---|
| `search` | `GET /api/accounts` | none (any authenticated user) | Paginated/filterable Chart of Accounts list. |
| `listGroups` | `GET /api/accounts/groups` | none | All active `AccountGroup`s. |
| `getById` | `GET /api/accounts/{id}` | none | Single account. |
| `create` | `POST /api/accounts` | `hasRole('ADMIN')` | Creates a new (non-system) account. |
| `update` | `PUT /api/accounts/{id}` | `hasRole('ADMIN')` | Updates an account. |

**`AccountingController`** (`@RequestMapping("/api/accounting/journals")`)

| Method | Endpoint | `@PreAuthorize` | Description |
|---|---|---|---|
| `search` | `GET /api/accounting/journals` | none | Journal Register search (`voucherType`, `status`, `fromDate`, `toDate`, `search`, `page`, `size`). |
| `getById` | `GET /api/accounting/journals/{id}` | none | Full journal with lines. |
| `create` | `POST /api/accounting/journals` | `hasRole('ADMIN')` | Posts a manual `JOURNAL`-type voucher. |

**Note on authorization granularity**: `Permission.ACCOUNT_VIEW`, `JOURNAL_VIEW`, `JOURNAL_CREATE`, `JOURNAL_POST` are defined in `Permission.java` and granted to `Role.ACCOUNTANT` in `RolePermissions.java`, but neither `AccountController` nor `AccountingController` actually checks `hasAuthority('...')` for these permissions — both controllers gate their write endpoints with the coarser `hasRole('ADMIN')` only, and their read endpoints have no `@PreAuthorize` at all (any authenticated user can read). This means the fine-grained `JOURNAL_*`/`ACCOUNT_VIEW` permissions exist in the permission model but are **not enforced at the controller layer for these two controllers** — an ACCOUNTANT role (which lacks `ADMIN`) cannot actually POST `/api/accounts` or `/api/accounting/journals` despite holding `JOURNAL_CREATE`.

### DTOs

| DTO | Fields | Validation |
|---|---|---|
| `AccountRequest` | `accountCode`, `accountName`, `accountType`, `accountGroupId`, `openingBalance`, `openingBalanceType`, `active` | `@NotBlank accountCode`, `@NotBlank accountName`, `@NotNull accountType`, `@NotNull openingBalance`, `@NotNull openingBalanceType` |
| `AccountResponse` | `id, accountCode, accountName, accountType, accountGroupId, accountGroupName, openingBalance, openingBalanceType, systemAccount, active, createdBy, createdAt, modifiedBy, updatedAt` | — (response only) |
| `AccountGroupResponse` | `id, name, accountType, parentGroupId, parentGroupName, active` | — |
| `JournalLineRequest` | `accountId`, `debitAmount` (default `0`), `creditAmount` (default `0`), `narration`, `partyType`, `partyId` | `@NotNull accountId` |
| `JournalCreateRequest` | `journalDate`, `narration`, `lines: List<JournalLineRequest>` | `@NotNull journalDate`, `@NotEmpty @Valid lines` (cascades `JournalLineRequest`'s own `@NotNull accountId`) |
| `JournalDetailResponse` | `id, accountId, accountCode, accountName, debitAmount, creditAmount, narration, partyType, partyId, referenceType, referenceId` | — |
| `JournalHeaderResponse` | `id, journalNumber, journalDate, voucherType, voucherId, voucherNumber, narration, status, reversalOfJournalId, postedBy, postedAt, createdBy, createdAt, totalDebit, totalCredit, lines: List<JournalDetailResponse>` | — (`totalDebit`/`totalCredit` are computed by summing `lines` in `fromEntity`) |

Not a DTO but the two carriers used internally by `AccountingService` for lines before a `JournalDetail` exists:
- `JournalLine` (record, `com.storehub.service`): `(SystemAccountCode account, BigDecimal debitAmount, BigDecimal creditAmount, String narration, AccountingPartyType partyType, Long partyId)` with static factories `debit(...)`/`credit(...)` — used by the Sale/Purchase/Receipt/Payment posting methods, which reference accounts by `SystemAccountCode`.
- `ManualJournalLine` (record, `com.storehub.service`): `(Long accountId, BigDecimal debitAmount, BigDecimal creditAmount, String narration, AccountingPartyType partyType, Long partyId)` — used by `postManualJournal`/`postJournalByAccountId`, which reference accounts by real id (e.g. an Expense Category's configured account).

### Services

#### `AccountingService` — every public method

- **`postSaleJournal(Sale sale)`** `@Transactional` — Books the Sale's accounting entries. If the sale has a named `Customer`: debits `CUSTOMER_RECEIVABLE` for the *full* `totalAmount` (tagged `AccountingPartyType.CUSTOMER` + customer id) and credits `SALES` for `taxableAmount` plus, if `GstType.GST`, `OUTPUT_CGST`/`OUTPUT_SGST`/`OUTPUT_IGST` for any positive tax amounts — regardless of how much was paid immediately (a later Receipt clears the receivable via its own journal). If there is no customer (a walk-in sale) and `paidAmount > 0`: debits Cash or Bank (via `resolveCashOrBank`) for `paidAmount` and credits the same Sales/GST lines. If no customer and nothing paid, the method returns without posting anything. Calls `postJournal(VoucherType.SALE, sale.getId(), sale.getInvoiceNumber(), sale.getSaleDate(), "Sale " + invoiceNumber, lines, storeId)`.
- **`reverseSaleJournal(Sale sale, String reason)`** `@Transactional` — delegates to `reverseJournal(VoucherType.SALE, sale.getId(), reason)`.
- **`postPurchaseJournal(Purchase purchase)`** `@Transactional` — Debits `PURCHASE` for `taxableAmount`, plus `INPUT_CGST`/`INPUT_SGST`/`INPUT_IGST` if `GstType.GST`, and always credits `SUPPLIER_PAYABLE` for the full `totalAmount` (tagged `SUPPLIER` + supplier id), regardless of how much is paid immediately.
- **`reversePurchaseJournal(Purchase purchase, String reason)`** `@Transactional` — delegates to `reverseJournal(VoucherType.PURCHASE, ...)`.
- **`postReceiptJournal(Receipt receipt)`** `@Transactional` — Debits Cash/Bank (via `resolveCashOrBank(receipt.getPaymentMode())`) for `receipt.getAmount()`, credits `CUSTOMER_RECEIVABLE` for the same amount (tagged `CUSTOMER` + customer id).
- **`reverseReceiptJournal(Receipt receipt, String reason)`** `@Transactional` — delegates to `reverseJournal(VoucherType.RECEIPT, ...)`.
- **`postPaymentJournal(Payment payment)`** `@Transactional` — Debits `SUPPLIER_PAYABLE` (tagged `SUPPLIER` + supplier id) for `payment.getAmount()`, credits Cash/Bank for the same amount.
- **`reversePaymentJournal(Payment payment, String reason)`** `@Transactional` — delegates to `reverseJournal(VoucherType.PAYMENT, ...)`.
- **`getJournalById(Long id): JournalHeaderResponse`** `@Transactional(readOnly = true)` — throws `JournalNotFoundException` if absent.
- **`searchJournals(VoucherType, LocalDate from, LocalDate to, String search, int page, int size): PagedResponse<JournalHeaderResponse>`** `@Transactional(readOnly = true)` — overload without status, delegates to the 7-arg version with `status = null`.
- **`searchJournals(VoucherType, JournalStatus, LocalDate, LocalDate, String, int, int): PagedResponse<JournalHeaderResponse>`** — the Journal Register query, sorted `journalDate DESC, id DESC`.
- **`resolveCashOrBank(PaymentMode mode): SystemAccountCode`** — maps `PaymentMode.CASH → SystemAccountCode.CASH`, anything else → `SystemAccountCode.BANK`. Documented as "the one place that maps a payment mode to a system account," reused by `ExpenseService` and `CashTransactionService`.
- **`postJournal(VoucherType, Long voucherId, String voucherNumber, LocalDate journalDate, String narration, List<JournalLine> lines): JournalHeader`** — 6-arg overload, delegates to the 7-arg version with `storeId = null`.
- **`postJournal(VoucherType, Long voucherId, String voucherNumber, LocalDate journalDate, String narration, List<JournalLine> lines, Long storeId): JournalHeader`** `@Transactional` — validates the lines (`validateJournal`), resolves each `SystemAccountCode` to a live, active `Account` (`resolveActiveAccount`, throwing `BadRequestException` if the account is inactive), then calls `buildAndSaveJournal`.
- **`postManualJournal(LocalDate journalDate, String narration, List<ManualJournalLine> lines): JournalHeader`** `@Transactional` — throws `BadRequestException("A journal must have at least one line")` if `lines` is null/empty; resolves each line's `accountId` to a live active `Account` (`resolveActiveAccountById`), validates balance (`validateResolvedLines`), then `buildAndSaveJournal(VoucherType.JOURNAL, null, null, journalDate, narration, resolved, null)` — no source `voucherId`, so duplicate-posting protection does not apply to manual journals.
- **`postManualJournal(JournalCreateRequest request): JournalHeaderResponse`** `@Transactional` — REST-facing overload; maps `request.getLines()` to `ManualJournalLine`s and wraps the saved entity in `JournalHeaderResponse.fromEntity`.
- **`postJournalByAccountId(VoucherType, Long voucherId, String voucherNumber, LocalDate journalDate, String narration, List<ManualJournalLine> lines): JournalHeader`** — 6-arg overload, delegates with `storeId = null`.
- **`postJournalByAccountId(VoucherType, Long voucherId, String voucherNumber, LocalDate journalDate, String narration, List<ManualJournalLine> lines, Long storeId): JournalHeader`** `@Transactional` — like `postJournal`, but lines reference accounts by real id rather than `SystemAccountCode` (used where the posting account is configurable per source record, e.g. an Expense Category's linked account); duplicate-posting protection and voucher-id-based reversal both still apply since a real `voucherId`/`voucherType` is supplied.
- **`buildAndSaveJournal(VoucherType, Long voucherId, String voucherNumber, LocalDate journalDate, String narration, List<ResolvedLine> lines, Long storeId): JournalHeader`** *(private)* `@Transactional` — the actual save path; see §9/§14.
- **`reverseJournal(VoucherType voucherType, Long voucherId, String reason): void`** `@Transactional` — see §14; no-op if `voucherId == null` or no active POSTED original exists (never posted, or already reversed).
- **`validateJournal(List<JournalLine> lines): void`** *(private)* — throws `BadRequestException` if `lines` is null/empty, else delegates amount validation.
- **`validateResolvedLines(List<ResolvedLine> lines): void`** *(private)* — same, for the id-based line path.
- **`validateAmounts(List<BigDecimal[]> debitCreditPairs): void`** *(private)* — the shared debit/credit rule engine; see §6.
- **`resolveActiveAccount(SystemAccountCode code): Account`** *(private)* — `accountService.getSystemAccount(code)`, throws `BadRequestException` if `!account.isActive()`.
- **`resolveActiveAccountById(Long accountId): Account`** *(private)* — `accountService.findOrThrow(accountId)`, same inactive-account guard.

`SystemAccountCode.values()`, `VoucherType` values, `AccountingPartyType` are documented in §1/§5. `AccountingService` depends on `AccountService`, `JournalHeaderRepository`, `FinancialYearService`, `StoreRepository`.

#### `VoucherNumberService` — FY-aware numbering algorithm

- **`next(VoucherDocType docType, LocalDate transactionDate): String`** — the only public method, annotated `@Transactional(propagation = Propagation.REQUIRES_NEW)`:
  1. `financialYearService.resolveForDate(transactionDate)` resolves the `FinancialYear` the date falls in (does **not** require it to be OPEN — a closed FY's historical documents must remain viewable/re-printable). Throws `BadRequestException` if no FY covers the date.
  2. `voucherSequenceRepository.ensureRowExists(docType.name(), fy.getId())` — a native `INSERT ... ON DUPLICATE KEY UPDATE id = id` upsert-if-absent, chosen specifically so a race never throws a constraint violation that would mark the JPA transaction rollback-only.
  3. `voucherSequenceRepository.lockForUpdate(docType, fy.getId())` — `SELECT ... FOR UPDATE` (`@Lock(LockModeType.PESSIMISTIC_WRITE)`), row-locking the `(docType, financialYearId)` counter for the duration of this `REQUIRES_NEW` transaction, serializing concurrent increments so two callers can never receive the same number.
  4. `seq.setLastNumber(seq.getLastNumber() + 1)`, saved.
  5. Returns `docType.getPrefix() + "/" + fy.getCode() + "/" + String.format("%06d", seq.getLastNumber())`, e.g. `SALE/26-27/000001`.
- **Concurrency design**: runs in its own `REQUIRES_NEW` transaction so the counter lock is acquired and released independently of the caller's (often much larger) transaction — a long-running Sale-posting transaction never holds the numbering lock, and a numbering failure never poisons the caller's transaction.
- **Draft numbering policy**: a number is allocated at document *creation* time regardless of draft/posted status; a draft later cancelled or never posted still permanently consumes its number (never reused).
- **`VoucherDocType` values and prefixes**: `SALE("SALE")`, `SALE_CHALLAN("SC")`, `PURCHASE("PUR")`, `PURCHASE_CHALLAN("PC")`, `SALES_ORDER("SO")`, `PURCHASE_ORDER("PO")`, `RECEIPT("REC")`, `PAYMENT("PAY")`, `CREDIT_NOTE("CN")`, `DEBIT_NOTE("DN")`, `EXPENSE("EXP")`, `CASH_TRANSACTION("CT")`, `STOCK_TRANSFER("ST")`. It is deliberately separate from `VoucherType` (the accounting journal classification) — e.g. a Kacchi Sale gets its own `SC/26-27/000001` numbering series while still posting its accounting journal under the single `VoucherType.SALE` bucket.

#### `LedgerService` — every public method

Every posting is an immutable row; a reversal is a new offsetting row, never a delete/update of the original.

*Customer receivable ledger / output GST / cash movement (Sale, Receipt side):*
- `recordSaleDebit(Sale sale): void` `@Transactional` — no-op if `sale.getCustomer() == null`; else saves a `CustomerLedgerEntry(DEBIT, sale.getTotalAmount(), referenceType=SALE)`.
- `reverseSaleDebit(Sale sale, String reason): void` `@Transactional` — mirrors with `CREDIT`.
- `recordGstEntry(Sale sale): void` `@Transactional` — no-op unless `GstType.GST`; saves `GstEntry(taxableAmount, cgstAmount, sgstAmount, igstAmount)`.
- `reverseGstEntry(Sale sale): void` `@Transactional` — same amounts negated.
- `recordReceiptCredit(Receipt receipt): void` `@Transactional` — `CustomerLedgerEntry(CREDIT, receipt.getAmount(), referenceType=RECEIPT)`.
- `reverseReceiptCredit(Receipt receipt, String reason): void` `@Transactional` — mirrors with `DEBIT`.
- `recordCashEntry(Receipt receipt): void` `@Transactional` — `CashLedgerEntry(paymentMode, amount, referenceType=RECEIPT)`.
- `reverseCashEntry(Receipt receipt, String reason): void` `@Transactional` — same, amount negated.
- `recordCashEntryForSale(Sale sale): void` `@Transactional` — no-op unless `paidAmount > 0`; used for a walk-in sale paid immediately (money moved, no receivable to track).
- `reverseCashEntryForSale(Sale sale, String reason): void` `@Transactional` — mirrors, negated.
- `getOutstandingForCustomer(Long customerId): BigDecimal` — delegates to repository.
- `getTotalOutstanding(): BigDecimal` — delegates to repository.

*Supplier payable ledger / input GST / cash outflow (Purchase, Payment side):*
- `recordPurchaseCredit(Purchase purchase): void` `@Transactional` — `SupplierLedgerEntry(CREDIT, totalAmount, referenceType=PURCHASE)`.
- `reversePurchaseCredit(Purchase purchase, String reason): void` `@Transactional` — mirrors with `DEBIT`.
- `recordInputGstEntry(Purchase purchase): void` `@Transactional` — no-op unless GST; saves `PurchaseGstEntry`.
- `reverseInputGstEntry(Purchase purchase): void` `@Transactional` — negated.
- `recordPaymentDebit(Payment payment): void` `@Transactional` — `SupplierLedgerEntry(DEBIT, amount, referenceType=PAYMENT)`.
- `reversePaymentDebit(Payment payment, String reason): void` `@Transactional` — mirrors with `CREDIT`.
- `recordCashEntryOut(Payment payment): void` `@Transactional` — `CashLedgerEntry(amount negated, referenceType=PAYMENT)`.
- `reverseCashEntryOut(Payment payment, String reason): void` `@Transactional` — amount positive (reversing the negation).
- `getOutstandingForSupplier(Long supplierId): BigDecimal`, `getTotalPayables(): BigDecimal` — delegate to repository.

*Expense (Account-module) and Credit/Debit Note:*
- `recordExpenseCredit(Expense expense): void` `@Transactional` — "A credit (party) Expense increases what is owed to the supplier — same polarity as `recordPurchaseCredit`"; saves `SupplierLedgerEntry(CREDIT, expense.getTotalAmount(), referenceType=EXPENSE)`.
- `reverseExpenseCredit(Expense expense, String reason): void` `@Transactional` — mirrors with `DEBIT`.
- `recordCreditNoteEntry(CreditNote note): void` `@Transactional` — "always REDUCES what the customer owes — the opposite polarity of `recordSaleDebit`"; `CustomerLedgerEntry(CREDIT, note.getTotalAmount(), referenceType=CREDIT_NOTE)`.
- `reverseCreditNoteEntry(CreditNote note, String reason): void` `@Transactional` — mirrors with `DEBIT`.
- `recordDebitNoteEntry(DebitNote note): void` `@Transactional` — "always REDUCES what is owed to the supplier — the opposite polarity of `recordPurchaseCredit`"; `SupplierLedgerEntry(DEBIT, note.getTotalAmount(), referenceType=DEBIT_NOTE)`.
- `reverseDebitNoteEntry(DebitNote note, String reason): void` `@Transactional` — mirrors with `CREDIT`.

#### `AccountService` — CRUD for Account/AccountGroup

- `ensureSystemAccountsExist(): void` `@PostConstruct @Transactional` — for each `AccountType`, finds/creates a top-level `AccountGroup` (Assets/Liabilities/Income/Expenses); for each `SystemAccountCode`, finds-or-creates the corresponding `Account` (idempotent by `accountCode`), with `systemAccount=true`, `active=true`, `openingBalance=ZERO`.
- `getSystemAccount(SystemAccountCode code): Account` — resolves via an in-memory `ConcurrentHashMap<SystemAccountCode, Long>` cache (code → account id) to avoid a query per journal line; falls back to `accountRepository.findByAccountCode`, throws `AccountNotFoundException(code.getCode())` if missing.
- `search(String search, AccountType accountType, Boolean active, int page, int size): PagedResponse<AccountResponse>` — sorted `accountCode ASC`.
- `getById(Long id): AccountResponse` — throws `AccountNotFoundException` if absent.
- `listGroups(): List<AccountGroupResponse>` — all active groups, sorted by name.
- `create(AccountRequest request): AccountResponse` `@Transactional` — throws `BadRequestException` if `accountCode` already exists (case-insensitive); new accounts are always `systemAccount=false`.
- `update(Long id, AccountRequest request): AccountResponse` `@Transactional` — if the code changes: throws `BadRequestException("The code of a system account cannot be changed")` for a system account, or a duplicate-code `BadRequestException` otherwise; `accountType` is only updated for non-system accounts (a system account's type stays fixed).
- `findOrThrow(Long id): Account` — throws `AccountNotFoundException(id)`.

### Repository

**`AccountRepository`**: `findByAccountCode(String)`, `existsByAccountCodeIgnoreCase(String)`, `findByActiveTrueOrderByAccountNameAsc()`, `search(search, accountType, active, Pageable)` (JPQL, case-insensitive `LIKE` on code/name).

**`AccountGroupRepository`**: `findByNameIgnoreCase(String)`, `findByActiveTrueOrderByNameAsc()`.

**`JournalHeaderRepository`**: `existsByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(VoucherType, Long, JournalStatus)`, `findByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(...)`, `search(voucherType, fromDate, toDate, search, Pageable)`, `search(voucherType, status, fromDate, toDate, search, Pageable)`, `findDuplicateActivePostings()`, `findActivePostedVoucherIds(VoucherType)`, `findDistinctVoucherIds(VoucherType)`, `findPostedDatesWithoutFinancialYear()` (the last four back the Accounting Health Check report).

**`JournalDetailRepository`**: `findByJournalIdOrderById(Long)`, `findLedgerLines(accountId, from, to)`, `sumDebitCreditByAccount(asOfDate)` (Trial Balance), `sumDebitCreditBefore(accountId, beforeDate)`, `sumDebitCreditForAccount(accountId)`, `dayBookRows(from, to)` / `dayBookRows(voucherType, from, to)`, `sumDebitCreditByAccountRange(from, to)`, `accountVoucherRows(accountId, from, to)`, `sumByPartyBefore(partyType, beforeDate)`, `sumByPartyAndReferenceTypeRange(partyType, from, to)`, `findLedgerLinesByParty(partyType, partyId, from, to)`, `sumByParty(partyType)`, `findUnbalancedJournals()`.

**`VoucherSequenceRepository`**: `lockForUpdate(VoucherDocType, Long financialYearId)` (`@Lock(PESSIMISTIC_WRITE)`), `ensureRowExists(String docType, Long financialYearId)` (native upsert).

## 5. Database

### Tables

- **`accounts`** — columns: `id`, `account_code` (unique, not null), `account_name`, `account_type` (VARCHAR(20)), `account_group_id` (FK), `opening_balance` (precision 12, scale 2), `opening_balance_type` (VARCHAR(10)), `system_account` (boolean), `active` (boolean), `created_by`, `created_at`, `modified_by`, `updated_at`.
- **`account_groups`** — `id`, `name`, `account_type`, `parent_group_id` (self-FK), `active`, `created_at`, `updated_at`.
- **`journal_headers`** — `id`, `journal_number` (unique), `journal_date` (indexed), `voucher_type` (VARCHAR(20), indexed with `voucher_id`), `voucher_id`, `store_id` (FK), `voucher_number`, `narration` (TEXT), `status` (VARCHAR(10), indexed), `reversal_of_journal_id` (self-FK), `posted_by`, `posted_at`, `created_by`, `created_at`.
- **`journal_details`** — `id`, `journal_id` (FK, not null), `account_id` (FK, not null, indexed), `debit_amount` (precision 12, scale 2, default 0), `credit_amount` (precision 12, scale 2, default 0), `narration`, `party_type` (VARCHAR(10), indexed with `party_id`), `party_id`, `reference_type` (VARCHAR(20)), `reference_id`.
- **`voucher_sequences`** — `id`, `doc_type` (VARCHAR(20)), `financial_year_id`, `last_number`; unique constraint `uk_voucher_sequence` on `(doc_type, financial_year_id)`.

### Relationships

- `JournalHeader` 1—* `JournalDetail`: `@OneToMany(mappedBy = "journal", cascade = ALL, orphanRemoval = true)` on `JournalHeader.lines`; `JournalDetail.journal` is the owning `@ManyToOne`. `JournalHeader.addLine(line)` sets both sides.
- `JournalDetail` *—1 `Account`: `@ManyToOne` on `JournalDetail.account`.
- `Account` *—1 `AccountGroup`: `@ManyToOne` on `Account.accountGroup` (nullable).
- `AccountGroup` *—1 `AccountGroup`: self-referential `parentGroup` for the group hierarchy.
- `JournalHeader` *—1 `Store`: `@ManyToOne` on `JournalHeader.store`, nullable — present when the source voucher has a meaningful store (Sale/Purchase/Receipt/Payment/etc.), null for a manual `JOURNAL` entry or a pre-multi-store legacy voucher.
- `JournalHeader` *—1 `JournalHeader` (self): `reversalOfJournal` links a reversal journal back to the original it offsets.
- `JournalDetail.partyType` (`AccountingPartyType`) + `partyId` is a loose (non-FK) tag pointing at a `Customer` or `Supplier` id, letting a control account (e.g. Customer Receivable) be drilled down per party without a hard foreign key.

## 6. Validation

Debit=credit balance check and related invariants, from `AccountingService.validateAmounts` (exact code):

```java
private void validateAmounts(List<BigDecimal[]> debitCreditPairs) {
    BigDecimal totalDebit = BigDecimal.ZERO;
    BigDecimal totalCredit = BigDecimal.ZERO;
    boolean hasDebit = false;
    boolean hasCredit = false;

    for (BigDecimal[] pair : debitCreditPairs) {
        BigDecimal debit = pair[0];
        BigDecimal credit = pair[1];

        if (debit.signum() < 0 || credit.signum() < 0) {
            throw new BadRequestException("A journal line amount cannot be negative");
        }
        if (debit.signum() > 0 && credit.signum() > 0) {
            throw new BadRequestException("A journal line cannot have both a debit and a credit amount");
        }
        if (debit.signum() == 0 && credit.signum() == 0) {
            throw new BadRequestException("A journal line must have either a debit or a credit amount");
        }

        hasDebit = hasDebit || debit.signum() > 0;
        hasCredit = hasCredit || credit.signum() > 0;
        totalDebit = totalDebit.add(debit);
        totalCredit = totalCredit.add(credit);
    }

    if (!hasDebit || !hasCredit) {
        throw new BadRequestException("A journal must have at least one debit line and one credit line");
    }
    if (totalDebit.compareTo(totalCredit) != 0) {
        throw new BadRequestException("Journal is not balanced: total debit (" + totalDebit
                + ") does not equal total credit (" + totalCredit + ")");
    }
}
```

Other invariants enforced by `AccountingService`:

- **At least one line**: `postManualJournal` throws `BadRequestException("A journal must have at least one line")` if `lines == null || lines.isEmpty()`; `validateJournal`/`validateResolvedLines` throw the same for the `postJournal`/`postJournalByAccountId` paths.
- **FY-open check**: `buildAndSaveJournal` calls `financialYearService.resolveOpenForPosting(journalDate)` before saving; this throws `BadRequestException` if no FY covers the date, or `BadRequestException("Financial year " + fy.getName() + " is closed. This transaction (" + date + ") cannot be posted.")` if the FY is `CLOSED`. `reverseJournal` performs the same check against `LocalDate.now()` (the reversal's own posting date).
- **No double-posting**: `buildAndSaveJournal` throws `BadRequestException("An accounting journal has already been posted for this " + voucherType.name().toLowerCase())` if `journalHeaderRepository.existsByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(voucherType, voucherId, POSTED)` is already true (checked only when `voucherId != null`, so manual journals are exempt).
- **Active-account check**: `resolveActiveAccount`/`resolveActiveAccountById` throw `BadRequestException("Account '" + account.getAccountName() + "' is inactive and cannot be posted to")` if the resolved `Account.active` is false.
- **Account uniqueness (AccountService)**: `create`/`update` throw `BadRequestException("An account with code '...' already exists")` on a duplicate `accountCode` (case-insensitive), and `BadRequestException("The code of a system account cannot be changed")` if an attempt is made to rename a system account's code.

## 7. Authentication / Authorization

All `/api/accounts/**` and `/api/accounting/**` endpoints require an authenticated request — `SecurityConfig.filterChain` permits only `/api/auth/**` and `/actuator/health` without authentication (`.anyRequest().authenticated()`), enforced via a stateless JWT filter chain (`JwtAuthenticationFilter` added before `UsernamePasswordAuthenticationFilter`), with `@EnableMethodSecurity` enabling the controllers' `@PreAuthorize` annotations. There is no accounting-specific authentication mechanism; it relies entirely on the application-wide JWT/`UserPrincipal` setup.

## 8. Permissions

Defined in `Permission.java` under the `"Account"` module:

- `ACCOUNT_VIEW`
- `JOURNAL_VIEW`, `JOURNAL_CREATE`, `JOURNAL_POST`
- `EXPENSE_VIEW`, `EXPENSE_CREATE`, `EXPENSE_POST`, `EXPENSE_CANCEL` (Expense module, posts through this engine but is documented elsewhere)
- `REPORT_VIEW`, `REPORT_EXPORT`
- `CASH_MANAGE`
- `FY_MANAGE` (kept deliberately separate from `ACCOUNT_VIEW` per the code comment — "financial year close requires the highest authorization")

Per `RolePermissions.java`: `Role.ADMIN` holds every permission; `Role.STORE_MANAGER` holds all of ADMIN's operational permissions except `FY_MANAGE` (and the security/GST-config/store-admin tiers) — it *does* hold `JOURNAL_*`/`ACCOUNT_VIEW`; `Role.ACCOUNTANT` explicitly holds `ACCOUNT_VIEW, JOURNAL_VIEW, JOURNAL_CREATE, JOURNAL_POST` plus the related Sales/Purchase view, Expense, Report, Cash, and GST permissions. As noted in §4, these permissions are defined and assigned but **not actually checked** by `AccountController`/`AccountingController`, whose write endpoints instead gate on `hasRole('ADMIN')` directly — so in practice only ADMIN can create/edit an Account or post a manual Journal Entry through these two controllers, regardless of `JOURNAL_CREATE`/`JOURNAL_POST` grants elsewhere.

## 9. Transaction Handling

Posting must be atomic (all lines saved together, or none), and this is enforced entirely through Spring's declarative `@Transactional`:

- Every `post*Journal`/`reverse*Journal` public method on `AccountingService` (`postSaleJournal`, `postPurchaseJournal`, `postReceiptJournal`, `postPaymentJournal`, their `reverse*` counterparts, `postJournal(..., storeId)`, `postManualJournal` (both overloads), `postJournalByAccountId(..., storeId)`, `reverseJournal`) is annotated `@Transactional`. Because these are default-propagation (`REQUIRED`), they join the calling module's own transaction (e.g. `SaleService.createSale`'s transaction) when called from within one — so a Sale row, its `CustomerLedgerEntry`/`GstEntry` (via `LedgerService`), and its `JournalHeader`+`JournalDetail` rows (via `AccountingService`) all commit or roll back together as a single unit of work.
- `buildAndSaveJournal` (private, called from within an already-`@Transactional` public method) performs, in order: the FY-open check, the duplicate-posting check, builds and saves the `JournalHeader`, saves each `JournalDetail` line via `header.addLine(...)` (cascaded by `CascadeType.ALL` on `JournalHeader.lines`), then re-saves the header twice more to stamp `journalNumber = "JRN-%06d".formatted(id)` (needs the generated id first) and, if still null, `voucherNumber = journalNumber`. All of this happens inside one transaction — a failure partway (e.g. the duplicate-posting check firing) throws before any row is persisted, and Spring rolls back the whole enclosing transaction (the `RuntimeException` subclass `BadRequestException` triggers rollback by default).
- `reverseJournal` similarly does its lookup, FY check, builds the reversal `JournalHeader`+lines, saves it, then flips the original's `status` to `REVERSED` and saves it — all within the same `@Transactional` boundary, so the reversal journal and the original's status change are atomic together.
- **`VoucherNumberService.next(...)` is the one deliberate exception**: it is `@Transactional(propagation = Propagation.REQUIRES_NEW)`, meaning it always runs in its own, independent transaction rather than joining the caller's. This is intentional (see §4) — it decouples the pessimistic row lock on `voucher_sequences` from the (potentially much longer) posting transaction, so the lock is held only as long as the increment itself takes, and a numbering failure does not roll back whatever transaction called it (though in practice a numbering failure typically happens before the rest of the document is built, e.g. in `SaleService.createSale` before `accountingService.postSaleJournal` is even reached).
- Read paths (`getJournalById`, `searchJournals`) are `@Transactional(readOnly = true)`.

## 10. Error Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to a uniform `ApiError` body (`timestamp, status, error, message, path, fieldErrors`):

- `AccountNotFoundException` → 404, message `"Account not found with id: {id}"` or `"Account not found with code: {code}"`.
- `JournalNotFoundException` → 404, message `"Journal not found with id: {id}"`.
- `BadRequestException` (every validation failure in §6, plus AccountService's duplicate-code/system-account-code-change errors) → 400, `ex.getMessage()` as `message`, `fieldErrors = null`.
- `MethodArgumentNotValidException` (bean-validation failures on `AccountRequest`/`JournalCreateRequest`/`JournalLineRequest`, e.g. a missing `accountCode` or `accountId`) → 400, `message = "Validation failed"`, `fieldErrors` populated per-field from the binding result — per the project-wide rule, the frontend must surface these via `isInvalid`/feedback text rather than only the top-level message (see `AccountMaster.tsx`'s `fieldErrors` handling in §2).
- `AccessDeniedException` (a non-ADMIN hitting a `hasRole('ADMIN')` endpoint) → 403, generic message.
- Any other `Exception` → 500, generic `"An unexpected error occurred"`, logged server-side.

## 11. Audit Flow

`AccountingService`, `AccountService`, `LedgerService`, and `VoucherNumberService` do **not** call `AuditService` anywhere — grepped for `auditService.` across all four files with zero matches. Audit logging for financial-year state changes (open/close/mark-current), which gates whether a posting is even allowed, is done in `FinancialYearService` (`auditService.log(AuditAction.FY_CLOSE/FY_OPEN/CREATE/UPDATE, "ADMIN", "FinancialYear", ...)`), not in the accounting engine itself. There is **NOT FOUND IN CURRENT CODEBASE** any direct audit-trail entry written when a Journal is posted or reversed, or when an Account is created/updated — the only record of who posted/reversed a journal is the `postedBy`/`createdBy` string columns on `JournalHeader` itself (populated from `SecurityUtil.currentUsername()` / the authenticated `UserPrincipal`'s name).

## 12. Important Side Effects

- Posting a Sale/Purchase/Receipt/Payment/Expense/CreditNote/DebitNote/CashTransaction always writes to **two** parallel ledgers: the `LedgerService` operational sub-ledger (`CustomerLedgerEntry`/`SupplierLedgerEntry`/`CashLedgerEntry`/`GstEntry`/`PurchaseGstEntry`) and the `AccountingService` double-entry journal (`JournalHeader`+`JournalDetail`). These are maintained independently by each calling service (see §13) — nothing in `AccountingService` itself updates the sub-ledgers, and nothing in `LedgerService` posts a journal.
- `AccountService.ensureSystemAccountsExist()` runs on every application startup and is idempotent (find-or-create), so it has no effect after the first run except guaranteeing the fixed set of `SystemAccountCode` accounts and their top-level groups exist.
- `AccountService.getSystemAccount` caches `SystemAccountCode → Account.id` in an unbounded, never-invalidated `ConcurrentHashMap` for the process lifetime — updating a system account's other fields (name, group, balance) is reflected immediately since the cache only stores the id, but the cache is never cleared, which is fine because system-account ids never change once created.
- A walk-in Sale (no customer) with `paidAmount == 0` posts **no** journal at all (`postSaleJournal` returns early) — matching the pre-existing gap already present in `LedgerService.recordSaleDebit`'s own no-op for the same case.
- `postJournal`/`postJournalByAccountId` resolve accounts and validate balance *before* touching the database, but the FY-open check and duplicate-posting check happen inside `buildAndSaveJournal`, after line resolution — a caller could resolve valid accounts and then still fail on a closed FY or a duplicate voucher.
- `buildAndSaveJournal` saves the `JournalHeader` row up to three times (once to get a generated `id`, once to stamp `journalNumber`, and a third time if `voucherNumber` was still null) — each save happens within the same transaction, so this is not visible to other transactions until commit, but it means `journalNumber` generation depends on the auto-increment `id`, not on `VoucherNumberService`.

## 13. Dependencies on Other Modules

### Modules that call `AccountingService`

| Calling module (service) | Method(s) called |
|---|---|
| `SaleService` | `postSaleJournal(saved)`; `reverseSaleJournal(sale, "Sale revised: " + invoiceNumber)`; `reverseSaleJournal(sale, reason)` |
| `PurchaseService` | `postPurchaseJournal(saved)`; `reversePurchaseJournal(purchase, "Purchase revised: " + purchaseNumber)`; `reversePurchaseJournal(purchase, reason)` |
| `ReceiptService` | `postReceiptJournal(saved)` (×2 call sites); `reverseReceiptJournal(receipt, reason)` |
| `PaymentService` | `postPaymentJournal(saved)` (×2 call sites); `reversePaymentJournal(payment, reason)` |
| `ExpenseService` | `accountingService.resolveCashOrBank(expense.getPaymentMode())`; `postJournalByAccountId(VoucherType.EXPENSE, expense.getId(), expense.getExpenseNumber(), ...)`; `reverseJournal(VoucherType.EXPENSE, expense.getId(), reason)` |
| `CashTransactionService` | `resolveCashOrBank(txn.getPaymentMode())`; `postJournal(VoucherType.CASH_TRANSACTION, txn.getId(), txn.getTransactionNumber(), ...)`; `reverseJournal(VoucherType.CASH_TRANSACTION, txn.getId(), reason)` |
| `CreditNoteService` | `postJournal(VoucherType.CREDIT_NOTE, note.getId(), note.getVoucherNumber(), ...)`; `reverseJournal(VoucherType.CREDIT_NOTE, note.getId(), reason)` |
| `DebitNoteService` | `postJournal(VoucherType.DEBIT_NOTE, note.getId(), note.getVoucherNumber(), ...)`; `reverseJournal(VoucherType.DEBIT_NOTE, note.getId(), reason)` |

**8 distinct caller modules** call into `AccountingService`.

### Modules that call `VoucherNumberService.next(...)`

`SalesOrderService` (SALES_ORDER), `ReceiptService` (RECEIPT, ×2), `PurchaseService` (PURCHASE), `CashTransactionService` (CASH_TRANSACTION), `CreditNoteService` (CREDIT_NOTE), `StockTransferService` (STOCK_TRANSFER), `SaleService` (SALE), `ExpenseService` (EXPENSE), `PurchaseOrderService` (PURCHASE_ORDER), `PaymentService` (PAYMENT, ×2), `DebitNoteService` (DEBIT_NOTE) — **11 distinct caller modules**.

### Modules that call `LedgerService`

`SaleService` (`recordSaleDebit`, `recordGstEntry`, `recordCashEntryForSale`, and their `reverse*` counterparts), `PurchaseService` (`recordPurchaseCredit`, `recordInputGstEntry`, reverses), `ReceiptService` (`recordReceiptCredit`, `recordCashEntry`, reverses), `PaymentService` (`recordPaymentDebit`, `recordCashEntryOut`, reverses), `ExpenseService` (`recordExpenseCredit`, `reverseExpenseCredit`), `CreditNoteService` (`recordCreditNoteEntry`, `reverseCreditNoteEntry`), `DebitNoteService` (`recordDebitNoteEntry`, `reverseDebitNoteEntry`), `SalesSummaryService` (read-only: `getTotalOutstanding()`), `PurchaseSummaryService` (read-only: `getTotalPayables()`) — **9 distinct caller modules**.

### What this module depends on

- **`FinancialYearService`** — `AccountingService.buildAndSaveJournal`/`reverseJournal` call `resolveOpenForPosting(date)`; `VoucherNumberService.next` calls `resolveForDate(date)`. This is the only cross-module *inbound* dependency of the core posting/numbering engine.
- **`StoreRepository`/`Store`** — `AccountingService.buildAndSaveJournal` calls `storeRepository.getReferenceById(storeId)` to stamp `JournalHeader.store` when a `storeId` is supplied by the caller.
- **`AccountService`** — used internally by `AccountingService` (`resolveActiveAccount`, `resolveActiveAccountById`) to resolve accounts; part of this same module.

Per the task's framing, this module is depended upon by nearly every transactional module in the codebase; it in turn depends on almost nothing outside itself except Financial Year (for FY-open validation/FY code resolution) and Store (for stamping `storeId` on journals).

## 14. Key Operation Flows

### Create Manual Journal Entry (post)

1. **UI**: `JournalEntryForm.tsx` — user fills `journalDate`, `narration`, and 2+ line rows; client computes `totalDebit`/`totalCredit` and blocks submit unless balanced and both totals are present.
2. **Function**: `handleSubmit` builds `payloadLines: JournalLinePayload[]` (filtering out empty rows) and calls `journalApi.create({ journalDate, narration, lines: payloadLines })`.
3. **API**: `POST /api/accounting/journals` with body `JournalCreateRequest`.
4. **Controller**: `AccountingController.create(@Valid @RequestBody JournalCreateRequest request)` — `@PreAuthorize("hasRole('ADMIN')")`; bean validation runs first (`@NotNull journalDate`, `@NotEmpty @Valid lines`, each line's `@NotNull accountId`) — a failure here short-circuits into `GlobalExceptionHandler.handleValidation` before the controller body executes.
5. **DTO → Service**: controller calls `accountingService.postManualJournal(request)`.
6. **Service**: `AccountingService.postManualJournal(JournalCreateRequest)` maps each `JournalLineRequest` to a `ManualJournalLine`, then calls the 3-arg `postManualJournal(journalDate, narration, lines)`, which: (a) throws if `lines` is empty; (b) resolves each `accountId` via `accountService.findOrThrow` + active-account check (`resolveActiveAccountById`); (c) `validateResolvedLines` runs the debit=credit balance rule (§6); (d) `buildAndSaveJournal(VoucherType.JOURNAL, null, null, journalDate, narration, resolved, null)` — no `voucherId`, so no duplicate-posting check applies; the FY-open check still runs.
7. **Repository**: `buildAndSaveJournal` builds a `JournalHeader` (`status=POSTED`, `postedBy`/`createdBy` = current username, `postedAt=now()`), adds one `JournalDetail` per line via `header.addLine(...)`, and calls `journalHeaderRepository.save(header)` (cascades the lines via `CascadeType.ALL`); saves again to stamp `journalNumber = "JRN-%06d"`; saves a third time if `voucherNumber` is still null (set to `journalNumber`).
8. **Database**: one `INSERT` into `journal_headers`, N `INSERT`s into `journal_details` (via Hibernate's cascade), plus the follow-up `UPDATE`s to `journal_headers` for the number stamps — all inside the single `@Transactional` boundary.
9. **Response**: `postManualJournal(request)` wraps the saved `JournalHeader` in `JournalHeaderResponse.fromEntity(saved)`; controller returns `201 Created` with that body.
10. **UI**: `journalApi.create` resolves; `JournalEntryForm` toasts "Journal posted successfully" and navigates to `/accounting/journals`, where `Journals.tsx` will show the new row on next load.

### Reverse a Journal

There is no dedicated "reverse" UI/endpoint in the ground-truth frontend/controller files read for this module (`AccountingController` exposes only search/getById/create) — reversal is invoked *programmatically* by the owning transactional module when its source document is cancelled/revised, not by a direct user action against the accounting engine. Trace (using Sale cancellation as the example, `SaleService.cancelSale`/equivalent calling `accountingService.reverseSaleJournal(sale, reason)`):

1. **Caller service** (e.g. `SaleService`) reaches a point where a previously-posted Sale must be undone (cancel, or revise-then-repost) and calls `accountingService.reverseSaleJournal(sale, reason)` (or the equivalent `reverseJournal(VoucherType.X, id, reason)` for Purchase/Receipt/Payment/Expense/CreditNote/DebitNote/CashTransaction), inside its own `@Transactional` method.
2. **`AccountingService.reverseSaleJournal`** delegates to `reverseJournal(VoucherType.SALE, sale.getId(), reason)`.
3. **`reverseJournal`**: if `voucherId == null`, returns immediately (no-op). Otherwise looks up the currently-active original via `journalHeaderRepository.findByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(voucherType, voucherId, POSTED)`; if absent (never posted, or already reversed), returns without error (a deliberate no-op, not an exception).
4. If found: `financialYearService.resolveOpenForPosting(LocalDate.now())` re-validates the *current* date's FY is open (the reversal itself is dated today, independent of the original journal's date).
5. Builds a new `JournalHeader` (`journalDate = LocalDate.now()`, same `voucherType`/`voucherId`/`voucherNumber` as the original, `narration = reason`, `status = POSTED`, `reversalOfJournal = original`, same `store`), and for every line of the original adds a mirrored `JournalDetail` with debit and credit **swapped** (`debitAmount = originalLine.getCreditAmount()`, `creditAmount = originalLine.getDebitAmount()`) — an exact offsetting entry.
6. Saves the reversal `JournalHeader` (cascading its lines), stamps its `journalNumber`.
7. Sets `original.setStatus(JournalStatus.REVERSED)` and saves the original — all within the same `@Transactional` boundary as steps 5-6, so the new reversal journal and the original's status flip commit together.
8. **Database**: net effect — the original journal's `status` column flips to `REVERSED` (its rows are never deleted), and a brand-new `journal_headers`/`journal_details` row set exists representing the offsetting entry, permanently preserving both for audit.
9. **UI**: nothing in the ground-truth frontend triggers this directly, but `Journals.tsx`'s list/detail view will show both the original (now `REVERSED`, styled `muted`) and the new reversal journal (still `voucherType`/`voucherNumber` of the source, `POSTED`) once the caller's own cancel/revise flow completes and the user reloads the Journal Register.

### Cross-module example: Sale posting (SaleService → AccountingService)

(`SaleService` is documented in its own module doc; this traces only the call chain into the accounting engine, per `backend/src/main/java/com/storehub/service/SaleService.java` lines ~216-218.)

1. **`SaleService.createSale(...)`** (or the complete/revise variants), after validating and saving the `Sale` entity (`saved`), calls, in order: `ledgerService.recordSaleDebit(saved)`, `ledgerService.recordGstEntry(saved)`, (conditionally) `ledgerService.recordCashEntryForSale(saved)`, then **`accountingService.postSaleJournal(saved)`** — all within `SaleService`'s own `@Transactional` method, so `Sale` + `CustomerLedgerEntry`/`GstEntry`/`CashLedgerEntry` + `JournalHeader`/`JournalDetail` all commit atomically.
2. **`AccountingService.postSaleJournal(Sale sale)`**: builds a `List<JournalLine>` per the branching in §4 (customer present → debit `CUSTOMER_RECEIVABLE` for the full total + credit `SALES`/GST lines; no customer but paid → debit Cash/Bank + credit `SALES`/GST lines; no customer and unpaid → return, no journal). Calls `postJournal(VoucherType.SALE, sale.getId(), sale.getInvoiceNumber(), sale.getSaleDate(), "Sale " + sale.getInvoiceNumber(), lines, storeIdOf(sale.getStore()))`.
3. **`postJournal(..., storeId)`**: `validateJournal(lines)` runs the debit=credit balance check (§6); each `JournalLine.account()` (a `SystemAccountCode`, e.g. `SALES`, `CUSTOMER_RECEIVABLE`, `OUTPUT_CGST`) is resolved to a live `Account` via `resolveActiveAccount` → `accountService.getSystemAccount(code)` (cached lookup by `accountCode` in the `accounts` table).
4. **`buildAndSaveJournal(VoucherType.SALE, sale.getId(), sale.getInvoiceNumber(), sale.getSaleDate(), narration, resolved, storeId)`**: FY-open check on `sale.getSaleDate()`; duplicate-posting check on `(SALE, sale.getId())`; builds and saves the `JournalHeader` (`voucherType=SALE`, `voucherId=sale.getId()`, `voucherNumber=sale.getInvoiceNumber()`, `store` set if `storeId` given), adds one `JournalDetail` per resolved line (each tagged `referenceType=SALE`, `referenceId=sale.getId()`, and, for the receivable line, `partyType=CUSTOMER`/`partyId=customer.getId()`), saves, stamps `journalNumber`.
5. **Database**: one new `journal_headers` row (`voucher_type='SALE'`, `voucher_id=<sale.id>`) and its `journal_details` rows (e.g. a debit line on the Customer Receivable `accounts` row, credit lines on the Sales and Output-GST `accounts` rows) are persisted alongside the `sales` row and the `customer_ledger_entries`/`gst_entries` rows written by `LedgerService`, all in the one transaction `SaleService` opened.
6. **Response/UI**: `SaleService` returns its own `SaleResponse` to the Sale-creation UI (not documented here); separately, this posting becomes visible in the Accounting module's `Journals.tsx` (voucher type `SALE`, linked by `voucherNumber` = the sale's invoice number) and in `TrialBalance.tsx`/other reports once the transaction commits.

## 15. Manual Changes

- **Add a new Account**: use the Chart of Accounts UI (`AccountMaster.tsx` → `POST /api/accounts`) or insert via `AccountService.create`. A brand-new business account does **not** need a code change anywhere else. Only add a new `SystemAccountCode` constant (in `backend/src/main/java/com/storehub/entity/SystemAccountCode.java`) when a *new fixed account the application posts to by code* is needed — doing so requires: (1) adding the enum constant with its code/default name/`AccountType`/normal balance, (2) `AccountService.ensureSystemAccountsExist()` will auto-seed it on next startup (no manual seed script needed), (3) updating whichever `AccountingService` posting method(s) should reference it. Affected: any new `AccountingService.post*Journal` method that references the new code, and — since seeding is idempotent and additive — no other module needs to change just because a code was added.

- **Add a new `VoucherType`**: edit `backend/src/main/java/com/storehub/entity/VoucherType.java` (currently `SALE, PURCHASE, RECEIPT, PAYMENT, JOURNAL, CREDIT_NOTE, DEBIT_NOTE, EXPENSE, CASH_TRANSACTION`). This is the accounting-journal classification stamped on `JournalHeader.voucherType`/`JournalDetail.referenceType`. Affected by this change: (a) the new source-transaction service must call `accountingService.postJournal(...)`/`postJournalByAccountId(...)` with the new `VoucherType`; (b) `journalHeaderRepository`'s duplicate-posting guard (`existsByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull`) automatically covers it since it's parameterized by `VoucherType`; (c) frontend `types/accounting.ts`'s `VoucherType` union and `Journals.tsx`'s `VOUCHER_TYPES` filter array should be extended to keep the UI filter in sync (note: as of this read, the frontend union is already missing `EXPENSE` and `CASH_TRANSACTION`, which the backend enum already has — see discrepancy below).

- **Add a new `VoucherDocType`** (a new independently-numbered document series): edit `backend/src/main/java/com/storehub/entity/VoucherDocType.java`, adding the enum constant with its prefix string (e.g. `MY_DOC("MD")`). No other code change is required for numbering itself — `VoucherNumberService.next(docType, date)` and `VoucherSequenceRepository.ensureRowExists`/`lockForUpdate` are fully generic over `VoucherDocType`; a new counter row is created lazily on first use per `(docType, financialYearId)`. The new document-creating service must call `voucherNumberService.next(VoucherDocType.MY_DOC, date)` when assigning its number.

- **Change the voucher numbering format**: edit the single `return` statement in `VoucherNumberService.next(...)` (`docType.getPrefix() + "/" + fy.getCode() + "/" + String.format("%06d", seq.getLastNumber())`). Because every voucher-numbered module funnels through this one method, a format change here affects **every** transactional module's document numbers simultaneously (Sale, Purchase, SalesOrder, PurchaseOrder, Receipt, Payment, CreditNote, DebitNote, Expense, CashTransaction, StockTransfer) — there is no per-module override. Changing the digit-padding (`%06d`) or separator changes all of them at once; a module-specific prefix change instead belongs in that `VoucherDocType` constant's own `prefix` string.

- **Change the debit=credit validation**: edit `AccountingService.validateAmounts(...)` (§6). This one private method is the sole enforcement point for every posting path (`postJournal`, `postManualJournal`, `postJournalByAccountId`) — loosening or tightening it (e.g. allowing a zero-total journal, or requiring line-level party tagging) affects every caller module listed in §13 uniformly, since none of them perform their own balance check before calling in (the frontend's `JournalEntryForm.tsx` client-side check is a UX convenience only and is not authoritative — the backend re-validates independently).

- **Change `AccountingService`'s public method signatures** (e.g. `postSaleJournal(Sale sale)`, `postJournal(...)`, `postJournalByAccountId(...)`): because these are called directly, by name, from `SaleService`, `PurchaseService`, `ReceiptService`, `PaymentService`, `ExpenseService`, `CashTransactionService`, `CreditNoteService`, and `DebitNoteService` (§13), any signature change (new required parameter, renamed method, changed return type) requires updating every one of those 8 call sites. The generic `postJournal`/`postJournalByAccountId`/`reverseJournal` overloads are the lowest-risk to extend (add a new optional overload rather than changing an existing signature) since `CashTransactionService`, `CreditNoteService`, `DebitNoteService`, and `ExpenseService` already depend on their exact current shapes.

- **Frontend/backend `VoucherType` discrepancy found while documenting**: `frontend/src/types/accounting.ts`'s `VoucherType` union (`'SALE' | 'PURCHASE' | 'RECEIPT' | 'PAYMENT' | 'JOURNAL' | 'CREDIT_NOTE' | 'DEBIT_NOTE'`) and `Journals.tsx`'s `VOUCHER_TYPES` filter array both omit `EXPENSE` and `CASH_TRANSACTION`, which exist in the backend `VoucherType` enum and are actively posted to by `ExpenseService`/`CashTransactionService`. This does not break posting or viewing an individual Expense/CashTransaction journal (the `JournalHeader` object still carries whatever `voucherType` string the backend returns and TypeScript's structural typing does not reject it at runtime), but the Journals page's voucher-type filter dropdown has no option to filter specifically for `EXPENSE` or `CASH_TRANSACTION` journals.
