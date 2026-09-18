# StoreHub — Codebase Documentation

This is module-by-module developer documentation for the StoreHub ERP codebase, generated **directly from the current source code** at `/home/user/Myproject` (backend: Spring Boot / Java under `backend/src/main/java/com/storehub`; frontend: React + TypeScript + Vite under `frontend/src`). Nothing here was invented — every class name, method name, URL, table name, and validation rule quoted in these documents was read directly from the actual files. Where something was asked for but not found in the code, the module docs say so explicitly: `NOT FOUND IN CURRENT CODEBASE`.

**This documentation is read-only.** No application source file was modified, refactored, or renamed while producing it.

## How this was produced

The codebase was scanned to identify every controller (46), service (62), entity (~120 incl. enums), repository (58), and DTO (180) on the backend, and every page/component/API-client/type file on the frontend. These were grouped into 13 functional modules based on how the actual code is organized (not a guessed taxonomy), and each module was documented independently and in depth, including full UI → Function → API → Controller → DTO → Service → Repository → Database → Response → UI traces for every important operation, plus a "Manual Changes" section explaining exactly how to extend or modify that module yourself.

## Module Index

| # | Module | File |
|---|---|---|
| 1 | Authentication & Authorization | [01-authentication-authorization.md](./01-authentication-authorization.md) |
| 2 | User & Employee Management | [02-user-employee-management.md](./02-user-employee-management.md) |
| 3 | Store / Multi-Branch (Multi-Store) | [03-store-multi-branch.md](./03-store-multi-branch.md) |
| 4 | Lookup / Simple Masters (Country, State, City, Zone, Currency, Nationality, Unit, Item Group, HSN) | [04-lookup-masters.md](./04-lookup-masters.md) |
| 5 | Party / Customer / Supplier | [05-party-customer-supplier.md](./05-party-customer-supplier.md) |
| 6 | Product / Item Master & Inventory | [06-product-inventory.md](./06-product-inventory.md) |
| 7 | Purchase (Order, Bill, Kacchi Purchase, Payment, Debit Note) | [07-purchase.md](./07-purchase.md) |
| 8 | Sales (Order, Bill, Kacchi Sale, POS, Receipt, Credit Note) | [08-sales.md](./08-sales.md) |
| 9 | Accounting Core Engine (Chart of Accounts, Journal, Voucher Numbering, Ledger) | [09-accounting-core.md](./09-accounting-core.md) |
| 10 | Accounting Reports | [10-accounting-reports.md](./10-accounting-reports.md) |
| 11 | GST | [11-gst.md](./11-gst.md) |
| 12 | Expense, Cash Management, Day Closing, Payment Method, Financial Year, Audit Trail | [12-expense-cash-fy-audit.md](./12-expense-cash-fy-audit.md) |
| 13 | Alerts, Global Search & Dashboard | [13-alerts-search-dashboard.md](./13-alerts-search-dashboard.md) |

Each module file follows the same 15-section structure: Overview, Frontend, API Calls, Backend (Controllers/DTOs/Services/Repository), Database, Validation, Authentication/Authorization, Permissions, Transaction Handling, Error Handling, Audit Flow, Important Side Effects, Dependencies on Other Modules, Key Operation Flows, and Manual Changes.

---

## Complete Project Structure

