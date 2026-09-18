# Sales Module

## 1. Overview

The Sales module covers the full customer-facing revenue lifecycle in StoreHub: **Sales Order** (optional pre-sale commitment) → **Sales Bill** (`SaleController`/`SaleService`, covering GST Sale, Kacchi Sale/Sale Challan, and POS/Counter Sale — all the same `Sale` entity and `SaleService.createSale`) → **Receipt Entry** (payment collection against outstanding bills) → **Credit Note** (sales return / adjustment against a posted bill).

**`TransactionType`** (`com.storehub.entity.TransactionType`) distinguishes document kind at creation and is fixed thereafter:
- `SALE` — a normal GST or Non-GST Sales Bill. `gstReportingApplicable = true`.
- `SALE_CHALLAN` — a Kacchi Sale / Sale Challan. GST is still calculated **in full** (CGST/SGST/IGST populated exactly like a normal sale) but `gstReportingApplicable = false`, so it is excluded from GSTR-1/GSTR-3B reporting. It is the only `TransactionType` allowed to be saved as `SaleStatus.DRAFT`.
- `PURCHASE` / `PURCHASE_CHALLAN` belong to the Purchase module (same enum, out of scope here).

**`SaleStatus`** (`com.storehub.entity.SaleStatus`):
- `DRAFT` — Kacchi Sale only, saved but not posted: no stock/ledger/GST-log/accounting effects exist yet.
- `PENDING` — the `@PrePersist` default if `status` is left null (not reachable through the documented create flow, since `createSale` always sets `DRAFT` or `COMPLETED` explicitly).
- `COMPLETED` — posted: all side effects (stock deduction, customer ledger debit, GST log, accounting journal) have been applied.
- `CANCELLED` — soft-reversed: record kept for history, all posting side effects reversed.

**POS / Counter Sale** (`Pos.tsx`) is not a separate backend endpoint — it calls the exact same `POST /api/sales` (`SaleService.createSale`) as a normal Sales Bill, with `transactionType: 'SALE'`. It is protected from duplicate submission by a client-generated **idempotency key** (`clientRequestId`), detailed in section 4 (Services) and section 12.

Sales Orders (`SalesOrderController`/`SalesOrderService`) never touch stock or accounts — only a Sales Bill created against an order's remaining quantity does (`SalesOrderItem.billedQuantity`/`remainingQuantity`, tracked via `SalesOrderService.applyBilledQuantityDelta`).

## 2. Frontend

### Files and Components

| File | Role |
|---|---|
| `frontend/src/pages/sales/SalesHub.tsx` | Landing page for the module: summary stat cards (`salesSummaryApi.get()`) and navigation cards to POS, Sales Orders, Sales Bills, Kacchi Sales, Receipts, Credit Notes. |
| `frontend/src/pages/sales/Pos.tsx` | POS / Counter Sale screen: barcode-scanner-friendly product search, cart, customer lookup, payment, and idempotent `saleApi.create` submission with a printable receipt view. |
| `frontend/src/pages/sales/SalesOrders.tsx` | Sales Order list: search/filter (customer, status, date range), cancel action. |
| `frontend/src/pages/sales/SalesOrderForm.tsx` | Create/edit Sales Order: customer, dates, item rows with quantity/rate/discount/GST%, "already billed" quantity floor enforced client-side. |
| `frontend/src/pages/sales/SalesOrderDetail.tsx` | Sales Order detail: status, totals, items with billed/remaining columns, actions to Create Bill / Edit / Cancel. |
| `frontend/src/pages/sales/SalesBills.tsx` | Sales Bill (GST/Non-GST) list: search/filter, row actions (View/Print/Edit/Receipt/Delete). |
| `frontend/src/pages/sales/SalesBillForm.tsx` | Create/edit Sales Bill from scratch or (`?orderId=`) pre-filled from a Sales Order's remaining items; barcode scan-to-add; GST/Non-GST + tax-mode toggle; place-of-supply tax-mode suggestion on customer select; locked (`fieldset disabled`) once the bill has receipts. |
| `frontend/src/pages/sales/SalesBillDetail.tsx` | Printable invoice view; actions for Edit, Receipt, Return (Credit Note), Delete (guarded by `hasPermission('SALES_CANCEL')`). |
| `frontend/src/pages/sales/KacchiSales.tsx` | Kacchi Sale / Sale Challan list, filtered server-side by `transactionType: 'SALE_CHALLAN'`; shows GST Reporting column (always NO); Post action for DRAFT rows. |
| `frontend/src/pages/sales/KacchiSaleForm.tsx` | Create/edit challan: always GST (`gstType: 'GST'` fixed), "Save as Draft" vs "Post Now"/"Save & Post" buttons; a confirm dialog before posting. |
| `frontend/src/pages/sales/KacchiSaleDetail.tsx` | Printable challan view with explicit "GST Calculated: YES · GST Reporting: NO" banner; Post/Edit (DRAFT only)/Return/Delete actions. |
| `frontend/src/pages/sales/Receipts.tsx` | Receipt list: search/filter by customer/date, Delete action. |
| `frontend/src/pages/sales/ReceiptForm.tsx` | Record a receipt: customer select loads `receiptApi.getOutstanding`, an optional per-bill checkbox/amount allocation table (falls back to FIFO auto-allocation server-side if none checked). |
| `frontend/src/pages/sales/ReceiptDetail.tsx` | Printable receipt view with "Applied Against" allocation table (or "on-account credit" if none); Delete action. |
| `frontend/src/pages/sales/CreditNotes.tsx` | Credit Note list: search/filter by status. |
| `frontend/src/pages/sales/CreditNoteForm.tsx` | Create a Credit Note: search/select a `COMPLETED` source sale (or pre-loaded via `?saleId=`), note type/date/stock-impact/reason, per-line return-quantity inputs, "Save as Draft" vs "Save and Post". |
| `frontend/src/pages/sales/CreditNoteDetail.tsx` | Credit Note detail/print view; Post (DRAFT) / Cancel actions gated to `ADMIN`/`STORE_MANAGER` client-side. |
| `frontend/src/api/saleApi.ts` | Axios wrapper for `/api/sales/**` (list, getById, create, update, post, cancel, remove). |
| `frontend/src/api/salesOrderApi.ts` | Axios wrapper for `/api/sales-orders/**` (search, getById, create, update, setStatus, cancel). |
| `frontend/src/api/receiptApi.ts` | Axios wrapper for `/api/receipts/**` (search, getById, getOutstanding, create, remove). |
| `frontend/src/api/salesSummaryApi.ts` | Axios wrapper for `GET /api/sales-summary`. |
| `frontend/src/api/creditNoteApi.ts` | Axios wrapper for `/api/credit-notes/**` (list, getById, create, post, cancel). |
| `frontend/src/types/sale.ts` | `Sale`, `SaleItem`, `SaleCreatePayload`/`SaleUpdatePayload`, `SaleStatus`, `SaleTransactionType`, `GstType`, `TaxMode`, `PaymentMode`, `Customer`. |
| `frontend/src/types/salesOrder.ts` | `SalesOrder`, `SalesOrderItem`, `SalesOrderPayload`, `SalesOrderStatus`. |
| `frontend/src/types/receipt.ts` | `Receipt`, `ReceiptAllocation`, `ReceiptPayload`, `OutstandingBill`, `CustomerOutstanding`, `SalesSummary`. |
| `frontend/src/types/note.ts` | `CreditNote`, `CreditNoteItem`, `CreditNoteCreatePayload`, `NoteStatus`, `StockImpactType`, `CreditNoteType` (also carries the unrelated Debit Note types for the Purchase module). |

### Frontend Flow

**Sales Order create/edit** — `SalesOrderForm.tsx` builds a `SalesOrderPayload` (customer, dates, item rows with productId/quantity/rate/discount/gstPercent) and calls `salesOrderApi.create`/`update`. On edit, an item that already has `billedQuantity > 0` cannot be removed or reduced below that quantity (client-side `validate()`, mirrored server-side). New orders always start `DRAFT`; `SalesOrderDetail.tsx` offers Edit while `DRAFT`/`CONFIRMED`/`PARTIALLY_BILLED`, Cancel while `DRAFT`/`CONFIRMED`, and "Create Sales Bill" whenever not `COMPLETED`/`CANCELLED`.

