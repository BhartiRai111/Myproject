# Alerts, Global Search & Dashboard Module

## 1. Overview

This module covers three cross-cutting UI features that read data owned by other modules rather than owning data of their own:

- **Alert Center** (`/alerts`) — a read-only, computed-on-request list of business warnings (low/out-of-stock, overdue receivables/payables, financial year closing soon, accounting health-check failures). Backed by a single endpoint, `GET /api/alerts`.
- **Global Search** (the search box in the top layout bar) — an as-you-type, multi-entity search across Sales, Purchases, Products, Suppliers, Credit Notes, Debit Notes, Sales Orders, Purchase Orders, Receipts and Payments. Backed by a single endpoint, `GET /api/search`.
- **Dashboard** (`/dashboard`) — the landing page after login. It has **no backend module of its own**. It is entirely frontend-composed: it fans out to `productApi`, `customerApi`, `supplierApi`, `saleApi`, `purchaseApi`, `accountingReportApi.dashboard()`, `gstReportApi.liability()` and `alertApi.list()` — seven other modules' existing endpoints plus the Alert Center endpoint — and assembles the results client-side. This is verified directly from `Dashboard.tsx`'s imports and `Promise.allSettled` calls; there is no `DashboardController` or `DashboardService` anywhere in `backend/src/main/java/com/storehub`.

Both `AlertService` and `GlobalSearchService` are explicitly documented in their own Javadoc as deliberately holding **no new business logic and no persisted rows**: `AlertService` re-derives everything live each call from `InventoryRepository`, `OutstandingBillService`, `FinancialYearService` and `AccountingHealthCheckService`; `GlobalSearchService` re-uses each module's existing paginated `search(...)` repository query (the same one backing that module's list page) so results can never drift from the source page.

## 2. Frontend

### Files and Components