```
Myproject/
├── backend/                                   Spring Boot 3 / Java (Maven)
│   └── src/main/java/com/storehub/
│       ├── controller/     46 REST controllers  — one per module/sub-area
│       ├── service/        62 services          — business logic + 15 helper/enum-like classes
│       ├── entity/         ~120 JPA entities + enums
│       ├── repository/     58 Spring Data JPA repositories
│       ├── dto/             180 request/response DTOs
│       ├── exception/      24 custom exceptions + GlobalExceptionHandler
│       ├── security/       JWT auth (JwtUtil, JwtAuthenticationFilter, UserPrincipal, CustomUserDetailsService)
│       ├── config/         SecurityConfig and other Spring config
│       └── util/           GstinValidator, BarcodeUtil, SecurityUtil, etc.
│   └── src/test/java/com/storehub/service/     backend automated test suites (per phase/step)
│
├── frontend/                                   React 18 + TypeScript + Vite
│   └── src/
│       ├── pages/           ~105 page components, organized by module folder
│       │   ├── accounting/, accounting/reports/, admin/, gst/, inventory/, masters/, purchases/, sales/
│       ├── components/      shared UI building blocks + a few module-specific dialogs
│       │   ├── layout/      MainLayout, Sidebar, Topbar, GlobalSearch, StoreSwitcher, nav-items.ts
│       │   ├── masters/     MasterCrudPage.tsx (shared generic master-page component)
│       │   └── ui/          shadcn/ui-style primitives (Button, Table, Dialog, Select, ...)
│       ├── api/              31 API client files (one per module/entity), all built on axios.ts
│       ├── types/            26 TypeScript type files matching backend DTOs
│       ├── context/          AuthContext.tsx (auth/session/permissions/store-selection state)
│       ├── hooks/             useUnsavedChangesGuard.ts
│       └── App.tsx           all route definitions + route guards
│
└── docs/codebase/                              ← this documentation set
```

---

## Complete API List

Grouped by controller, with each controller's base `@RequestMapping` path. Method + sub-path combine with the base to form the full URL (e.g. `AccountController` base `/api/accounts` + `GET /{id}` = `GET /api/accounts/{id}`). Full request/response shapes and `@PreAuthorize` values are in each module's own doc — this is the master index.