**Sales Bill — from scratch** — `SalesBillForm.tsx` with no `orderId`/`id`: pick GST/Non-GST + tax mode, customer (best-effort tax-mode suggestion via `suggestTaxMode(sellerState, customer.state)`), item rows (with a barcode-scan-to-add box), payment mode + paid amount. `handleSubmit` builds a `SaleCreatePayload` (`transactionType` omitted → defaults to `SALE` server-side) and calls `saleApi.create`.

**Sales Bill — from a Sales Order** — navigated to via `/sales/bills/new?orderId={id}`; `loadFromOrder()` fetches the order and pre-fills customer/address fields and one row per item with `remainingQuantity > 0`, carrying `salesOrderItemId` and a `maxQuantity` cap that the quantity input and `validate()` both enforce. The submitted payload includes `salesOrderId` and each row's `salesOrderItemId`.

**Sales Bill — edit** — loads the existing `Sale`; if `sale.hasReceipts` is true the whole form is locked (`<fieldset disabled>`) because `SaleService.updateSale` rejects edits once a receipt has been recorded.

**Kacchi Sale create** — `KacchiSaleForm.tsx` is a near-identical form to `SalesBillForm` but `gstType` is fixed to `GST` (a banner states "GST Calculated: YES · GST Reporting: NO") and every submit sets `transactionType: 'SALE_CHALLAN'`. Two submit paths: "Save as Draft" (`saveAsDraft: true`) and "Post Now"/"Save & Post" (goes through a confirm dialog, then either `saleApi.create` with `saveAsDraft: false` or, on edit, `saleApi.update` followed by `saleApi.post(id)`).

**POS flow (including idempotency)** — `Pos.tsx`:
1. On mount, `clientRequestIdRef` is seeded once via `newClientRequestId()` (`pos-${Date.now()}-${random}`).
2. Product search is debounced (200ms); pressing Enter in the search box first tries an exact `productApi.getByBarcode` lookup (scanner-friendly), falling back to a name/SKU search.
3. Cart lines compute taxable/GST/total client-side as a **preview only** — the comment in the code is explicit that `SaleService` computes the authoritative amounts.
4. `handlePost()` builds a `SaleCreatePayload` with `clientRequestId: clientRequestIdRef.current` (the same ref value on every retry within this cart) and `transactionType: 'SALE'`, then calls `saleApi.create`.
5. On success the screen switches to a printable receipt view; "New Sale" (`clearCart`) generates a **fresh** `clientRequestId` for the next transaction.
6. On failure, the UI explicitly tells the cashier: *"Not charged. Safe to retry — the same sale won't be created twice."* — because retrying resends the same `clientRequestId`, and the backend returns the original `Sale` instead of creating a duplicate (see section 4/12).

**Receipt entry with multi-bill allocation** — `ReceiptForm.tsx`: selecting a customer calls `receiptApi.getOutstanding(customerId)`, populating one checkbox row per outstanding bill (pre-filled with its full `dueAmount`). The cashier may check zero, one, or several bills and adjust each row's amount (capped at that bill's due amount); if none are checked, `allocations: []` is sent and the backend auto-allocates FIFO. Client validation additionally blocks the sum of checked allocations from exceeding the entered receipt `amount`.

