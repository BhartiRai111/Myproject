# Product / Item Master & Inventory Module

## 1. Overview

This module covers the **Item Master** (Product + Category) and **core Inventory** (per-store stock, stock history, manual adjustment, CSV import/export). Stock **transfer between stores** is a separate module (`StockTransferService` / `StockTransferController`) and is only referenced here as a dependency (section 13).

**SKU / barcode generation.** A Product's `sku` is mandatory, always stored **trimmed + uppercased** (`ProductService#normalizeSku`), and unique. It can be typed manually or auto-generated via `SkuGeneratorService#generateNext()`, which produces `ITEM-000001`-style values from a single global counter row (`item_sku_sequence`, entity `ItemSkuSequence`) locked with `PESSIMISTIC_WRITE` inside its own `REQUIRES_NEW` transaction, so two concurrent "Generate SKU" clicks never collide. A generated number is never reused even if unused (gaps are fine, duplicates are not).

A Product's `barcode` is optional, stored **trimmed only** (never uppercased — preserves leading zeros / Code128 case), and unique. It can be scanned/typed manually or auto-generated via `BarcodeGeneratorService#generateNext()`, which produces `INT-000001`-style values from its own counter row (`item_barcode_sequence`, entity `ItemBarcodeSequence`), using the identical locking pattern. `BarcodeUtil.detectType()` auto-classifies a barcode's shape server-side (`EAN13`/`EAN8`/`UPC`/`INTERNAL`/`OTHER`) — the user never picks a type — and only a 13-digit barcode is checked against the standard EAN-13 checksum algorithm.

**Inventory is the single source of truth for stock, per (product, store).** There is no stock quantity stored on `Product` itself (the legacy `products.stock_quantity` column is kept in the DB for historical-data safety but is no longer mapped by the entity and is relaxed to nullable at startup). One `Inventory` row exists per `(product_id, store_id)` pair (unique constraint `uk_inventory_product_store`); a product's stock at a given store is exactly that row's `current_stock`, and a "global" figure (Products list, CSV export) is always a `SUM()` across a product's rows, never stored directly. `InventoryService.applyMovement(...)` is the single choke point every stock-mutating flow in the system must go through — see section 13 for every caller.

**CSV import/export.** Products and Inventory each support CSV export (filtered, matching the current list-page filters) and — Products only — CSV import (create-or-update by SKU match, with opening stock for new rows applied via a real `STOCK_IN` movement through `InventoryService.applyMovement`, never a direct DB write). Export values are passed through `CsvUtil.escape()`, which neutralizes spreadsheet formula injection (a leading `=`, `+`, `-`, `@`, tab or CR gets a literal-text `'` prefix) before RFC4180 quoting.

## 2. Frontend

### Files and Components