| Controller | Base Path | Endpoints (Method + sub-path) | Module Doc |
|---|---|---|---|
| AuthController | `/api/auth` | POST /register · POST /login · POST /logout · GET /me · GET /my-stores · PUT /current-store | 01 |
| PermissionController | `/api/permissions` | GET (list all permissions) | 01 |
| RoleController | `/api/roles` | GET (list roles) · GET /{role}/permissions | 01 |
| UserController | `/api/users` | GET · GET /{id} · GET /{id}/effective-permissions · POST · PUT /{id} · PATCH /{id}/status · POST /{id}/reset-password · GET /{id}/stores · PUT /{id}/stores · PUT /me/password | 02 |
| EmployeeController | `/api/masters/employees` | GET · GET /{id} · POST /generate-code · POST · PUT /{id} · PATCH /{id}/activate · PATCH /{id}/deactivate · GET /{id}/stores · PUT /{id}/stores | 02 |
| StoreController | `/api/masters/stores` | GET · GET /{id} · GET /{id}/gst-context · POST /generate-code · POST · PUT /{id} · PATCH /{id}/activate · PATCH /{id}/deactivate | 03 |
| StockTransferController | `/api/stock-transfers` | GET · GET /{id} · POST · PATCH /{id}/approve · PATCH /{id}/dispatch · PATCH /{id}/receive · PATCH /{id}/cancel | 03 |
| StoreComparisonController | `/api/reports/store-comparison` | GET | 03 |
| CountryController | `/api/masters/countries` | GET · GET /{id} · POST · PUT /{id} · PATCH /{id}/activate · PATCH /{id}/deactivate | 04 |
| StateController | `/api/masters/states` | (same CRUD shape as Country) | 04 |
| CityController | `/api/masters/cities` | (same CRUD shape) | 04 |
| ZoneController | `/api/masters/zones` | (same CRUD shape) | 04 |
| CurrencyController | `/api/masters/currencies` | (same CRUD shape) | 04 |
| NationalityController | `/api/masters/nationalities` | (same CRUD shape) | 04 |
| UnitController | `/api/masters/units` | (same CRUD shape) | 04 |
| ItemGroupController | `/api/masters/item-groups` | (same CRUD shape) | 04 |
| HsnController | `/api/masters/hsn` | (same CRUD shape, plus nested tax-rate rows in the request body) | 04 |
| PartyController | `/api/masters/parties` | GET · GET /{id} · POST · PUT /{id} · PATCH /{id}/activate · PATCH /{id}/deactivate | 05 |
| CustomerController | `/api/customers` | GET · POST (no update/deactivate endpoints — see module 05) | 05 |
| SupplierController | `/api/suppliers` | GET · GET /{id} · GET /{id}/purchases · POST · PUT /{id} · PATCH /{id}/activate · PATCH /{id}/deactivate | 05 |
| ProductController | `/api/products` | GET · GET /{id} · GET /barcode/{barcode} · POST /generate-sku · POST /generate-barcode · POST · PUT /{id} · PATCH /{id}/activate · PATCH /{id}/deactivate · GET /export · POST /import | 06 |
| CategoryController | `/api/categories` | GET · GET /{id} · POST · PUT /{id} · PATCH /{id}/activate · PATCH /{id}/deactivate | 06 |
| InventoryController | `/api/inventory` | GET · GET /summary · GET /history · GET /{id} · GET /{id}/history · POST /adjust · GET /export | 06 |
| PurchaseController | `/api/purchases` | GET · GET /{id} · POST · PUT /{id} · POST /{id}/post · PATCH /{id}/cancel · DELETE /{id} | 07 |
| PurchaseOrderController | `/api/purchase-orders` | GET · GET /{id} · POST · PUT /{id} · PATCH /{id}/status · PATCH /{id}/cancel | 07 |
| PaymentController | `/api/payments` | GET · GET /{id} · GET /outstanding/{supplierId} · POST · DELETE /{id} | 07 |
| PurchaseSummaryController | `/api/purchase-summary` | GET | 07 |
| DebitNoteController | `/api/debit-notes` | GET · GET /{id} · POST · POST /{id}/post · PATCH /{id}/cancel | 07 |
| SaleController | `/api/sales` | GET · GET /{id} · POST · PUT /{id} · POST /{id}/post · PATCH /{id}/cancel · DELETE /{id} | 08 |
| SalesOrderController | `/api/sales-orders` | GET · GET /{id} · POST · PUT /{id} · PATCH /{id}/status · PATCH /{id}/cancel | 08 |
| ReceiptController | `/api/receipts` | GET · GET /{id} · GET /outstanding/{customerId} · POST · DELETE /{id} | 08 |
| SalesSummaryController | `/api/sales-summary` | GET | 08 |
| CreditNoteController | `/api/credit-notes` | GET · GET /{id} · POST · POST /{id}/post · PATCH /{id}/cancel | 08 |
| AccountController | `/api/accounts` | GET · GET /groups · GET /{id} · POST · PUT /{id} | 09 |
| AccountingController | `/api/accounting/journals` | GET · GET /{id} · POST | 09 |
| AccountingReportController | `/api/accounting/reports` | GET /dashboard · /day-book · /account-ledger/{accountId} · /trial-balance · /cash-book · /bank-book · /party-ledger · /receivable · /payable · /outstanding/customers · /outstanding/suppliers · /profit-loss · /balance-sheet · /account-summary · /expense-summary · /income-summary · /health-check | 10 |
| GstReportController | `/api/gst-reports` | GET /gstr1 · /purchase · /gstr3b · /output · /input · /hsn · /tax-rate · /liability · /reconciliation | 11 |
| BusinessGstConfigController | `/api/masters/business-gst-config` | GET · PUT | 11 |
| ExpenseController | `/api/expenses` | GET · GET /reports/summary · GET /{id} · POST · PUT /{id} · POST /{id}/post · PATCH /{id}/cancel | 12 |
| ExpenseCategoryController | `/api/masters/expense-categories` | GET · GET /active · GET /{id} · POST · PUT /{id} · PATCH /{id}/activate · PATCH /{id}/deactivate | 12 |
| CashTransactionController | `/api/cash-transactions` | GET · GET /{id} · POST · POST /{id}/post · PATCH /{id}/cancel | 12 |
| DayClosingController | `/api/day-closing` | GET /summary · GET /history · POST /close | 12 |
| PaymentMethodController | `/api/payment-methods` | GET · POST · PUT /{id} · PATCH /{id}/activate · PATCH /{id}/deactivate | 12 |
| FinancialYearController | `/api/financial-years` | GET · GET /current · GET /{id} · GET /{id}/summary · POST · PATCH /{id}/open · PATCH /{id}/close · PATCH /{id}/mark-current | 12 |
| AuditLogController | `/api/audit-logs` | GET | 12 |
| AlertController | *(explicit path on the method)* | GET /api/alerts | 13 |
| GlobalSearchController | *(explicit path on the method)* | GET /api/search | 13 |

