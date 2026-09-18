# Store / Multi-Branch Module

## 1. Overview

The Store (aka Branch/Warehouse) module gives StoreHub multi-location support. It is deliberately **not** a new top-level module — per `StoreController`'s own doc comment, it "lives under Master → Store/Branch" (Multi-Store spec section 1/4/68). A `Store` (`backend/src/main/java/com/storehub/entity/Store.java`) represents an operational location — `StoreType` is `STORE`, `WAREHOUSE`, or `HEAD_OFFICE` — that every store-attributed transaction (Sale, Purchase, Inventory, Expense, CashTransaction, etc.) is recorded against.

**ALL_STORES vs ASSIGNED_STORES authorization model** (from `StoreAccessService`'s class doc, spec section 11): every user has exactly one of two access modes, decided centrally and never re-derived ad hoc in a controller:
- **ALL_STORES** — granted by the `Permission.STORE_ACCESS_ALL` permission (ADMIN holds it by default; see `RolePermissions`). Such a user can act on every store, active or inactive.
- **ASSIGNED_STORES** — backed by explicit `UserStore` join rows (one row per user-store pair). A user with neither `STORE_ACCESS_ALL` nor any `UserStore` rows has **zero** store access.

`StoreAccessService` is explicitly called out as "the single centralized store-authorization service" so that store-access checks are never scattered across dozens of controllers (mirroring `PermissionService`'s role for permissions), and it "never trusts a frontend-supplied storeId" — every requested store id is validated against one of the two access sources before use.

**Default Store historical-data anchor.** `StoreService.DEFAULT_STORE_CODE = "MAIN"` is the store code of an auto-created "Main Store" (`StoreService.getOrCreateDefaultStore()`), described as "the single anchor store that pre-Multi-Store historical data (Sales/Purchases/Inventory recorded before Stores existed) is backfilled onto, and the fallback every store-aware flow uses until a real store is selected" (spec section 76). It is idempotent (created once, found by `storeCodeIgnoreCase` thereafter) and is also the fallback `StoreAccessService.resolveEffectiveStoreId` and `InventoryService.applyMovement` (overloaded, no-storeId variant) use when there is no authenticated user in context (a system-triggered call).

**Stock Transfer workflow states.** `StockTransferStatus` (`DRAFT → APPROVED → DISPATCHED → RECEIVED`, or `CANCELLED` from `DRAFT`/`APPROVED`) models a store-to-store movement. Per `StockTransferService`'s class doc: stock only ever moves through the same centralized `InventoryService.applyMovement` every other store-attributed voucher uses — a `TRANSFER_OUT` deduction from the source store at `DISPATCHED`, and a `TRANSFER_IN` addition to the destination store at `RECEIVED`. `DRAFT`/`APPROVED` are "purely an authorization stage with no stock impact." Cancelling after `DISPATCHED` is rejected because it "would need a receive-back movement, which is out of scope for this workflow."

## 2. Frontend

### Files and Components

| File | Purpose |
|---|---|
| `frontend/src/pages/masters/StoreMaster.tsx` | CRUD page for Store master (list/create/edit/activate/deactivate), built on the generic `MasterCrudPage`. |
| `frontend/src/components/layout/StoreSwitcher.tsx` | Topbar dropdown to switch `currentStore`; renders `null` when `myStores.length <= 1`. |
| `frontend/src/components/StoreAccessDialog.tsx` | ADMIN dialog to assign a user's `ASSIGNED_STORES` list (checkbox list of active stores). |
| `frontend/src/pages/inventory/StockTransfers.tsx` | List/search stock transfers, with row actions (Approve/Dispatch/Receive/Cancel) gated by `hasPermission`. |
| `frontend/src/pages/inventory/StockTransferForm.tsx` | Create a new stock transfer (DRAFT). |
| `frontend/src/pages/inventory/StockTransferDetail.tsx` | View one transfer, run lifecycle actions, and see a status timeline. |
| `frontend/src/pages/accounting/reports/StoreComparison.tsx` | Store Comparison report — sales/purchases/stock KPIs per store for a date range. |
| `frontend/src/api/storeApi.ts` | Axios wrapper for `/api/masters/stores*`. |
| `frontend/src/api/stockTransferApi.ts` | Axios wrapper for `/api/stock-transfers*`. |
| `frontend/src/api/storeComparisonApi.ts` | Axios wrapper for `/api/reports/store-comparison`. |
| `frontend/src/types/store.ts` | `Store`, `StorePayload`, `StoreGstContext`, `StoreType` types. |
| `frontend/src/types/stockTransfer.ts` | `StockTransfer`, `StockTransferItem`, `StockTransferStatus`, payload types. |
| `frontend/src/types/storeComparison.ts` | `StoreComparisonRow`, `StoreComparisonResponse` types. |
| `frontend/src/context/AuthContext.tsx` | Holds `myStores`, `switchStore()`, and the single-store auto-select-on-login effect (store-relevant parts only — see Auth module doc for the rest). |
| `frontend/src/api/userApi.ts` | `getAssignedStores(id)` / `assignStores(id, storeIds)` used by `StoreAccessDialog`. |

### Frontend Flow

**Store CRUD** (`StoreMaster.tsx`): uses the shared `MasterCrudPage<Store, StorePayload>` component. The form has two tabs ("Basic Info", "Location & GST"). A "Generate Code" button next to Store Code calls `storeApi.generateCode()` and fills the field with the backend-generated `STR-000001`-style code; the field remains freely editable (manual entry bypasses the generator). GSTIN input uppercases on change. Field-level errors from the backend (`fieldErrors`) are surfaced per-field via `invalid`/inline `<p>` messages (matches the CLAUDE.md pattern). Deactivate/Activate call `PATCH .../activate|deactivate` instead of a delete (Store is never hard-deleted).

**Store switching**: `StoreSwitcher.tsx` reads `myStores`/`user.currentStoreId`/`switchStore` from `AuthContext`. It renders nothing if the user has 0–1 accessible stores. Clicking a store in the dropdown calls `switchStore(storeId)` → `authApi.setCurrentStore(storeId)` → `PUT /api/auth/current-store`; on success the returned `UserResponse` (with updated `currentStoreId`/`currentStoreName`) replaces `user` in context and `localStorage`.

**Store access assignment**: `StoreAccessDialog` (opened from the Users admin page, not itself listed as a route file here) loads all `ACTIVE` stores plus the target user's currently assigned store ids (`userApi.getAssignedStores`), lets the ADMIN toggle checkboxes, and on Save calls `userApi.assignStores(user.id, Array.from(selected))` — replace-all semantics.

**Stock transfer create**: `StockTransferForm.tsx` preselects `fromStoreId` from `user.currentStoreId` if set. Client-side `validate()` checks: fromStore required, toStore required, fromStore ≠ toStore, at least one item, every item with a productId must have quantity > 0. On submit, calls `stockTransferApi.create(...)`; on success navigates to the detail page. Backend `fieldErrors` populate per-field messages (`fromStoreId`, `toStoreId`, `transferDate`, etc.) alongside a top-level `error` alert.

**Stock transfer approve/dispatch/receive/cancel**: Both `StockTransfers.tsx` (row dropdown) and `StockTransferDetail.tsx` (page-level buttons) gate each action on both the transfer's current `status` (client-side state machine mirror) and the matching `hasPermission('STOCK_TRANSFER_*')` check, then call the corresponding `stockTransferApi` method and reload. Cancel goes through a `ConfirmDialog` first in the detail page (list page cancels directly from the dropdown without a confirm step).

**Store comparison report viewing**: `StoreComparison.tsx` defaults `fromDate`/`toDate` to first-of-month/today, fetches on mount and on "Apply", and renders per-store KPI cards (stores compared, total sales, total purchases, total stock units — summed client-side from the row list) plus a full table (Sales, Sales Count, Purchases, Purchase Count, Stock Units, Low Stock, Out of Stock), with badge highlighting for non-zero Low Stock (`warning`) / Out of Stock (`destructive`).

## 3. API Calls

| Function | HTTP Method | URL | Request shape | Response shape |
|---|---|---|---|---|
| `storeApi.list` | GET | `/api/masters/stores` | query: `search?, status?, page, size` | `PagedResponse<StoreResponse>` |
| `storeApi.getById` | GET | `/api/masters/stores/{id}` | — | `StoreResponse` |
| `storeApi.generateCode` | POST | `/api/masters/stores/generate-code` | — | `GenerateStoreCodeResponse { storeCode }` |
| `storeApi.create` | POST | `/api/masters/stores` | `StoreRequest` | `StoreResponse` (201) |
| `storeApi.update` | PUT | `/api/masters/stores/{id}` | `StoreRequest` | `StoreResponse` |
| `storeApi.activate` | PATCH | `/api/masters/stores/{id}/activate` | — | `StoreResponse` |
| `storeApi.deactivate` | PATCH | `/api/masters/stores/{id}/deactivate` | — | `StoreResponse` |
| `storeApi.gstContext` | GET | `/api/masters/stores/{id}/gst-context` | — | `StoreGstContextResponse` |
| `authApi.getMyStores` (used by `AuthContext`) | GET | `/api/auth/my-stores` | — | `StoreResponse[]` |
| `authApi.setCurrentStore` (used by `AuthContext`/`switchStore`) | PUT | `/api/auth/current-store` | `SetCurrentStoreRequest { storeId }` | `UserResponse` |
| `userApi.getAssignedStores` | GET | `/api/users/{id}/stores` | — | `number[]` (store ids) |
| `userApi.assignStores` | PUT | `/api/users/{id}/stores` | `AssignStoresRequest { storeIds: number[] }` | `UserResponse` |
| `employeeApi.getAssignedStores` (Employee module, same pattern) | GET | `/api/employees/{id}/stores` | — | `number[]` |
| `employeeApi.assignStores` | PUT | `/api/employees/{id}/stores` | `AssignStoresRequest { storeIds }` | `EmployeeResponse` |
| `stockTransferApi.list` | GET | `/api/stock-transfers` | query: `search?, status?, fromDate?, toDate?, storeId?, page, size` | `PagedResponse<StockTransferResponse>` |
| `stockTransferApi.getById` | GET | `/api/stock-transfers/{id}` | — | `StockTransferResponse` |
| `stockTransferApi.create` | POST | `/api/stock-transfers` | `StockTransferRequest` | `StockTransferResponse` (201) |
| `stockTransferApi.approve` | PATCH | `/api/stock-transfers/{id}/approve` | — | `StockTransferResponse` |
| `stockTransferApi.dispatch` | PATCH | `/api/stock-transfers/{id}/dispatch` | — | `StockTransferResponse` |
| `stockTransferApi.receive` | PATCH | `/api/stock-transfers/{id}/receive` | — | `StockTransferResponse` |
| `stockTransferApi.cancel` | PATCH | `/api/stock-transfers/{id}/cancel` | — | `StockTransferResponse` |
| `storeComparisonApi.compare` | GET | `/api/reports/store-comparison` | query: `fromDate, toDate` (ISO dates) | `StoreComparisonResponse` |

## 4. Backend

### Controllers

**`StoreController`** (`/api/masters/stores`) — GET endpoints unguarded (doc comment: "every authenticated user needs to read the store list to populate a store picker"), mutations require `STORE_*` permissions:
- `GET /` — `search(search?, status?, page=0, size=10)` — no `@PreAuthorize`.
- `GET /{id}` — `getById(id)` — no `@PreAuthorize`.
- `GET /{id}/gst-context` — `gstContext(id)` — no `@PreAuthorize`.
- `POST /generate-code` — `@PreAuthorize("hasAuthority('PERM_STORE_CREATE')")`.
- `POST /` — `create(StoreRequest)` — `@PreAuthorize("hasAuthority('PERM_STORE_CREATE')")` — 201.
- `PUT /{id}` — `update(id, StoreRequest)` — `@PreAuthorize("hasAuthority('PERM_STORE_EDIT')")`.
- `PATCH /{id}/activate` — `@PreAuthorize("hasAuthority('PERM_STORE_EDIT')")`.
- `PATCH /{id}/deactivate` — `@PreAuthorize("hasAuthority('PERM_STORE_EDIT')")`.

**`StockTransferController`** (`/api/stock-transfers`):
- `GET /` — `search(search?, status?, fromDate?, toDate?, storeId?, page=0, size=20)` — `@PreAuthorize("hasAuthority('PERM_STOCK_TRANSFER_VIEW')")`.
- `GET /{id}` — `getById(id)` — `@PreAuthorize("hasAuthority('PERM_STOCK_TRANSFER_VIEW')")`.
- `POST /` — `create(StockTransferRequest)` — `@PreAuthorize("hasAuthority('PERM_STOCK_TRANSFER_CREATE')")` — 201.
- `PATCH /{id}/approve` — `@PreAuthorize("hasAuthority('PERM_STOCK_TRANSFER_APPROVE')")`.
- `PATCH /{id}/dispatch` — `@PreAuthorize("hasAuthority('PERM_STOCK_TRANSFER_DISPATCH')")`.
- `PATCH /{id}/receive` — `@PreAuthorize("hasAuthority('PERM_STOCK_TRANSFER_RECEIVE')")`.
- `PATCH /{id}/cancel` — `@PreAuthorize("hasAuthority('PERM_STOCK_TRANSFER_CANCEL')")`.

**`StoreComparisonController`** (`/api/reports/store-comparison`):
- `GET /` — `compare(fromDate, toDate)` — no `@PreAuthorize`; class doc: "every authenticated user may call this; the rows returned are already scoped to their own accessible stores."

**Related endpoints in other controllers** (store-assignment surface, listed here since they are part of this module's flow):
- `UserController` (`/api/users`, class-level `@PreAuthorize("hasRole('ADMIN')")`): `GET /{id}/stores` → `getAssignedStores`; `PUT /{id}/stores` → `assignStores(id, AssignStoresRequest)`. Both inherit the class-level `ADMIN`-only guard; no additional method-level `@PreAuthorize` (i.e. not gated on `PERM_STORE_ASSIGN` specifically, only on the `ADMIN` role via the class guard).
- `EmployeeController` (`/api/employees`): `GET /{id}/stores` → `getAssignedStores` (no method-level `@PreAuthorize` shown for the GET); `PUT /{id}/stores` → `assignStores` — `@PreAuthorize("hasAuthority('PERM_EMPLOYEE_EDIT')")`.
- `AuthController` (`/api/auth`): `GET /my-stores` → `getMyStores` (no `@PreAuthorize`, any authenticated user); `PUT /current-store` → `setCurrentStore(SetCurrentStoreRequest)` (no `@PreAuthorize`, any authenticated user — self-service, backend-validated via `StoreAccessService.assertStoreAccess` inside `UserService.setCurrentStore`).

### DTOs

| DTO | Fields | Validation |
|---|---|---|
| `StoreRequest` | `storeCode, storeName, storeType, legalName, address, countryId, stateId, cityId, zoneId, pincode, phone, email, gstin` | `@NotBlank storeCode`, `@NotBlank storeName`, `@NotNull storeType`, `@Email email`, `@Pattern` on `gstin`: `^$\|^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z]{1}[1-9A-Za-z]{1}Z[0-9A-Za-z]{1}$` (blank or valid 15-char GSTIN). |
| `StoreResponse` | `id, storeCode, storeName, storeType, legalName, address, countryId, countryName, stateId, stateName, stateCode, cityId, cityName, zoneId, zoneName, pincode, phone, email, gstin, status, createdAt, updatedAt` | Built via `StoreResponse.fromEntity(Store)`. |
| `GenerateStoreCodeResponse` | `storeCode` | none (pure output DTO). |
| `StoreGstContextResponse` | `storeId, gstin, legalName, stateCode, source` (`"STORE"` or `"BUSINESS"`) | none. |
| `SetCurrentStoreRequest` | `storeId` | `@NotNull(message = "storeId is required")`. |
| `AssignStoresRequest` | `storeIds: Set<Long>` | `@NotNull(message = "storeIds is required (use an empty array to clear all store access)")`. |
| `StockTransferItemRequest` | `productId, quantity, notes` | `@NotNull productId`, `@NotNull @Min(1) quantity`, `notes` unvalidated. |
| `StockTransferItemResponse` | `id, productId, productName, sku, quantity, notes` | Built via `fromEntity(StockTransferItem)`. |
| `StockTransferRequest` | `transferDate, fromStoreId, toStoreId, remarks, items: List<StockTransferItemRequest>` | `@NotNull transferDate`, `@NotNull fromStoreId`, `@NotNull toStoreId`, `@NotEmpty @Valid items` (cascades into item-level validation). |
| `StockTransferResponse` | `id, transferNumber, transferDate, fromStoreId, fromStoreName, fromStoreCode, toStoreId, toStoreName, toStoreCode, status, remarks, items, createdBy, createdAt, approvedBy, approvedAt, dispatchedBy, dispatchedAt, receivedBy, receivedAt, cancelledBy, cancelledAt, cancellationReason` | Built via `fromEntity(StockTransfer)`. |
| `StoreComparisonRow` | `storeId, storeName, storeCode, totalSales, salesCount, totalPurchases, purchaseCount, totalStockUnits, lowStockCount, outOfStockCount` | none (server-computed). |
| `StoreComparisonResponse` | `fromDate, toDate, rows: List<StoreComparisonRow>` | none. |

### Services

**`StoreService`** — Store CRUD. `search`, `getById`, `generateCode()` (delegates to `StoreCodeGeneratorService.generateNext()` — "the ONLY place an auto-generated store code is produced"), `create` (rejects duplicate `storeCode` case-insensitively via `existsByStoreCodeIgnoreCase`; rejects invalid GSTIN via `GstinValidator.isValid`; uppercases/trims stored GSTIN), `update` (same duplicate/GSTIN checks, excluding self via `existsByStoreCodeIgnoreCaseAndIdNot`), `setStatus(id, status)` (used for activate/deactivate — never a hard delete: "Never physically deletes a store... status is set to INACTIVE instead, so historical transactions referencing it remain valid"), `resolveGstContext(id)` (returns the store's own GSTIN as `source="STORE"` if set, else falls back to `BusinessGstConfigService.get()` as `source="BUSINESS"`), `findOrThrow(id)` (throws `MasterNotFoundException`), `getOrCreateDefaultStore()` (idempotent Default Store creation/lookup, `storeCode="MAIN"`, `storeType=HEAD_OFFICE`, `status=ACTIVE`).

**`StoreAccessService`** — the centralized authorization choke point. Every public method:
- `hasAllStoresAccess(User user)` — returns `permissionService.hasPermission(user, Permission.STORE_ACCESS_ALL)`. No throw.
- `getAccessibleStoreIds(User user)` — returns `List.of()` if `user == null`; if `hasAllStoresAccess`, returns every `Store` id in the system (active + inactive); otherwise returns the ids from the user's `UserStore` rows (`userStoreRepository.findByUserId`). No throw.
- `hasStoreAccess(User user, Long storeId)` — returns `false` if `user == null`; `true` if ALL_STORES; `false` if `storeId == null` (a null storeId means "no store on the record" — only possible on a pre-Default-Store-migration leftover row; even an ALL_STORES user only "can still see it", this method itself returns `false` for a null storeId regardless); otherwise `userStoreRepository.existsByUserIdAndStoreId(...)`. No throw.
- `assertStoreAccess(User user, Long storeId)` — calls `hasStoreAccess`; throws `AccessDeniedException("You do not have access to this store")` (→ HTTP 403) if it returns `false`. "The one place that decision is ever made."
- `resolveEffectiveStoreId(User user, Long requestedStoreId)` — resolves which store a **new transaction** belongs to. If `requestedStoreId != null`: calls `assertStoreAccess` then returns it (throws 403 if not accessible). Else if `user == null`: returns `storeService.getOrCreateDefaultStore().getId()` (system-triggered call fallback). Else if `user.getCurrentStore() != null`: asserts access to it and returns its id. Else: throws `BadRequestException("A store must be selected for this transaction")` (→ 400).
- `resolveViewableStoreId(User user, Long requestedStoreId)` — resolves which store id a list/summary/export view should filter by. If `requestedStoreId != null`: asserts access, returns it. Else if `user == null` or `hasAllStoresAccess(user)`: returns `null` (meaning "aggregate/unfiltered across every store"). Else if `user.getCurrentStore() != null`: returns its id. Else if the user's `getAccessibleStoreIds` has exactly one entry: returns it. Else: throws `BadRequestException` with `"You do not have access to any store"` (if empty) or `"Please select a store"` (if 2+ and none selected).
- `assignStores(Long userId, Set<Long> storeIds)` — `@Transactional`. Loads the `User` (throws `UserNotFoundException` if missing), deletes all existing `UserStore` rows for that user (`userStoreRepository.deleteByUserId`), then inserts one `UserStore` row per id in `storeIds` (throws `MasterNotFoundException("Store", storeId)` for any invalid store id). Replace-all semantics. If the user's `currentStore` is no longer in the new assignment and they aren't ALL_STORES, `currentStore` is cleared (`setCurrentStore(null)`) and saved — "rather than leave a stale selection the user can no longer actually use."
- `getAssignedStoreIds(Long userId)` — returns the raw list of store ids from `UserStore` rows for that user (ignores ALL_STORES — this is specifically the assignment list, not the effective access list). No throw.

**`StoreCodeGeneratorService`** — `generateNext()`, `@Transactional(propagation = REQUIRES_NEW)`. Ensures the single counter row exists (`ensureRowExists`, native upsert), locks it (`lockForUpdate`, `PESSIMISTIC_WRITE`), increments `lastNumber`, formats as `STR-` + 6-digit zero-padded number, loops while `storeRepository.existsByStoreCodeIgnoreCase(candidate)` (skips any number a manually-entered code already claimed), saves, returns. Runs in its own transaction "so the single counter row is locked/released independently of the caller's transaction."

**`StockTransferService`** — `search` (resolves `storeId` via `storeAccessService.resolveViewableStoreId`), `getById` (via `assertEitherStoreAccess` — visible if the user can access EITHER `fromStore` or `toStore`, else throws `AccessDeniedException`), `create` (asserts access to `fromStoreId`; rejects `fromStoreId == toStoreId` with `BadRequestException`; resolves both stores active via `resolveActiveStore` — throws `BadRequestException` if either is `INACTIVE`; builds items, each requiring the product not be `INACTIVE`; status starts `DRAFT`; assigns `financialYearId` via `financialYearService.resolveForDate`; generates `transferNumber` via `voucherNumberService.next(VoucherDocType.STOCK_TRANSFER, transferDate)` after the initial save), `approve` (asserts access to `fromStore`; requires current status `DRAFT` via `requireStatus`, else `BadRequestException`; sets `APPROVED`, `approvedBy`, `approvedAt`), `dispatch` (asserts access to `fromStore`; requires `APPROVED`; for each item calls `inventoryService.applyMovement(productId, fromStoreId, -quantity, TRANSFER_OUT, STOCK_TRANSFER, transferId, reason)`; sets `DISPATCHED`), `receive` (asserts access to `toStore`; requires `DISPATCHED`; for each item calls `applyMovement(productId, toStoreId, +quantity, TRANSFER_IN, STOCK_TRANSFER, transferId, reason)`; sets `RECEIVED`), `cancel` (asserts access to `fromStore`; only allowed from `DRAFT` or `APPROVED`, else `BadRequestException` explaining a `DISPATCHED`/`RECEIVED` transfer "already moved stock and cannot be cancelled"; sets `CANCELLED` + `cancelledBy/At` + `cancellationReason`).

**`StoreComparisonService`** — `compare(fromDate, toDate)`: gets `accessibleStoreIds` for the current user via `storeAccessService.getAccessibleStoreIds`, loads those `Store` rows, sorts by name, and for each store builds a `StoreComparisonRow` from per-store aggregate queries: `saleRepository.sumAndCountByDateRangeAndStore`, `purchaseRepository.sumAndCountByDateRangeAndStore`, `inventoryRepository.sumCurrentStock`, `inventoryRepository.countLowStock`, `inventoryRepository.countOutOfStock` — "one query per store per metric — never fetched in bulk and summed client-side."

### Repository

`StoreRepository extends JpaRepository<Store, Long>`:
- `findByStoreCodeIgnoreCase(String)`
- `existsByStoreCodeIgnoreCase(String)`
- `existsByStoreCodeIgnoreCaseAndIdNot(String, Long)`
- `findByStatus(StoreStatus)`
- `search(search, status, Pageable)` — JPQL: matches `storeName`/`storeCode` (case-insensitive `LIKE`) when `search` is non-null/non-empty, and `status` when non-null.

`StoreCodeSequenceRepository extends JpaRepository<StoreCodeSequence, Long>`:
- `lockForUpdate(Long id)` — `@Lock(PESSIMISTIC_WRITE)` JPQL select by id.
- `ensureRowExists(Long id)` — `@Modifying` native `INSERT ... ON DUPLICATE KEY UPDATE id = id`.

`UserStoreRepository extends JpaRepository<UserStore, Long>`:
- `findByUserId(Long)`
- `existsByUserIdAndStoreId(Long, Long)`
- `deleteByUserId(Long)`
- `countByStoreId(Long)`

`StockTransferRepository extends JpaRepository<StockTransfer, Long>`:
- `search(search, status, fromDate, toDate, storeId, Pageable)` — JPQL: `transferNumber` case-insensitive `LIKE` when `search` set; `status` equality when set; `transferDate >= fromDate` / `<= toDate` when set; `storeId` matches **either** `fromStore.id` or `toStore.id` when set — "a store-scoped user sees a transfer if it touches any store they can access."

## 5. Database

### Tables

| Entity | `@Table` name | Key columns |
|---|---|---|
| `Store` | `stores` | `id` (IDENTITY), `store_code` (unique, len 30), `store_name` (len 150), `store_type` (enum string, len 20), `legal_name` (len 200), `address` (TEXT), `country_id`/`state_id`/`city_id`/`zone_id` (FK), `pincode` (len 10), `phone` (len 15), `email` (len 100), `gstin` (len 15), `status` (enum string, len 20), `created_at`, `updated_at`. |
| `UserStore` | `user_stores` | `id`, `user_id` (FK, not null), `store_id` (FK, not null), `created_at`. Unique constraint `uk_user_store` on `(user_id, store_id)`. |
| `StockTransfer` | `stock_transfers` | `id`, `transfer_number` (unique, len 30), `transfer_date`, `from_store_id` (FK, not null), `to_store_id` (FK, not null), `status` (enum string, `VARCHAR(15)`), `remarks` (TEXT), `financial_year_id`, `created_by`/`created_at`, `approved_by`/`approved_at`, `dispatched_by`/`dispatched_at`, `received_by`/`received_at`, `cancelled_by`/`cancelled_at`, `cancellation_reason` (len 255). |
| `StockTransferItem` | `stock_transfer_items` | `id`, `transfer_id` (FK, not null), `product_id` (FK, not null), `quantity` (not null), `notes` (len 255). |
| `StoreCodeSequence` | `store_code_sequence` | `id` (always `1` — single global counter row), `last_number` (not null). |

Schema is Hibernate-managed (`spring.jpa.hibernate.ddl-auto=update` in `application.properties`); no separate SQL migration files were found for these tables.

### Relationships

- **Store ↔ User**: many-to-many via `UserStore` (`user_stores.user_id` / `user_stores.store_id`), representing `ASSIGNED_STORES`. Additionally `User.currentStore` is a direct `@ManyToOne` FK (`users.current_store_id` → `stores.id`) holding the user's active/selected store.
- **Store ↔ StockTransfer**: two FKs from `stock_transfers` to `stores` — `from_store_id` and `to_store_id`, both `optional = false`.
- **Store ↔ Inventory**: `inventory.store_id` (FK, part of the unique constraint `uk_inventory_product_store` on `(product_id, store_id)`).
- **Store ↔ Employee**: many-to-many via `EmployeeStore` (`employee_stores.employee_id` / `employee_stores.store_id`, unique constraint `uk_employee_store`) — the Employee-module mirror of `UserStore`.
- **Store-attributed transaction tables** — every entity below has a direct `@ManyToOne Store` mapped to a `store_id` FK column (found via grep across `backend/src/main/java/com/storehub/entity`):
  - `Sale.store` → `sales.store_id`
  - `SalesOrder.store` → `sales_orders.store_id`
  - `Purchase.store` → `purchases.store_id`
  - `PurchaseOrder.store` → `purchase_orders.store_id`
  - `Payment.store` → `payments.store_id`
  - `Receipt.store` → `receipts.store_id`
  - `Expense.store` → `expenses.store_id`
  - `CashTransaction.store` → `cash_transactions.store_id`
  - `JournalHeader.store` → `journal_headers.store_id` (nullable — null for a manual JOURNAL voucher)
  - `GstTransaction.store` → `gst_transactions.store_id` (nullable — null for pre-migration data)
  - `AuditLog.store` → `audit_logs.store_id` (nullable — null for a global/system-level action)
  - `StockHistory.store` → `stock_history.store_id`
  - `Inventory.store` → `inventory.store_id`
- **CreditNote / DebitNote** do **not** carry their own `store_id` column — `CreditNote.sourceSale` and `DebitNote.sourcePurchase` reference the parent `Sale`/`Purchase`, which itself carries the store. (NOT FOUND IN CURRENT CODEBASE: a direct `store`/`storeId` field on `CreditNote` or `DebitNote`.)

## 6. Validation

- `StoreRequest`: `storeCode`/`storeName` required (`@NotBlank`); `storeType` required (`@NotNull`); `email` must be `@Email`; `gstin` optional but if non-blank must match `^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z]{1}[1-9A-Za-z]{1}Z[0-9A-Za-z]{1}$` (standard 15-char GSTIN format) via `@Pattern`.
- `StoreService.create`/`update` additionally re-validate GSTIN with `GstinValidator.isValid(...)` (a second, service-level check beyond the DTO `@Pattern`) and reject a duplicate `storeCode` (case-insensitive) with `BadRequestException`.
- `SetCurrentStoreRequest.storeId`: `@NotNull`.
- `AssignStoresRequest.storeIds`: `@NotNull` (an empty `Set` is valid and explicitly documented as the way to clear all store access).
- `StockTransferRequest`: `transferDate`, `fromStoreId`, `toStoreId` all `@NotNull`; `items` must be `@NotEmpty` and each item is validated via cascading `@Valid`.
- `StockTransferItemRequest`: `productId` `@NotNull`; `quantity` `@NotNull @Min(1)`.
- Service-level validation in `StockTransferService.create`: source and destination store must differ (`BadRequestException`); both stores must be `ACTIVE` (`resolveActiveStore` throws otherwise); every product must not be `INACTIVE`.
- Status-transition validation lives in `StockTransferService.requireStatus`/`cancel` — throws `BadRequestException` describing the required prior state and the transfer's actual current state.
- All 400-level validation failures from `@Valid`/DTO annotations surface as `MethodArgumentNotValidException`, converted by `GlobalExceptionHandler.handleValidation` into `ApiError.fieldErrors` (a `Map<String, String>` keyed by DTO field name).

## 7. Authentication / Authorization

Every store-scoping decision in this module funnels through `StoreAccessService` (see section 4 for exact per-method behavior). The model:

- **ALL_STORES**: a user holding `Permission.STORE_ACCESS_ALL` (checked via `PermissionService.hasPermission`, which reads `RolePermissions.has(user.getRole(), permission)` — a fixed, code-defined `Role → Set<Permission>` map, not a DB table). `StoreAccessService.hasAllStoresAccess` is the single check. An ALL_STORES user: sees every store (active+inactive) from `getAccessibleStoreIds`; passes `hasStoreAccess` for any `storeId`; `resolveViewableStoreId` returns `null` for them when no store is explicitly requested (meaning "aggregate/unfiltered").
- **ASSIGNED_STORES**: a user without `STORE_ACCESS_ALL` is scoped to exactly the stores with a `UserStore` row for them. `getAccessibleStoreIds` returns those ids; `hasStoreAccess` checks `userStoreRepository.existsByUserIdAndStoreId`; a requested `storeId` outside that set always fails `assertStoreAccess` with `AccessDeniedException` → HTTP 403.
- A user with **neither** has zero store access — `getAccessibleStoreIds` returns an empty list, `hasStoreAccess` always returns `false`.
- Endpoint-level authorization is a second, separate layer on top: Spring Security `@PreAuthorize("hasAuthority('PERM_...')")` checks (backed by the same `RolePermissions` map, surfaced as granted authorities) gate whether the caller may call the endpoint at all (e.g. `PERM_STORE_CREATE`, `PERM_STOCK_TRANSFER_APPROVE`); `StoreAccessService` then gates *which* store(s) within that endpoint the caller may touch. Both layers must pass.
- A frontend-supplied `storeId` (e.g. on `StockTransferRequest.fromStoreId`, or a list/report's `storeId` filter param) is never trusted directly — every code path resolves it through `assertStoreAccess`, `resolveEffectiveStoreId`, or `resolveViewableStoreId` before use.

## 8. Permissions

From `Permission.java` and `RolePermissions.java`:

| Permission | Module | ADMIN | STORE_MANAGER | ACCOUNTANT | SALES_USER | PURCHASE_USER | INVENTORY_USER | STAFF |
|---|---|---|---|---|---|---|---|---|
| `STORE_VIEW` | Master | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `STORE_CREATE` | Master | ✓ | ✗ (explicitly removed) | ✗ | ✗ | ✗ | ✗ | ✗ |
| `STORE_EDIT` | Master | ✓ | ✗ (explicitly removed) | ✗ | ✗ | ✗ | ✗ | ✗ |
| `STORE_ASSIGN` | Security | ✓ | ✗ (explicitly removed) | ✗ | ✗ | ✗ | ✗ | ✗ |
| `STORE_ACCESS_ALL` | Security | ✓ | ✗ (explicitly removed) | ✗ | ✗ | ✗ | ✗ | ✗ |
| `STOCK_TRANSFER_VIEW` | Inventory | ✓ | ✓ | ✗ | ✗ | ✗ | ✓ | ✗ |
| `STOCK_TRANSFER_CREATE` | Inventory | ✓ | ✓ | ✗ | ✗ | ✗ | ✓ | ✗ |
| `STOCK_TRANSFER_APPROVE` | Inventory | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ |
| `STOCK_TRANSFER_DISPATCH` | Inventory | ✓ | ✓ | ✗ | ✗ | ✗ | ✓ | ✗ |
| `STOCK_TRANSFER_RECEIVE` | Inventory | ✓ | ✓ | ✗ | ✗ | ✗ | ✓ | ✗ |
| `STOCK_TRANSFER_CANCEL` | Inventory | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ |

Note: `STORE_MANAGER`'s permission set is computed as "every permission ADMIN has, minus an explicit exclusion set" (`RolePermissions.build()`), and that exclusion set explicitly includes `STORE_CREATE, STORE_EDIT, STORE_ASSIGN, STORE_ACCESS_ALL` — commented as "Creating/editing Store records and assigning users to stores, or bypassing store scoping entirely, are structural/admin-tier decisions... STORE_MANAGER works within their assigned store(s), never manages the store list itself." `INVENTORY_USER` deliberately excludes `STOCK_TRANSFER_APPROVE`/`STOCK_TRANSFER_CANCEL` (can create/dispatch/receive but not approve or cancel). Only `ADMIN` and `STORE_MANAGER` hold every `STOCK_TRANSFER_*` permission.

## 9. Transaction Handling

- `StoreService.create`, `update`, `setStatus`, `getOrCreateDefaultStore` are all `@Transactional`.
- `StoreCodeGeneratorService.generateNext()` runs in its **own** transaction (`@Transactional(propagation = Propagation.REQUIRES_NEW)`) so the pessimistic row lock on the single counter row is acquired/released independently of the caller's transaction, preventing two concurrent "Generate Code" calls from colliding.
- `StoreAccessService.assignStores` is `@Transactional` (delete-then-insert of `UserStore` rows, plus a possible `currentStore` clear, all atomic).
- `StockTransferService.search`/`getById` are `@Transactional(readOnly = true)`; `create`, `approve`, `dispatch`, `receive`, `cancel` are `@Transactional` — each status transition (and, for `dispatch`/`receive`, the associated `InventoryService.applyMovement` calls per item) commits atomically with the transfer's own status/timestamp update.
- `StoreComparisonService.compare` is `@Transactional(readOnly = true)` — one read-only transaction spans all the per-store aggregate queries.
- `InventoryService.applyMovement` (called from `dispatch`/`receive`) is itself `@Transactional`; since it's called within the already-`@Transactional` `dispatch`/`receive` methods, all item movements plus the transfer status update share one transaction (default `REQUIRED` propagation) — a mid-loop failure rolls back the whole dispatch/receive.

## 10. Error Handling

Mapped by `GlobalExceptionHandler` (`backend/src/main/java/com/storehub/exception/GlobalExceptionHandler.java`):

| Exception | HTTP Status | Notes |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `fieldErrors` map populated from `BindingResult` field errors — used for all `@Valid` DTO failures (`StoreRequest`, `StockTransferRequest`, `SetCurrentStoreRequest`, `AssignStoresRequest`, etc.). |
| `BadRequestException` | 400 | Used for: duplicate store code, invalid GSTIN, inactive store/product in a transfer, same from/to store, missing current-store selection, invalid status transition, "no store access"/"please select a store". Message only, no `fieldErrors`. |
| `MasterNotFoundException` | 404 | Thrown by `StoreService.findOrThrow` and `StoreAccessService.assignStores` (invalid store id in the set). |
| `StockTransferNotFoundException` | 404 | Thrown by `StockTransferService.findOrThrow`; simple `RuntimeException` subclass with message `"Stock transfer not found with id: " + id`. |
| `AccessDeniedException` (Spring Security) | 403 | Thrown by `StoreAccessService.assertStoreAccess` and `StockTransferService.assertEitherStoreAccess`; response message is a generic `"You do not have permission to access this resource"` (the specific `"You do not have access to this store"` / `"...this stock transfer"` message thrown by the service is not echoed to the client — the handler overwrites it with the generic text). |
| `Exception` (catch-all) | 500 | Fallback handler for anything unmapped. |

## 11. Audit Flow

Exact `AuditService.log(...)` call sites:

- `StoreService.create` — `AuditAction.CREATE`, module `"MASTER"`, entityType `"Store"`, entityId = new store id, documentNumber = store code, description `"Store " + code + " (" + name + ") created"`.
- `StoreService.update` — `AuditAction.UPDATE`, `"MASTER"`/`"Store"`, description `"Store " + code + " updated"`.
- `StoreService.setStatus` — `AuditAction.UPDATE`, `"MASTER"`/`"Store"`, `oldValue`/`newValue` = old/new `StoreStatus` name, description `"Store " + code + " status changed to " + status"`.
- `StoreService.getOrCreateDefaultStore` (only on first creation) — `AuditAction.CREATE`, `"MASTER"`/`"Store"`, description `"Default store 'MAIN' auto-created to hold pre-Multi-Store historical data"`.
- `StockTransferService.create` — `AuditAction.CREATE`, module `"INVENTORY"`, entityType `"StockTransfer"`, documentNumber = transfer number, description `"Stock transfer " + number + " created: " + fromCode + " -> " + toCode`, **storeId = fromStore.id** (the overloaded `log(..., storeId)` variant).
- `StockTransferService.approve` — `AuditAction.UPDATE`, `"INVENTORY"`/`"StockTransfer"`, description `"...approved"`, storeId = `fromStore.id`.
- `StockTransferService.dispatch` — `AuditAction.UPDATE`, `"INVENTORY"`/`"StockTransfer"`, description `"...dispatched: stock deducted from " + fromStoreCode`, storeId = `fromStore.id`.
- `StockTransferService.receive` — `AuditAction.UPDATE`, `"INVENTORY"`/`"StockTransfer"`, description `"...received: stock added to " + toStoreCode`, storeId = `toStore.id`.
- `StockTransferService.cancel` — `AuditAction.CANCEL`, `"INVENTORY"`/`"StockTransfer"`, description `"Stock transfer cancelled: " + transferNumber`, storeId = `fromStore.id`.
- `UserService.assignStores` (store-access assignment) — `AuditAction.UPDATE`, module `"ADMIN"`, entityType `"User"`, description `"Store access for user " + email + " set to " + storeIds.size() + " store(s)"`.
- `UserService.setCurrentStore` — `AuditAction.UPDATE`, module `"AUTH"`, entityType `"User"`, documentNumber = new store code, description `"User " + email + " switched current store to " + storeCode"`.

Every `AuditService.log` call is wrapped internally in a try/catch (per `AuditService`'s own implementation) so an audit-logging failure never blocks the underlying operation.

## 12. Important Side Effects

- **Default Store historical-data-safety fallback**: `StoreService.getOrCreateDefaultStore()` (code `"MAIN"`) is the anchor every pre-Multi-Store transaction was backfilled onto, and is the fallback both `StoreAccessService.resolveEffectiveStoreId` (when `user == null`) and `InventoryService.applyMovement`'s legacy no-storeId overload use, so that call sites not yet migrated to pass an explicit `storeId` keep working unchanged.
- **Single-store auto-select on login**: lives in `frontend/src/context/AuthContext.tsx` (not in this module's own backend/frontend files, but directly store-relevant) — after login, `authApi.getMyStores()` is called; if the result has exactly one store **and** the user has no `currentStoreId` yet, `authApi.setCurrentStore(that store's id)` is called silently so a single-store business never has to manually pick "the only store that exists." A multi-store user with none selected is left alone (the `StoreSwitcher` is available in the Topbar).
- **Stock only moves at DISPATCHED/RECEIVED, never at DRAFT/APPROVED**: `StockTransferService.dispatch()` is the only point stock leaves the `fromStore` (`TRANSFER_OUT`, negative delta), and `receive()` is the only point stock lands in the `toStore` (`TRANSFER_IN`, positive delta). `create()`/`approve()` never touch `InventoryService` — they are "purely an authorization/paperwork stage."
- **Cancel is only possible before stock moves**: `cancel()` is rejected outside `DRAFT`/`APPROVED` specifically because reversing a `DISPATCHED` transfer would require a compensating receive-back movement, which this workflow does not implement.
- **Store deactivation never rewrites history**: `StoreService.setStatus(id, INACTIVE)` only flips the `status` column; it does not touch any historical `Sale`/`Purchase`/`Inventory`/`StockTransfer` row that already references the store. An `INACTIVE` store is, however, blocked from being used as either side of a **new** stock transfer (`StockTransferService.resolveActiveStore`).
- **`StoreAccessService.assignStores` can silently clear a user's current store**: if the new assignment no longer includes the user's `currentStore` and they are not ALL_STORES, `currentStore` is set to `null` as part of the same transaction, so their next transaction requires re-selecting a store (`resolveEffectiveStoreId` would otherwise throw `BadRequestException`).

## 13. Dependencies on Other Modules

**Modules that depend on Store** (every entity with a direct `store`/`storeId` FK, confirmed by grep across `backend/src/main/java/com/storehub/entity`): `Sale`, `SalesOrder`, `Purchase`, `PurchaseOrder`, `Payment`, `Receipt`, `Expense`, `CashTransaction`, `JournalHeader`, `GstTransaction`, `AuditLog`, `StockHistory`, `Inventory`, plus the store-assignment tables `UserStore` and `EmployeeStore`. `CreditNote`/`DebitNote` depend on Store only indirectly, through their `sourceSale`/`sourcePurchase` reference.

**Modules Store depends on**:
- **Inventory** — `StockTransferService.dispatch`/`receive` call `InventoryService.applyMovement(...)` directly to move stock; this module never duplicates stock-mutation logic. (`InventoryService` itself falls back to `StoreService.getOrCreateDefaultStore()` in its legacy no-storeId overload.)
- **User** — `StoreAccessService`/`UserService` depend on `User`/`UserRepository`/`UserStoreRepository` for access assignment (`assignStores`, `getAssignedStoreIds`) and current-store selection (`setCurrentStore`).
- **Employee** — `EmployeeService`/`EmployeeController` reuse the exact same `AssignStoresRequest` DTO and replace-all pattern for `EmployeeStore` (a parallel table to `UserStore`), though `EmployeeService.assignStores` itself is out of scope here (belongs to the Employee module).
- **Permission/Role** — `StoreAccessService.hasAllStoresAccess` depends on `PermissionService`/`RolePermissions` for the `STORE_ACCESS_ALL` check.
- **Country/State/City/Zone (masters)** — `StoreService` resolves these as optional FK fields on `Store` via `CountryService`/`StateService`/`CityService`/`ZoneService`.
- **BusinessGstConfig** — `StoreService.resolveGstContext` falls back to `BusinessGstConfigService.get()` when the store has no GSTIN override.
- **Sale/Purchase/Inventory repositories** — `StoreComparisonService` reads aggregate queries from `SaleRepository`, `PurchaseRepository`, `InventoryRepository` to build its report rows.
- **VoucherNumberService / FinancialYearService** — `StockTransferService.create` uses these to assign `transferNumber` and `financialYearId`.

## 14. Key Operation Flows

**Create Store**
`StoreMaster.tsx` (form submit) → `storeApi.create(payload)` → `POST /api/masters/stores` (`@PreAuthorize PERM_STORE_CREATE`) → `StoreController.create(StoreRequest)` → `StoreService.create(request)` (checks `existsByStoreCodeIgnoreCase`, validates GSTIN via `GstinValidator`, resolves optional Country/State/City/Zone FKs, builds `Store` with `status` defaulted to `ACTIVE` via `@PrePersist`) → `storeRepository.save(store)` → `stores` table INSERT → `auditService.log(CREATE, "MASTER", "Store", ...)` → `StoreResponse.fromEntity(saved)` → 201 response → `MasterCrudPage` closes the form and refreshes the list.

**Switch Current Store**
`StoreSwitcher.tsx` dropdown item click → `switchStore(storeId)` (in `AuthContext`) → `authApi.setCurrentStore(storeId)` → `PUT /api/auth/current-store` (`SetCurrentStoreRequest { storeId }`, no `@PreAuthorize` — any authenticated user) → `AuthController.setCurrentStore` → `UserService.setCurrentStore(username, storeId)` → `storeAccessService.assertStoreAccess(user, storeId)` (throws 403 if not accessible) → `storeRepository.findById(storeId)` (throws `MasterNotFoundException` if missing) → `user.setCurrentStore(store)` → `userRepository.save(user)` → `users.current_store_id` UPDATE → `auditService.log(UPDATE, "AUTH", "User", ...)` → `UserResponse.fromEntity(saved)` → frontend replaces `user` in context + `localStorage`.

**Assign User to Stores**
`StoreAccessDialog` Save → `userApi.assignStores(userId, storeIds)` → `PUT /api/users/{id}/stores` (class-level `@PreAuthorize hasRole('ADMIN')`, `AssignStoresRequest { storeIds }`) → `UserController.assignStores` → `UserService.assignStores(id, storeIds)` → `storeAccessService.assignStores(id, storeIds)` (`@Transactional`: `userStoreRepository.deleteByUserId(id)` then one `userStoreRepository.save(UserStore)` insert per id, validating each id via `storeRepository.findById` — throws `MasterNotFoundException` on an invalid id; clears `currentStore` if it fell outside the new set) → `user_stores` table DELETE+INSERTs (+ possible `users.current_store_id` UPDATE) → `auditService.log(UPDATE, "ADMIN", "User", ...)` → `UserResponse.fromEntity(...)` → dialog closes, parent list reloads.

**Stock Transfer Lifecycle**

1. **Create (DRAFT)**: `StockTransferForm.tsx` submit → `stockTransferApi.create(payload)` → `POST /api/stock-transfers` (`PERM_STOCK_TRANSFER_CREATE`, `StockTransferRequest`) → `StockTransferController.create` → `StockTransferService.create` (`storeAccessService.assertStoreAccess(user, fromStoreId)`; rejects `fromStoreId == toStoreId`; `resolveActiveStore` for both stores — 400 if either `INACTIVE`; builds `StockTransfer` with `status=DRAFT`, items validated against `ProductRepository`/`ProductStatus`; `financialYearService.resolveForDate`; two saves — first to get an id, second after `voucherNumberService.next(...)` assigns `transferNumber`) → `stockTransferRepository.save` → `stock_transfers`/`stock_transfer_items` INSERT → `auditService.log(CREATE, "INVENTORY", ...)` → `StockTransferResponse.fromEntity` → 201 → frontend navigates to the detail page.
2. **Approve**: detail/list action → `stockTransferApi.approve(id)` → `PATCH /{id}/approve` (`PERM_STOCK_TRANSFER_APPROVE`) → `StockTransferService.approve` (asserts access to `fromStore`; `requireStatus(DRAFT)` else 400) → sets `APPROVED`/`approvedBy`/`approvedAt` → save → audit log → response.
3. **Dispatch (stock deducted)**: → `PATCH /{id}/dispatch` (`PERM_STOCK_TRANSFER_DISPATCH`) → `StockTransferService.dispatch` (asserts access to `fromStore`; `requireStatus(APPROVED)`) → for each item: `inventoryService.applyMovement(productId, fromStoreId, -quantity, TRANSFER_OUT, STOCK_TRANSFER, transferId, reason)` → `Inventory.currentStock` UPDATE at the source store (throws `BadRequestException` "Insufficient stock..." if it would go negative) → sets `DISPATCHED`/`dispatchedBy`/`dispatchedAt` → save → audit log → response.
4. **Receive (stock credited)**: → `PATCH /{id}/receive` (`PERM_STOCK_TRANSFER_RECEIVE`) → `StockTransferService.receive` (asserts access to `toStore`; `requireStatus(DISPATCHED)`) → for each item: `inventoryService.applyMovement(productId, toStoreId, +quantity, TRANSFER_IN, STOCK_TRANSFER, transferId, reason)` → `Inventory.currentStock` UPDATE (or insert) at the destination store → sets `RECEIVED`/`receivedBy`/`receivedAt` → save → audit log → response.
5. **Cancel (only from DRAFT/APPROVED)**: → `PATCH /{id}/cancel` (`PERM_STOCK_TRANSFER_CANCEL`) → `StockTransferService.cancel` (asserts access to `fromStore`; if status is not `DRAFT`/`APPROVED`, throws `BadRequestException` naming the current status) → sets `CANCELLED`/`cancelledBy`/`cancelledAt`/`cancellationReason` → save → `auditService.log(CANCEL, ...)` → response. No `InventoryService` call — no stock had moved yet.

Throughout, the frontend (`StockTransfers.tsx`/`StockTransferDetail.tsx`) gates each action button on both the transfer's live `status` and `hasPermission('STOCK_TRANSFER_*')`, then calls `load()`/`getById()` again after every action to refresh the displayed state and timeline from the authoritative backend response.

## 15. Manual Changes

- **Add a new Store field**: add the column to `Store.java` (entity), add it to `StoreRequest`/`StoreResponse` DTOs (with any `@NotBlank`/`@Pattern`/etc. validation), wire it into `StoreService.create`/`update` (builder/setters) and `StoreResponse.fromEntity`, and add the corresponding form field to `StoreMaster.tsx` (`StorePayload` in `frontend/src/types/store.ts`, `EMPTY` default, `toFormValues`, `renderForm`, `renderView`). Since `ddl-auto=update` is in effect, no manual migration is needed, but double-check column length/nullability annotations match intent.
- **Change the ALL_STORES/ASSIGNED_STORES logic**: everything lives in `StoreAccessService` (`backend/src/main/java/com/storehub/service/StoreAccessService.java`) — this is the single place to modify. Any change to `hasAllStoresAccess`, `hasStoreAccess`, `resolveEffectiveStoreId`, or `resolveViewableStoreId` immediately affects every module that calls it (Sale, Purchase, Inventory, Expense, CashTransaction, StockTransfer, StoreComparison, etc.) — grep for `storeAccessService.` call sites before changing method signatures or semantics.
- **Add a new Stock Transfer status**: add the enum constant to `StockTransferStatus.java`; add a new service method (or extend an existing one) in `StockTransferService` following the `requireStatus(transfer, REQUIRED_STATUS, "actionPastTense")` pattern; add a new `@PreAuthorize`-guarded endpoint to `StockTransferController` plus a new `STOCK_TRANSFER_*` constant in `Permission.java` and assign it to the right roles in `RolePermissions.java`; add the corresponding `stockTransferApi` method (`frontend/src/api/stockTransferApi.ts`) and wire buttons into `StockTransfers.tsx`/`StockTransferDetail.tsx` with the new status guard and `statusVariant()` badge color.
- **Change the store code format**: modify `StoreCodeGeneratorService.PREFIX`/`format(long)` only (`backend/src/main/java/com/storehub/service/StoreCodeGeneratorService.java`) — this is documented as "the ONLY place an auto-generated store code is produced," so no other file needs to change. Manually-entered codes bypass this entirely and are only checked for uniqueness in `StoreService.create`/`update`.
- **Change which roles get `STORE_ACCESS_ALL`**: edit `RolePermissions.build()` (`backend/src/main/java/com/storehub/service/RolePermissions.java`) — add/remove `Permission.STORE_ACCESS_ALL` from the relevant role's `EnumSet`. This is a single centralized map; no controller or service needs to change since every check reads through `PermissionService`/`StoreAccessService`.
- **Add store scoping to a new module**: (a) add a `store`/`@ManyToOne @JoinColumn(name = "store_id")` field to the new entity; (b) add `storeId` to its create-request DTO; (c) in its service's create method, call `storeAccessService.resolveEffectiveStoreId(currentUser, request.getStoreId())` to get the authorized store id rather than trusting the request directly; (d) in its list/search method, call `storeAccessService.resolveViewableStoreId(currentUser, requestedStoreId)` to scope reads; (e) if it mutates stock, call `inventoryService.applyMovement(productId, storeId, delta, ...)` with the resolved store id (never the legacy no-storeId overload) — following the exact pattern `StockTransferService` uses.
