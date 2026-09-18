# Expense, Cash Management, Day Closing, Payment Method, Financial Year & Audit Trail Module

## 1. Overview

**Expense** — records operational business expenses (rent, electricity, transport, etc.). `DRAFT` has zero accounting effect; `POST` debits the Expense account (category's linked account, or the generic `SystemAccountCode.EXPENSES`) plus Input GST accounts when ITC-eligible, and credits either Cash/Bank or Supplier Payable (for a credit/party expense); `CANCEL` reverses the journal (idempotent). Backed by `backend/src/main/java/com/storehub/entity/Expense.java`, `backend/src/main/java/com/storehub/service/ExpenseService.java`.

**Cash Management** — ad-hoc Cash In / Cash Out entries for money that moves through Cash/Bank without a customer/supplier bill (petty cash, other income, owner's contribution). Posts through the same `AccountingService.postJournal` every other voucher uses; the existing Cash Book/Bank Book (`CashBankBookService`) reads straight off the journal — no separate balance is kept in `CashTransaction` itself. Backed by `backend/src/main/java/com/storehub/entity/CashTransaction.java`, `backend/src/main/java/com/storehub/service/CashTransactionService.java`.

**Day Closing** — one record per calendar date, computing expected cash (read live from `CashBankBookService.cashBook`, never independently calculated) versus the actual cash counted by the operator; requires an explicit reason when they differ. It is a record only — closing a day never adjusts accounting balances; a real shortage/overage needs an explicit `CashTransaction` (Cash Adjustment) if the business wants it reflected in the ledger. Backed by `backend/src/main/java/com/storehub/entity/DayClosing.java`, `backend/src/main/java/com/storehub/service/DayClosingService.java`.

**Payment Method** — a configurable, orderable master list of payment options a store accepts (Cash, Bank Transfer, UPI, Card, ...). Deliberately reference/configuration data only: it labels and orders the existing `PaymentMode` enum values, which is what every Sale/Purchase/Receipt/Payment/Expense actually posts against via `AccountingService.resolveCashOrBank`; adding a Payment Method row does not rewire that posting logic. Backed by `backend/src/main/java/com/storehub/entity/PaymentMethod.java`, `backend/src/main/java/com/storehub/service/PaymentMethodService.java`.

**Financial Year** — an Indian financial year (1-Apr to 31-Mar, e.g. code "26-27"). The single source of truth for "which FY does this date belong to, and is it OPEN". Every posting path resolves and gates on this before posting a journal. A `@PostConstruct` startup hook auto-seeds a FinancialYear row covering "today" if none exists. Backed by `backend/src/main/java/com/storehub/entity/FinancialYear.java`, `backend/src/main/java/com/storehub/service/FinancialYearService.java`.

**Audit Trail** — an append-only log of every important business action across the whole application (who did what, when, from where). `AuditService.log(...)` is the one place every audited action is recorded; it is a CENTRAL/COMMON service used by nearly every module (18 service files call it), not exclusive to this module — see section 11. Backed by `backend/src/main/java/com/storehub/entity/AuditLog.java`, `backend/src/main/java/com/storehub/service/AuditService.java`.

## 2. Frontend

### Files and Components

| Sub-area | File | Purpose |
|---|---|---|
| Expense | `frontend/src/pages/accounting/Expenses.tsx` | List/search page with filters (status, category, party, payment mode, GST, ITC, date range) |
| Expense | `frontend/src/pages/accounting/ExpenseForm.tsx` | Create/edit form (DRAFT only for edit); cash vs. credit-expense toggle, GST preview |
| Expense | `frontend/src/pages/accounting/ExpenseDetail.tsx` | Detail view with Post/Cancel/Edit/Print actions |
| Expense | `frontend/src/pages/masters/ExpenseCategoryMaster.tsx` | CRUD for Expense Category master, uses generic `MasterCrudPage` |
| Expense reports | `frontend/src/pages/accounting/reports/ExpenseAnalysis.tsx` | Category/payment-mode/party/GST-ITC totals from `expenseApi.summary` (`ExpenseRepository` GROUP BY aggregations) |
| Expense reports | `frontend/src/pages/accounting/reports/ExpenseSummary.tsx` | Thin wrapper around `ExpenseIncomeSummaryView`, fed by `accountingReportApi.expenseSummary` — a different, Chart-of-Accounts-account-grouped report (Accounting Core, not `ExpenseRepository`) |
| Expense reports | `frontend/src/pages/accounting/reports/IncomeSummary.tsx` | Same shared view, fed by `accountingReportApi.incomeSummary` |
| Expense reports | `frontend/src/pages/accounting/reports/ExpenseIncomeSummaryView.tsx` | Shared presentational component for both of the above (accordion of account groups + posted vouchers) |
| Cash Management | `frontend/src/pages/accounting/CashTransactions.tsx` | List/search page (type, status filters) |
| Cash Management | `frontend/src/pages/accounting/CashTransactionForm.tsx` | Create Cash In / Cash Out form |
| Cash Management | `frontend/src/pages/accounting/CashTransactionDetail.tsx` | Detail view with Post/Cancel actions |
| Day Closing | `frontend/src/pages/accounting/DayClosingPage.tsx` | Live summary (sales/purchases/cash-by-mode/receipts/payments/expenses/cash-in-out), expected-vs-actual reconciliation, close action, closing history table |
| Payment Method | `frontend/src/pages/accounting/PaymentMethods.tsx` | ADMIN-only CRUD list with activate/deactivate |
| Financial Year | `frontend/src/pages/admin/FinancialYears.tsx` | List, create, open/close, mark-current, per-year summary dialog |
| Audit Trail | `frontend/src/pages/admin/AuditTrail.tsx` | Read-only, filterable/searchable log table, ADMIN only |

### Frontend Flow

**Expense**: `Expenses.tsx` loads `expenseCategoryApi.listActive()` and `supplierApi.list()` for filter dropdowns, then `expenseApi.list(query)` on mount/filter-change; row click navigates to `ExpenseDetail`. `ExpenseForm.tsx` toggles "credit expense" (party/Supplier picked) vs. cash/bank (vendor free-text + payment mode); computes a live GST preview client-side (CGST/SGST split on Intra-State, IGST on Inter-State) purely for display — the server recomputes authoritatively. Submits via `expenseApi.create`/`update` with `post: boolean` to optionally post in the same call. `ExpenseDetail.tsx` exposes Post/Cancel/Edit(DRAFT only)/Print gated by `canManage = role ADMIN|STORE_MANAGER` (note: this is a frontend-only convenience gate; the real authorization is the backend's `@PreAuthorize`).

**Cash Management**: `CashTransactions.tsx` lists/filters; `CashTransactionForm.tsx` is a simple type/mode/amount/reason form with Save-as-Draft/Save-and-Post; `CashTransactionDetail.tsx` mirrors the Expense detail pattern (Post/Cancel).

**Day Closing**: `DayClosingPage.tsx` calls `dayClosingApi.summary(date)` whenever `date` changes (always live-computed, whether or not already closed) and `dayClosingApi.history()` once. The UI computes `difference = actualCash - expectedCash` client-side to decide whether to show/require the Difference Reason textarea before enabling Close; the server independently re-validates the same rule.

**Payment Method**: `PaymentMethods.tsx` — ADMIN-only page; list + modal form (create/edit) + activate/deactivate toggle via dropdown menu.

**Financial Year**: `FinancialYears.tsx` — table with per-row actions (View Summary / Mark as Current / Close / Re-open), all ADMIN-gated in the UI; create dialog with optional name/code (server derives if blank).

**Audit Trail**: `AuditTrail.tsx` — filterable/paginated read-only table (search, action-type filter); no create/edit/delete UI exists, matching the append-only backend.

## 3. API Calls

### Expense

| Function | Method | URL | Request | Response |
|---|---|---|---|---|
| `expenseApi.list` | GET | `/expenses` | query params: search, status, category, categoryId, supplierId, paymentMode, gstApplicable, itcEligible, fromDate, toDate, page, size | `PagedResponse<Expense>` |
| `expenseApi.getById` | GET | `/expenses/{id}` | — | `Expense` |
| `expenseApi.create` | POST | `/expenses` | `ExpenseCreatePayload` | `Expense` |
| `expenseApi.update` | PUT | `/expenses/{id}` | `ExpenseUpdatePayload` | `Expense` |
| `expenseApi.post` | POST | `/expenses/{id}/post` | — | `Expense` |
| `expenseApi.cancel` | PATCH | `/expenses/{id}/cancel` | — | `Expense` |
| `expenseApi.summary` | GET | `/expenses/reports/summary` | `fromDate`, `toDate` | `ExpenseSummaryReportResponse` |
| `expenseCategoryApi.list/create/update/activate/deactivate` | GET/POST/PUT/PATCH | `/masters/expense-categories[...]` | `ExpenseCategoryRequest` (write) | `ExpenseCategoryResponse` |

### Cash Management

| Function | Method | URL | Request | Response |
|---|---|---|---|---|
| `cashTransactionApi.list` | GET | `/cash-transactions` | search, transactionType, status, fromDate, toDate, page, size | `PagedResponse<CashTransaction>` |
| `cashTransactionApi.getById` | GET | `/cash-transactions/{id}` | — | `CashTransaction` |
| `cashTransactionApi.create` | POST | `/cash-transactions` | `CashTransactionCreatePayload` | `CashTransaction` |
| `cashTransactionApi.post` | POST | `/cash-transactions/{id}/post` | — | `CashTransaction` |
| `cashTransactionApi.cancel` | PATCH | `/cash-transactions/{id}/cancel` | — | `CashTransaction` |

### Day Closing

| Function | Method | URL | Request | Response |
|---|---|---|---|---|
| `dayClosingApi.summary` | GET | `/day-closing/summary` | `date` | `DayClosingSummary` |
| `dayClosingApi.history` | GET | `/day-closing/history` | — | `DayClosingSummary[]` |
| `dayClosingApi.close` | POST | `/day-closing/close` | `DayClosingCloseInput` | `DayClosingSummary` |

### Payment Method

| Function | Method | URL | Request | Response |
|---|---|---|---|---|
| `paymentMethodApi.listAll` | GET | `/payment-methods` | — | `PaymentMethod[]` |
| `paymentMethodApi.listActive` | GET | `/payment-methods?activeOnly=true` | — | `PaymentMethod[]` |
| `paymentMethodApi.create` | POST | `/payment-methods` | `PaymentMethodPayload` | `PaymentMethod` |
| `paymentMethodApi.update` | PUT | `/payment-methods/{id}` | `PaymentMethodPayload` | `PaymentMethod` |
| `paymentMethodApi.activate` | PATCH | `/payment-methods/{id}/activate` | — | `PaymentMethod` |
| `paymentMethodApi.deactivate` | PATCH | `/payment-methods/{id}/deactivate` | — | `PaymentMethod` |

### Financial Year

| Function | Method | URL | Request | Response |
|---|---|---|---|---|
| `financialYearApi.list` | GET | `/financial-years` | — | `FinancialYear[]` |
| `financialYearApi.getCurrent` | GET | `/financial-years/current` | — | `FinancialYear` |
| `financialYearApi.getById` | GET | `/financial-years/{id}` | — | `FinancialYear` |
| `financialYearApi.getSummary` | GET | `/financial-years/{id}/summary` | — | `FinancialYearSummary` |
| `financialYearApi.create` | POST | `/financial-years` | `FinancialYearCreatePayload` | `FinancialYear` |
| `financialYearApi.open` | PATCH | `/financial-years/{id}/open` | — | `FinancialYear` |
| `financialYearApi.close` | PATCH | `/financial-years/{id}/close` | — | `FinancialYear` |
| `financialYearApi.markCurrent` | PATCH | `/financial-years/{id}/mark-current` | — | `FinancialYear` |

### Audit Trail

| Function | Method | URL | Request | Response |
|---|---|---|---|---|
| `auditLogApi.list` | GET | `/audit-logs` | action, module, entityType, entityId, userId, search, fromDate, toDate, page, size | `PagedResponse<AuditLog>` |

Note: the backend `AuditLogController` also accepts a `storeId` query param not currently sent by `auditLogApi.list` (frontend never filters by store on this page).

## 4. Backend

### Controllers

**`ExpenseController`** (`/api/expenses`): `GET` (search, `PERM_EXPENSE_VIEW`) · `GET /reports/summary` (`PERM_EXPENSE_VIEW`) · `GET /{id}` (`PERM_EXPENSE_VIEW`) · `POST` (create, `PERM_EXPENSE_CREATE`) · `PUT /{id}` (update, `PERM_EXPENSE_CREATE`) · `POST /{id}/post` (`PERM_EXPENSE_POST`) · `PATCH /{id}/cancel` (`PERM_EXPENSE_CANCEL`).

**`ExpenseCategoryController`** (`/api/masters/expense-categories`): `GET` (list, no explicit `@PreAuthorize` — any authenticated user) · `GET /active` (no explicit auth) · `GET /{id}` (no explicit auth) · `POST` (`PERM_MASTER_MANAGE`) · `PUT /{id}` (`PERM_MASTER_MANAGE`) · `PATCH /{id}/activate` (`PERM_MASTER_MANAGE`) · `PATCH /{id}/deactivate` (`PERM_MASTER_MANAGE`).

**`CashTransactionController`** (`/api/cash-transactions`): class-level `@PreAuthorize("hasAuthority('PERM_CASH_MANAGE')")` covers all its endpoints — `GET`, `GET /{id}`, `POST`, `POST /{id}/post`, `PATCH /{id}/cancel`.

**`DayClosingController`** (`/api/day-closing`): `GET /summary` (`PERM_CASH_MANAGE`) · `GET /history` (`PERM_CASH_MANAGE`) · `POST /close` (`PERM_CASH_MANAGE`).

**`PaymentMethodController`** (`/api/payment-methods`): `GET` (no explicit auth — any authenticated user, `activeOnly` query flag) · `POST` (`hasRole('ADMIN')`) · `PUT /{id}` (`hasRole('ADMIN')`) · `PATCH /{id}/activate` (`hasRole('ADMIN')`) · `PATCH /{id}/deactivate` (`hasRole('ADMIN')`).

**`FinancialYearController`** (`/api/financial-years`): `GET` (no explicit auth) · `GET /current` (no explicit auth) · `GET /{id}` (no explicit auth) · `GET /{id}/summary` (no explicit auth) · `POST` (`PERM_FY_MANAGE`) · `PATCH /{id}/open` (`PERM_FY_MANAGE`) · `PATCH /{id}/close` (`PERM_FY_MANAGE`) · `PATCH /{id}/mark-current` (`PERM_FY_MANAGE`). Comment in source: "Any authenticated user may VIEW financial years... only ADMIN may mutate them" (mutation is actually gated by `PERM_FY_MANAGE`, which only `Role.ADMIN` holds per `RolePermissions`).

**`AuditLogController`** (`/api/audit-logs`): class-level `@PreAuthorize("hasAuthority('PERM_AUDIT_VIEW')")`, read-only — `GET` (search) only. No POST/PUT/DELETE exists anywhere in the audit trail API surface; comment: "the audit trail is append-only and never editable/deletable via the API".

### DTOs

**Expense**
- `ExpenseCreateRequest` — `expenseDate` (`@NotNull`), `storeId` (optional), `categoryId` (preferred) or `category` (legacy free text), `supplierId` (optional, makes it a credit expense), `vendorName`, `paymentMode` (`@NotNull`), `taxMode`, `gstPercent` (`@DecimalMin("0")`), `itcEligible` (boolean), `grossAmount`, `discountAmount` (`@DecimalMin("0")`), `taxableAmount` (used only if `grossAmount` absent), `description`, `referenceNumber`, `remarks`, `post` (boolean).
- `ExpenseUpdateRequest` — identical shape to create (no `storeId`/`categoryId` swap logic difference); only usable while the Expense is `DRAFT`.
- `ExpenseResponse` — full read model incl. `categoryId`, `storeId/storeName/storeCode`, `supplierId/supplierGstin`, tax breakdown (`cgstAmount/sgstAmount/igstAmount`), `paidAmount/payableAmount/paymentStatus`, `status`, audit fields (`createdBy/postedBy/postedAt/cancelledBy/cancelledAt/createdAt/updatedAt`).
- `ExpenseCategoryRequest` — `code` (`@NotBlank`), `name` (`@NotBlank`), `linkedAccountId` (optional), `description`.
- `ExpenseCategoryResponse` — incl. `linkedAccountId/linkedAccountName`, `active` + a mirrored `status` string ("ACTIVE"/"INACTIVE") for the generic `MasterCrudPage` component.
- `ExpenseSummaryGroupRow` — `key`, `label`, `totalAmount`, `count` (used for category/payment-mode/party groupings).
- `ExpenseSummaryReportResponse` — `fromDate/toDate`, `totalAmount`, `totalCount`, `byCategory`/`byPaymentMode`/`byParty` (`List<ExpenseSummaryGroupRow>`), `gstApplicableAmount`, `nonGstAmount`, `itcEligibleTax`, `itcIneligibleTax`.
- `ExpenseIncomeSummaryResponse` / `ExpenseIncomeAccountGroup` / `ExpenseIncomeVoucherRow` — used by the separate, Accounting-Core-driven Expense/Income Summary reports (account-grouped, not `ExpenseRepository`-grouped); populated by `AccountingReportService`, not `ExpenseService`.

**Cash Management**
- `CashTransactionCreateRequest` — `transactionDate` (`@NotNull`), `storeId` (optional), `transactionType` (`@NotNull`), `paymentMode` (`@NotNull`), `amount` (`@NotNull @DecimalMin("0.01")`), `reason` (`@NotBlank`), `post` (boolean).
- `CashTransactionResponse` — full read model incl. `storeId/storeName/storeCode`, `status`, audit fields.
- `CashBankBookResponse`/`CashBankBookRow` — belong to `CashBankBookService` (Phase 4), reused by `DayClosingService` to compute `expectedCash`; not owned by `CashTransactionService`.

**Day Closing**
- `DayClosingCloseRequest` — `closingDate` (`@NotNull`), `actualCash` (`@NotNull`), `differenceReason` (optional at DTO level; enforced conditionally in the service when `difference != 0`).
- `DayClosingResponse` — every summary figure (`totalSales/totalPurchases/cashSales/cardSales/upiSales/creditSales/totalReceipts/totalPayments/totalExpenses/cashIn/cashOut/expectedCash`) plus, once closed, `actualCash/difference/differenceReason/closedBy/closedAt` and `closed: boolean`.

**Payment Method**
- `PaymentMethodRequest` — `name` (`@NotBlank`), `type` (`PaymentMode`, `@NotNull`), `sortOrder` (optional).
- `PaymentMethodResponse` — `id/name/type/active/sortOrder/createdAt/updatedAt`.

**Financial Year**
- `FinancialYearRequest` — `name` (optional, auto-derived), `code` (optional, auto-derived), `startDate` (`@NotNull`), `endDate` (`@NotNull`).
- `FinancialYearResponse` — `id/name/code/startDate/endDate/status/current/createdAt/updatedAt`.
- `FinancialYearSummaryResponse` — `financialYear` (nested `FinancialYearResponse`), `totalSales/totalPurchases/saleCount/purchaseCount`.

**Audit Trail**
- `AuditLogResponse` — `id/userId/username/action/module/entityType/entityId/storeId/storeName/storeCode/documentNumber/oldValue/newValue/timestamp/ipAddress/userAgent/description`. No request DTO exists — writes only happen internally via `AuditService.log(...)`, never through a controller-exposed request body.

### Services

**`ExpenseService`** — `create`: resolves the caller's effective store (`StoreAccessService.resolveEffectiveStoreId`), rejects an INACTIVE store, builds a `DRAFT` Expense via `applyRequest` (resolves category/supplier, computes `taxable = gross - discount` or uses raw `taxableAmount`, calls `GstCalculationService.calculateLine` for CGST/SGST/IGST split, requires `taxMode` when GST > 0), assigns the voucher number via `VoucherNumberService.next(EXPENSE, date)`, audit-logs `CREATE`, optionally posts immediately if `request.isPost()`. `update`: only allowed while `status == DRAFT`; re-runs `applyRequest`; audit-logs `UPDATE`. `post`: only from `DRAFT`; computes the Expense-account debit line (`expenseLineAmount` = taxable-only if ITC-eligible, else taxable+tax, since a non-recoverable tax is expensed); adds Input CGST/SGST/IGST debit lines when ITC-eligible; if `supplier != null` (credit expense) credits `SUPPLIER_PAYABLE` party-tagged to that Supplier and calls `LedgerService.recordExpenseCredit` (writes a `SupplierLedgerEntry`, NOT a `CashLedgerEntry`) and sets `paidAmount=0/payableAmount=total/paymentStatus=UNPAID`; else credits Cash/Bank (`AccountingService.resolveCashOrBank(paymentMode)`) and sets `paidAmount=total/payableAmount=0/paymentStatus=PAID`; posts via `AccountingService.postJournalByAccountId(VoucherType.EXPENSE, ...)`; syncs GST reporting via `GstTransactionSyncService.syncExpense`; audit-logs `POST`. `cancel`: idempotent no-op if already `CANCELLED`; refuses to cancel if any `PaymentAllocation` already exists against it (payments must be deleted first); if `POSTED`, reverses the journal (`AccountingService.reverseJournal`), reverses the supplier-ledger credit if applicable (`reverseExpenseCredit`), and reverses the GST sync row (`gstTransactionSyncService.reverseExpense`); resets `paidAmount/payableAmount` to 0, sets `status=CANCELLED`; audit-logs `CANCEL`. `resolveExpenseAccountId`: uses the category's `linkedAccount` if set and active, else falls back to `SystemAccountCode.EXPENSES`.

**`ExpenseCategoryService`** — plain CRUD with uniqueness checks on `code`/`name` (case-insensitive); `setActive` is the only "delete" path (never a hard delete — an already-referenced category must stay visible on historical expenses); `findActiveOrThrow` additionally rejects an inactive category when used on a new/edited Expense.

**`CashTransactionService`** — `create`: same store-resolution/INACTIVE-store guard pattern as Expense; builds `DRAFT` `CashTransaction`, numbers it via `VoucherNumberService.next(CASH_TRANSACTION, date)`, audit-logs `CREATE`, optional immediate post. `post`: builds two journal lines depending on type — `CASH_IN` debits Cash/Bank and credits `OTHER_INCOME`; `CASH_OUT` debits `EXPENSES` and credits Cash/Bank; posts via `AccountingService.postJournal` (not `postJournalByAccountId` — this uses the simpler `JournalLine` helper, unlike Expense which needs party-tagging); audit-logs `POST`. `cancel`: idempotent; reverses the journal if `POSTED`; audit-logs `CANCEL`. No credit/party support exists for Cash Transactions (always cash/bank, never a payable).

**`DayClosingService`** — `computeSummary(date)` (read-only, safe to call repeatedly/preview): pulls `totalSales`/`byMode` from `SaleRepository`, `totalPurchases` from `PurchaseRepository`, `totalReceipts`/`totalPayments` from `ReceiptRepository`/`PaymentRepository`, `totalExpenses` from `ExpenseRepository.getTotalExpensesForDate`, `cashIn`/`cashOut` from `CashTransactionRepository.sumForDateAndType`, and critically `expectedCash` = `cashBankBookService.cashBook(date, date).getClosingBalance()` — the exact same Cash Book every other page reads, so it can never drift from the accounting journal. If a `DayClosing` row already exists for that date, wraps the computed figures with the persisted `actualCash/difference/differenceReason/closedBy/closedAt` via `DayClosingResponse.fromClosed`. `history()` re-runs `computeSummary` for every historical `DayClosing` row (not a cached snapshot). `close(request)`: rejects if `existsByClosingDate` already true (one close per date, ever); recomputes the summary fresh; computes `difference = actualCash - expectedCash`; **if `difference != 0` and `differenceReason` is null/blank, throws `BadRequestException`** — this is the difference-reason-audit requirement; resolves and stamps `financialYearId` via `FinancialYearService.resolveForDate`; saves the `DayClosing`; audit-logs `DAY_CLOSE` with the difference amount/reason embedded in the description when non-zero. Closing never blocks a later backdated transaction itself — that gate is Financial Year open/closed status, per the class Javadoc.

**`PaymentMethodService`** — simple CRUD: `create`/`update` check name uniqueness (case-insensitive); `setActive` is the only deactivation path (never hard-deleted, so historical transactions still display the method they used).

**`FinancialYearService`** — `@PostConstruct ensureCurrentFinancialYearExists()`: idempotent startup hook — if no FY row covers `LocalDate.now()`, builds and saves one (via `buildFor`, using `FinancialYearUtil.startOf/endOf/shortCode/label` for the 1-Apr–31-Mar Indian convention) with `status=OPEN, current=true`. `resolveForDate(date)`: looks up the FY containing `date`; throws `BadRequestException` ("Ask an ADMIN to create it") if none exists — does NOT require the FY to be OPEN (used for numbering/reporting on historical/closed-year documents). `resolveOpenForPosting(date)`: calls `resolveForDate` then additionally throws if `status == CLOSED` — this is the one real posting gate, called from `AccountingService.buildAndSaveJournal` (and its reversal path) before every journal post, so FY enforcement lives in exactly one place. `create`: validates `endDate > startDate`, auto-derives `code`/`name` if blank, rejects a duplicate code and any overlapping date range (`countOverlapping`); audit-logs `CREATE`. `setStatus(id, status)`: refuses to `CLOSED` the currently-`current` FY ("Mark another year current first"); audit-logs `FY_CLOSE` or `FY_OPEN` with old/new status values. `markCurrent(id)`: refuses on a `CLOSED` target; unmarks whichever other FY was `current` (at most one `current=true` row ever, enforced here in application code, not a DB constraint); audit-logs `UPDATE`. `summary(id)`: aggregates `SaleRepository`/`PurchaseRepository` totals+counts over the FY's date range.

**`AuditService`** — the one place every audited action is recorded. Two overloads:
- `log(AuditAction action, String module, String entityType, Long entityId, String documentNumber, String oldValue, String newValue, String description)` — delegates to the 9-arg overload with `storeId = null`.
- `log(AuditAction action, String module, String entityType, Long entityId, String documentNumber, String oldValue, String newValue, String description, Long storeId)` — the real implementation: resolves the current user via `SecurityUtil.currentUserOrNull()` (username = "System" if null), builds and saves an `AuditLog` row with `userId/username`, `action`, `module`, `entityType`, `entityId`, `store` (via `storeRepository.getReferenceById(storeId)` when non-null), `documentNumber`, `oldValue`, `newValue`, `ipAddress` (from `X-Forwarded-For` header, falling back to `request.getRemoteAddr()`), `userAgent` (from the `User-Agent` header), `description`. Runs `@Transactional` with default propagation (deliberately NOT `REQUIRES_NEW` like `VoucherNumberService`) — an audit entry for an action that later rolls back rolls back with it. Wraps the whole body in try/catch and only `log.warn(...)`s on failure — a transient audit-write problem never blocks the business operation it describes.

### Repository

**Expense**: `ExpenseRepository` — `search(...)` (dynamic JPQL over search/status/category/categoryId/supplierId/paymentMode/gstApplicable/itcEligible/fromDate/toDate/storeId), `findPostedIds`, `findAllIds`, `getTotalExpensesForDate`, `sumTotalAmountByDateRange`, `findOutstandingBySupplier` (POSTED credit expenses with `payableAmount > 0`), plus report aggregations `sumByCategory`/`sumByPaymentMode`/`sumByParty`/`gstSummary` (all server-side GROUP BY — no per-row loading). **`ExpenseCategoryRepository`** — `existsByCodeIgnoreCase(AndIdNot)`, `existsByNameIgnoreCase(AndIdNot)`, `findByActiveTrueOrderByNameAsc`, `search`.

**Cash Management**: `CashTransactionRepository` — `search(...)`, `sumForDateAndType(date, type)` (used by `DayClosingService`). **`CashLedgerEntryRepository`** — `findByReferenceTypeAndReferenceId`, `getBalanceForMode`; this ledger is a Receipt-side (customer money in) movement log per its Javadoc — it is NOT written to by `CashTransactionService` or `ExpenseService` (see section 5 relationships).

**Day Closing**: `DayClosingRepository` — `existsByClosingDate`, `findByClosingDate`, `findAllByOrderByClosingDateDesc`.

**Payment Method**: `PaymentMethodRepository` — `existsByNameIgnoreCase(AndIdNot)`, `findByActiveTrueOrderBySortOrderAscNameAsc`, `findAllByOrderBySortOrderAscNameAsc`.

**Financial Year**: `FinancialYearRepository` — `existsByCodeIgnoreCase`, `findByCodeIgnoreCase`, `findByCurrentTrue`, `findByDate(date)` (`WHERE :date BETWEEN startDate AND endDate`), `findAllByOrderByStartDateDesc`, `countOverlapping`.

**Audit Trail**: `AuditLogRepository` — `search(...)` only (dynamic JPQL over action/module/entityType/entityId/userId/storeId/search/fromDate/toDate). No update/delete method exists anywhere on this repository — enforced append-only at the code level.

## 5. Database

### Tables

| Sub-area | Table |
|---|---|
| Expense | `expenses`, `expense_categories` |
| Cash Management | `cash_transactions`, `cash_ledger_entries` |
| Day Closing | `day_closings` |
| Payment Method | `payment_methods` |
| Financial Year | `financial_years` |
| Audit Trail | `audit_logs` (indexes: `idx_audit_logs_entity(entity_type, entity_id)`, `idx_audit_logs_timestamp(timestamp)`, `idx_audit_logs_user(user_id)`) |

### Relationships

- **`expenses.expense_category_id` → `expense_categories.id`** (`Expense.expenseCategory`, `ManyToOne`, nullable — null on legacy free-text-category rows). `expense_categories.linked_account_id → accounts.id` (optional Chart-of-Accounts mapping).
- **`expenses.supplier_id` → suppliers.id`** (`Expense.supplier`, `ManyToOne`, nullable — set only for a credit expense; reuses the existing Supplier master, never a separate vendor entity). A credit expense's payable is tracked via a `SupplierLedgerEntry` row (written by `LedgerService.recordExpenseCredit`/`reverseExpenseCredit`, `referenceType=EXPENSE`), not by `CashLedgerEntry`.
- **`expenses.store_id` → stores.id`** (Multi-Store — fixed at creation, `ManyToOne`).
- **`cash_transactions.store_id` → stores.id`** (fixed at creation).
- **`cash_ledger_entries`** has NO foreign key to `cash_transactions` — it is a Receipt-side ledger (`referenceType`/`referenceId` polymorphic reference, one row per Receipt movement) per its class Javadoc; `CashTransactionService` never writes to it. The Cash Book/Bank Book instead reads straight off `journal_headers`/`journal_lines` (Accounting Core), which is what `CashTransactionService.post` and `ExpenseService.post` actually write to via `AccountingService`.
- **`day_closings.financial_year_id`** — a plain `Long` column (no JPA `@ManyToOne`/FK), set from `FinancialYearService.resolveForDate(closingDate).getId()` at close time; same denormalized-FK-by-id pattern used by `expenses.financial_year_id` and `cash_transactions.financial_year_id`.
- **`audit_logs.store_id` → stores.id`** (`AuditLog.store`, `ManyToOne`, nullable — null for a global/system-level action). **`audit_logs.user_id`** is a plain `Long`, not a JPA relationship to `User` — `AuditLogResponse` carries a denormalized `username` snapshot alongside it, so a row remains readable even if the user is later deleted/deactivated.
- **`payment_methods`** has no foreign keys — it is a standalone label/order master over the `PaymentMode` enum, not wired into any posting relationship.
- **`financial_years`** has no foreign keys; every other entity in this module (`expenses`, `cash_transactions`, `day_closings`) stores `financial_year_id` as a plain denormalized long, resolved on write and never re-tagged later.

## 6. Validation

**Expense**: `expenseDate` and `paymentMode` required (`@NotNull`); `gstPercent`/`discountAmount` must be `>= 0` (`@DecimalMin`); category is required — resolved from `categoryId` (must be an active `ExpenseCategory`, else `BadRequestException`) or the legacy `category` free-text field, and a blank result throws `BadRequestException("Category is required")`; an inactive Supplier cannot be used (`BadRequestException`); an inactive Store cannot be used (`BadRequestException`); taxable amount (computed as `gross - discount`, or the raw `taxableAmount` if no `grossAmount` sent) must be `> 0` else `BadRequestException("Taxable amount must be greater than 0")`; `taxMode` is required whenever the computed GST amount is `> 0` (`BadRequestException`); update is only permitted while `status == DRAFT`; post is only permitted from `DRAFT`; cancel is blocked while any `PaymentAllocation` exists against the expense.

**Cash Management**: `transactionDate`, `transactionType`, `paymentMode` required (`@NotNull`); `amount` required and `@DecimalMin("0.01")` (must be `> 0`); `reason` required (`@NotBlank`); an inactive Store cannot be used; post only from `DRAFT`; cancel is idempotent (no error re-cancelling).

**Day Closing**: `closingDate` and `actualCash` required (`@NotNull` at DTO level); a date can only be closed once (`BadRequestException` if `existsByClosingDate`); **`differenceReason` is conditionally required** — enforced in `DayClosingService.close`, not by a DTO annotation: if `actualCash - expectedCash != 0` and `differenceReason` is null/blank, throws `BadRequestException` with the exact expected/actual/difference figures in the message.

**Payment Method**: `name` required (`@NotBlank`) and must be unique case-insensitively (create and update, excluding self on update); `type` (`PaymentMode`) required (`@NotNull`).

**Financial Year**: `startDate`/`endDate` required (`@NotNull`); service-level: `endDate` must be strictly after `startDate`; `code` must be unique case-insensitively; the new range must not overlap any existing FY (`countOverlapping`); closing the currently-`current` FY is blocked ("Mark another year current first"); marking a `CLOSED` FY as current is blocked ("Re-open it first").

**Audit Trail**: no write-side validation exists — this module only exposes a read/search endpoint; write path (`AuditService.log`) has no validation, only a try/catch that swallows failures.

## 7. Authentication / Authorization

All endpoints in this module require an authenticated request (standard Spring Security filter chain, JWT-based per `01-authentication-authorization.md`). Endpoint-level authorization is enforced via `@PreAuthorize("hasAuthority('PERM_...')")` (or, for Payment Method, `hasRole('ADMIN')`) — see section 8 for the exact permission-to-endpoint mapping. Several read endpoints (Expense Category list/active/getById, Payment Method list, Financial Year list/current/getById/summary) carry no explicit `@PreAuthorize`, meaning any authenticated user can read them (financial years in particular must be visible everywhere per the controller's own comment). Multi-Store access is additionally enforced in the service layer for Expense and Cash Transaction: `StoreAccessService.resolveEffectiveStoreId`/`resolveViewableStoreId`/`assertStoreAccess` scope creation and single-record reads to stores the caller has access to — `getById` throws if the caller lacks access to the record's store.

## 8. Permissions

| Permission | Module (per `Permission.java`) | Used by |
|---|---|---|
| `EXPENSE_VIEW` | Account | `ExpenseController` GET/search/summary/getById |
| `EXPENSE_CREATE` | Account | `ExpenseController` POST/create, PUT/update |
| `EXPENSE_POST` | Account | `ExpenseController` POST `/post` |
| `EXPENSE_CANCEL` | Account | `ExpenseController` PATCH `/cancel` |
| `CASH_MANAGE` | Account | `CashTransactionController` (all endpoints, class-level) and `DayClosingController` (all endpoints) — comment: "Cash In/Out, Day Closing" |
| `FY_MANAGE` | Account | `FinancialYearController` create/open/close/mark-current — comment: "deliberately its own permission, never bundled with ACCOUNT_VIEW" |
| `AUDIT_VIEW` | Audit | `AuditLogController` (class-level, all endpoints) |
| `MASTER_MANAGE` | Master | `ExpenseCategoryController` create/update/activate/deactivate |

Role coverage (from `RolePermissions.build()`): `ADMIN` holds every `Permission` (`EnumSet.allOf`). `STORE_MANAGER` holds everything ADMIN does **except** `FY_MANAGE` and `AUDIT_VIEW` (explicitly removed, alongside the USER_*/ROLE_*/PERMISSION_VIEW security tier) — so a Store Manager can manage Expenses/Cash/Day Closing but cannot manage Financial Years or view the Audit Trail. `ACCOUNTANT` explicitly holds `EXPENSE_VIEW/CREATE/POST/CANCEL` and `CASH_MANAGE` (listed among its granted set) but does NOT hold `FY_MANAGE` or `AUDIT_VIEW`. `SALES_USER`, `PURCHASE_USER`, `INVENTORY_USER`, and `STAFF` hold none of `EXPENSE_*`, `CASH_MANAGE`, `FY_MANAGE`, or `AUDIT_VIEW` — this module's write paths are Accountant/Store-Manager/Admin-tier only, and FY management plus the audit trail are Admin-only.

## 9. Transaction Handling

- `ExpenseService`/`CashTransactionService`/`DayClosingService`/`FinancialYearService`/`PaymentMethodService` methods are `@Transactional` (read methods `readOnly = true`), default propagation — the standard pattern across this codebase.
- Voucher numbering (`VoucherNumberService.next`, used by both Expense and Cash Transaction creation) runs in its own `@Transactional(propagation = REQUIRES_NEW)` transaction with a `SELECT ... FOR UPDATE` row lock on the sequence counter, so the numbering lock is independent of — and released before — the larger creation transaction, and a numbering failure never poisons the caller's transaction.
- Journal posting (`AccountingService.postJournalByAccountId`/`postJournal`/`reverseJournal`) runs inside the caller's own transaction (Expense/Cash Transaction posting and their DB state changes are atomic together — either both the journal and the entity status flip, or neither does).
- `AuditService.log(...)` deliberately runs in the SAME transaction as its caller (default propagation, NOT `REQUIRES_NEW`) — per its Javadoc, an audit entry for an action that later rolls back should roll back with it; recording "this happened" for something that didn't would be worse than not recording it. Its body is also wrapped in try/catch so a transient audit-write failure is logged (`log.warn`) but never thrown, never blocking or rolling back the business operation it describes.
- `FinancialYearService.resolveOpenForPosting(date)` is invoked from inside `AccountingService.buildAndSaveJournal` (and the reversal path) — i.e., the FY-open gate is checked in the SAME transaction as the journal write, so a closed-FY rejection prevents the posting atomically rather than as a separate pre-check that could race.
- `DayClosing.close` is a single-row insert guarded by `existsByClosingDate` inside the same transaction as the `dayClosingRepository.save` — no unique-constraint race window is explicitly discussed in the code beyond the DB's own `unique = true` constraint on `closing_date`.

## 10. Error Handling

`GlobalExceptionHandler` (`backend/src/main/java/com/storehub/exception/GlobalExceptionHandler.java`) centrally maps:

- `MethodArgumentNotValidException` (bean validation failures, e.g. `@NotNull`/`@NotBlank`/`@DecimalMin` on the DTOs above) → HTTP 400 with `ApiError.fieldErrors` populated per-field (`field → defaultMessage`), message = "Validation failed".
- `ExpenseNotFoundException` → HTTP 404 with the exception's own message ("Expense not found with id: {id}").
- `CashTransactionNotFoundException` → HTTP 404 ("Cash transaction not found with id: {id}").
- `FinancialYearNotFoundException` → HTTP 404 ("Financial year not found with id: {id}").
- `BadRequestException` (the generic business-rule violation type — used throughout this module for inactive store/supplier/category, invalid amounts, missing tax mode, already-closed day, overlapping FY range, closing the current FY, etc.) → HTTP 400 with the exception's own message as `ApiError.message` (no `fieldErrors` — this is a top-level, not per-field, error).
- `AuditService.log` never throws to its caller — any persistence failure inside it is caught and logged, so an audit-write problem can never surface as an HTTP error on the business operation.

The `ApiError` shape (`timestamp`, `status`, `error`, `message`, `path`, `fieldErrors`) is the standard shape used across the whole application; per project convention (see this repo's CLAUDE.md), a 400 with `fieldErrors` should always be checked field-by-field before assuming a generic cause.

## 11. Audit Flow

`AuditService` is a CENTRAL/COMMON logging mechanism, not exclusive to this module — a grep of `auditService.log(` across `backend/src/main/java/com/storehub/service` found 18 distinct service files calling it (representative, not exhaustive): `ProductService`, `InventoryService`, `StockTransferService`, `StoreService`, `DebitNoteService`, `CreditNoteService`, `CashTransactionService`, `ExpenseService`, `PaymentService`, `ReceiptService`, `PurchaseService`, `SaleService`, `EmployeeService`, `UserService`, `AuthService`, `BusinessGstConfigService`, `DayClosingService`, `FinancialYearService`. Every module in the application funnels its "who did what" history through this one service and the single `audit_logs` table.

This module's OWN entities are audited as follows:

- **Expense**: `CREATE` on creation (description names the expense number + category), `UPDATE` when a DRAFT is edited, `POST` when posted ("accounting journal applied"), `CANCEL` when cancelled (reason echoes the expense number). Every call passes `module="EXPENSE"`, `entityType="Expense"`, and the expense's `storeId`.
- **Cash Transaction**: `CREATE`, `POST`, `CANCEL` — same pattern, `module="CASH"`, `entityType="CashTransaction"`, with `storeId`.
- **Day Closing**: `DAY_CLOSE` on close, with the difference amount and reason embedded directly in the free-text `description` when non-zero — `module="CASH"`, `entityType="DayClosing"`; notably `DayClosingService.close`'s audit call does NOT pass a `storeId` (uses the 8-arg overload), unlike Expense/CashTransaction.
- **Financial Year**: `CREATE` on creation, `FY_CLOSE`/`FY_OPEN` on status change (records `oldStatus`/`newStatus` in `oldValue`/`newValue`), `UPDATE` on mark-current — `module="ADMIN"`, `entityType="FinancialYear"`; none of these pass a `storeId` (financial years are global, not store-scoped).
- **Payment Method**: NOT FOUND IN CURRENT CODEBASE — `PaymentMethodService` never calls `auditService.log(...)`; create/update/activate/deactivate on Payment Method leave no audit trail entry.
- **Expense Category**: NOT FOUND IN CURRENT CODEBASE — `ExpenseCategoryService` never calls `auditService.log(...)` either.

## 12. Important Side Effects

- **Expense posting → Accounting Core**: `ExpenseService.post` calls `accountingService.postJournalByAccountId(VoucherType.EXPENSE, ...)` — verified directly in `ExpenseService.java`. It debits the resolved Expense account (category's `linkedAccount` if active, else `SystemAccountCode.EXPENSES`) for the ITC-adjusted amount, adds Input CGST/SGST/IGST debit lines when `itcEligible`, and credits either `SUPPLIER_PAYABLE` (party-tagged) for a credit expense or the resolved Cash/Bank system account otherwise.
- **Expense GST/ITC sync**: `ExpenseService.post` calls `gstTransactionSyncService.syncExpense(posted)` (only creates/refreshes a `GstTransaction` row if `GstReportingEligibility.isEligibleForGstReporting(expense)` — a non-GST expense, i.e. no `taxMode`, is never synced). Its `b2b` flag is set directly from `expense.isItcEligible()` (not a GSTIN-validity proxy like Sale/Purchase use). `ExpenseService.cancel` calls `gstTransactionSyncService.reverseExpense(expense)`, which flips the existing GST row to `REVERSED` in place (a no-op if the expense was never synced).
- **Cash Transaction posting → Accounting Core**: `CashTransactionService.post` calls `accountingService.postJournal(VoucherType.CASH_TRANSACTION, ...)` with simple `JournalLine.debit/credit` pairs (CASH_IN: debit Cash/Bank, credit `OTHER_INCOME`; CASH_OUT: debit `EXPENSES`, credit Cash/Bank) — no GST/ITC involvement for Cash Transactions at all.
- **Day Closing's difference-reason requirement**: `DayClosingService.close` throws `BadRequestException` if `actualCash != expectedCash` and no `differenceReason` was supplied — this is a hard server-side gate, not merely a frontend nicety (the frontend independently computes and enforces the same rule for UX, but the backend re-validates authoritatively).
- **Day Closing never touches accounting balances**: confirmed by the entity Javadoc and the service code — `close` only persists a `DayClosing` row; any real cash shortage/overage must be separately recorded as an explicit `CashTransaction` (Cash Adjustment) if the business wants it reflected in the ledger.
- **FinancialYear's startup auto-seed**: `FinancialYearService.ensureCurrentFinancialYearExists()` (`@PostConstruct`) runs once per application startup; it is idempotent — if a FY row already covers `LocalDate.now()`, it does nothing; otherwise it creates one with `status=OPEN, current=true` using `FinancialYearUtil` for the Apr–Mar boundaries. This guarantees "today" always has a resolvable FY on a fresh database, so the very first Sale/Purchase/Expense/Cash Transaction/Day Closing never fails purely for lack of a FY row.
- **FY-open gate is centralized, not per-service**: `AccountingService.buildAndSaveJournal` (and its reversal counterpart) call `financialYearService.resolveOpenForPosting(...)` before every journal write — this is the single choke point that blocks posting into a CLOSED financial year for every voucher type in the system, including Expense and Cash Transaction from this module.

## 13. Dependencies on Other Modules

- **Accounting Core** (`AccountingService`, `AccountService`, journal headers/lines) — Expense and Cash Transaction posting/reversal both go through `AccountingService.postJournal`/`postJournalByAccountId`/`reverseJournal`; `ExpenseService` also depends on `AccountService.getSystemAccount(SystemAccountCode...)` for the Expenses/Input-GST/Supplier-Payable system accounts, and `ExpenseCategoryService`/`ExpenseCategoryController` depend on `Account` for the optional linked-account mapping. `DayClosingService` depends on `CashBankBookService.cashBook` (also Accounting Core) for `expectedCash`.
- **GST** (`GstCalculationService`, `GstTransactionSyncService`, `GstReportingEligibility`, `GstinValidator`) — `ExpenseService.applyRequest` uses `GstCalculationService.calculateLine` for the CGST/SGST/IGST split; `ExpenseService.post`/`cancel` sync/reverse the expense's `GstTransaction` reporting row via `GstTransactionSyncService`.
- **Supplier** (Party module) — a credit Expense reuses the existing `Supplier` entity (`SupplierService.findSupplierOrThrow`) rather than a separate vendor master; its payable is tracked through `LedgerService`'s `SupplierLedgerEntry` (`recordExpenseCredit`/`reverseExpenseCredit`), and later settlement of that payable happens entirely through the existing Payment module (`PaymentService`) — Expense never implements its own payment mechanism. `ExpenseRepository.findOutstandingBySupplier` mirrors `PurchaseRepository`'s equivalent query.
- **Store (Multi-Store)** — `Expense.store`, `CashTransaction.store`, and `AuditLog.store` are all `@ManyToOne` to `Store`; `storeAccessService.resolveEffectiveStoreId`/`resolveViewableStoreId`/`assertStoreAccess` gate both creation and single-record access for Expense and Cash Transaction. `DayClosing` and `FinancialYear` are NOT store-scoped (no `store_id` column on either).
- **User** — `Expense.createdBy/postedBy/cancelledBy`, `CashTransaction.createdBy/postedBy/cancelledBy`, `DayClosing.closedBy`, and `AuditLog.userId/username` are all stamped from `SecurityUtil.currentUsername()`/`currentUserOrNull()`.
- **Day Closing → Sale/Purchase/Receipt/Payment/Expense/CashTransaction repositories** — `DayClosingService.computeSummary` directly queries `SaleRepository`, `PurchaseRepository`, `ReceiptRepository`, `PaymentRepository`, `ExpenseRepository`, and `CashTransactionRepository` for its daily aggregates — it is a read-only rollup across nearly every transactional module, not an independent ledger.
- **Financial Year → Sale/Purchase** — `FinancialYearService.summary(id)` aggregates `SaleRepository`/`PurchaseRepository` totals for the FY's date range; `VoucherNumberService` (used by Expense/Cash Transaction numbering) depends on `FinancialYearService.resolveForDate` for the FY code embedded in every voucher number.

## 14. Key Operation Flows

**Create Expense (cash)**: `ExpenseForm.tsx` (credit-expense toggle OFF, vendor/paymentMode filled) → `expenseApi.create(payload)` → `POST /api/expenses` → `ExpenseController.create` (`@PreAuthorize PERM_EXPENSE_CREATE`) → `ExpenseCreateRequest` (validated: `expenseDate`, `paymentMode` `@NotNull`) → `ExpenseService.create`: resolves store, builds `DRAFT` `Expense` via `applyRequest` (resolves category, computes taxable/GST via `GstCalculationService`), saves, numbers via `VoucherNumberService.next(EXPENSE, date)`, audit-logs `CREATE` → if `post: true`, immediately calls `ExpenseService.post` (see below) → `expenses` table row(s) → `ExpenseResponse` → UI navigates to `ExpenseDetail`.

**Create Expense (credit, against a Supplier)**: same form with the credit-expense toggle ON and a `supplierId` chosen → same create path, but `applyRequest` resolves and validates the Supplier (must be active) and sets `vendorName` from the Supplier's name → on `post`, `ExpenseService.post` detects `supplier != null` → credits `SUPPLIER_PAYABLE` (party-tagged to that Supplier) instead of Cash/Bank, sets `paymentStatus=UNPAID`/`payableAmount=total`, and calls `LedgerService.recordExpenseCredit` → writes a `SupplierLedgerEntry` (`referenceType=EXPENSE`) → `GstTransactionSyncService.syncExpense` if GST-applicable → `ExpenseResponse` shows `supplierId`/`payableAmount`; later settlement happens via the existing Payment module, not this flow.

**Cancel Expense**: `ExpenseDetail.tsx` Cancel button → `expenseApi.cancel(id)` → `PATCH /api/expenses/{id}/cancel` → `ExpenseController.cancel` (`PERM_EXPENSE_CANCEL`) → `ExpenseService.cancel`: no-op if already `CANCELLED`; throws `BadRequestException` if any `PaymentAllocation` exists against it; if `POSTED`, reverses the journal (`AccountingService.reverseJournal`), reverses the supplier-ledger credit if it was a credit expense, reverses the GST sync row; resets `paidAmount/payableAmount` to 0, sets `status=CANCELLED`, stamps `cancelledBy/cancelledAt`; audit-logs `CANCEL` → `ExpenseResponse`.

**Cash In**: `CashTransactionForm.tsx` (type = CASH_IN) → `cashTransactionApi.create(payload)` → `POST /api/cash-transactions` → `CashTransactionController.create` (class-level `PERM_CASH_MANAGE`) → `CashTransactionCreateRequest` validated → `CashTransactionService.create`: resolves store, builds `DRAFT`, numbers via `VoucherNumberService.next(CASH_TRANSACTION, date)`, audit-logs `CREATE` → if `post: true`, `CashTransactionService.post`: debits resolved Cash/Bank, credits `OTHER_INCOME`, posts via `AccountingService.postJournal`, audit-logs `POST` → `cash_transactions` row → `CashTransactionResponse` → UI navigates to detail.

**Cash Out**: identical flow with type = CASH_OUT; `post` instead debits `SystemAccountCode.EXPENSES` and credits the resolved Cash/Bank account.

**Perform Day Closing**: `DayClosingPage.tsx` loads `dayClosingApi.summary(date)` (live, re-derivable any time) → operator enters `actualCash`; if it differs from `expectedCash` the UI requires a `differenceReason` before enabling Close → `dayClosingApi.close(payload)` → `POST /api/day-closing/close` → `DayClosingController.close` (`PERM_CASH_MANAGE`) → `DayClosingCloseRequest` → `DayClosingService.close`: rejects if the date was already closed; recomputes the summary fresh server-side; recomputes `difference`; throws if non-zero and no reason given; resolves `financialYearId` via `FinancialYearService.resolveForDate`; saves the `DayClosing` row; audit-logs `DAY_CLOSE` → `day_closings` table → `DayClosingResponse.fromClosed` → UI shows the closed state and appends to Closing History.

**Create Financial Year**: `FinancialYears.tsx` create dialog (ADMIN only in UI) → `financialYearApi.create(payload)` → `POST /api/financial-years` → `FinancialYearController.create` (`PERM_FY_MANAGE`) → `FinancialYearRequest` (`startDate`/`endDate` `@NotNull`) → `FinancialYearService.create`: validates `endDate > startDate`, derives `code`/`name` if blank via `FinancialYearUtil`, rejects a duplicate code or an overlapping date range (`countOverlapping`), saves with `status=OPEN, current=false`, audit-logs `CREATE` (`module="ADMIN"`) → `financial_years` row → `FinancialYearResponse`.

**Close Financial Year**: `FinancialYears.tsx` row action "Close Year" → `financialYearApi.close(id)` → `PATCH /api/financial-years/{id}/close` → `FinancialYearController.close` (`PERM_FY_MANAGE`) → `FinancialYearService.setStatus(id, CLOSED)`: throws `BadRequestException` if the target FY `isCurrent()` ("Mark another year current first"); flips `status` to `CLOSED`; audit-logs `FY_CLOSE` (`oldValue`/`newValue` = old/new status) → `financial_years` row updated → `FinancialYearResponse` → from this point, `AccountingService.buildAndSaveJournal`'s call to `resolveOpenForPosting` rejects any new posting dated inside this FY, application-wide (Expense/Cash Transaction included).

## 15. Manual Changes

- **Add a new Expense field**: add the column + getter/setter to `backend/src/main/java/com/storehub/entity/Expense.java`; add the field to `ExpenseCreateRequest`/`ExpenseUpdateRequest` (with validation annotations as needed) and to `ExpenseResponse.fromEntity`; wire it through `ExpenseService.applyRequest`; if it affects the posted journal amount, update the line-building logic in `ExpenseService.post`. Affects: Accounting (if it changes what gets posted), GST (if it's tax-related — also touch `GstCalculationService`/`GstTransactionSyncService.syncExpense`), and the frontend `Expense` type (`frontend/src/types/expense.ts`), `ExpenseCreatePayload`, `ExpenseForm.tsx`, `ExpenseDetail.tsx`.
- **Change Day Closing's reconciliation logic**: the expected-cash source and the aggregation queries live in `DayClosingService.computeSummary` (each figure is a separate repository call — `SaleRepository`, `PurchaseRepository`, `ReceiptRepository`, `PaymentRepository`, `ExpenseRepository`, `CashTransactionRepository`, `CashBankBookService`); the difference-reason gate itself is the single `if (difference.signum() != 0 && ...)` check in `DayClosingService.close`. Never change `expectedCash` to be computed independently of `CashBankBookService.cashBook` — the class Javadoc explicitly states it must never drift from the accounting journal. Affects: Accounting Core (`CashBankBookService`) if the Cash Book's own logic changes; the frontend `DayClosingSummary` type and `DayClosingPage.tsx` if new fields are added to the response.
- **Change FY validation rules**: overlap/date-range/code-uniqueness checks live in `FinancialYearService.create`; the posting gate itself (`status == CLOSED` rejection) is in `resolveOpenForPosting`, called centrally from `AccountingService.buildAndSaveJournal`/reversal — changing that gate changes posting behavior for every voucher type in the whole application, not just this module. The startup auto-seed rule (`ensureCurrentFinancialYearExists`) and the Apr–Mar boundary calculation (`FinancialYearUtil.startOf/endOf/shortCode/label`) are separate concerns to touch only if the fiscal-year convention itself changes.
- **Add a new AuditAction type**: add the constant to `backend/src/main/java/com/storehub/entity/AuditAction.java`; add the matching string literal to the frontend `AuditAction` union type in `frontend/src/types/auditLog.ts` (note: the current frontend union is missing `DAY_CLOSE`, which the backend enum already has — this is an existing frontend/backend drift worth fixing alongside any new addition); if the new action needs a distinct badge color, update `actionVariant` in `frontend/src/pages/admin/AuditTrail.tsx`.
- **Add a new CashTransactionType**: add the constant to `backend/src/main/java/com/storehub/entity/CashTransactionType.java`; extend the `if/else` in `CashTransactionService.post` that currently branches only on `CASH_IN`/`CASH_OUT` to build the correct journal lines for the new type; update the frontend `CashTransactionType` union in `frontend/src/types/cashTransaction.ts` and the type `<Select>` options in `CashTransactionForm.tsx`/`CashTransactions.tsx`. Affects: Accounting Core (new journal line combination for the new type).