**Note:** all 9 lookup-master controllers (Country/State/City/Zone/Currency/Nationality/Unit/ItemGroup/Hsn) share the exact same 6-endpoint CRUD shape: `GET` (list), `GET /{id}`, `POST` (create), `PUT /{id}` (update), `PATCH /{id}/activate`, `PATCH /{id}/deactivate` — this is the `MasterCrudPage.tsx` pattern, see module 04.

---

## Complete Database / Table List

64 JPA entities map to 64 tables (from each entity's `@Table(name = "...")`). Grouped by owning module:

**Authentication/User/Employee/Store** — `users`, `employees`, `employee_code_sequence`, `employee_stores`, `stores`, `store_code_sequence`, `user_stores`

**Lookup Masters** — `countries`, `states`, `cities`, `zones`, `currencies`, `nationalities`, `units`, `item_groups`, `hsn_codes`, `hsn_tax_rates`

**Party/Customer/Supplier** — `parties`, `party_addresses`, `customers`, `suppliers`

**Product/Inventory** — `products`, `categories`, `item_sku_sequence`, `item_barcode_sequence`, `inventory`, `stock_history`

**Purchase** — `purchases`, `purchase_items`, `purchase_orders`, `purchase_order_items`, `payments`, `payment_allocations`, `supplier_ledger_entries`, `purchase_gst_entries`, `debit_notes`, `debit_note_items`

**Sales** — `sales`, `sale_items`, `sales_orders`, `sales_order_items`, `receipts`, `receipt_allocations`, `customer_ledger_entries`, `gst_entries`, `credit_notes`, `credit_note_items`

**Accounting Core** — `accounts`, `account_groups`, `journal_headers`, `journal_details`, `voucher_sequences`

**GST** — `gst_transactions`, `business_gst_config`

**Expense/Cash/FY/Audit** — `expenses`, `expense_categories`, `cash_transactions`, `cash_ledger_entries`, `day_closings`, `payment_methods`, `financial_years`, `audit_logs`

**Stock Transfer (Multi-Store)** — `stock_transfers`, `stock_transfer_items`

**Accounting Reports module** — owns **no tables**; it is pure read-aggregation over the tables above (see module 10).

Every `_id`/entity-name-typed column is a FK to the corresponding table above; `store_id` appears on 13+ transactional tables as part of the Multi-Store design (see module 03) — the module docs give the exact FK list per table.

---

## Complete Frontend Route List

All routes are defined in `frontend/src/App.tsx`. Every route (except `/login`, `/register`) sits under `ProtectedRoute` (requires a valid session) and `MainLayout` (sidebar shell). Additional guard wrappers are noted.

| Path | Page Component | Guard |
|---|---|---|
| `/login` | Login.tsx | none (public) |
| `/register` | Register.tsx | none (public) |
| `/dashboard` | Dashboard.tsx | ProtectedRoute |
| `/alerts` | AlertsCenter.tsx | ProtectedRoute |
| `/unauthorized` | Unauthorized.tsx | ProtectedRoute |
| `/users` | Users.tsx | AdminRoute |
| `/admin/financial-years` | FinancialYears.tsx | AdminRoute |
| `/admin/audit-trail` | AuditTrail.tsx | AdminRoute |
| `/admin/roles-permissions` | RolesAndPermissions.tsx | AdminRoute |
| `/purchases` + 15 sub-routes (orders, bills, payments, kacchi, debit-notes — list/new/:id/:id/edit) | PurchaseHub.tsx + purchases/* | ManagerRoute(+PURCHASE_USER) |
| `/sales` , `/sales/pos` | SalesHub.tsx, Pos.tsx | ProtectedRoute (no manager gate) |
| `/sales/orders`, `/sales/bills`, `/sales/kacchi` (list/new/:id) | sales/* | ProtectedRoute |
| `/sales/orders/:id/edit`, `/sales/bills/:id/edit`, `/sales/kacchi/:id/edit` | sales/*Form.tsx | ManagerRoute(+SALES_USER) — edit only |
| `/sales/receipts` (list/new/:id) | Receipts.tsx + ReceiptForm/Detail | ProtectedRoute |
| `/sales/credit-notes` (list/new/:id) | CreditNotes.tsx + Form/Detail | ProtectedRoute |
| `/accounting` + 15 sub-routes (accounts, journals, trial-balance, expenses, cash-transactions, payment-methods, day-closing, reports/*) | accounting/* | ManagerRoute(+ACCOUNTANT) |
| `/accounting/journals/new` | JournalEntryForm.tsx | AdminRoute (nested inside the ACCOUNTANT gate) |
| `/gst-reports` + 9 sub-routes | gst/* | ManagerRoute(+ACCOUNTANT) |
| `/products` | Products.tsx | ProtectedRoute |
| `/products/new`, `/products/:id/edit` | ProductForm.tsx | ManagerRoute |
| `/inventory` | Inventory.tsx | ProtectedRoute |
| `/inventory/stock-transfers` (list/new/:id) | inventory/StockTransfer*.tsx | ProtectedRoute |
| `/suppliers` (list/new/:id/edit) | Suppliers.tsx, SupplierForm.tsx | ManagerRoute |
| `/masters` + 14 sub-routes (stores, currencies, countries, states, cities, zones, nationalities, units, item-groups, hsn, employees, parties, business-gst-config, expense-categories) | MastersDashboard.tsx + masters/* | ManagerRoute |
| `/customers`, `/payments`, `/reports` | ComingSoon.tsx | ProtectedRoute (disabled placeholder modules — see `DISABLED_MODULES` in App.tsx) |
| `*` (catch-all) | redirects to `/dashboard` | — |

`ManagerRoute` = ADMIN or STORE_MANAGER (plus any `extraRoles` listed). `AdminRoute` = ADMIN only. Full guard implementation is in module 01.

---

## Important Classes & Services (Central / Common)

These are used by **multiple** modules and are the highest-leverage files in the codebase — changing them has wide blast radius.

| Class | File | Role |
|---|---|---|
| `AuditService` | `backend/.../service/AuditService.java` | Central audit-log writer (`log(...)`, 8-arg and 9-arg overloads). Called from ~18+ services across nearly every module for create/update/status-change events. Writes to `audit_logs`. |
| `AccountingService` | `backend/.../service/AccountingService.java` | The double-entry posting engine. Every transactional module (Sale, Purchase, Receipt, Payment, Expense, CashTransaction, CreditNote, DebitNote) posts through this rather than writing journal rows directly. 8 distinct caller modules found. |
| `VoucherNumberService` | `backend/.../service/VoucherNumberService.java` | Centralized FY-aware document numbering for every voucher type. 11 distinct caller modules found. |
| `LedgerService` | `backend/.../service/LedgerService.java` | Records Customer/Supplier ledger entries and Expense credit/reversal entries. 9 distinct caller modules found. |
| `StoreAccessService` | `backend/.../service/StoreAccessService.java` | The single authorization choke point for the ALL_STORES vs ASSIGNED_STORES model. Every store-scoped read/write across every module resolves its storeId through here. |
| `InventoryService.applyMovement` | `backend/.../service/InventoryService.java` | The only path by which stock is ever mutated. Called by Purchase, Sale, Credit Note, Debit Note, Stock Transfer. |
| `GstCalculationService` | `backend/.../service/GstCalculationService.java` | Central tax-split (CGST/SGST/IGST) calculation logic, reused by Sale/Purchase/Notes/Expense. |
| `GstTransactionSyncService` | `backend/.../service/GstTransactionSyncService.java` | Syncs/reverses `gst_transactions` rows whenever Sale/Purchase/CreditNote/DebitNote/Expense post or cancel. |
| `PermissionService` / `RolePermissions` | `backend/.../service/PermissionService.java`, `RolePermissions.java` | The single source of truth for which Role has which Permission; backs every `@PreAuthorize("hasAuthority('PERM_...')")` and `UserPrincipal.getAuthorities()`. |
| `FinancialYearService` | `backend/.../service/FinancialYearService.java` | `resolveForDate`/`resolveOpenForPosting` gate almost every posting flow; auto-seeds a FY covering "today" at startup. |
| `GlobalExceptionHandler` | `backend/.../exception/GlobalExceptionHandler.java` | Maps every custom + framework exception to the standard `ApiError` JSON shape and HTTP status across the whole API surface. |
| `SecurityConfig` + `JwtAuthenticationFilter` + `JwtUtil` + `UserPrincipal` | `backend/.../config/`, `backend/.../security/` | The entire authentication/authorization plumbing every request passes through. |
| `AuthContext.tsx` | `frontend/src/context/AuthContext.tsx` | Frontend session state: current user, permissions set, `myStores`/`switchStore`, `hasPermission()` — read by nearly every page/guard. |
| `axios.ts` | `frontend/src/api/axios.ts` | The single configured axios instance every `api/*.ts` client is built on (base URL, auth header injection, interceptors). |
| `MasterCrudPage.tsx` | `frontend/src/components/masters/MasterCrudPage.tsx` | The shared generic component every simple master page (9 of them) is built on top of. |
| `ProtectedRoute` / `AdminRoute` / `ManagerRoute` | `frontend/src/components/*.tsx` | Frontend route guards every protected route in `App.tsx` is wrapped in. |

---

## Module Dependencies

Read as "the row depends on the column" (row's code calls into column's services/data).

| Module ↓ depends on → | Auth | User/Emp | Store | Masters | Party | Product/Inv | Purchase | Sales | Acct Core | Acct Reports | GST | Expense/Cash/FY/Audit |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| User/Employee | ✓ | | ✓ | | | | | | | | | |
| Store | ✓ | ✓ | | | | ✓ | | | | | | |
| Party/Customer/Supplier | | | | ✓ | | | | | | | | |
| Product/Inventory | ✓ | | ✓ | | | | | | | | | |
| Purchase | ✓ | | ✓ | | ✓ | ✓ | | | ✓ | | ✓ | ✓ (FY) |
| Sales | ✓ | | ✓ | | ✓ | ✓ | | | ✓ | | ✓ | ✓ (FY) |
| Accounting Core | | | ✓ | | | | | | | | | ✓ (FY) |
| Accounting Reports | | | | | | | ✓ | ✓ | ✓ | | | ✓ |
| GST | | | ✓ | | ✓ | | ✓ | ✓ | | | | ✓ (Expense) |
| Expense/Cash/FY/Audit | | | ✓ | | ✓ | | | | ✓ | | ✓ | |
| Alerts/Search/Dashboard | | | | | | ✓ | ✓ | ✓ | ✓ | | ✓ | |

**Nearly everything depends on Auth** (JWT/permissions) and **Store** (storeId resolution) — they are not shown as columns receiving many checks above only because the table would be all-✓; treat both as implicit universal dependencies. **Accounting Core and GST are depended on by every transactional module but depend on almost nothing themselves** (only Financial Year) — they are the highest fan-in, lowest fan-out modules, i.e. the most sensitive to break.

---

## Critical Files

Files where a change ripples across many modules — treat edits here with extra care and re-check every module doc's "Dependencies on Other Modules" section before changing:

1. **`AccountingService.java`** — every financial posting in the system goes through it.
2. **`VoucherNumberService.java`** — every voucher-numbered document depends on its exact algorithm/format.
3. **`StoreAccessService.java`** — every store-scoped authorization decision in the app.
4. **`InventoryService.java` (`applyMovement`, `getCurrentStock`)** — the only legitimate way stock changes; bypassing it anywhere would desync `inventory`/`stock_history`.
5. **`RolePermissions.java`** — the single source of truth for every `@PreAuthorize("hasAuthority('PERM_...')")` check in the app; a typo here silently changes access control everywhere.
6. **`GlobalExceptionHandler.java`** — the error-shape contract every frontend `parseApiError` call relies on.
7. **`SecurityConfig.java` / `JwtAuthenticationFilter.java`** — the entire request pipeline's authentication.
8. **`Permission.java` (enum)** — adding/removing a value here requires touching `RolePermissions.java`, every relevant `@PreAuthorize`, `UserPrincipal.getAuthorities()`, and the frontend `Role`/permission types.
9. **`App.tsx`** — the single frontend routing table; every new page must be registered here or it's unreachable.
10. **`axios.ts`** — every single frontend API call goes through this one configured instance.
11. **`MasterCrudPage.tsx`** — 9 master pages share this component; a breaking prop change breaks all 9 at once.

---

## Manual Change Quick Reference

| I want to... | Primary files to touch | Also check |
|---|---|---|
| Add a field to an existing entity/master | Entity class + its DTO(s) + service create/update methods + frontend type + frontend form | The module's own "Manual Changes" section for exact line-level guidance |
| Add a brand-new simple lookup master | New entity+status enum, repository, DTO(s), service, controller, frontend type, `mastersApi.ts` export, new page via `MasterCrudPage.tsx`, `MastersDashboard.tsx` card, route in `App.tsx` | Module 04's step-by-step checklist |
| Add a new Role or Permission | `Role.java` / `Permission.java` enums, `RolePermissions.java`, relevant `@PreAuthorize` values, `UserPrincipal.getAuthorities()`, frontend `Role`/permission types | Module 01 |
| Add a new Store-scoped field to a module | That entity's `store` FK, its create-request DTO's `storeId`, its service calling `storeAccessService.resolveEffectiveStoreId`, its list/report queries calling `resolveViewableStoreId` | Module 03 |
| Change stock movement logic | `InventoryService.applyMovement`/`StockMovementType` enum | Every caller: Purchase, Sale, Credit/Debit Note, Stock Transfer services (module 06's caller list) |
| Add a new voucher/document type | `VoucherType`/`VoucherDocType` enums, `VoucherNumberService`, the owning module's service | Module 09 |
| Change GST calculation/split logic | `GstCalculationService.java` | Every module that calls it (Sale, Purchase, Notes, Expense) — module 11 |
| Add a new accounting report | New service method, `AccountingReportController` endpoint, frontend api export + page + route | Module 10 |
| Change JWT expiry/claims or login flow | `JwtUtil.java`, `AuthService.java`, `SecurityConfig.java` | Module 01 |
| Add a new Permission-gated frontend action | Backend `@PreAuthorize`, `RolePermissions.java`, frontend `hasPermission()` check in the component | Modules 01 + the relevant feature module |
| Fix the Alert Center 400-on-no-current-FY bug | `AlertService.java`'s call into `FinancialYearService.getCurrent()` | Module 13 |
| Enforce `REPORT_VIEW`/`REPORT_EXPORT` server-side (currently unenforced) | Add `@PreAuthorize` to `AccountingReportController` | Module 10 |
| Enforce `GST_VIEW`/`GST_REPORT`/`GST_EXPORT` server-side (currently unenforced) | Add `@PreAuthorize` to `GstReportController` | Module 11 |

---

## Known Gaps & Inconsistencies Found During Documentation

These are real, code-grounded observations surfaced while writing this documentation — not opinions, and not exhaustive. Each is cited in its owning module's doc with exact file/method references.

- **Permission definitions that exist but aren't enforced server-side:** `MASTER_VIEW` (module 04), `REPORT_VIEW`/`REPORT_EXPORT` (module 10 — `AccountingReportController` has zero `@PreAuthorize` annotations), `GST_VIEW`/`GST_REPORT`/`GST_EXPORT` (module 11 — only `GST_CONFIG` is actually checked). These permissions are defined in `RolePermissions.java` and granted to roles, but nothing currently reads them.
- **Frontend/backend permission mismatch:** `ManagerRoute` gates the entire `/masters/*` frontend route tree to ADMIN/STORE_MANAGER only, so a role like ACCOUNTANT or SALES_USER that legitimately holds `MASTER_VIEW` on the backend can never reach those pages through the UI (module 04).
- **Missing `@PreAuthorize` on some controllers/endpoints:** `CustomerController` (module 05, any authenticated user can create/list customers), `AccountController`/`AccountingController` gate on `hasRole('ADMIN')` directly rather than the `ACCOUNT_VIEW`/`JOURNAL_*` permissions defined for them (module 09), `DebitNoteController`'s GET endpoints and all of `PurchaseSummaryController` (module 07), `EmployeeController` has no class-level guard unlike `UserController` (module 02).
- **Audit trail gaps:** `PurchaseOrderService`, `SalesOrderService`, `ExpenseCategoryService`, `PaymentMethodService`, and the entire Accounting Core engine (`AccountingService`/`AccountService`/`LedgerService`/`VoucherNumberService`) make no `AuditService.log(...)` calls — confirmed by grep, not just absence in the sampled files.
- **`Party` master is effectively dormant:** it has zero incoming FKs from any transactional entity and is only used by its own master page — `Customer`/`Supplier` (separate entities) are what Sales/Purchase actually reference (module 05).
- **Real bug:** `AlertController`'s `/api/alerts` will 400 entirely whenever there is no "current" Financial Year, because `AlertService` calls `FinancialYearService.getCurrent()` (which throws rather than returning null/empty) without a guard (module 13).
- **Frontend/backend enum drift:** `frontend/src/types/auditLog.ts`'s `AuditAction` union is missing `DAY_CLOSE`, which exists in the backend enum (module 12); `frontend/src/types/accounting.ts`'s `VoucherType` union and the Journals filter omit `EXPENSE` and `CASH_TRANSACTION`, which the backend enum has (module 09).
- **Dead/unwired code paths:** no frontend caller was found for `userApi.changeOwnPassword` or for the Employee `assignStores`/`getAssignedStores` endpoints, despite the backend supporting them (module 02); no frontend page calls `purchaseApi.cancel` (soft cancel) — the UI's "Delete" action always uses the hard-delete endpoint instead (module 07); `PurchaseStatus.PENDING` is defined and set as the JPA default but no code path ever assigns it after creation (module 07).
- **GST reports never send `storeId`:** every backend GST report endpoint and query supports a `storeId` filter (Multi-Store), but none of the 9 frontend GST report pages actually pass one (module 11).

None of these were fixed as part of this documentation task, per the read-only instruction — they are flagged here for your awareness and are each documented in more depth in their owning module file.