| File | Purpose |
|---|---|
| `frontend/src/pages/Products.tsx` | Product list: search/category/status/stock filters, sort, pagination, activate/deactivate, CSV export/import dialog, opens `ProductViewModal` and `CategoryManagerDialog`. |
| `frontend/src/pages/ProductForm.tsx` | Create/Edit product form (Basic Info, Pricing, Inventory thresholds, Item Master Details cards); SKU/barcode Generate buttons; edit-lock when `hasTransactions` is true; surfaces backend `fieldErrors`. |
| `frontend/src/components/ProductViewModal.tsx` | Read-only product detail modal (basic info, pricing, current stock/status). |
| `frontend/src/components/ProductQuickAddModal.tsx` | Minimal inline "Add Product" modal (name, SKU + generate, category + quick-add, unit, prices) used from other modules' item pickers. |
| `frontend/src/components/CategoryQuickAddModal.tsx` | Minimal inline "Add Category" modal (name, description). |
| `frontend/src/components/CategoryManagerDialog.tsx` | Full category CRUD dialog: add (with itemType/applicableProperty), inline edit, activate/deactivate. |
| `frontend/src/pages/Inventory.tsx` | Inventory list: summary cards (total/low/out-of-stock/overstock/reorder), search/category/store/stock-status filters, CSV export, "Adjust Stock" action, opens view/history/adjust modals. |
| `frontend/src/components/InventoryViewModal.tsx` | Read-only inventory row detail (stock levels, status, timestamps). |
| `frontend/src/components/StockAdjustmentDialog.tsx` | Two-step (form → confirm) manual stock adjustment: STOCK_IN / STOCK_OUT / ADJUSTMENT, live "new stock" preview, low-stock warning, negative-stock client-side guard. |
| `frontend/src/components/StockHistoryModal.tsx` | Paginated stock-movement history for a product, filterable by movement type, reference type, date range. |
| `frontend/src/api/productApi.ts` | Axios wrapper for `/api/products*`. |
| `frontend/src/api/categoryApi.ts` | Axios wrapper for `/api/categories*`. |
| `frontend/src/api/inventoryApi.ts` | Axios wrapper for `/api/inventory*`. |
| `frontend/src/types/product.ts` | `Product`, `ProductPayload`, `Category`, `CategoryPayload`, `ProductStatus`, `BarcodeType`, `TaxTreatment`, `CategoryStatus`. |
| `frontend/src/types/inventory.ts` | `Inventory`, `StockHistory`, `StockAdjustmentPayload`, `InventorySummary`, `StockStatus`, `StockMovementType`, `ManualAdjustmentType`, `ReferenceType`. |
| `frontend/src/types/importResult.ts` | `ImportResult` (totalRows/created/updated/skipped/errors). |
| `frontend/src/utils/stockStatus.ts` | Client-side stock-status classifier used on the Products page (`getStockStatus`, based on `stockQuantity`/`minStockLevel` only — does not know about `maxStockLevel`/overstock, unlike the backend's `InventoryResponse.fromEntity`). |
| `frontend/src/utils/apiError.ts` | `parseApiError()` — turns an axios error into a message + per-field `fieldErrors` map. |

### Frontend Flow

**Product create/edit.** `ProductForm.tsx` loads categories/item groups/HSN codes/units in parallel, and (edit mode) the product itself via `productApi.getById`, populating `hasTransactions` from the response. The SKU and Barcode fields each have a "Generate" button (`Sparkles` icon) calling `productApi.generateSku()` / `generateBarcode()`; both the input **and** the generate button are `disabled` when `isEdit && hasTransactions` — the edit-protection lock (backend: `ProductService.updateProduct`'s `skuChanging`/`barcodeChanging` + `hasTransactionHistory` guard). On submit, `validate()` runs client-side (required/non-negative checks mirroring the backend `@NotBlank`/`@DecimalMin`/`@Min`), then POST/PUT; a 400 response's `fieldErrors` populate both a per-field message (`fieldErrors.sku` etc., shown via `invalid`/red text under each Input) and a top-level `error` Alert, per `parseApiError`.

**Barcode entry/scan.** Manual typing or the Generate button in `ProductForm`; separately, `productApi.getByBarcode(barcode)` (used elsewhere, e.g. POS/Sales/Purchase scanning) hits `GET /api/products/barcode/{barcode}` which 404s if no product matches and 400s if the product is `INACTIVE`.

**Inventory list with store filter.** `Inventory.tsx` shows a Store `Select` only when `myStores.length > 1` (single-store users never see it); `storeFilter` flows into both `inventoryApi.list()` and `inventoryApi.getSummary()`. Sorting supports `currentStock`/`updatedAt`. Rows show a computed `StockStatus` badge from the backend response (`IN_STOCK`/`LOW_STOCK`/`OUT_OF_STOCK`/`OVERSTOCK`).

**Stock adjustment dialog flow.** `StockAdjustmentDialog` is opened either pre-selected (from an Inventory row's "Adjust Stock" action, `preselected` prop) or blank (from the page-level "Adjust Stock" button, which loads up to 200 inventory rows into a picker). It is a two-step wizard: **form** step computes a live `newStock` preview (`STOCK_IN`→+qty, `STOCK_OUT`→-qty, `ADJUSTMENT`→set-to-qty) and blocks submission client-side if it would go negative or if `selected.storeId` is missing; **confirm** step shows current→new stock and a low-stock warning (`newStock/currentStock <= 0.2`) before calling `inventoryApi.adjust()`.

**Stock history view.** `StockHistoryModal` loads `inventoryApi.getHistory({ productId, ... })` (the productId-level endpoint, not store-scoped) with movement-type, reference-type and date-range filters, paginated.

**CSV import/export flow.** Products page: Export button calls `productApi.exportCsv()` with current search/category/status filters and downloads the blob; Import opens a dialog with a hidden file input, calls `productApi.importCsv(file)` (multipart), and renders the `ImportResult` (created/updated/skipped counts + a scrollable list of per-row error strings). Inventory page: Export only (no import), same filtered-CSV pattern via `inventoryApi.exportCsv()`.

## 3. API Calls

| Function | Method | URL | Request shape | Response shape |
|---|---|---|---|---|
| `productApi.list` | GET | `/api/products` | query: `search, categoryId, status, page, size, sortBy, sortDir` | `PagedResponse<Product>` |
| `productApi.getById` | GET | `/api/products/{id}` | — | `Product` |
| `productApi.getByBarcode` | GET | `/api/products/barcode/{barcode}` | — | `Product` (404/400 on miss/inactive) |
| `productApi.generateSku` | POST | `/api/products/generate-sku` | — | `{ sku: string }` |
| `productApi.generateBarcode` | POST | `/api/products/generate-barcode` | — | `{ barcode: string }` |
| `productApi.create` | POST | `/api/products` | `ProductPayload` (→ `ProductCreateRequest`) | `Product` (201) |
| `productApi.update` | PUT | `/api/products/{id}` | `ProductPayload` (→ `ProductUpdateRequest`) | `Product` |
| `productApi.activate` | PATCH | `/api/products/{id}/activate` | — | `Product` |
| `productApi.deactivate` | PATCH | `/api/products/{id}/deactivate` | — | `Product` |
| `productApi.exportCsv` | GET | `/api/products/export` | query: `search, categoryId, status` | `Blob` (`text/csv`) |
| `productApi.importCsv` | POST | `/api/products/import` | `multipart/form-data`, field `file` | `ImportResult` |
| `categoryApi.list` | GET | `/api/categories` | — | `Category[]` |
| `categoryApi.getById` | GET | `/api/categories/{id}` | — | `Category` |
| `categoryApi.create` | POST | `/api/categories` | `CategoryPayload` (→ `CategoryCreateRequest`) | `Category` (201) |
| `categoryApi.update` | PUT | `/api/categories/{id}` | `CategoryPayload` (→ `CategoryUpdateRequest`) | `Category` |
| `categoryApi.activate` | PATCH | `/api/categories/{id}/activate` | — | `Category` |
| `categoryApi.deactivate` | PATCH | `/api/categories/{id}/deactivate` | — | `Category` |
| `inventoryApi.list` | GET | `/api/inventory` | query: `search, categoryId, storeId, stockStatus, page, size, sortBy, sortDir` | `PagedResponse<Inventory>` |
| `inventoryApi.getById` | GET | `/api/inventory/{id}` | — | `Inventory` |
| `inventoryApi.getSummary` | GET | `/api/inventory/summary` | query: `storeId?` | `InventorySummary` |
| `inventoryApi.adjust` | POST | `/api/inventory/adjust` | `StockAdjustmentPayload` (→ `StockAdjustmentRequest`) | `Inventory` |
| `inventoryApi.getHistoryForInventory` | GET | `/api/inventory/{id}/history` | query: `page, size` | `PagedResponse<StockHistory>` |
| `inventoryApi.getHistory` | GET | `/api/inventory/history` | query: `productId, storeId, movementType, referenceType, fromDate, toDate, page, size` | `PagedResponse<StockHistory>` |
| `inventoryApi.exportCsv` | GET | `/api/inventory/export` | query: `search, categoryId, storeId, stockStatus` | `Blob` (`text/csv`) |

## 4. Backend

### Controllers

- **`ProductController`** (`/api/products`): `GET` (list, no explicit `@PreAuthorize`), `GET /{id}`, `GET /barcode/{barcode}`, `POST /generate-sku` (`PERM_ITEM_CREATE`), `POST /generate-barcode` (`PERM_ITEM_CREATE`), `POST` (`PERM_ITEM_CREATE`), `PUT /{id}` (`PERM_ITEM_EDIT`), `PATCH /{id}/activate` (`PERM_ITEM_EDIT`), `PATCH /{id}/deactivate` (`PERM_ITEM_EDIT`), `GET /export` (`PERM_ITEM_EDIT`), `POST /import` (`PERM_ITEM_CREATE`).
- **`CategoryController`** (`/api/categories`): `GET`, `GET /{id}` (no `@PreAuthorize`); `POST`, `PUT /{id}`, `PATCH /{id}/activate`, `PATCH /{id}/deactivate` all require `PERM_MASTER_MANAGE`.
- **`InventoryController`** (`/api/inventory`): `GET`, `GET /summary`, `GET /history`, `GET /{id}`, `GET /{id}/history` (no explicit `@PreAuthorize`, but every list/summary/history endpoint resolves/validates `storeId` through `StoreAccessService.resolveViewableStoreId` / `assertStoreAccess`); `POST /adjust` (`PERM_INVENTORY_ADJUST`, plus explicit `storeAccessService.assertStoreAccess`); `GET /export` (`PERM_INVENTORY_ADJUST`).

All GET endpoints above with no method-level `@PreAuthorize` still require an authenticated session — `SecurityConfig` sets `.anyRequest().authenticated()` with only `/api/auth/**` and `/actuator/health` as `permitAll()`.

### DTOs (fields + validation)

- **`ProductCreateRequest` / `ProductUpdateRequest`** (identical field sets): `name` `@NotBlank`; `sku` `@NotBlank @Size(max=50) @Pattern(^[A-Za-z0-9\-_/]+$)`; `barcode` `@Size(max=50) @Pattern(^[A-Za-z0-9\-_/]*$)` (optional, `*` allows empty); `categoryId` `@NotNull`; `brand`, `unit` (plain strings); `purchasePrice` `@NotNull @DecimalMin(0)`; `sellingPrice` `@NotNull @DecimalMin(0)`; `tax` `@DecimalMin(0)`; `taxTreatment` (defaults to `TAXABLE` in the service if null); `minStockLevel`/`reorderLevel`/`reorderQuantity`/`maxStockLevel` `@Min(0)`; `mrp`/`wholesalePrice`/`freeValue`/`tolerancePercent` `@DecimalMin(0)`; plus free-text `description`, `manualCode`, `itemGroupId`, `hsnId`, `purchaseUnitId`, `saleUnitId`, `itemType`, `taxNature`, `taxBasedOn`, `partyName`, `partyProductName`, `applicableProperty` (all unvalidated/optional).
- **`ProductResponse`**: full product projection plus `stockQuantity` (current stock — see section 12 for aggregate vs. per-store), `hasTransactions` (drives frontend SKU/barcode lock), category/item-group/HSN/unit *names* denormalized alongside their ids.
- **`CategoryCreateRequest` / `CategoryUpdateRequest`**: `name` `@NotBlank`; `description`, `itemType`, `applicableProperty` unvalidated.
- **`CategoryResponse`**: id, name, description, itemType, applicableProperty, status, timestamps.
- **`StockAdjustmentRequest`**: `productId` `@NotNull`; `storeId` `@NotNull`; `movementType` `@NotNull` (any `StockMovementType`, but `InventoryService.adjustStock` further restricts it to `STOCK_IN`/`STOCK_OUT`/`ADJUSTMENT` — a `BadRequestException` if not); `quantity` `@NotNull @Min(1)`; `reason` `@NotBlank`; `notes` optional.
- **`InventoryResponse`**: id, product id/name/sku/category, store id/name/code, unit, `currentStock`, `minStockLevel` (defaults 0), `maxStockLevel` (from Product, nullable), `reorderLevel`/`reorderQuantity`, computed `stockStatus` (`OUT_OF_STOCK` if ≤0, else `LOW_STOCK` if ≤ minStockLevel, else `OVERSTOCK` if maxStockLevel set and exceeded, else `IN_STOCK`), timestamps.
- **`StockHistoryResponse`**: id, product id/name/sku, store id/name/code, movementType, quantity, previousStock, newStock, reason, referenceType, referenceId, notes, createdAt, createdBy.
- **`InventorySummaryResponse`**: totalProducts, totalStockUnits, lowStockCount, outOfStockCount, overstockCount, reorderCandidateCount.
- **`ImportResultResponse`**: totalRows, created, updated, skipped, errors (`List<String>`).
- **`GenerateSkuResponse`** / **`GenerateBarcodeResponse`**: single-field wrappers (`sku` / `barcode`).

### Services

**`ProductService`**
- `normalizeSku(String sku)` — private; `sku.trim().toUpperCase()`; the canonical form used everywhere (create/update/import/lookup), so `"  abc-001 "` and `"ABC-001"` are recognized as the same SKU.
- `validateBarcodeOrThrow(String barcode)` — private; null-safe no-op for a null barcode; else checks `BarcodeUtil.isValidCharacters` (`400` if not) and, only when `BarcodeUtil.detectType(barcode) == EAN13`, `BarcodeUtil.isValidEan13Checksum` (`400` "checksum failed" if not). Called from `createProduct`, `updateProduct`, and per-row in `importCsv` — deliberately at the service layer (not just DTO `@Pattern`) because CSV import builds a `Product` directly and never goes through the create/update DTOs.
- `createProduct(ProductCreateRequest)` — normalizes SKU, normalizes+detects barcode type; checks name/SKU/barcode uniqueness (`existsByNameIgnoreCase`, `existsBySkuIgnoreCase`, `existsByBarcodeIgnoreCase`) each throwing `BadRequestException`; resolves `Category` (`categoryService.findCategoryOrThrow`, mandatory) and optional `ItemGroup`/`Hsn`/`Unit` refs; builds+saves `Product`; **then calls `inventoryService.createInventoryForProduct(saved)`** to create the baseline (0-stock) Inventory row; logs `AuditAction.CREATE` on module `"ITEM_MASTER"`; returns `ProductResponse.fromEntity(saved, 0, request.getMaxStockLevel())`.
- `updateProduct(Long id, ProductUpdateRequest)` — the **edit-protection rule**: reads `hasTransactionHistory = stockHistoryRepository.existsByProductId(id)`; computes `skuChanging`/`barcodeChanging` by comparing normalized old vs. new values; if `skuChanging && hasTransactionHistory` → `BadRequestException` ("SKU cannot be changed because this item already has recorded stock, sales, or purchase history..."); same guard for `barcodeChanging && hasTransactionHistory`. Name/SKU/barcode uniqueness re-checked with `*AndIdNot` variants. On success, audit-logs either an SKU-changed entry, a barcode-changed entry, or (if neither changed) a generic "updated" entry — via three separate `auditService.log(...)` calls guarded by `if (skuChanging)` / `if (barcodeChanging)` / `if (!skuChanging && !barcodeChanging)`.
- `setStatus(Long id, ProductStatus status)` — activate/deactivate; audit-logs `UPDATE` with a lower-cased status description.
- `generateSku()` / `generateBarcode()` — thin passthroughs to `SkuGeneratorService.generateNext()` / `BarcodeGeneratorService.generateNext()`.
- `findByBarcode(String barcode)` — normalizes then `productRepository.findByBarcodeIgnoreCase(...).orElseThrow(ProductNotFoundException)`; if the matched product is `INACTIVE`, throws `BadRequestException` ("is inactive and cannot be used in new transactions") — used by POS/Sales/Purchase scanning.
- `backfillMissingSkus()` — startup safety net; iterates all products, generates+saves an SKU for any with none, audit-logs each backfill. Idempotent.
- **CSV export**: `exportCsv(search, categoryId, status)` — reuses `productRepository.search(...)` (the `Sort`-based, `LEFT JOIN FETCH category` variant) plus `inventoryService.getCurrentStockBulk(productIds)` (aggregate stock), builds a CSV via `CsvUtil.row(...)` with header `EXPORT_HEADER` (name, sku, barcode, category, brand, unit, purchasePrice, sellingPrice, tax, minStockLevel, reorderLevel, reorderQuantity, maxStockLevel, mrp, wholesalePrice, currentStock, status, description).
- **CSV import**: `importCsv(MultipartFile file)` — parses the file with `CsvUtil.parseLine`; requires header columns `name, sku, category, purchasePrice, sellingPrice` (`IMPORT_REQUIRED_COLUMNS`, else `400`); per row: validates required fields present, resolves `Category` by name (row skipped with an error if not found), parses numeric fields (negative purchase/selling price rejected), validates barcode; if a product with the normalized SKU exists → **update** path (name/category/brand/unit/prices/tax/minStockLevel/reorder fields/mrp/wholesalePrice/description/barcode updated, **current stock is never touched**); else → **create** path (new `Product` built and saved, `inventoryService.createInventoryForProduct(saved)` called for the baseline row, and if `openingStock > 0`, `inventoryService.applyMovement(saved.getId(), openingStock, StockMovementType.STOCK_IN, ReferenceType.MANUAL, null, "Opening stock via CSV import")` — i.e. opening stock is applied through the normal movement choke point, never a direct write). Per-row exceptions are caught and appended to `errors` without aborting the whole import. Returns `ImportResultResponse` with totals.

**`InventoryService`**
- `getCurrentStock(Long productId, Long storeId)` — **store-specific**; `inventoryRepository.findByProductIdAndStoreId(...).map(getCurrentStock).orElse(0)`. "The only figure a store-scoped transaction may ever act on."
- `getCurrentStock(Long productId)` — **cross-store aggregate**; `inventoryRepository.sumCurrentStockForProduct(productId)`. For global Item Master displays only, never transaction validation.
- `getCurrentStockBulk(List<Long> productIds)` — bulk aggregate equivalent (Products list / CSV export).
- `getCurrentStockBulk(List<Long> productIds, Long storeId)` — bulk **store-scoped** equivalent (store-filtered inventory/report views).
- `createInventoryForProduct(Product product)` — called from `ProductService.createProduct`/`importCsv`; resolves `storeService.getOrCreateDefaultStore()` and, if no `(product, defaultStore)` row exists yet, saves a 0-stock `Inventory` row. A real store-aware movement later creates whichever actual `(product, store)` row it needs via `getOrCreateInventory`.
- `getOrCreateInventory(Product, Store)` — private; `findByProductIdAndStoreId(...).orElseGet(save new 0-stock row)`.
- `adjustStock(StockAdjustmentRequest request)` — the manual-adjustment entry point: rejects any `movementType` not in `{STOCK_IN, STOCK_OUT, ADJUSTMENT}` (`400`); resolves `Product`, `resolveActiveStore(storeId)` (`400` if store `INACTIVE`), `getOrCreateInventory`; computes `newStock` per type (`STOCK_IN`→+qty, `STOCK_OUT`→-qty, `ADJUSTMENT`→set exactly to qty); rejects negative result (`400` with current/requested detail); saves `Inventory`, writes a `StockHistory` row (`referenceType = MANUAL`), and audit-logs `UPDATE` on module `"INVENTORY"` with old/new stock and the store's `storeId`.
- `applyMovement(Long productId, Long storeId, int delta, StockMovementType, ReferenceType, Long referenceId, String reason)` — **primary store-aware overload**, the single choke point for every system-triggered movement (spec: "the only one a store-attributed transaction should call"); resolves `Product`, `resolveActiveStore(storeId)`, `getOrCreateInventory`; `newStock = previousStock + delta`; `400` if negative; saves `Inventory`, writes `StockHistory` (`quantity = Math.abs(delta)`).
- `applyMovement(Long productId, int delta, StockMovementType, ReferenceType, Long referenceId, String reason)` — **transitional overload** without a storeId; delegates to the primary overload using `storeService.getOrCreateDefaultStore().getId()`. Used by call sites not yet migrated to pass their own resolved store (see section 13).
- `getSummary(Long storeId)` — `storeId == null` aggregates across every store (ALL_STORES admin view); else scoped counts via `InventoryRepository`.
- `getReorderCandidates(Long storeId)` — products at/below `reorderLevel`, `ACTIVE` only, ordered by stock ascending; used by the Alert Center.
- `exportCsv(search, categoryId, storeId, stockStatus)` — CSV of the filtered Inventory list (header: store, product, sku, category, unit, currentStock, minStockLevel, maxStockLevel, reorderLevel, reorderQuantity, stockStatus, lastUpdated).
- Startup-only maintenance methods: `relaxLegacyStockQuantityColumn()` (ALTERs `products.stock_quantity` to `NULL DEFAULT 0`, best-effort/idempotent), `relaxLegacyInventoryProductUniqueConstraint()` (drops any leftover single-column unique index on `inventory.product_id` other than `uk_inventory_product_store`, via `INFORMATION_SCHEMA`), `backfillInventoryForExistingProducts()` (seeds an `Inventory` row per pre-existing product from the legacy `stock_quantity` column, and backfills a null `store` on any pre-Multi-Store row, onto the Default Store).
- `resolveActiveStore(Long storeId)` — private; `storeRepository.findById(...).orElseThrow(MasterNotFoundException)`, `400` if `StoreStatus.INACTIVE`.
- `currentUsername()` — private; resolves the audit `createdBy` string from `SecurityContextHolder`, `"System"` if none.

**`CategoryService`** — straightforward CRUD: `getAllCategories()` (sorted by name), `getCategoryById`, `createCategory` (name-uniqueness check), `updateCategory` (name-uniqueness check excluding self), `setStatus` (activate/deactivate), `findCategoryOrThrow`. No audit logging calls in this service (unlike `ProductService`/`InventoryService`).

**`SkuGeneratorService`** — `generateNext()`: `@Transactional(propagation = REQUIRES_NEW)`; `itemSkuSequenceRepository.ensureRowExists(1L)` (native upsert-if-absent, avoids constraint-violation rollback under a race), then `lockForUpdate(1L)` (`PESSIMISTIC_WRITE`); increments `lastNumber`, formats `"ITEM-" + %06d`, loops while `productRepository.existsBySkuIgnoreCase(candidate)` (defensive, in case a manually-entered SKU already matches the pattern); saves the sequence row; returns the candidate.

**`BarcodeGeneratorService`** — `generateNext()`: identical pattern against `item_barcode_sequence` / `ItemBarcodeSequence`, prefix `"INT-"`, loop-guard against `productRepository.existsByBarcodeIgnoreCase(candidate)`.

### Repository (exact method names)

- **`ProductRepository`**: `existsByNameIgnoreCase`, `existsBySkuIgnoreCase`, `existsBySkuIgnoreCaseAndIdNot`, `existsByBarcodeIgnoreCase`, `existsByBarcodeIgnoreCaseAndIdNot`, `findBySkuIgnoreCase`, `findByBarcodeIgnoreCase`, `search(search, categoryId, status, Pageable)`, `search(search, categoryId, status, Sort)` (export-only, `LEFT JOIN FETCH p.category`).
- **`CategoryRepository`**: `existsByNameIgnoreCase`, `existsByNameIgnoreCaseAndIdNot`, `findByNameIgnoreCase`.
- **`ItemSkuSequenceRepository`**: `lockForUpdate(id)` (`@Lock(PESSIMISTIC_WRITE)`), `ensureRowExists(id)` (native `INSERT ... ON DUPLICATE KEY UPDATE`).
- **`ItemBarcodeSequenceRepository`**: `lockForUpdate(id)`, `ensureRowExists(id)` — identical shape.
- **`InventoryRepository`** (store-aware methods are the primary/authoritative ones): `findByProductIdAndStoreId(productId, storeId)`, `findByProductId(productId)` (every store's row — cross-store aggregation only), `findByStoreId(storeId)`, `sumCurrentStockForProduct(productId)`, `sumCurrentStockGroupedByProduct(productIds)`, `findCurrentStockByProductIdsAndStoreId(productIds, storeId)`, `search(search, categoryId, storeId, stockStatus, Pageable)`, `search(..., Sort)` (export-only, `JOIN FETCH`), `sumCurrentStock(storeId)`, `countLowStock(storeId)`, `countOutOfStock(storeId)`, `countOverstock(storeId)`, `findReorderCandidates(storeId)`, `countReorderCandidates(storeId)`.
- **`StockHistoryRepository`**: `findByProductIdOrderByCreatedAtDesc(productId, Pageable)`, `existsByProductId(productId)` (drives the edit-protection rule), `search(productId, storeId, movementType, referenceType, fromDateTime, toDateTime, Pageable)`.

## 5. Database

### Tables

- **`products`** — id, name (unique), sku (unique), barcode (unique), barcode_type, category_id (FK), brand, unit, purchase_price, selling_price, tax, tax_treatment, min_stock_level, reorder_level, reorder_quantity, max_stock_level, mrp, wholesale_price, status, description, manual_code, item_group_id (FK), hsn_id (FK), purchase_unit_id (FK), sale_unit_id (FK), tolerance_percent, item_type, tax_nature, tax_based_on, party_name, party_product_name, free_value, applicable_property, created_at, updated_at. Legacy `stock_quantity` column still physically present but unmapped (nullable, defaulted to 0 at startup).
- **`categories`** — id, name (unique), description, item_type, applicable_property, status, created_at, updated_at.
- **`inventory`** — id, product_id (FK, not null), store_id (FK, nullable at schema level only), current_stock, created_at, updated_at. Unique constraint `uk_inventory_product_store` on `(product_id, store_id)`.
- **`stock_history`** — id, product_id (FK, not null), store_id (FK, nullable — pre-Multi-Store rows only), movement_type, quantity, previous_stock, new_stock, reason, reference_type, reference_id, notes, created_at, created_by.
- **`item_sku_sequence`** — id (always 1), last_number.
- **`item_barcode_sequence`** — id (always 1), last_number.

### Relationships

- `Inventory.product` → `Product` (`@ManyToOne`, `product_id`, not null).
- `Inventory.store` → `Store` (`@ManyToOne`, `store_id`, nullable only for schema-migration safety).
- `StockHistory.product` → `Product` (`@ManyToOne`, not null).
- `StockHistory.store` → `Store` (`@ManyToOne`, nullable for pre-Multi-Store rows).
- `Product.category` → `Category` (`@ManyToOne`, `category_id`).
- `Product.itemGroup` → `ItemGroup`, `Product.hsn` → `Hsn`, `Product.purchaseUnit`/`Product.saleUnit` → `Unit` (all `@ManyToOne`, optional, other masters modules).

## 6. Validation

- **DTO-level (Bean Validation)**: see section 4's DTO list — `@NotBlank`, `@NotNull`, `@Size`, `@Pattern`, `@DecimalMin`, `@Min` on `ProductCreateRequest`/`ProductUpdateRequest`, `CategoryCreateRequest`/`CategoryUpdateRequest`, `StockAdjustmentRequest`.
- **Service-level (beyond DTO annotations, since CSV import bypasses the DTOs entirely)**: SKU/barcode/name uniqueness checks in `ProductService`; `validateBarcodeOrThrow` (character set + EAN-13 checksum); `StockHistoryRepository.existsByProductId` edit-protection guard on SKU/barcode change; `InventoryService.adjustStock`/`applyMovement` negative-stock rejection; `resolveActiveStore` inactive-store rejection; `CategoryService` name-uniqueness.
- **CSV import row-level**: required-column presence, required-field-per-row presence, category-name lookup, non-negative numeric parsing (purchase/selling price), barcode validation, duplicate-name/barcode detection distinct from the create/update DTO path.

## 7. Authentication / Authorization

Every endpoint in this module requires an authenticated session (`SecurityConfig`'s `.anyRequest().authenticated()`); there is no `permitAll()` carve-out for Product/Category/Inventory. Mutating/administrative endpoints additionally require a specific `@PreAuthorize("hasAuthority('PERM_...')")` (see section 4); plain read endpoints (list/detail/barcode-lookup) rely only on authentication, so any authenticated role can view products/categories/inventory. `InventoryController` additionally layers **store-access authorization** via `StoreAccessService.resolveViewableStoreId` / `assertStoreAccess`, checked against the authenticated `UserPrincipal`'s accessible stores — this is independent of the `PERM_*` permission checks.

## 8. Permissions

From `Permission.java` / `RolePermissions.java`:

- `ITEM_VIEW`, `ITEM_CREATE`, `ITEM_EDIT` — module `"Master"`. `ITEM_CREATE`/`ITEM_EDIT` gate `ProductController`'s mutating/import/export endpoints. `ITEM_VIEW` is defined but **not** enforced via `@PreAuthorize` on any Product/Category GET endpoint in this codebase (those rely on `.anyRequest().authenticated()` only); it is granted to `ADMIN`(implicit)/`ACCOUNTANT`/`SALES_USER`/`PURCHASE_USER`/`INVENTORY_USER`/`STAFF` roles per `RolePermissions`.
- `MASTER_MANAGE` — module `"Master"`. Gates all mutating `CategoryController` endpoints.
- `INVENTORY_VIEW`, `INVENTORY_ADJUST` — module `"Inventory"`. `INVENTORY_ADJUST` gates `POST /api/inventory/adjust` and `GET /api/inventory/export`. `INVENTORY_VIEW` is defined and assigned to `ACCOUNTANT`/`INVENTORY_USER` roles but, like `ITEM_VIEW`, is **not** enforced via `@PreAuthorize` on `InventoryController`'s GET endpoints in the current code — access there is controlled by authentication + `StoreAccessService`, not this permission.
- `STORE_TRANSFER_*` permissions exist but belong to the separate Stock Transfer module (see section 13).

Role assignment excerpt (`RolePermissions`): `INVENTORY_USER` gets `ITEM_VIEW, INVENTORY_VIEW, INVENTORY_ADJUST, STOCK_TRANSFER_VIEW, STOCK_TRANSFER_CREATE, STOCK_TRANSFER_DISPATCH, STOCK_TRANSFER_RECEIVE, MASTER_VIEW, STORE_VIEW`; `ACCOUNTANT` gets `ITEM_VIEW, ..., INVENTORY_VIEW` (view-only, no adjust); `SALES_USER`/`PURCHASE_USER`/`STAFF` get `ITEM_VIEW` but no `INVENTORY_*` permissions at all.

## 9. Transaction Handling

- `ProductService.createProduct`, `updateProduct`, `setStatus`, `backfillMissingSkus`, `importCsv` are all `@Transactional` (default propagation) — a failure anywhere (including the `inventoryService.createInventoryForProduct` call inside `createProduct`) rolls back the whole product write.
- `InventoryService.adjustStock`, `applyMovement` (both overloads), `createInventoryForProduct`, and the startup migration methods are all `@Transactional` (default propagation) — an `Inventory` update and its paired `StockHistory` insert commit or roll back together.
- `SkuGeneratorService.generateNext()` and `BarcodeGeneratorService.generateNext()` run in their **own `REQUIRES_NEW` transaction**, deliberately separate from the caller's transaction, so the counter row's pessimistic lock is acquired/released independently — mirrors `VoucherNumberService`'s pattern. This means a generated SKU/barcode number is "spent" even if the caller's own transaction later rolls back (by design — gaps are acceptable, duplicate numbers are not).
- `AuditService.log(...)` is `@Transactional` with **default propagation** (explicitly *not* `REQUIRES_NEW`) — an audit entry participates in and rolls back with the same transaction as the business action it describes, and any exception inside audit-writing itself is caught/logged rather than propagated, so a transient audit failure never blocks the business operation.
- CSV import (`ProductService.importCsv`) processes all rows inside one `@Transactional` method but catches per-row exceptions internally (adding them to `errors` and continuing) rather than letting one bad row abort the whole batch.

## 10. Error Handling

`GlobalExceptionHandler` (backend/src/main/java/com/storehub/exception/GlobalExceptionHandler.java) maps:
- `MethodArgumentNotValidException` (DTO `@Valid` failures) → `400` with `ApiError.fieldErrors` populated from each field's `FieldError` (field name → `defaultMessage`), message `"Validation failed"`.
- `ProductNotFoundException` → `404` (has both an `id`-based and a `barcode`-based message constructor).
- `CategoryNotFoundException` → `404`.
- `InventoryNotFoundException` → `404`.
- `BadRequestException` (used throughout `ProductService`/`InventoryService`/`CategoryService` for uniqueness violations, edit-protection, invalid barcode, negative stock, inactive store/product) → `400`, message is the exact string thrown by the service (always specific — SKU/barcode/name/value named explicitly per the project's debugging-rule convention).

On the frontend, `parseApiError()` (`frontend/src/utils/apiError.ts`) reads `err.response.data` as `ApiErrorResponse`; if `fieldErrors` is non-empty it builds a combined "Field: reason | Field: reason" message *and* returns the raw map for per-field `invalid`/`Form.Control.Feedback`-style rendering (`ProductForm.tsx`, `ProductQuickAddModal.tsx`, `CategoryQuickAddModal.tsx`, `StockAdjustmentDialog.tsx` all consume `fieldErrors`); otherwise it falls back to `apiError.message` or a caller-supplied fallback string. A missing HTTP response at all (network/CORS/backend-down) is distinguished with its own message.

## 11. Audit Flow

`AuditService.log(...)` call sites in this module (all via `com.storehub.entity.AuditAction`):

- `ProductService.createProduct` — `AuditAction.CREATE`, module `"ITEM_MASTER"`, entityType `"Product"`, description `"Item '<name>' (SKU <sku>) created"`.
- `ProductService.updateProduct` — up to one of three `AuditAction.UPDATE` calls, module `"ITEM_MASTER"`: (a) if SKU changed, description `"SKU changed for item '<name>'"` with old/new SKU; (b) if barcode changed, `"Barcode changed for item '<name>'"` with old/new barcode; (c) if neither changed, generic `"Item '<name>' (SKU <sku>) updated"`.
- `ProductService.setStatus` — `AuditAction.UPDATE`, module `"ITEM_MASTER"`, description `"Item '<name>' (SKU <sku>) <active/inactive>"`.
- `ProductService.backfillMissingSkus` — `AuditAction.UPDATE`, module `"ITEM_MASTER"`, description `"SKU backfilled for legacy item '<name>' which had none"`, per product.
- `InventoryService.adjustStock` — `AuditAction.UPDATE`, module `"INVENTORY"`, entityType `"Product"`, old/new value = previous/new stock as strings, description `"Stock adjustment (<type>) on '<name>' at store '<storeCode>': <reason>"`, **with the store's id passed** (the `storeId` overload of `log`, so the entry is attributed to a specific store — Multi-Store spec).

`CategoryService` has **no** `AuditService` calls — category create/update/status changes are not audited in this module.

## 12. Important Side Effects

- **Store-aware inventory isolation.** The same `Product` can have completely independent `current_stock` at every store it has an `Inventory` row for; nothing about the Product entity itself carries a quantity. A store-scoped operation (`adjustStock`, the primary `applyMovement` overload) only ever reads/writes the one `(product, store)` row it resolves — it is architecturally impossible for a store-scoped write to affect another store's stock.
- **Default Store fallback.** `createInventoryForProduct` (new product's baseline row) and the transitional `applyMovement(productId, delta, ...)` overload (used by call sites not yet passing their own storeId — see section 13) both attribute to `storeService.getOrCreateDefaultStore()`. This is the same "historical-data-safety anchor" used by the Multi-Store startup backfill (`backfillInventoryForExistingProducts`) for pre-existing data.
- **Low stock / reorder threshold logic — two distinct thresholds, not one.** `minStockLevel` (on `Product`) drives `LOW_STOCK` classification (`InventoryResponse.fromEntity`: `currentStock <= minStockLevel` → `LOW_STOCK`) and the `InventoryRepository.countLowStock`/`.search` queries. `reorderLevel` (also on `Product`, separately configurable) drives `InventoryRepository.findReorderCandidates`/`countReorderCandidates` (`currentStock <= reorderLevel AND status = ACTIVE`) — used by the Alert Center, distinct from the LOW_STOCK stock-status badge. `maxStockLevel` (on `Product`, moved off `Inventory` specifically so it isn't duplicated per-store) drives `OVERSTOCK` classification.
- **CSV injection protection.** `CsvUtil.escape()` prefixes any cell whose first character is `=`, `+`, `-`, `@`, tab, or CR with a literal `'` before RFC4180 quoting — guards against a product name/category planted by one user executing as a spreadsheet formula when another user opens the exported CSV. `BarcodeUtil`'s `SAFE_CHARACTERS` pattern (`^[A-Za-z0-9\-_/]+$`) also incidentally closes off a leading `=` at the barcode-validation layer.
- **CSV import never double-counts stock.** Re-importing an existing SKU updates all its master-data fields but explicitly never touches `currentStock`; opening stock only applies to newly-created rows, and even then only through `applyMovement` (a real, audited `STOCK_IN` movement), never a direct `Inventory` write.
- **Legacy schema accommodations.** `products.stock_quantity` remains in the DB (per migration policy, never dropped) but is unmapped by the entity; `InventoryStartupRunner` relaxes it to nullable and backfills `Inventory` rows from it on every startup (idempotent, best-effort, swallows failures).

## 13. Dependencies on Other Modules

**`InventoryService.applyMovement(...)` callers** (grepped across `backend/src/main/java/com/storehub/service`):

| Caller | File:Line | Context |
|---|---|---|
| Purchase reversed (cancel) | `PurchaseService.java:437-438` | `restoreStock`: `applyMovement(productId, purchase.getStore().getId(), -qty, PURCHASE_CANCEL, ReferenceType.PURCHASE, purchase.getId(), reason)` |
| Purchase posted | `PurchaseService.java:445-446` | `addStock`: `applyMovement(productId, purchase.getStore().getId(), +qty, PURCHASE, ReferenceType.PURCHASE, purchase.getId(), reason)` |
| Sale cancelled | `SaleService.java:488-489` | `restoreStock`: `applyMovement(productId, sale.getStore().getId(), +qty, SALE_CANCEL, ReferenceType.SALE, sale.getId(), reason)` |
| Sale posted | `SaleService.java:496-497` | `deductStock`: `applyMovement(productId, sale.getStore().getId(), -qty, SALE, ReferenceType.SALE, sale.getId(), reason)` |
| Credit Note stock return | `CreditNoteService.java:217-218` (store known) / `220-221` (transitional overload, store unknown) | `applyMovement(productId, [storeId,] +qty, SALES_RETURN, ReferenceType.CREDIT_NOTE, note.getId(), "Sales return: Credit Note ...")` |
| Credit Note cancel/reverse | `CreditNoteService.java:257-258` (store known) / `260-261` (transitional) | `applyMovement(productId, [storeId,] -qty, ADJUSTMENT, ReferenceType.CREDIT_NOTE, note.getId(), reason)` |
| Debit Note stock return | `DebitNoteService.java:206-207` (store known) / `209-210` (transitional) | `applyMovement(productId, [storeId,] -qty, PURCHASE_RETURN, ReferenceType.DEBIT_NOTE, note.getId(), "Purchase return: Debit Note ...")` |
| Debit Note cancel/reverse | `DebitNoteService.java:245-246` (store known) / `248-249` (transitional) | `applyMovement(productId, [storeId,] +qty, ADJUSTMENT, ReferenceType.DEBIT_NOTE, note.getId(), reason)` |
| Stock Transfer dispatch | `StockTransferService.java:148-150` | `applyMovement(productId, transfer.getFromStore().getId(), -qty, TRANSFER_OUT, ReferenceType.STOCK_TRANSFER, transfer.getId(), "...dispatched...")` |
| Stock Transfer receive | `StockTransferService.java:172-174` | `applyMovement(productId, transfer.getToStore().getId(), +qty, TRANSFER_IN, ReferenceType.STOCK_TRANSFER, transfer.getId(), "...received...")` |
| CSV opening stock (this module) | `ProductService.java:515-516` | `applyMovement(saved.getId(), openingStock, STOCK_IN, ReferenceType.MANUAL, null, "Opening stock via CSV import")` (transitional overload) |

So this module's `InventoryService` is a dependency **of** the Purchase, Sale, Credit Note, Debit Note, and Stock Transfer modules (all call into it), while this module itself only calls into the **Store** module: `InventoryService.applyMovement(productId, delta, ...)` (the transitional, no-storeId overload) and `createInventoryForProduct` both call `storeService.getOrCreateDefaultStore()` — the only outbound dependency of Product/Inventory on another module (`StoreService`, `StoreAccessService`). `InventoryController` also depends on `StoreAccessService` for view-access scoping (section 7).

**Note on Stock Transfer**: `StockTransferService`/`StockTransferController` (a separate module) is the *only* caller that moves stock **between** two stores for the same product (`TRANSFER_OUT` from source, `TRANSFER_IN` to destination) — it is documented separately but depends on this module's `InventoryService.applyMovement` exactly like every other caller.

## 14. Key Operation Flows

**Create Product (with baseline Inventory row).**
`ProductForm.tsx` (submit) → `productApi.create(payload)` → `POST /api/products` → `ProductController.createProduct` (`@PreAuthorize PERM_ITEM_CREATE`, `@Valid ProductCreateRequest`) → `ProductService.createProduct`: normalize SKU/barcode → uniqueness checks (name/SKU/barcode) → resolve Category (+ optional ItemGroup/Hsn/Unit) → build+save `Product` (`ProductRepository.save`) → **`InventoryService.createInventoryForProduct(saved)`** → `storeService.getOrCreateDefaultStore()` → `InventoryRepository.findByProductIdAndStoreId` (miss) → `InventoryRepository.save` (new 0-stock row, table `inventory`) → `AuditService.log(CREATE, "ITEM_MASTER", ...)` (table `audit_logs`, other module) → returns `ProductResponse` → `201 Created` → frontend navigates to `/products` with a success toast.

**Generate SKU.**
`ProductForm.tsx` "Generate" button → `productApi.generateSku()` → `POST /api/products/generate-sku` (`PERM_ITEM_CREATE`) → `ProductController.generateSku` → `ProductService.generateSku()` → `SkuGeneratorService.generateNext()` (own `REQUIRES_NEW` tx) → `ItemSkuSequenceRepository.ensureRowExists(1L)` (native upsert) → `lockForUpdate(1L)` (row lock on `item_sku_sequence`) → increment `lastNumber`, format `ITEM-%06d`, loop while `productRepository.existsBySkuIgnoreCase(candidate)` → `save` the sequence row → returns candidate string → `GenerateSkuResponse` → frontend sets the SKU input value (still editable/submittable, not yet persisted to a product).

**Generate Internal Barcode.**
Same shape as SKU generation: `ProductForm.tsx` → `productApi.generateBarcode()` → `POST /api/products/generate-barcode` (`PERM_ITEM_CREATE`) → `ProductService.generateBarcode()` → `BarcodeGeneratorService.generateNext()` (own `REQUIRES_NEW` tx, `item_barcode_sequence` row lock) → format `INT-%06d`, loop while `productRepository.existsByBarcodeIgnoreCase(candidate)` → `GenerateBarcodeResponse` → frontend sets the Barcode field.

**Manual Stock Adjustment (STOCK_IN / STOCK_OUT / ADJUSTMENT).**
`StockAdjustmentDialog.tsx` (form step: pick product/store, type, quantity, reason → client-side `validate()` → review step → Confirm) → `inventoryApi.adjust(payload)` → `POST /api/inventory/adjust` (`PERM_INVENTORY_ADJUST`, `@Valid StockAdjustmentRequest`) → `InventoryController.adjustStock`: `storeAccessService.assertStoreAccess(user, request.getStoreId())` (403 if not accessible) → `InventoryService.adjustStock(request)`: reject non-manual movement types → resolve `Product`, `resolveActiveStore` (400 if inactive) → `getOrCreateInventory` → compute `newStock` per type → reject if negative (400, exact current/requested numbers in the message) → `InventoryRepository.save` (updates `inventory.current_stock`) → `StockHistoryRepository.save` (new row in `stock_history`, `referenceType = MANUAL`) → `AuditService.log(UPDATE, "INVENTORY", ..., storeId)` → returns `InventoryResponse` → frontend success toast, reloads list + summary.

**CSV Import.**
`Products.tsx` (file picker) → `productApi.importCsv(file)` → `POST /api/products/import` (`PERM_ITEM_CREATE`, multipart) → `ProductController.importProducts` → `ProductService.importCsv(file)`: read+parse lines (`CsvUtil.parseLine`) → validate header has required columns → per row: validate required fields, resolve Category, parse numerics, validate barcode → **existing SKU** → update fields on the existing `Product` (never touches stock) → **new SKU** → build+save `Product`, `createInventoryForProduct` (baseline row), if `openingStock > 0` → `InventoryService.applyMovement(id, openingStock, STOCK_IN, MANUAL, null, "Opening stock via CSV import")` (writes `inventory` + `stock_history`) → accumulate created/updated/skipped/errors → `ImportResultResponse` → frontend renders the result summary + per-row error list, reloads the product list on any success.

**CSV Export.**
`Products.tsx` / `Inventory.tsx` Export button → `productApi.exportCsv(filters)` / `inventoryApi.exportCsv(filters)` → `GET /api/products/export` (`PERM_ITEM_EDIT`) or `GET /api/inventory/export` (`PERM_INVENTORY_ADJUST`) → `ProductService.exportCsv` / `InventoryService.exportCsv`: re-run the same filtered `search(...)` query used by the list page (unpaged, `Sort`/`LEFT JOIN FETCH` variant), plus bulk stock lookup for products → build CSV rows via `CsvUtil.row`/`CsvUtil.escape` (formula-injection-safe) → controller sets `Content-Disposition: attachment; filename="products-<date>.csv"` (or `inventory-<date>.csv`), `text/csv; charset=UTF-8` → frontend `downloadBlob(res.data, filename)` triggers the browser download.

## 15. Manual Changes

- **Add a new Product field**: add the column to `Product` entity (`backend/.../entity/Product.java`), add it to `ProductCreateRequest`/`ProductUpdateRequest` with appropriate Bean Validation annotations, wire it through `ProductService.createProduct`/`updateProduct` (builder/setters) and `ProductResponse.fromEntity`, add it to the CSV `EXPORT_HEADER`/export row in `ProductService.exportCsv` and (if importable) to the CSV import row-parsing block in `ProductService.importCsv`, then add the corresponding field to frontend `types/product.ts` (`Product`/`ProductPayload`) and the relevant form section in `ProductForm.tsx` (and `ProductViewModal.tsx` if it should be displayed).
- **Change SKU format**: `SkuGeneratorService` (prefix `"ITEM-"`, `%06d` padding in `format(long)`) — the `@Pattern` on `ProductCreateRequest`/`ProductUpdateRequest.sku` (`^[A-Za-z0-9\-_/]+$`) must stay permissive enough for whatever new format is chosen, or be updated in lockstep. `normalizeSku` in `ProductService` controls case/whitespace normalization independent of format.
- **Change barcode format/detection**: `BarcodeUtil` (`backend/.../util/BarcodeUtil.java`) — `SAFE_CHARACTERS`, `detectType()`'s pattern order (INTERNAL prefix checked first), and `isValidEan13Checksum()`. `BarcodeGeneratorService`'s `PREFIX = "INT-"` must stay in sync with `BarcodeUtil.INTERNAL_PREFIX` so generated codes are still auto-detected as `INTERNAL`.
- **Change the edit-protection rule**: `ProductService.updateProduct` — the `hasTransactionHistory` check is `stockHistoryRepository.existsByProductId(id)`; the guard conditions are `if (skuChanging && hasTransactionHistory)` and `if (barcodeChanging && hasTransactionHistory)`. To protect additional fields (e.g. category) the same pattern (compute a `*Changing` boolean, guard on `hasTransactionHistory`) would need to be added here, and `ProductResponse.hasTransactions` / `ProductForm.tsx`'s `disabled={isEdit && hasTransactions}` logic extended to the new field's inputs.
- **Add a new `StockMovementType`**: add the enum constant to `backend/.../entity/StockMovementType.java`; if it should be selectable via manual adjustment, add it to `InventoryService.MANUAL_MOVEMENT_TYPES`; add it to the frontend `types/inventory.ts` `StockMovementType` union (and `ManualAdjustmentType` if manual) and to `StockHistoryModal.tsx`'s `MOVEMENT_TYPES` filter list / `movementVariant()` color mapping; decide which caller (Purchase/Sale/Credit Note/Debit Note/Stock Transfer/this module) should pass it to `applyMovement`.
- **Change low-stock threshold logic**: `InventoryResponse.fromEntity` (`stockStatus` computation: `LOW_STOCK` at `currentStock <= minStockLevel`, `OVERSTOCK` at `currentStock > maxStockLevel`) and the matching JPQL in `InventoryRepository.search`/`countLowStock`/`countOverstock`/`countOutOfStock` must be changed together (the JPQL literally repeats the same boundary logic as the Java code — no shared helper). Reorder-specific logic (distinct from low-stock) lives in `InventoryRepository.findReorderCandidates`/`countReorderCandidates` against `Product.reorderLevel`. The frontend's own classifier `utils/stockStatus.ts` (`getStockStatus`, Products page only, ignores `maxStockLevel`/overstock) would need a matching update if threshold semantics change, since it currently duplicates only the low/out-of-stock half of the backend logic.
- **Modules affected by any `InventoryService.applyMovement` signature or behavior change**: every caller listed in section 13 — `PurchaseService`, `SaleService`, `CreditNoteService`, `DebitNoteService`, `StockTransferService`, and this module's own `ProductService.importCsv`. **Every place stock is displayed** that would need re-checking after a stock-computation change: `Products.tsx` (`stockQuantity`, aggregate), `Inventory.tsx`/`InventoryViewModal.tsx`/`StockAdjustmentDialog.tsx` (`currentStock`, per-store), `ProductViewModal.tsx`, `StockHistoryModal.tsx` (previous/new stock columns), plus `ProductResponse.stockQuantity` and `InventoryResponse.currentStock`/`stockStatus` on the backend, and any Dashboard/Alert Center widgets that read `InventoryService.getSummary`/`getReorderCandidates` (outside this module's ground-truth file list — NOT FOUND IN CURRENT CODEBASE for this doc's read set, cross-check `AlertService`/dashboard controllers separately if changing summary semantics).