| File | Role |
|---|---|
| `frontend/src/pages/AlertsCenter.tsx` | Full-page Alert Center: loads all alerts, shows Critical/Warning/Total stat tiles, lists every alert, click-to-navigate via `path`. No dismiss/mark-read — alerts are not stored, so there is nothing to dismiss. |
| `frontend/src/pages/Dashboard.tsx` | Landing dashboard: stat cards, two 7-day bar charts (Sales/Purchases), an "Alerts" summary card (top 5), Recent Sales list, Low Stock Products list. |
| `frontend/src/components/layout/GlobalSearch.tsx` | Debounced (300ms) as-you-type search input rendered in the top bar; dropdown of grouped results; click-outside-to-close; Escape/clear button. |
| `frontend/src/api/alertApi.ts` | `alertApi.list()` → `GET /alerts`. |
| `frontend/src/api/globalSearchApi.ts` | `globalSearchApi.search(q)` → `GET /search?q=...`. |
| `frontend/src/types/alert.ts` | `AlertSeverity`, `AlertItem` (`severity`, `category`, `message`, `path?`). |
| `frontend/src/types/globalSearch.ts` | `GlobalSearchResultItem`, `GlobalSearchResponse`. |
| `frontend/src/utils/stockStatus.ts` | `getStockStatus()` — pure client-side helper Dashboard uses to compute its own "Low Stock" list from the already-fetched product list (independent of `AlertService`'s server-side inventory-count logic). |
| `frontend/src/components/layout/page-title.ts` | Maps `/alerts` route to page title "Alert Center". |
| `frontend/src/components/layout/Topbar.tsx` | Notification bell / dropdown item that navigates to `/alerts`. |

### Frontend Flow

**Alert Center (list):**
1. On mount, `AlertsCenter` calls `load()` → `alertApi.list()` → `GET /api/alerts`.
2. Response `AlertItem[]` is split client-side into `critical` (`severity === 'CRITICAL'`) and `warning` (`severity === 'WARNING'`) for the two stat tiles; a third tile shows the total count.
3. Each row is clickable only if `a.path` is set; click calls `navigate(a.path)`.
4. A manual "Refresh" button re-runs `load()`. There is **no dismiss/acknowledge action** — the page's own description text states alerts are "not stored or dismissible."
5. Empty state ("No active alerts") is shown when the array is empty; loading state uses `Skeleton` placeholders.

**Global Search (as-you-type):**
1. User types into the `Input` in `GlobalSearch.tsx`; `query` state updates on every keystroke and `open` is set true on focus.
2. A `useEffect` on `query` clears any pending debounce timer, and if the trimmed query is `< 2` characters, clears `results`/`loading` and does nothing further (matches the backend's own `q.length() < 2` short-circuit).
3. Otherwise `loading` is set true and a 300ms `setTimeout` fires `globalSearchApi.search(trimmed)` → `GET /api/search?q=...`.
4. On success, `results` is replaced with `res.data.results`; on any error, `results` is silently reset to `[]` (no toast).
5. The dropdown renders each result's `title`, optional `subtitle`, and a category pill; clicking a row closes the dropdown, clears the query, and calls `navigate(item.path)`.
6. A click outside the container (`mousedown` listener on `document`) closes the dropdown without clearing the query.

**Dashboard load:**
1. On mount (and whenever `canSeePurchases` changes — derived from `user.role === 'ADMIN' || 'STORE_MANAGER'`), `Dashboard` fires a first `Promise.allSettled` batch of 6 calls: `productApi.list({size:200})`, `customerApi.list()`, `supplierApi.list({size:1,status:'ACTIVE'})`, `saleApi.list({fromDate:today,toDate:today,size:200})` (today's sales), `saleApi.list({size:5})` (recent sales), `saleApi.list({fromDate:from,toDate:today,size:200})` (7-day chart, `from` = `daysAgoISO(6)`).
2. If `canSeePurchases`, a second `Promise.allSettled` batch of 4 calls runs: `purchaseApi.list({size:1})` (count), `purchaseApi.list({fromDate,toDate,size:200})` (7-day chart), `accountingReportApi.dashboard()`, `gstReportApi.liability(currentReturnPeriod())`. Non-manager/admin roles never issue these 4 calls (all four values stay `null`).
3. Independently, `alertApi.list()` is called (`.catch(() => null)` so a failure never blocks the rest of the page).
4. Each settled promise is checked individually (`status === 'fulfilled'`) and used to populate its own piece of state — a failure in one call does not block the others from rendering (this is the purpose of `Promise.allSettled` over `Promise.all`).
5. Cards rendered and their data source:
   - **Total Products** — `totalProducts` from `productApi.list` (`totalElements`).
   - **Today's Sales** — sum of today's non-cancelled sales' `totalAmount`, computed client-side from `saleApi.list({fromDate:today,toDate:today})`.
   - **Total Purchases** — `purchaseApi.list({size:1}).totalElements`; shown as `—` and marked `restricted` (lock icon + tooltip "Only visible to Admin and Store Manager") for other roles.
   - **Total Customers** — `customerApi.list().length`.
   - **Low Stock Products** — computed client-side: `products.filter(p => getStockStatus(p) !== 'IN_STOCK')`, sorted ascending by `stockQuantity`. This is a **separate** client-side low-stock computation from `AlertService`'s server-side `countLowStock`/`countOutOfStock` — see §12 for the discrepancy this can cause.
   - **Total Suppliers** — `supplierApi.list({status:'ACTIVE'}).totalElements`.
   - **Cash + Bank Balance / Receivable / Payable** (manager/admin only) — `accountingReportApi.dashboard()` → `cashBalance + bankBalance`, `receivableBalance`, `payableBalance`.
   - **GST Liability (this period)** — `gstReportApi.liability(currentReturnPeriod())` → `netTotal`.
   - **Sales Overview / Purchase Overview bar charts** — client-aggregated daily totals (`buildDailyTotals`) over the last 7 days from the two chart `saleApi.list`/`purchaseApi.list` calls (non-cancelled rows only), rendered with Recharts `BarChart`.
   - **Alerts card** — first 5 items of `alertApi.list()`'s response, with a "View all" link to `/alerts` and a critical-count badge.
   - **Recent Sales** — last 5 sales from `saleApi.list({size:5})`, click-through to the sale's bill/challan detail page.
   - **Low Stock Products list** — first 6 of the client-computed `lowStockProducts`, click-through to `/inventory`.
6. Financial stat cards and the Purchase Overview chart are conditionally rendered only when `canSeePurchases` is true (a **frontend-only** role gate — see §7/§8, there is no corresponding backend restriction on these underlying endpoints beyond their own module's permission checks).

## 3. API Calls

**Alerts**

| Function | Method | URL | Request shape | Response shape |
|---|---|---|---|---|
| `alertApi.list()` | GET | `/api/alerts` | none | `AlertItem[]` — `{severity: 'CRITICAL'\|'WARNING', category: string, message: string, path?: string}[]` |

**Global Search**

| Function | Method | URL | Request shape | Response shape |
|---|---|---|---|---|
| `globalSearchApi.search(q)` | GET | `/api/search?q={q}` | query param `q: string` | `GlobalSearchResponse` — `{query: string, results: {category, id, title, subtitle?, path}[]}` |

**Dashboard — every other module's endpoint it calls** (Dashboard.tsx has no API file of its own; these are imported directly from other modules' API files):

| Import (from Dashboard.tsx) | Function called | Method | URL |
|---|---|---|---|
| `productApi` (`@/api/productApi`) | `productApi.list({ size: 200 })` | GET | `/api/products` |
| `customerApi` (`@/api/customerApi`) | `customerApi.list()` | GET | `/api/customers` |
| `supplierApi` (`@/api/supplierApi`) | `supplierApi.list({ size: 1, status: 'ACTIVE' })` | GET | `/api/suppliers` |
| `saleApi` (`@/api/saleApi`) | `saleApi.list({ fromDate, toDate, size: 200 })` (today), `saleApi.list({ size: 5 })` (recent), `saleApi.list({ fromDate, toDate, size: 200 })` (7-day chart) — 3 separate calls | GET | `/api/sales` |
| `purchaseApi` (`@/api/purchaseApi`) | `purchaseApi.list({ size: 1 })`, `purchaseApi.list({ fromDate, toDate, size: 200 })` — 2 separate calls, manager/admin only | GET | `/api/purchases` |
| `accountingReportApi` (`@/api/accountingApi`) | `accountingReportApi.dashboard()` — manager/admin only | GET | `/api/accounting/reports/dashboard` |
| `gstReportApi` (`@/api/gstReportApi`) | `gstReportApi.liability(currentReturnPeriod())` — manager/admin only | GET | `/api/gst-reports/liability?returnPeriod=...` |
| `alertApi` (`@/api/alertApi`) | `alertApi.list()` | GET | `/api/alerts` |

Confirmed: **Dashboard has no dedicated backend controller/service.** A repo-wide case-insensitive search for `Dashboard` under `backend/src/main/java/com/storehub/controller` and `.../service` finds nothing named `DashboardController`/`DashboardService`; the only backend hit for "Dashboard" is `dto/AccountingDashboardResponse.java`, which belongs to the Accounting module and is served by `/api/accounting/reports/dashboard` (accounting-owned, just reused by the frontend Dashboard page).

## 4. Backend

### Controllers (every endpoint)

**`AlertController`** (`backend/src/main/java/com/storehub/controller/AlertController.java`)
- `GET /api/alerts` → `alertService.getAlerts()` → `List<AlertItem>`. No path/query params. No `@PreAuthorize` annotation on the controller or method.

**`GlobalSearchController`** (`backend/src/main/java/com/storehub/controller/GlobalSearchController.java`)
- `GET /api/search?q={q}` → `globalSearchService.search(q)` → `GlobalSearchResponse`. `q` defaults to `""` (`@RequestParam(defaultValue = "")`). No `@PreAuthorize` annotation.

No dedicated Dashboard controller exists (see §3).

### DTOs (fields)

- **`AlertItem`** (`dto/AlertItem.java`) — `severity: String`, `category: String`, `message: String`, `path: String`. Plain `@Builder`/`@Getter`/`@AllArgsConstructor`, no `@NoArgsConstructor`, no validation annotations (it's an outbound-only DTO).
- **`GlobalSearchResponse`** (`dto/GlobalSearchResponse.java`) — `query: String`, `results: List<GlobalSearchResultItem>`.
- **`GlobalSearchResultItem`** (`dto/GlobalSearchResultItem.java`) — `category: String`, `id: Long`, `title: String`, `subtitle: String`, `path: String`. Javadoc: "One row in the Global Search dropdown — enough to render and to navigate straight to the source document."
- No `Search*.java` DTOs beyond the two above (glob of `dto/Alert*.java`, `dto/GlobalSearch*.java`, `dto/Search*.java` returns exactly `AlertItem.java`, `GlobalSearchResponse.java`, `GlobalSearchResultItem.java`).

### Services

**`AlertService`** (`backend/src/main/java/com/storehub/service/AlertService.java`) — `@Transactional(readOnly = true)`. Exact trigger conditions, in the order they're evaluated in `getAlerts()`:

1. **Out of stock** — `inventoryRepository.countOutOfStock(null)` (aggregated across all stores, `storeId = null`). Repository query: `Inventory i WHERE i.currentStock <= 0`. If count `> 0` → `severity=CRITICAL, category=Inventory, message="{n} product(s) are OUT OF STOCK", path=/inventory?stockStatus=OUT_OF_STOCK`.
2. **Low stock** — `inventoryRepository.countLowStock(null)`. Query: `Inventory i JOIN i.product p WHERE i.currentStock > 0 AND i.currentStock <= COALESCE(p.minStockLevel, 0)`. If count `> 0` → `severity=WARNING, category=Inventory, message="{n} product(s) are LOW STOCK", path=/inventory?stockStatus=LOW_STOCK`.
3. **Reorder point reached** — `inventoryRepository.countReorderCandidates(null)`. Query: `Inventory i JOIN i.product p WHERE p.reorderLevel IS NOT NULL AND i.currentStock <= p.reorderLevel AND p.status = ACTIVE` — explicitly a **distinct threshold from low-stock's `minStockLevel`** (code comment: "distinct from LOW_STOCK's minStockLevel threshold"). If count `> 0` → `severity=WARNING, category=Inventory, message="{n} product(s) have reached their reorder point", path=/inventory`.
4. **Receivables overdue** — via `addOutstandingAlert(alerts, outstandingBillService.customerOutstanding(LocalDate.now()), "Receivable", "/accounting/reports/receivable")`. Sums every ageing bucket in the report **except** the `"0-30 Days"` bucket (bucket labels are `{"0-30 Days", "31-60 Days", "61-90 Days", "91-180 Days", "180+ Days"}` from `OutstandingBillService`) — i.e. **overdue = any bill older than 30 days**. If the summed overdue count `> 0` → `severity=WARNING, category=Receivable, message="{overdueCount} receivable bill(s) overdue (30+ days), totalling {overdueAmount}", path=/accounting/reports/receivable`.
5. **Payables overdue** — same logic via `outstandingBillService.supplierOutstanding(LocalDate.now())`, `category="Payable"`, `path=/accounting/reports/payable`.
6. **Financial year nearing close** — `financialYearService.getCurrent()`; if `currentFy.getEndDate()` is set, `daysToClose = ChronoUnit.DAYS.between(today, endDate)`. If `0 <= daysToClose <= FY_NEARING_CLOSE_DAYS` (constant `= 30`) → `severity=WARNING, category="Financial Year", message="Financial year {name} closes in {daysToClose} day(s)", path=/admin/financial-years`. **Caveat**: `FinancialYearService.getCurrent()` throws `BadRequestException("No current financial year is set")` (via `.orElseThrow(...)`) rather than returning null when no FY is marked current — so the `currentFy != null` guard in `AlertService` is effectively dead code, and if no financial year is currently marked `current` in the database, `GET /api/alerts` (and therefore both the Alert Center page and the Dashboard's Alerts card) will fail with HTTP 400 instead of degrading gracefully. See §10/§12.
7. **Accounting health check findings** — iterates `accountingHealthCheckService.runHealthCheck().getFindings()`; any finding with `status == WARNING` or `status == ERROR` becomes an alert (`ERROR → CRITICAL`, `WARNING → WARNING`), `category="Accounting Health Check"`, `message="{checkName}: {message}"`, `path=/accounting/reports/health-check"`. The underlying checks (in `AccountingHealthCheckService`) are: `checkJournalBalance`, `checkDuplicatePosting`, `checkSourcePosting`, `checkOrphanJournals`, `checkLedgerMismatch`, `checkFinancialYearCoverage`, `checkNotePosting`.

Alerts are **one per condition, not one per row** (e.g. one "12 products are LOW STOCK" alert, not 12 separate alerts) — stated explicitly in the class Javadoc to keep the list "meaningful rather than noisy."

**`GlobalSearchService`** (`backend/src/main/java/com/storehub/service/GlobalSearchService.java`) — `@Transactional(readOnly = true)`, `LIMIT_PER_CATEGORY = 5`.

- Query is trimmed; if `< 2` characters, returns an empty result list immediately (`GlobalSearchResponse.builder().query(q).results(List.of())`).
- Store scoping (`resolveSearchStoreIdOrNull()`): if the caller has all-stores access (`storeAccessService.hasAllStoresAccess(user)`, e.g. ADMIN) → `null` (no filter, searches every store). Otherwise, uses `user.getCurrentStore()` if set; if not set and the user has exactly one accessible store, uses that store's id; if not set and the user has multiple accessible stores, uses a sentinel id `-1L` (`NO_ACCESSIBLE_STORE_SENTINEL`) that no real store can match, so store-sensitive categories quietly return zero results rather than erroring the whole search.
- Queries each of the following repositories' own existing `search(...)` method, in this exact order, each capped to top 5 results (`PageRequest.of(0, 5)`), and appends results to one combined flat list (no cross-category ranking/sorting — order is purely the fixed category iteration order below, and within a category whatever order that module's own `search` query returns):
  1. `saleRepository.search(q, null, null, null, null, null, storeId, top)` → category `"Sale"`, title = `invoiceNumber`, subtitle = customer name or `"Walk-in"`, path = `/sales/kacchi/{id}` (if `SALE_CHALLAN`) or `/sales/bills/{id}`.
  2. `purchaseRepository.search(...)` → category `"Purchase"`, title = `purchaseNumber`, subtitle = supplier name, path = `/purchases/kacchi/{id}` or `/purchases/bills/{id}`.
  3. `productRepository.search(q, null, null, top)` → category `"Product"`, title = `name`, subtitle = `"SKU: {sku}"`, path = `/products/{id}/edit`.
  4. `supplierRepository.search(q, null, top)` → category `"Supplier"`, title = `name`, subtitle = `mobile`, path = `/suppliers/{id}/edit`.
  5. `creditNoteRepository.search(q, null, null, null, storeId, top)` → category `"Credit Note"`, title = `voucherNumber`, subtitle = customer name, path = `/sales/credit-notes/{id}`.
  6. `debitNoteRepository.search(...)` → category `"Debit Note"`, title = `voucherNumber`, subtitle = supplier name, path = `/purchases/debit-notes/{id}`.
  7. `salesOrderRepository.search(...)` → category `"Sales Order"`, title = `orderNumber`, subtitle = customer name, path = `/sales/orders/{id}`.
  8. `purchaseOrderRepository.search(...)` → category `"Purchase Order"`, title = `orderNumber`, subtitle = supplier name, path = `/purchases/orders/{id}`.
  9. `receiptRepository.search(...)` → category `"Receipt"`, title = `receiptNumber`, subtitle = customer name, path = `/sales/receipts/{id}`.
  10. `paymentRepository.search(...)` → category `"Payment"`, title = `paymentNumber`, subtitle = supplier name, path = `/purchases/payments/{id}`.
- No overall result cap across categories: worst case = 10 categories × 5 = 50 results in one response.
- Combination/ranking: results are **not** re-ranked or scored across categories — they are simply concatenated in the fixed order above, each category internally ordered however that module's own repository `search` query orders it (typically by relevance/recency of that module, not documented here since it belongs to those modules).

### Repository (which repositories these services query into)

Neither `AlertService` nor `GlobalSearchService` owns any repository or entity of its own. They reuse repositories belonging to other modules:

- `InventoryRepository` (Inventory module) — `countOutOfStock`, `countLowStock`, `countReorderCandidates`.
- `OutstandingBillService` (Accounting module) — wraps its own repository queries for `customerOutstanding`/`supplierOutstanding` ageing buckets.
- `FinancialYearService` (Admin/Financial Year module) — `getCurrent()`.
- `AccountingHealthCheckService` (Accounting module) — `runHealthCheck()`.
- `SaleRepository`, `PurchaseRepository`, `ProductRepository`, `SupplierRepository`, `CreditNoteRepository`, `DebitNoteRepository`, `SalesOrderRepository`, `PurchaseOrderRepository`, `ReceiptRepository`, `PaymentRepository` — each module's own repository, called via its existing `search(...)` method.
- `StoreAccessService` — used only for store-scoping the search (`hasAllStoresAccess`, `getAccessibleStoreIds`).

## 5. Database

### Tables

This module has **no tables of its own**. It only reads from other modules' tables, via the repositories/services listed in §4:
- `inventory` (joined with `product`) — for stock counts.
- Whatever tables back `OutstandingBillService`'s ageing report (sales/purchases + payment/receipt allocations).
- `financial_year`.
- Whatever tables `AccountingHealthCheckService` inspects (journal entries, accounts, notes).
- `sale`, `purchase`, `product`, `supplier`, `credit_note`, `debit_note`, `sales_order`, `purchase_order`, `receipt`, `payment` — for Global Search.
- Dashboard reads from `product`, `customer`, `supplier`, `sale`, `purchase`, plus whatever Accounting's `/reports/dashboard` and GST's `/gst-reports/liability` endpoints read (control-account balances / GST return computations, owned by the Accounting/GST modules, not this one).

There is no `alert`, `notification`, or `search_index`/`search_log` table anywhere in the schema for this module — confirmed by `AlertService`'s own Javadoc ("no stored 'notification' rows") and by the absence of any repository field on either service besides the reused ones above.

### Relationships

N/A at this module's own level — no owned entities means no owned foreign keys or relationships. All relationships (Inventory→Product, Sale→Customer, Purchase→Supplier, etc.) belong to and are documented by their owning modules.

## 6. Validation

- `GlobalSearchController`/`GlobalSearchService`: no `@Valid`/DTO validation annotations. The only "validation" is the service's own `q.length() < 2` short-circuit (returns an empty result set rather than a 400).
- `AlertController`/`AlertService`: no request body/params at all, so nothing to validate.
- Neither module defines its own DTO field-level `@NotNull`/`@Pattern`/etc. constraints (both DTOs above are outbound-only response shapes). No `fieldErrors` map is ever produced by either endpoint — this module's CLAUDE.md-referenced field-error UX pattern (Register.tsx/UserFormModal.tsx) does not apply here since neither endpoint accepts a validated request body.

## 7. Authentication / Authorization

- Both `GET /api/alerts` and `GET /api/search` fall under `SecurityConfig`'s catch-all `.anyRequest().authenticated()` rule (only `/api/auth/**` and `/actuator/health` are `permitAll()`). So both require a valid JWT, but **neither controller method carries a `@PreAuthorize` annotation** — any authenticated user of any role can call them, there is no permission-specific gate at the controller level.
- `GlobalSearchService` performs its own **data-level** authorization for store-sensitive categories only (Sale, Sales Order, Purchase, Purchase Order, Receipt, Payment, Credit Note, Debit Note all take a `storeId` filter) via `resolveSearchStoreIdOrNull()` — see §4. Product and Supplier are not store-filtered by Global Search's own query calls (both pass `null` for store where a store param exists, matching those repositories' own `search` signatures — Product/Supplier are not store-scoped entities in this codebase).
- `AlertService`'s inventory counts are explicitly aggregated across **all** stores (`storeId = null` is hardcoded, with a code comment: "Alert Center itself isn't store-scoped yet") — i.e. a store-scoped user still sees company-wide inventory alert counts, not just their own store's.
- Frontend routing: `/dashboard` and `/alerts` are both wrapped only in `<ProtectedRoute>` (requires `user` to be non-null, i.e. logged in) — not in `<AdminRoute>` or `<ManagerRoute>`, so every authenticated role can reach both pages. `Dashboard.tsx` gates only specific *cards* client-side via `canSeePurchases = user?.role === 'ADMIN' || user?.role === 'STORE_MANAGER'`; this is a **frontend-only presentation gate** — the underlying `purchaseApi`, `accountingReportApi`, `gstReportApi` calls it skips for other roles are still reachable directly by any authenticated user unless those endpoints enforce their own `@PreAuthorize` (governed by those modules, not this one).

## 8. Permissions (DASHBOARD_VIEW etc, from RolePermissions.java)

- `Permission.DASHBOARD_VIEW` exists and is granted to **every** role defined in `RolePermissions.build()`: ADMIN (via `EnumSet.allOf`), STORE_MANAGER (inherits ADMIN's set minus the admin-tier exclusions, which don't include DASHBOARD_VIEW), ACCOUNTANT, SALES_USER, PURCHASE_USER, INVENTORY_USER, and STAFF all explicitly list `Permission.DASHBOARD_VIEW` in their `EnumSet.of(...)`.
- However, `DASHBOARD_VIEW` is **not actually enforced anywhere** in this module — grep of `AlertController`, `GlobalSearchController`, and the frontend route table shows no `@PreAuthorize("hasAuthority('PERM_DASHBOARD_VIEW')")` and no permission-gated `<Route>` for `/dashboard` or `/alerts`. It exists in the permission map (satisfying the "every role can see the dashboard" intent) but is not wired to a concrete check in this module's code.
- No `ALERT_VIEW` or `SEARCH_VIEW` (or similar) permission constant exists in `entity/Permission.java` — confirmed by grep; Alerts and Global Search have no permission of their own at all, only implicit "any authenticated user" access.

## 9. Transaction Handling

- `AlertService.getAlerts()` — `@Transactional(readOnly = true)`.
- `GlobalSearchService.search(query)` — `@Transactional(readOnly = true)`.
- Both are pure read/aggregation operations; neither writes to the database, so there is no commit/rollback behavior of interest — a single read-only transaction wraps each call for consistency across the several repository/service reads made within it.
- Dashboard, being frontend-composed, has no server-side transaction of its own; each of its underlying endpoint calls is transactional within its own owning module.

## 10. Error Handling

- Uncaught exceptions from either controller fall through to `GlobalExceptionHandler`'s generic `@ExceptionHandler(Exception.class)` → HTTP 500 with message `"An unexpected error occurred"`, unless a more specific handled exception type is thrown.
- The one concrete failure path identified: if no `FinancialYear` is currently marked `current` in the database, `FinancialYearService.getCurrent()` throws `BadRequestException("No current financial year is set")`, which `GlobalExceptionHandler.handleBadRequest` maps to **HTTP 400** with that exact message. Since `AlertService.getAlerts()` calls this unconditionally as step 6 of its alert list, **the entire `/api/alerts` call fails with a 400 in that state** — not just the "financial year nearing close" alert being skipped. This would also break the Dashboard's Alerts card (its `alertApi.list().catch(() => null)` swallows the error and just shows zero alerts, no error surfaced) and break the standalone `AlertsCenter` page (shows a `toast.error('Failed to load alerts')` via `parseApiError`).
- `GlobalSearch.tsx`'s search call has no error toast at all — a failed `/api/search` request silently resets `results` to `[]`, so a broken search backend looks identical to "no results found" from the user's perspective.
- `AlertsCenter.tsx` surfaces load failures via `toast.error(parseApiError(err, 'Failed to load alerts').message)`.

## 11. Audit Flow

NOT FOUND IN CURRENT CODEBASE. Neither `AlertController`/`AlertService` nor `GlobalSearchController`/`GlobalSearchService` writes any audit-log entry, and searching a search performed or an alert viewed is not recorded anywhere (there is no `AuditService`/`AuditLog` call in either service file). The codebase does have an Admin "Audit Trail" module (`AuditController`/`AUDIT_VIEW` permission referenced in `RolePermissions.java`) but it is unrelated to and not invoked by this module.

## 12. Important Side Effects

- **Alert Center failing entirely on a missing current financial year** (see §10) — a single missing/unset "current" FY row breaks the whole alert feed, not just that one alert.
- **Two independent, inconsistent low-stock computations**: `AlertService.countLowStock`/`countOutOfStock` (server-side, compares `currentStock` against `minStockLevel`, aggregated across all stores) vs. Dashboard's own client-side `getStockStatus()` (compares `product.stockQuantity` against `product.minStockLevel`, computed only over the first 200 products fetched by `productApi.list({size:200})`, i.e. can be **incomplete** if the catalog has more than 200 products, and uses a different field name/source than Inventory's per-store `currentStock`). The Alert Center's "Low Stock" count and the Dashboard's "Low Stock Products" tile are **not guaranteed to agree** — different data sources, different field, different scope (store-aggregated inventory rows vs. one flat product page).
- Global Search silently returns fewer results than exist (capped at 5 per category, 10 categories max) with no "show more"/pagination — a user searching a common term only ever sees the first 5 matches per document type.
- Global Search for a store-scoped user with multiple accessible stores and no store currently selected silently returns **zero** results for every store-sensitive category (Sale, Purchase, Credit Note, Debit Note, Sales Order, Purchase Order, Receipt, Payment) via the `-1L` sentinel — this fails silently/gracefully rather than erroring, but could confuse a user who doesn't realize their search coverage is incomplete.
- Dashboard's `Promise.allSettled` design means a failure in any one underlying call (e.g. GST liability failing) degrades that one card only (shows `0`/`—`) without blocking the rest of the page — by design, not a bug, but worth noting as it means partial/incomplete dashboard data can be shown without any visible error to the user.

## 13. Dependencies on Other Modules

- **Inventory** — `InventoryRepository.countOutOfStock/countLowStock/countReorderCandidates` (Alert Center); Dashboard also independently reads `Product.stockQuantity`/`minStockLevel` via `productApi`.
- **Accounting** — `OutstandingBillService` (customer/supplier ageing → receivable/payable overdue alerts), `AccountingHealthCheckService` (health-check findings → alerts), `accountingReportApi.dashboard()` (cash/bank/receivable/payable balances on Dashboard).
- **Admin / Financial Year** — `FinancialYearService.getCurrent()` (FY-nearing-close alert).
- **Sales** — `SaleRepository.search` (Global Search "Sale" category); `saleApi.list` (Dashboard's today's-sales total, recent sales list, 7-day chart).
- **Purchases** — `PurchaseRepository.search` (Global Search "Purchase" category); `purchaseApi.list` (Dashboard's purchase count and 7-day chart, manager/admin only).
- **Products** — `ProductRepository.search` (Global Search "Product" category); `productApi.list` (Dashboard's product count and low-stock list).
- **Suppliers** — `SupplierRepository.search` (Global Search "Supplier" category); `supplierApi.list` (Dashboard's supplier count).
- **Customers** — `customerApi.list()` (Dashboard's customer count only; Global Search has no "Customer" category — see §15).
- **Credit/Debit Notes** — `CreditNoteRepository.search`/`DebitNoteRepository.search` (Global Search categories).
- **Sales/Purchase Orders** — `SalesOrderRepository.search`/`PurchaseOrderRepository.search` (Global Search categories).
- **Receipts/Payments** — `ReceiptRepository.search`/`PaymentRepository.search` (Global Search categories).
- **GST** — `gstReportApi.liability()` (Dashboard's GST Liability card, manager/admin only).
- **Multi-Store / StoreAccessService** — store-scoping of Global Search results.

## 14. Key Operation Flows

**Loading the Alert Center**
1. UI: `AlertsCenter` mounts → `load()`.
2. Function: `alertApi.list()`.
3. API: `GET /api/alerts` (no params).
4. Controller: `AlertController.getAlerts()`.
5. Service: `AlertService.getAlerts()` — runs the 7 checks in §4 in order (inventory out-of-stock/low-stock/reorder counts → receivable/payable overdue → FY nearing close → accounting health check findings), building a `List<AlertItem>`.
6. Repository/other services: `InventoryRepository`, `OutstandingBillService`, `FinancialYearService`, `AccountingHealthCheckService`.
7. Database: `inventory`/`product` tables, ageing report source tables, `financial_year`, accounting journal/ledger tables.
8. Response: `List<AlertItem>` JSON (`severity`, `category`, `message`, `path`).
9. UI: split into critical/warning counts for stat tiles, full list rendered with click-to-navigate via `path`.

**Performing a Global Search**
1. UI: user types in `GlobalSearch.tsx`'s input; `query` state updates.
2. Function: debounced (300ms) `globalSearchApi.search(trimmed)`, only once `trimmed.length >= 2`.
3. API: `GET /api/search?q={trimmed}`.
4. Controller: `GlobalSearchController.search(q)`.
5. Service: `GlobalSearchService.search(q)` — trims/length-checks `q`, resolves `storeId` via `resolveSearchStoreIdOrNull()`, then queries the 10 repositories in §4's fixed order, each capped to 5 results, building one flat `List<GlobalSearchResultItem>`.
6. Repository: `SaleRepository`, `PurchaseRepository`, `ProductRepository`, `SupplierRepository`, `CreditNoteRepository`, `DebitNoteRepository`, `SalesOrderRepository`, `PurchaseOrderRepository`, `ReceiptRepository`, `PaymentRepository` — each module's own existing `search(...)`.
7. Database: `sale`, `purchase`, `product`, `supplier`, `credit_note`, `debit_note`, `sales_order`, `purchase_order`, `receipt`, `payment` tables.
8. Response: `GlobalSearchResponse` (`query`, `results[]`).
9. UI: results shown grouped by category pill in the dropdown; click navigates to `item.path`.

**Loading the Dashboard** (fan-out to multiple backend endpoints — Dashboard has no backend of its own)
1. UI: `Dashboard` mounts (or `canSeePurchases` changes) → `load()`.
2. Functions/APIs fired in parallel via `Promise.allSettled` — batch 1 (all roles): `productApi.list({size:200})` → `GET /api/products`; `customerApi.list()` → `GET /api/customers`; `supplierApi.list({size:1,status:ACTIVE})` → `GET /api/suppliers`; `saleApi.list(...)` ×3 → `GET /api/sales` (today's totals, recent 5, 7-day chart).
3. Batch 2 (manager/admin only, `canSeePurchases`): `purchaseApi.list(...)` ×2 → `GET /api/purchases`; `accountingReportApi.dashboard()` → `GET /api/accounting/reports/dashboard`; `gstReportApi.liability(period)` → `GET /api/gst-reports/liability`.
4. Independently: `alertApi.list().catch(() => null)` → `GET /api/alerts`.
5. Controllers: `ProductController`, `CustomerController`, `SupplierController`, `SaleController`, `PurchaseController`, `AccountingReportController` (or equivalent), `GstReportController`, `AlertController` — each belonging to its own module, each with its own service/repository/DB chain (not this module's).
6. Response: each endpoint's own response shape (paged lists, `AccountingDashboardResponse`, `GstLiabilityResponse`, `AlertItem[]`).
7. UI: each `Promise.allSettled` result is checked individually (`status === 'fulfilled'`) and mapped into its own piece of Dashboard state, feeding the stat cards, two bar charts, Alerts card, Recent Sales list, and Low Stock Products list described in §2.

## 15. Manual Changes

- **Adding a new alert type/condition**: add a new block inside `AlertService.getAlerts()` (`backend/src/main/java/com/storehub/service/AlertService.java`), following the existing pattern — inject/reuse the owning module's existing repository or service (do not write new persistence for the alert itself, per the class's own documented design), compute a count/condition, and conditionally `alerts.add(AlertItem.builder().severity(...).category(...).message(...).path(...).build())`. Choose `severity` as `"CRITICAL"` or `"WARNING"` (the only two values the frontend `AlertSeverity` type and both `AlertsCenter.tsx`/`Dashboard.tsx` UIs recognize — any other string would render with no badge styling since the frontend only special-cases those two). Set `path` to the frontend route the alert should deep-link to. No frontend change is needed unless a new severity value is introduced.
- **Adding a new entity to Global Search's scope**: (1) confirm/add a `search(...)` repository method on that entity's existing repository, matching the pattern in `SaleRepository`/`ProductRepository`/etc. (paged, filterable); (2) inject that repository into `GlobalSearchService` via constructor (`@RequiredArgsConstructor` — just add the field); (3) add a new loop block in `GlobalSearchService.search()` following the existing 10 blocks, choosing a new `category` string, mapping `title`/`subtitle`/`path` appropriately, and deciding whether the entity is store-sensitive (pass `storeId` if so, matching the Sale/Purchase/etc. pattern; omit if the entity isn't store-scoped, matching Product/Supplier). No DTO change needed (`GlobalSearchResultItem` is already generic). No frontend change needed — `GlobalSearch.tsx` renders any `category` string generically.
- **Adding a new Dashboard card**: edit `frontend/src/pages/Dashboard.tsx` only. If the data is already available from an existing owning-module endpoint (preferred, matches this module's established "compose, don't own" pattern), add that API call to one of the two `Promise.allSettled` batches (or a new independent call, per the `alertApi` pattern, if it should never block the rest of the page) and a new entry in the `stats`/`financialStats` array or a new `Card`. If no existing summary endpoint covers the needed data, a new endpoint would need to be added to the **owning module** (e.g. a new field on `AccountingDashboardResponse` if it's an accounting number, or a new endpoint on that module's controller) — this module's own `AlertController`/`GlobalSearchController` would only be the right place if the new card is itself an alert-like or search-like aggregate.
- **Modules affected by such changes**: any module whose entity/repository is touched by (1) or (2) above — e.g. adding a Sales Order alert would touch the Sales module's read path (via its repository) plus `AlertService`; adding a Customer category to Global Search would touch the Customer module's repository (it would need to gain a `search(...)` method, since `CustomerRepository` is not currently one of the 10 repositories queried) plus `GlobalSearchService`. Adding a new Dashboard card touches whichever module owns the underlying data plus `Dashboard.tsx` — never this module's own backend, since it has none.