**Credit Note create from a Bill** — `CreditNoteForm.tsx`: either search+select a `COMPLETED` sale with a customer, or arrive pre-loaded via `/sales/credit-notes/new?saleId={id}` (wired from both `SalesBillDetail` and `KacchiSaleDetail`'s "Return" button). Once a sale is selected, one return-quantity input per `SaleItem` is shown (capped at `item.quantity`); `handleSubmit(post: boolean)` collects only lines with `qty > 0` into `CreditNoteItemPayload[]` and calls `creditNoteApi.create` with `post: false` (Save as Draft) or `post: true` (Save and Post, which the backend immediately runs through `post()` after creating).

## 3. API Calls

| Function | HTTP Method | URL | Request shape | Response shape |
|---|---|---|---|---|
| `salesOrderApi.list` | GET | `/sales-orders` | query: `search?`, `customerId?`, `status?`, `fromDate?`, `toDate?`, `page`, `size` | `PagedResponse<SalesOrder>` |
| `salesOrderApi.getById` | GET | `/sales-orders/{id}` | — | `SalesOrder` |
| `salesOrderApi.create` | POST | `/sales-orders` | `SalesOrderPayload` | `SalesOrder` |
| `salesOrderApi.update` | PUT | `/sales-orders/{id}` | `SalesOrderPayload` | `SalesOrder` |
| `salesOrderApi.setStatus` | PATCH | `/sales-orders/{id}/status?status=` | — | `SalesOrder` |
| `salesOrderApi.cancel` | PATCH | `/sales-orders/{id}/cancel` | — | `SalesOrder` |
| `saleApi.list` | GET | `/sales` | query: `search?`, `paymentStatus?`, `status?`, `fromDate?`, `toDate?`, `transactionType?`, `page`, `size` (`storeId` supported server-side, not sent by these pages) | `PagedResponse<Sale>` |
| `saleApi.getById` | GET | `/sales/{id}` | — | `Sale` |
| `saleApi.create` | POST | `/sales` | `SaleCreatePayload` (used for Sales Bill, Kacchi Sale, and POS — `transactionType`/`saveAsDraft`/`clientRequestId` vary by caller) | `Sale` |
| `saleApi.update` | PUT | `/sales/{id}` | `SaleUpdatePayload` (`SaleCreatePayload` + `status`) | `Sale` |
| `saleApi.post` | POST | `/sales/{id}/post` | — (Kacchi Sale only) | `Sale` |
| `saleApi.cancel` | PATCH | `/sales/{id}/cancel` | — | `Sale` (defined in `saleApi.ts`; no page in this module's read set calls it — `SalesBills`/detail pages call `remove` instead) |
| `saleApi.remove` | DELETE | `/sales/{id}` | — | 204 No Content |
| `receiptApi.list` | GET | `/receipts` | query: `search?`, `customerId?`, `fromDate?`, `toDate?`, `page`, `size` | `PagedResponse<Receipt>` |
| `receiptApi.getById` | GET | `/receipts/{id}` | — | `Receipt` |
| `receiptApi.getOutstanding` | GET | `/receipts/outstanding/{customerId}` | — | `CustomerOutstanding` |
| `receiptApi.create` | POST | `/receipts` | `ReceiptPayload` | `Receipt` |
| `receiptApi.remove` | DELETE | `/receipts/{id}` | — | 204 No Content |
| `salesSummaryApi.get` | GET | `/sales-summary` | — | `SalesSummary` |
| `creditNoteApi.list` | GET | `/credit-notes` | query: `search?`, `status?`, `fromDate?`, `toDate?`, `page`, `size` | `PagedResponse<CreditNote>` |
| `creditNoteApi.getById` | GET | `/credit-notes/{id}` | — | `CreditNote` |
| `creditNoteApi.create` | POST | `/credit-notes` | `CreditNoteCreatePayload` | `CreditNote` |
| `creditNoteApi.post` | POST | `/credit-notes/{id}/post` | — | `CreditNote` |
| `creditNoteApi.cancel` | PATCH | `/credit-notes/{id}/cancel` | — | `CreditNote` |

## 4. Backend

### Controllers

**`SaleController`** (`/api/sales`):
| Endpoint | Method | `@PreAuthorize` |
|---|---|---|
| `GET /api/sales` | `getSales` | none (any authenticated user; query filters: search, paymentStatus, status, fromDate, toDate, transactionType, storeId, page, size) |
| `GET /api/sales/{id}` | `getSaleById` | none |
| `POST /api/sales` | `createSale` | `PERM_SALES_CREATE` |
| `PUT /api/sales/{id}` | `updateSale` | `PERM_SALES_EDIT` |
| `POST /api/sales/{id}/post` | `postSaleChallan` | `PERM_SALES_POST` |
| `PATCH /api/sales/{id}/cancel` | `cancelSale` | `PERM_SALES_CANCEL` |
| `DELETE /api/sales/{id}` | `deleteSale` | `PERM_SALES_CANCEL` |

**`SalesOrderController`** (`/api/sales-orders`):
| Endpoint | Method | `@PreAuthorize` |
|---|---|---|
| `GET /api/sales-orders` | `search` | none |
| `GET /api/sales-orders/{id}` | `getById` | none |
| `POST /api/sales-orders` | `create` | `PERM_SALES_CREATE` |
| `PUT /api/sales-orders/{id}` | `update` | `PERM_SALES_EDIT` |
| `PATCH /api/sales-orders/{id}/status` | `setStatus` | `PERM_SALES_EDIT` |
| `PATCH /api/sales-orders/{id}/cancel` | `cancel` | `PERM_SALES_CANCEL` |

**`ReceiptController`** (`/api/receipts`):
| Endpoint | Method | `@PreAuthorize` |
|---|---|---|
| `GET /api/receipts` | `search` | none |
| `GET /api/receipts/{id}` | `getById` | none |
| `GET /api/receipts/outstanding/{customerId}` | `getOutstanding` | none |
| `POST /api/receipts` | `create` | `PERM_RECEIPT_CREATE` |
| `DELETE /api/receipts/{id}` | `delete` | `PERM_RECEIPT_POST` |

**`SalesSummaryController`** (`/api/sales-summary`):
| Endpoint | Method | `@PreAuthorize` |
|---|---|---|
| `GET /api/sales-summary` | `getSummary` | none |

**`CreditNoteController`** (`/api/credit-notes`):
| Endpoint | Method | `@PreAuthorize` |
|---|---|---|
| `GET /api/credit-notes` | `search` | none |
| `GET /api/credit-notes/{id}` | `getById` | none |
| `POST /api/credit-notes` | `create` | `PERM_CREDIT_NOTE_CREATE` |
| `POST /api/credit-notes/{id}/post` | `post` | `PERM_CREDIT_NOTE_POST` |
| `PATCH /api/credit-notes/{id}/cancel` | `cancel` | `PERM_CREDIT_NOTE_CANCEL` |

All GET endpoints in this module have no explicit `@PreAuthorize` — access is still gated by the global JWT filter (must be authenticated) and by row-level store scoping inside the service layer (`StoreAccessService.assertStoreAccess`/`resolveViewableStoreId`), not by a permission annotation.

### DTOs

**`SaleCreateRequest`** — `clientRequestId` (optional idempotency key, no annotation — validated by uniqueness at the DB/service layer, not `@NotNull`), `customerId` (optional), `storeId` (optional), `saleDate` `@NotNull`, `gstType` `@NotNull`, `taxMode` (required only when `gstType == GST`, enforced in service), `customerPhone`, `customerGstin`, `billingAddress`, `shippingAddress`, `paymentMode` `@NotNull`, `paidAmount` `@NotNull @DecimalMin("0")`, `notes`, `salesOrderId` (optional), `items` `@NotEmpty @Valid List<SaleItemRequest>`, `transactionType` (optional, defaults to `SALE`), `saveAsDraft` (boolean, Kacchi Sale only).

**`SaleItemRequest`** — `productId` `@NotNull`, `quantity` `@NotNull @Min(1)`, `sellingPrice` `@NotNull @DecimalMin("0")`, `discount` `@NotNull @DecimalMin("0")`, `tax` `@NotNull @DecimalMin("0")` (client-computed preview, not authoritative), `gstPercent` `@DecimalMin("0")`, `salesOrderItemId` (optional).

**`SaleUpdateRequest`** — same shape as `SaleCreateRequest` minus `clientRequestId`/`salesOrderId`/`transactionType`/`saveAsDraft`, plus `status` `@NotNull` (`SaleStatus`).

**`SalesOrderRequest`** — `customerId`, `storeId` (both optional), `customerPhone`, `customerGstin`, `billingAddress`, `shippingAddress`, `orderDate` `@NotNull`, `expectedDeliveryDate`, `remarks`, `items` `@NotEmpty @Valid List<SalesOrderItemRequest>`.

**`SalesOrderItemRequest`** — `productId` `@NotNull`, `quantity` `@NotNull @Min(1)`, `rate` `@NotNull @DecimalMin("0")`, `discount` `@NotNull @DecimalMin("0")`, `gstPercent` `@NotNull @DecimalMin("0")`.

**`ReceiptRequest`** — `customerId` `@NotNull`, `storeId` (optional), `receiptDate` `@NotNull`, `amount` `@NotNull @DecimalMin("0.01")`, `paymentMode` `@NotNull`, `remarks`, `allocations` `@Valid List<ReceiptAllocationRequest>` (optional; empty ⇒ FIFO auto-allocation).

**`ReceiptAllocationRequest`** — `saleId` `@NotNull`, `amountApplied` `@NotNull @DecimalMin("0.01")`.

**`CreditNoteCreateRequest`** — `sourceSaleId` `@NotNull`, `noteType` `@NotNull` (`CreditNoteType`), `noteDate` `@NotNull`, `stockImpact` `@NotNull` (`StockImpactType`), `reason`, `remarks`, `items` `@NotEmpty @Valid List<CreditNoteItemRequest>`, `post` (boolean; if true, `post()` runs immediately after create).

**`CreditNoteItemRequest`** — deliberately carries only `saleItemId` `@NotNull` and `quantity` `@NotNull @Min(1)`; rate/discount/GST are always derived server-side from the source `SaleItem` (per the code comment: "backend validation is the source of truth" — the client can never claim a different price/tax on a return than what was actually billed).

Response DTOs (`SaleResponse`, `SaleItemResponse`, `SalesOrderResponse`, `SalesOrderItemResponse`, `ReceiptResponse`, `ReceiptAllocationResponse`, `CreditNoteResponse`, `CreditNoteItemResponse`, `SalesSummaryResponse`, `CustomerOutstandingResponse`, `OutstandingBillResponse`) are plain `@Builder` read models built by static `fromEntity(...)` mappers; none carry validation annotations.

### Services

**`SaleService`**
- `getSales(...)` — resolves the viewable store via `storeAccessService.resolveViewableStoreId`, delegates to `SaleRepository.search`, maps to `SaleResponse`.
- `getSaleById(id)` — loads the sale, calls `storeAccessService.assertStoreAccess` (a sale never returned to a caller without access to its store), sets `hasReceipts` from `ReceiptAllocationRepository.findBySaleId`.
- `createSale(request)` — **the central Sales Bill/Kacchi Sale/POS entry point**:
  1. **Idempotency check**: if `clientRequestId` is present and non-blank, looks it up via `SaleRepository.findByClientRequestId`; if found, returns the existing `Sale` unchanged instead of creating a new one.
  2. Validates `taxMode` is present when `gstType == GST`, and that `customerGstin` (if given) passes `GstinValidator.isValid`.
  3. Resolves `customer` (optional), `salesOrder` (optional), and `store` via `storeAccessService.resolveEffectiveStoreId` + `storeService.findOrThrow`; rejects an `INACTIVE` store.
  4. Determines `TransactionType` (default `SALE`) and rejects `saveAsDraft` for anything but `SALE_CHALLAN`.
  5. Builds the `Sale` (`status = DRAFT` if `saveAsDraft`, else `COMPLETED`; `gstReportingApplicable = type != SALE_CHALLAN`), calls `applyItems(...)` (validates no duplicate product, inactive-product guard, stock availability, sales-order-remaining-quantity guard, calls `GstCalculationService.calculateLine` per line) and `applyPayment(...)` (paid ≤ total, derives `PaymentStatus`).
  6. Saves the sale; **catches `DataIntegrityViolationException`** from the unique `client_request_id` DB constraint — if a concurrent duplicate request already won the race, re-looks-up by `clientRequestId` and returns that result instead of a 500 (see section 12 for the full mechanism).
  7. Generates the invoice number via `voucherNumberService.next(SALE_CHALLAN or SALE, saleDate)`.
  8. If not a draft, calls `applyPostingEffects(saved)`.
  9. `auditService.log(CREATE, "SALES", "Sale", ...)`.
- `postSaleChallan(id)` — Kacchi Sale only: rejects if not `SALE_CHALLAN`/not `DRAFT`; re-checks stock availability per line (stock may have moved since the draft was saved); flips to `COMPLETED`; calls `applyPostingEffects`.
- `applyPostingEffects(saved)` (private) — the single posting pipeline used by both `createSale` (non-draft) and `postSaleChallan`: `deductStock` → `ledgerService.recordSaleDebit` → `ledgerService.recordGstEntry` → `accountingService.postSaleJournal` → `consumeOrderQuantities(+1)` → `gstTransactionSyncService.syncSale` → if `paidAmount > 0`: `receiptService.createSystemReceiptForSale` (customer sale) or `ledgerService.recordCashEntryForSale` (walk-in/no customer).
- `updateSale(id, request)` — blocked if the sale is `CANCELLED`, if the new status is `CANCELLED` (must use delete/cancel instead), if the sale already has any receipt allocation, or if `gstType == GST` with no `taxMode`. If the old status was posted (`!= DRAFT`), fully reverses the old posting effects first (`restoreStock` if it was `COMPLETED`, `ledgerService.reverseSaleDebit`/`reverseGstEntry`, `accountingService.reverseSaleJournal`, `reverseCashEntryForSale` if walk-in, `consumeOrderQuantities(-1)`, `gstTransactionSyncService.reverseSale`), then rebuilds items/payment from the new request and re-applies posting effects if the new status isn't `DRAFT`.
- `cancelSale(id)` — soft reversal: rejects if already `CANCELLED`; `guardNoManualReceipts` (rejects if any *manual* — non-system-generated — receipt is allocated against it); calls `reverseSaleEffects`; sets `status = CANCELLED`, `dueAmount = 0`; `auditService.log(CANCEL, ...)`.
- `deleteSale(id)` — hard delete: same `guardNoManualReceipts` guard, `reverseSaleEffects`, then `saleRepository.delete`.
- `reverseSaleEffects(sale, reason)` (private) — if `COMPLETED`, `restoreStock`; if `DRAFT`, returns immediately (nothing was ever posted); otherwise deletes every system-generated receipt allocated against it (`receiptService.deleteSystemReceipt`), reverses the walk-in cash entry if there were no allocations and no customer, then `reverseSaleDebit`/`reverseGstEntry`/`reverseSaleJournal`/`consumeOrderQuantities(-1)`/`gstTransactionSyncService.reverseSale`.
- `applyItems(...)` (private) — per-line validation (no duplicate product; inactive product blocked unless it was already on the sale being edited; stock availability via `inventoryService.getCurrentStock`; sales-order-remaining-quantity check) and GST computation via `gstCalculationService.calculateLine`; accumulates sale-level `taxableAmount`/`cgstAmount`/`sgstAmount`/`igstAmount`/`totalAmount`.
- `restoreStock`/`deductStock` (private) — call `inventoryService.applyMovement(productId, storeId, ±quantity, SALE_CANCEL|SALE, ReferenceType.SALE, saleId, reason)` per line.
- `findSaleOrThrow(id)` — throws `SaleNotFoundException`.
- `countTodaysSalesCount()` — `saleRepository.count()` (used by a dashboard, not this module's pages).

**`SalesOrderService`**
- `search(...)` — store-scoped list via `SalesOrderRepository.search`.
- `getById(id)` — store-access-asserted single read.
- `create(request)` — resolves customer/store (rejects `INACTIVE` store), builds items via `applyItems` (duplicate-product guard, no stock check — orders don't touch stock), status always starts `DRAFT`, generates `orderNumber` via `voucherNumberService.next(SALES_ORDER, orderDate)`.
- `update(id, request)` — blocked if `CANCELLED` or `COMPLETED`; enforces that any item already partially/fully billed (`billedQuantity > 0`) stays present at ≥ its billed quantity; rebuilds items, then `recomputeStatus`.
- `setStatus(id, status)` — manual transition restricted to `DRAFT`/`CONFIRMED` only; blocked once any item has billed quantity (status then becomes automatic).
- `cancel(id)` — blocked if already `CANCELLED` or if any item has been billed.
- `applyBilledQuantityDelta(salesOrderItemId, delta)` — called by `SaleService.consumeOrderQuantities` on post (+quantity) and on reversal (−quantity); clamps at 0 and rejects exceeding the ordered quantity; calls `recomputeStatus` after.
- `recomputeStatus(order)` (private) — `COMPLETED` if every item's `remainingQuantity <= 0`, else `PARTIALLY_BILLED` if any item has `billedQuantity > 0`, else left unchanged (`DRAFT`/`CONFIRMED`).
- `findOrThrow(id)` — throws `SalesOrderNotFoundException`.
- `countPendingOrders()` / `countTotalOrders()` — used by `SalesSummaryService`.

**`ReceiptService`**
- `search(...)` / `getById(id)` (store-access-asserted) / `getOutstandingForCustomer(customerId)` — reads; the latter sums `SaleRepository.findOutstandingByCustomer`'s `dueAmount`s.
- `create(request)` — builds a manual (`systemGenerated = false`) `Receipt`, generates `receiptNumber` via `voucherNumberService.next(RECEIPT, receiptDate)`, calls `allocate(...)`, then `ledgerService.recordReceiptCredit` + `ledgerService.recordCashEntry` + `accountingService.postReceiptJournal`; `auditService.log(RECEIPT, ...)`.
- `createSystemReceiptForSale(sale)` — called only from `SaleService.applyPostingEffects`/`updateSale` when a Sales Bill records an immediate payment against a customer: builds a `systemGenerated = true` `Receipt` allocated 1:1 against that sale for `paidAmount`, same ledger/accounting posting as a manual receipt.
- `delete(id)` — rejects deleting a system-generated receipt directly (instructs the caller to delete the sales bill instead); otherwise `reverseAndRemove`; `auditService.log(CANCEL, ...)`.
- `deleteSystemReceipt(id)` (package-private) — used only by `SaleService` when reversing a sale that has its own auto-generated receipt; same `reverseAndRemove`.
- `reverseAndRemove(receipt)` (private) — for every allocation, restores the sale's `paidAmount`/`dueAmount`/`paymentStatus`; reverses `ledgerService.reverseReceiptCredit`/`reverseCashEntry`/`accountingService.reverseReceiptJournal`; hard-deletes the `Receipt` row (cascades to its `ReceiptAllocation`s).
- `allocate(receipt, explicit, customerId)` (private) — if explicit allocations were given, validates each sale belongs to the customer and each amount doesn't exceed that sale's `dueAmount`, and that the sum doesn't exceed the receipt amount; otherwise walks `SaleRepository.findOutstandingByCustomer` oldest-first (FIFO) applying `min(remaining, dueAmount)` to each until the receipt amount is exhausted — any leftover becomes an unlinked on-account credit (still reduces the customer's net outstanding via the ledger CREDIT entry).
- `findOrThrow(id)` — throws `ReceiptNotFoundException`.

**`SalesSummaryService.getSummary()`** — aggregates `salesOrderRepository.count()`, `salesOrderService.countPendingOrders()`, `saleRepository.count()`, `ledgerService.getTotalOutstanding()`, `saleRepository.getTotalSalesForDate(today)`.

**`CreditNoteService`** (doc comment: DRAFT has zero side effects; POSTED reuses the same centralized engines as every other document — `AccountingService.postJournal`, `InventoryService.applyMovement`, `LedgerService`, `GstTransactionSyncService` — never a parallel implementation; CANCELLED reverses everything POSTED applied and is idempotent).
- `search(...)` / `getById(id)` (store-access-asserted via the source sale's store) — reads.
- `create(request)` — rejects if the source sale is `CANCELLED`, `DRAFT` (not yet posted), or has no customer (walk-in). Per item: looks up the `SaleItem`, validates it belongs to the source sale, and validates the requested return quantity against `saleItem.quantity - CreditNoteItemRepository.sumReturnedQuantity(saleItemId)` (already-returned/pending across every non-cancelled note, so two concurrent drafts can't double-claim the same units). Line amounts (taxable/discount/CGST/SGST/IGST) are computed by proportion of the original sale line (`proportion = totalForLine * returnedQty / soldQty`, `HALF_UP` to 2dp) — never re-derived from a client-supplied rate. `gstReportingApplicable` is copied verbatim from the source sale. Generates `voucherNumber` via `voucherNumberService.next(CREDIT_NOTE, noteDate)`; `auditService.log(CREATE, ...)`; if `request.isPost()`, immediately calls `post(saved.getId())`.
- `post(id)` — rejects if not `DRAFT`, or if the source sale has since been `CANCELLED`. Builds a journal (`debit SALES` for taxable amount, `debit OUTPUT_CGST/SGST/IGST` for any positive tax lines, `credit CUSTOMER_RECEIVABLE` for the total) and posts it via `accountingService.postJournal(VoucherType.CREDIT_NOTE, ...)`. If `stockImpact == STOCK_RETURN`, calls `inventoryService.applyMovement(..., SALES_RETURN, ReferenceType.CREDIT_NOTE, ...)` per item (store-aware overload when the source sale has a store). Calls `ledgerService.recordCreditNoteEntry`. Sets `status = POSTED`, `postedBy`/`postedAt`, then — **after** the status flip, because `GstReportingEligibility` checks `status == POSTED` — calls `gstTransactionSyncService.syncCreditNote`. `auditService.log(POST, ...)`.
- `cancel(id)` — idempotent no-op if already `CANCELLED`. If `POSTED`, reverses the journal (`accountingService.reverseJournal`), reverses the stock movement if `STOCK_RETURN` (opposite-sign `applyMovement` with `StockMovementType.ADJUSTMENT`), `ledgerService.reverseCreditNoteEntry`, `gstTransactionSyncService.reverseCreditNote`. Sets `status = CANCELLED`, `cancelledBy`/`cancelledAt`. `auditService.log(CANCEL, ...)`.
- `findOrThrow(id)` — throws `NoteNotFoundException`.

**POS idempotency guard mechanism (exact)**: `Sale.clientRequestId` is a `@Column(unique = true, length = 100)` field, optional. `Pos.tsx` generates one token per cart (`newClientRequestId()`) before the first submit and resends the *same* token on every retry of that same cart (double-click, network timeout, page refresh before the response arrives). `SaleService.createSale` (1) looks the token up first via `SaleRepository.findByClientRequestId` and returns the existing `Sale` verbatim if found — no new row, no re-posting; (2) if two requests race past that check simultaneously, the DB-level unique constraint on `client_request_id` throws `DataIntegrityViolationException` on the losing insert (the `Sale.id` is `IDENTITY`-generated, so the insert — and the constraint check — happens synchronously in this call, not deferred to commit); the service catches that exception and re-queries by `clientRequestId`, returning the winning request's `Sale` instead of surfacing a 500. Either path is safe to retry from the client with no risk of a duplicate sale.

### Repository

**`SaleRepository`**: `findByClientRequestId`, `findOutstandingByCustomer`, `findAllOutstanding`, `findCompletedIds`, `findAllIds`, `getTotalSalesForDate`, `sumByPaymentModeForDate`, `sumCreditSalesForDate`, `sumTotalAmountByDateRange`, `countByDateRange`, `sumAndCountByDateRangeAndStore`, `search`, `findEligibleForReconciliation`.

**`SaleItemRepository`**: `hsnSummary`, `taxRateSummary` (both GST-report-only, scoped to `COMPLETED` + `gstReportingApplicable = true`).

**`SalesOrderRepository`**: `search`, `countByStatusIn`.

**`SalesOrderItemRepository`**: plain `JpaRepository<SalesOrderItem, Long>` (no custom methods).

**`ReceiptRepository`**: `findAllIds`, `sumAmountForDate`, `search`.

**`ReceiptAllocationRepository`**: `findBySaleId`.

**`CustomerLedgerEntryRepository`**: `findByCustomerIdOrderByEntryDateAscCreatedAtAsc`, `getOutstandingForCustomer`, `getTotalOutstanding`, `findByReferenceTypeAndReferenceId`, `sumByCustomer`.

**`GstEntryRepository`**: `findBySaleId`.

**`CreditNoteRepository`**: `search`, `findPostedIds`, `findAllIds`.

**`CreditNoteItemRepository`**: `sumReturnedQuantity` (sums quantity already returned against one `SaleItem` across every non-`CANCELLED` note — DRAFT notes count too, so this is the concurrency guard against double-returning).

## 5. Database

### Tables

- `sales` — one row per Sales Bill / Kacchi Sale / POS sale (all the same entity).
- `sale_items` — line items of `sales`.
- `sales_orders` — one row per Sales Order.
- `sales_order_items` — line items of `sales_orders`.
- `receipts` — one row per receipt (manual or system-generated).
- `receipt_allocations` — join table linking a `receipts` row to one or more `sales` rows with the amount applied.
- `customer_ledger_entries` — immutable DEBIT/CREDIT log of customer receivable movements.
- `gst_entries` — output GST log, one set per GST-type sale (used for GST reporting, not directly rendered by this module's pages).
- `credit_notes` — one row per Credit Note.
- `credit_note_items` — line items of `credit_notes`.

### Relationships

- `Sale` ↔ `SaleItem`: one-to-many, `mappedBy = "sale"`, `cascade = ALL`, `orphanRemoval = true` (`sale.addItem`/`clearItems`).
- `Sale` ↔ `Customer`: many-to-one, nullable (walk-in sale has no customer).
- `Sale` ↔ `Store`: many-to-one, fixed at creation, never changed on edit.
- `Sale` ↔ `SalesOrder`: many-to-one, nullable — set when the bill originates from an order.
- `SaleItem` ↔ `SalesOrderItem`: many-to-one, nullable — set per line when that line bills against a specific order line (drives `SalesOrderItem.billedQuantity` tracking).
- `SalesOrder` ↔ `SalesOrderItem`: one-to-many, `cascade = ALL`, `orphanRemoval = true`.
- `Receipt` ↔ `ReceiptAllocation`: one-to-many, `cascade = ALL`, `orphanRemoval = true` (`receipt.addAllocation`).
- `ReceiptAllocation` ↔ `Sale`: many-to-one (`ReceiptAllocation.sale`) — this is the join that ties a receipt's payment to one or more specific bills; `ReceiptAllocationRepository.findBySaleId` is how `SaleService` determines `hasReceipts`/blocks edits/blocks cancel.
- `Receipt` ↔ `Customer`: many-to-one, `nullable = false`.
- `Receipt` ↔ `Store`: many-to-one, fixed at creation.
- `CreditNote` ↔ `CreditNoteItem`: one-to-many, `cascade = ALL`, `orphanRemoval = true` (`creditNote.addItem`).
- `CreditNote` ↔ `Sale` (`sourceSale`): many-to-one, `nullable = false` — every Credit Note is always linked to the sale it adjusts.
- `CreditNote` ↔ `Customer`: many-to-one, `nullable = false` (copied from the source sale's customer at creation).
- `CreditNoteItem` ↔ `SaleItem`: many-to-one, `nullable = false` — the specific line being returned/adjusted; `CreditNoteItemRepository.sumReturnedQuantity` sums against this FK.
- `CustomerLedgerEntry` ↔ `Customer`: many-to-one, `nullable = false`; `referenceType`/`referenceId` polymorphically point back at the originating `Sale`/`Receipt`/`CreditNote` (not a JPA FK).
- `GstEntry` ↔ `Sale`: `saleId` is a plain `Long` column (not a JPA relationship) — `GstEntryRepository.findBySaleId` looks it up manually.

## 6. Validation

Backend (authoritative — see `CLAUDE.md`'s field-level-errors rule):
- Bean Validation on every request DTO (`@NotNull`, `@NotEmpty`, `@Min`, `@DecimalMin`) surfaces as `400` with `ApiError.fieldErrors` keyed by DTO field name (`GlobalExceptionHandler.handleValidationExceptions`, triggered by `MethodArgumentNotValidException`).
- Service-level `BadRequestException` (plain `400`, `message` only, no `fieldErrors` map) enforces business rules that span fields/entities: GST sale requires `taxMode`; GSTIN format (`GstinValidator.isValid`); inactive store/product blocks; per-line stock availability (`quantity > currentStock`); duplicate product in one sale/order; discount exceeding the line amount; sales-order-remaining-quantity guard; paid amount exceeding total; edit blocked once a receipt exists; cancel/delete blocked while a manual receipt is allocated; `saveAsDraft` only allowed for `SALE_CHALLAN`; Kacchi Sale post only from `DRAFT`; Credit Note return quantity exceeding `quantity - alreadyReturned`; Credit Note against a `CANCELLED`/`DRAFT`/no-customer sale; Credit Note post only from `DRAFT`; source sale cancelled since the note was created.
- Frontend mirrors the same rules pre-submit (`validate()` functions in each form) purely as a fast-fail UX layer — every form also renders `fieldErrors[field]` inline (via `parseApiError`) next to the offending input where the backend returns them, per the project's field-error-surfacing convention.

## 7. Authentication / Authorization

Every endpoint requires an authenticated request (standard JWT bearer auth, enforced globally — not specific to this module; see `01-authentication-authorization.md`). Within that, write/action endpoints are further gated by `@PreAuthorize("hasAuthority('PERM_...')")` per section 4; list/detail (GET) endpoints have no `@PreAuthorize` and rely on row-level store scoping instead: `StoreAccessService.resolveViewableStoreId`/`resolveEffectiveStoreId` restrict which store's records a non-"all-stores" user can see or create against, and `StoreAccessService.assertStoreAccess` throws when a single-record `getById` targets a store the caller cannot access.

## 8. Permissions

From `Permission.java`/`RolePermissions.java`:
- `SALES_VIEW`, `SALES_CREATE`, `SALES_EDIT`, `SALES_POST`, `SALES_CANCEL`
- `RECEIPT_VIEW`, `RECEIPT_CREATE`, `RECEIPT_POST`
- `CREDIT_NOTE_VIEW`, `CREDIT_NOTE_CREATE`, `CREDIT_NOTE_POST`, `CREDIT_NOTE_CANCEL`
- `POS_ACCESS`

Role grants (`RolePermissions.build()`):
- `ADMIN` — every permission.
- `STORE_MANAGER` — every permission except the security-admin tier (`USER_*`, `ROLE_*`, `PERMISSION_VIEW`), `FY_MANAGE`, `AUDIT_VIEW`, `GST_CONFIG`, and store-management (`STORE_CREATE/EDIT/ASSIGN/ACCESS_ALL`) — i.e. it has full Sales module access.
- `ACCOUNTANT` — `SALES_VIEW`, `RECEIPT_VIEW`, `CREDIT_NOTE_VIEW` only (read-only on this module).
- `SALES_USER` — `SALES_VIEW/CREATE/EDIT/POST`, `RECEIPT_VIEW/CREATE/POST`, `CREDIT_NOTE_VIEW`, `POS_ACCESS` (no `SALES_CANCEL`/`CREDIT_NOTE_CREATE`/`CREDIT_NOTE_POST`/`CREDIT_NOTE_CANCEL` — cannot cancel a sale, delete a receipt, or manage credit notes beyond viewing).
- `PURCHASE_USER` / `INVENTORY_USER` — no Sales module permissions.
- `STAFF` — `POS_ACCESS`, `SALES_VIEW`, `SALES_CREATE`, `RECEIPT_VIEW`, `RECEIPT_CREATE` only (the exact set POS needs — create a sale, create its receipt — per the code comment noting these were already reachable pre-authorization and are now explicit rather than implicit).

## 9. Transaction Handling

Every mutating `SaleService`/`SalesOrderService`/`ReceiptService`/`CreditNoteService` method is `@Transactional` (class-level default propagation `REQUIRED`), so each request's full side-effect chain — item validation, stock movement, ledger entries, GST log, accounting journal, order-quantity consumption, GST-sync, and (for a paid sale) the system receipt — commits or rolls back atomically as one unit. Read methods on `CreditNoteService` are explicitly `@Transactional(readOnly = true)`.

`VoucherNumberService.next(...)` runs in its **own** `@Transactional(propagation = Propagation.REQUIRES_NEW)` transaction with a `SELECT ... FOR UPDATE` row lock on the sequence, so voucher-number allocation commits independently of (and is never rolled back by) the outer sale/order/receipt/note transaction — it is claimed even if the calling transaction later fails for an unrelated reason, matching the documented "never re-issue a number" guarantee.

On **post** (`applyPostingEffects` / `CreditNoteService.post`): stock deduction/return, customer ledger debit/credit, GST log entry, accounting journal posting, sales-order billed-quantity consumption, and GST-transaction sync all happen inside the same transaction as the status flip — a failure partway through (e.g. `accountingService.postSaleJournal` throwing because the financial year is closed) rolls back the stock movement and ledger entry that ran before it, leaving the `Sale`/`CreditNote` at its pre-post status.

On **cancel/delete** (`reverseSaleEffects` / `CreditNoteService.cancel`): stock restoration, deletion of any system-generated receipts (which itself reverses ledger/accounting for that receipt), ledger/GST/accounting reversal, order-quantity release, and GST-sync reversal are likewise one atomic unit with the status flip to `CANCELLED` (or the hard delete).

## 10. Error Handling

`GlobalExceptionHandler` (shared across all modules):
- `MethodArgumentNotValidException` → `400` with `ApiError.fieldErrors` (per-DTO-field messages from `@NotNull`/`@Min`/`@DecimalMin` etc.).
- `SaleNotFoundException`, `SalesOrderNotFoundException`, `ReceiptNotFoundException`, `NoteNotFoundException` → `404` with `ex.getMessage()` (e.g. `"Sale not found with id: 42"`), `fieldErrors: null`.
- `BadRequestException` → `400` with `ex.getMessage()` only (all the business-rule messages listed in section 6).
- `AccessDeniedException` (thrown by `StoreAccessService.assertStoreAccess` or Spring Security's `@PreAuthorize` rejection) → `403`, generic message "You do not have permission to access this resource".
- Any other unhandled `Exception` → `500`, generic "An unexpected error occurred" (logged server-side with full stack trace).

Frontend: every list/form/detail page wraps its API calls in try/catch and surfaces the error via `parseApiError(err, fallbackMessage)`, which reads `ApiError.message` and `ApiError.fieldErrors` from the response — the message is shown as a page-level `Alert`/toast, and `fieldErrors[field]` (when present) drives per-input `invalid`/error-text styling in the forms (`SalesOrderForm`, `SalesBillForm`, `KacchiSaleForm`).

## 11. Audit Flow

`AuditService.log(...)` call sites in this module (all inside `@Transactional` service methods, so an audit row commits together with the business change it describes):
- `SaleService.createSale` — `AuditAction.CREATE`, module `"SALES"`, entity `"Sale"`, on every create (draft or posted).
- `SaleService.cancelSale` — `AuditAction.CANCEL`, entity `"Sale"`.
- `ReceiptService.create` — `AuditAction.RECEIPT`, entity `"Receipt"`.
- `ReceiptService.delete` — `AuditAction.CANCEL`, entity `"Receipt"`.
- `CreditNoteService.create` — `AuditAction.CREATE`, entity `"CreditNote"`.
- `CreditNoteService.post` — `AuditAction.POST`, entity `"CreditNote"`.
- `CreditNoteService.cancel` — `AuditAction.CANCEL`, entity `"CreditNote"`.

Not audited in the read files: `SaleService.updateSale`, `SaleService.deleteSale`, `SaleService.postSaleChallan`, and all of `SalesOrderService` — NOT FOUND IN CURRENT CODEBASE (no `auditService.log` call in those methods).

## 12. Important Side Effects

- **Stock movement on sale post**: `SaleService.deductStock` calls `inventoryService.applyMovement(productId, storeId, -quantity, StockMovementType.SALE, ReferenceType.SALE, saleId, reason)` per line. An **insufficient-stock guard** runs twice: once per-line inside `applyItems` at create/update time (`itemRequest.getQuantity() > availableStock` → `BadRequestException`), and again in `postSaleChallan` immediately before posting a draft challan (stock may have moved since the draft was saved).
- **Stock reversal on cancel/delete**: `restoreStock` calls the same `applyMovement` with a positive delta and `StockMovementType.SALE_CANCEL`.
- **Credit Note stock impact**: only when `stockImpact == StockImpactType.STOCK_RETURN` — `CreditNoteService.post` calls `applyMovement(..., +quantity, StockMovementType.SALES_RETURN, ReferenceType.CREDIT_NOTE, ...)`; `cancel` reverses with `-quantity, StockMovementType.ADJUSTMENT`. `FINANCIAL_ADJUSTMENT` notes never touch stock.
- **Accounting journal posting**: `AccountingService.postSaleJournal(sale)` on sale post, `reverseSaleJournal` on reversal; `postReceiptJournal`/`reverseReceiptJournal` on receipt create/delete; `CreditNoteService` builds its own journal lines (`SALES` debit, `OUTPUT_CGST/SGST/IGST` debits, `CUSTOMER_RECEIVABLE` credit) and calls the generic `accountingService.postJournal(VoucherType.CREDIT_NOTE, ...)` / `reverseJournal`.
- **GST transaction sync**: `GstTransactionSyncService.syncSale`/`reverseSale` on sale post/reversal; `syncCreditNote`/`reverseCreditNote` on note post/cancel (note: `syncCreditNote` is called **after** the status flip to `POSTED` specifically because GST-reporting eligibility is computed from `status`).
- **Customer ledger entry creation**: `LedgerService.recordSaleDebit`/`reverseSaleDebit` on sale post/reversal; `recordGstEntry`/`reverseGstEntry` (writes/reverses `gst_entries`, GST-type sales only); `recordReceiptCredit`/`reverseReceiptCredit` + `recordCashEntry`/`reverseCashEntry` on receipt create/delete; `recordCashEntryForSale`/`reverseCashEntryForSale` specifically for a walk-in (no-customer) sale paid in full; `recordCreditNoteEntry`/`reverseCreditNoteEntry` on note post/cancel.
- **Voucher numbering**: `VoucherNumberService.next(docType, date)` — format `PREFIX/FY-CODE/000001`; called for `SALE`/`SALE_CHALLAN` (sale), `SALES_ORDER` (order), `RECEIPT` (receipt, both manual and system-generated), `CREDIT_NOTE` (note). Runs in its own `REQUIRES_NEW` transaction with a locking `SELECT ... FOR UPDATE` so numbers are never reused even if the outer transaction rolls back.
- **POS idempotency key mechanism**: see section 4 for the exact mechanism (`Sale.clientRequestId`, unique DB constraint, `findByClientRequestId` pre-check plus `DataIntegrityViolationException` catch-and-requery on a race). This is the specific mechanism that makes POS (and any other caller passing a `clientRequestId`) safe to retry.
- **Immediate system receipt on a paid sale**: any sale posted with `paidAmount > 0` and a `customer` set automatically gets a `systemGenerated = true` `Receipt` (`ReceiptService.createSystemReceiptForSale`), fully allocated against that sale — this is why `SalesBillDetail`/`SalesBills` treat `hasReceipts` as a lock on further item/amount edits (the sale must be reversed, not edited, once a receipt of any kind exists).

## 13. Dependencies on Other Modules

- **Product/Inventory** — `ProductService.findProductOrThrow`/inactive-product checks; `InventoryService.getCurrentStock`/`applyMovement` for stock deduction on sale, restoration on cancel, and return on Credit Note.
- **Customer (party)** — `CustomerService.findCustomerOrThrow`; every `Sale`/`SalesOrder`/`Receipt`/`CreditNote` optionally or mandatorily links to a `Customer`.
- **Store** — `StoreService.findOrThrow`, `StoreAccessService` (`resolveEffectiveStoreId`/`resolveViewableStoreId`/`assertStoreAccess`) — every sale/order/receipt is fixed to one store at creation and scoped for viewing per Multi-Store spec section 14/22/24.
- **Accounting** — `AccountingService.postSaleJournal`/`reverseSaleJournal`, `postReceiptJournal`/`reverseReceiptJournal`, `postJournal`/`reverseJournal` (Credit Note); `VoucherNumberService.next` for all voucher numbering in this module.
- **GST** — `GstCalculationService.calculateLine` (per-line CGST/SGST/IGST split, called from `SaleService.applyItems`); `GstTransactionSyncService.syncSale`/`reverseSale`/`syncCreditNote`/`reverseCreditNote` (feeds GSTR-1/GSTR-3B reporting, respecting `gstReportingApplicable`).
- **Financial Year** — `FinancialYearService.resolveForDate` (stamps `CreditNote.financialYearId` at creation); `AccountingService.postJournal`/`postSaleJournal` internally enforce the financial-year-open rule (not re-documented here — see the Accounting module doc).

## 14. Key Operation Flows

**Create Sales Order**: `SalesOrderForm.tsx` submit → `salesOrderApi.create(payload)` → `POST /api/sales-orders` → `SalesOrderController.create` (`PERM_SALES_CREATE`) → `SalesOrderRequest` (validated) → `SalesOrderService.create` → resolves customer/store, `applyItems` (duplicate-product guard, computes taxable/GST/total per line), status `DRAFT`, `voucherNumberService.next(SALES_ORDER, orderDate)` → `SalesOrderRepository.save` (×2, for the generated order number) → `sales_orders`/`sales_order_items` rows → `SalesOrderResponse.fromEntity` → UI navigates to the new order's detail page.

**Create Sales Bill (direct)**: `SalesBillForm.tsx` (no `orderId`) submit → `saleApi.create(payload)` (`transactionType` omitted ⇒ `SALE`) → `POST /api/sales` → `SaleController.createSale` (`PERM_SALES_CREATE`) → `SaleCreateRequest` (validated) → `SaleService.createSale` → no `clientRequestId` short-circuit (or first attempt) → GST/GSTIN checks → resolve customer/store → `applyItems` (stock check, GST calc) + `applyPayment` → save (invoice number via `voucherNumberService.next(SALE, saleDate)`) → `applyPostingEffects` (deduct stock, customer ledger debit, GST log, accounting journal, `gstTransactionSyncService.syncSale`, system receipt if paid) → `auditService.log(CREATE)` → `sales`/`sale_items` (+ `stock_movements`/journal/`gst_entries`/`customer_ledger_entries`/possibly `receipts` rows from the dependent services) → `SaleResponse` → UI navigates to the invoice detail/print view.

**Create Sales Bill from Sales Order**: `SalesOrderDetail`/`SalesOrders` "Create Bill" → `SalesBillForm.tsx?orderId={id}` → `loadFromOrder()` fetches the order, pre-fills rows (`salesOrderItemId`, capped at `remainingQuantity`) → submit builds `SaleCreatePayload` with `salesOrderId` + per-line `salesOrderItemId` → same `POST /api/sales` → `SaleService.createSale` → `applyItems` additionally validates each line's quantity against `SalesOrderItem.remainingQuantity` → on posting, `consumeOrderQuantities(items, +1)` calls `SalesOrderService.applyBilledQuantityDelta` per line, which increments `billedQuantity` and calls `recomputeStatus` (order becomes `PARTIALLY_BILLED` or `COMPLETED`) → `sales_order_items.billed_quantity` updated, `sales_orders.status` updated.

**Create Kacchi Sale**: `KacchiSaleForm.tsx` submit ("Save as Draft" or "Post Now") → `saleApi.create(payload)` with `transactionType: 'SALE_CHALLAN'`, `saveAsDraft: true|false` → `POST /api/sales` → `SaleService.createSale` → `gstReportingApplicable = false` set on the entity; if `saveAsDraft`, status `DRAFT` and `applyPostingEffects` is **skipped** (no stock/ledger/GST-log/accounting rows yet) — only the `sales`/`sale_items` rows exist. If posting immediately, same `applyPostingEffects` pipeline as a normal sale runs (GST is still fully calculated and logged in `gst_entries`, but excluded from reporting via the flag). A later "Post" on a saved draft → `saleApi.post(id)` → `POST /api/sales/{id}/post` → `SaleService.postSaleChallan` → re-checks stock, flips to `COMPLETED`, runs `applyPostingEffects`.

**POS checkout**: `Pos.tsx` scan/search → `addToCart` → `handlePost()` → `saleApi.create({ clientRequestId, transactionType: 'SALE', ... })` → `POST /api/sales` → `SaleController.createSale` (`PERM_SALES_CREATE`, granted to `STAFF`/`SALES_USER` via `POS_ACCESS`+`SALES_CREATE`) → `SaleService.createSale` — `clientRequestId` lookup first (idempotency, section 4/12); on a fresh token, proceeds through the normal create → validate → `applyItems`/`applyPayment` → save (unique-constraint race handled) → `voucherNumberService.next(SALE, ...)` → `applyPostingEffects` (status is always `COMPLETED`, POS never drafts) → `SaleResponse` → `Pos.tsx` switches to the printable receipt view; retry on network failure resends the same `clientRequestId` and gets the original `Sale` back, never a duplicate.

**Cancel a Sale**: `SalesBills`/`SalesBillDetail`/`KacchiSales`/`KacchiSaleDetail` "Delete" → confirm dialog → `saleApi.remove(id)` → `DELETE /api/sales/{id}` → `SaleController.deleteSale` (`PERM_SALES_CANCEL`) → `SaleService.deleteSale` → `guardNoManualReceipts` (rejects if a manual receipt is allocated — must delete that receipt first) → `reverseSaleEffects`: if `COMPLETED`, `restoreStock` (`inventoryService.applyMovement(+qty, SALE_CANCEL)`); if `DRAFT`, nothing to reverse; otherwise deletes any system-generated receipt (`receiptService.deleteSystemReceipt`, which itself reverses that receipt's ledger/accounting entries), reverses the walk-in cash entry if applicable, `ledgerService.reverseSaleDebit`/`reverseGstEntry`, `accountingService.reverseSaleJournal`, `consumeOrderQuantities(items, -1)` (releases any Sales Order billed quantity back), `gstTransactionSyncService.reverseSale` → `saleRepository.delete` (hard delete; `PATCH .../cancel` exists as a *soft* reversal alternative — same reversal logic via `cancelSale`, but keeps the `Sale` row with `status = CANCELLED`) → UI toasts and navigates back to the list. Net reversal: stock restored, customer ledger DEBIT offset by a reversing entry, GST log offset, accounting journal reversed, and (if linked) the Sales Order's billed quantity released.

**Record a Receipt against one or more bills**: `ReceiptForm.tsx` → customer select loads `receiptApi.getOutstanding(customerId)` → cashier optionally checks specific bills/amounts → submit → `receiptApi.create(payload)` → `POST /api/receipts` → `ReceiptController.create` (`PERM_RECEIPT_CREATE`) → `ReceiptRequest` (validated) → `ReceiptService.create` → builds `Receipt` (`systemGenerated = false`), `voucherNumberService.next(RECEIPT, ...)` → `allocate(...)` — explicit allocations (validated against customer ownership + due amount) or FIFO across `findOutstandingByCustomer` if none given, any leftover becomes an unlinked on-account credit → each allocation updates the target `Sale`'s `paidAmount`/`dueAmount`/`paymentStatus` and is saved → `ledgerService.recordReceiptCredit` + `recordCashEntry` → `accountingService.postReceiptJournal` → `auditService.log(RECEIPT)` → `receipts`/`receipt_allocations` rows + updated `sales` rows + `customer_ledger_entries` + journal → `ReceiptResponse` → UI navigates to the receipt detail/print view.

**Create a Credit Note against a Bill**: `SalesBillDetail`/`KacchiSaleDetail` "Return" → `CreditNoteForm.tsx?saleId={id}` (or manual search) → set note type/date/stock-impact/reason, enter return quantities per line → `handleSubmit(post)` → `creditNoteApi.create({ ..., post })` → `POST /api/credit-notes` → `CreditNoteController.create` (`PERM_CREDIT_NOTE_CREATE`) → `CreditNoteCreateRequest` (validated) → `CreditNoteService.create` — rejects a `CANCELLED`/`DRAFT`/no-customer source sale; per item validates against `SaleItem` ownership and `sumReturnedQuantity` (already-returned-or-pending guard); computes proportional taxable/CGST/SGST/IGST from the original sale line; copies `gstReportingApplicable` verbatim from the source sale; `voucherNumberService.next(CREDIT_NOTE, ...)`; `auditService.log(CREATE)` → if `post: true`, immediately calls `post(id)` — builds and posts the accounting journal, applies the stock movement if `STOCK_RETURN`, `ledgerService.recordCreditNoteEntry`, flips to `POSTED`, then `gstTransactionSyncService.syncCreditNote`, `auditService.log(POST)` → `credit_notes`/`credit_note_items` rows (+ stock movement/journal/ledger/GST-sync rows if posted) → `CreditNoteResponse` → UI navigates to the note's detail/print view, where a separate "Post" action is also available for a note saved as `DRAFT`.

## 15. Manual Changes

- **Add a new field to `Sale`/`SalesOrder`**: add the `@Column` to the entity (`backend/src/main/java/com/storehub/entity/Sale.java` or `SalesOrder.java`), add it to the relevant request DTO(s) (`SaleCreateRequest`/`SaleUpdateRequest`/`SalesOrderRequest`) with validation annotations, wire it into the `Sale.builder()`/`applyItems`/setter calls in `SaleService`/`SalesOrderService`, add it to the response DTO's `fromEntity(...)` mapper, then add the field to the corresponding frontend type (`types/sale.ts`/`types/salesOrder.ts`) and the relevant form component(s) (`SalesBillForm.tsx`, `KacchiSaleForm.tsx`, `Pos.tsx`, `SalesOrderForm.tsx`) and payload builders. Remember a DB migration (this codebase does not use Flyway/Liquibase in the read files — confirm the schema-generation strategy before assuming Hibernate DDL auto-update is relied on in production).
- **Change the cancel/reversal logic**: `SaleService.reverseSaleEffects` (private method, used by both `cancelSale` and `deleteSale`) is the single place stock/ledger/GST/accounting/order-quantity reversal is orchestrated for a Sale — change it there, not in `cancelSale`/`deleteSale` individually, to keep soft-cancel and hard-delete behavior identical. For a Credit Note, the equivalent logic lives inline in `CreditNoteService.cancel`.
- **Add a new `SaleStatus`**: edit `backend/src/main/java/com/storehub/entity/SaleStatus.java`, then update every `switch`/`if`-based status check across `SaleService` (`createSale`, `updateSale`, `cancelSale`, `deleteSale`, `reverseSaleEffects`, `postSaleChallan`) and the frontend `statusVariant()` helpers/status filters in `SalesBills.tsx`, `KacchiSales.tsx`, `KacchiSaleDetail.tsx` (`types/sale.ts`'s `SaleStatus` union too).
- **Change receipt allocation logic**: `ReceiptService.allocate` (private) is the single place both explicit-allocation validation and FIFO auto-allocation live — change the FIFO ordering by changing `SaleRepository.findOutstandingByCustomer`'s `ORDER BY`, or change the auto-allocation strategy (e.g. largest-bill-first) entirely inside `allocate`. The reversal counterpart is `ReceiptService.reverseAndRemove`, which must stay symmetric with whatever `allocate` does.
- **Change the POS idempotency mechanism**: the DB constraint is `Sale.clientRequestId` (`@Column(unique = true, length = 100)`); the check-then-catch logic is in `SaleService.createSale` (the `findByClientRequestId` pre-check plus the `DataIntegrityViolationException` catch block). The frontend token generator is `newClientRequestId()` in `Pos.tsx` — any other POS-like caller must generate and persist its own token across retries the same way (one token per logical submission attempt, regenerated only after a confirmed success or an intentional new transaction).
- **Modules affected by a Sale/Credit-Note change**: Inventory (stock reversal via `InventoryService.applyMovement`), Accounting (journal reversal via `AccountingService.reverseSaleJournal`/`reverseJournal`, and the financial-year-open check inside posting), GST (resync via `GstTransactionSyncService.reverseSale`/`reverseCreditNote`, and the `gstReportingApplicable` flag that gates GSTR-1/GSTR-3B), Customer ledger (`LedgerService.reverseSaleDebit`/`reverseGstEntry`/`reverseCreditNoteEntry`/`reverseCashEntryForSale`) — any change to the posting pipeline should be mirrored exactly (same lines, opposite sign/direction) in the corresponding reversal path, since `reverseSaleEffects`/`CreditNoteService.cancel` are expected to fully undo `applyPostingEffects`/`CreditNoteService.post`.
