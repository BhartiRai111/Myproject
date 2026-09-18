# Purchase Module

## 1. Overview

The Purchase module covers the full buy-side transactional lifecycle in StoreHub:

**Purchase Order (optional)** → **Purchase Bill** (GST-applicable, `TransactionType.PURCHASE`) **OR** **Kacchi Purchase / Purchase Challan** (`TransactionType.PURCHASE_CHALLAN`, starts as `PurchaseStatus.DRAFT`, non-GST-reportable) → **Payment** (against one or more bills, or on-account) → **Debit Note** (purchase return / supplier debit / price or tax adjustment against a posted Purchase Bill).

All five documents share one `Purchase` entity (`backend/src/main/java/com/storehub/entity/Purchase.java`) and one `PurchaseService` (`backend/src/main/java/com/storehub/service/PurchaseService.java`) — a Purchase Bill and a Kacchi Purchase are the *same* table/entity, differentiated only by `transactionType` and, while in draft, `status`.

### TransactionType (`backend/src/main/java/com/storehub/entity/TransactionType.java`)

```java
public enum TransactionType { SALE, SALE_CHALLAN, PURCHASE, PURCHASE_CHALLAN }
```

- `PURCHASE` — a normal GST or Non-GST Purchase Bill. Always created already `COMPLETED` (posted).
- `PURCHASE_CHALLAN` — a Kacchi Purchase / Purchase Challan. GST is **still calculated in full** exactly like a normal purchase (see `gstReportingApplicable` below); it is *only* excluded from GST return reporting (GSTR-1/GSTR-3B). Only a `PURCHASE_CHALLAN` may be saved as `DRAFT` (`PurchaseService.createPurchase`: "Only a Kacchi Purchase / Purchase Challan can be saved as a draft").

`Purchase.gstReportingApplicable` is set at creation to `transactionType != PURCHASE_CHALLAN` (`Purchase.onCreate()` / `PurchaseService.createPurchase`) and is read everywhere GST reporting eligibility is decided (`PurchaseRepository.findEligibleForReconciliation`, `PurchaseItemRepository.hsnSummary`/`taxRateSummary`).

### PurchaseStatus (`backend/src/main/java/com/storehub/entity/PurchaseStatus.java`)

```java
public enum PurchaseStatus { DRAFT, PENDING, COMPLETED, CANCELLED }
```

- `DRAFT` — saved but not posted; **no stock, ledger, GST-log, or accounting effects yet**. Only reachable via a Kacchi Purchase / Purchase Challan (`saveAsDraft=true`).
- `PENDING` — defined in the enum and used as the JPA `@PrePersist` default (`Purchase.onCreate()`), but no code path in `PurchaseService`/`PurchaseController` ever sets a purchase to `PENDING` — every non-draft create goes straight to `COMPLETED`. Treat `PENDING` as effectively unused in the current codebase (`grep` confirms no assignment other than the default).
- `COMPLETED` — posted: stock added, supplier ledger CREDIT entry, GST log entry (if GST type), accounting journal posted, purchase-order received-quantity consumed, and (if `paidAmount > 0`) a system-generated `Payment`.
- `CANCELLED` — soft reversal; all of the above effects are reversed, `payableAmount` is zeroed, and the row is kept for history.

A `PurchaseOrder` is a separate entity/table (`purchase_orders`) with no stock/ledger/accounting effects at all — it is a pre-billing commitment tracked through `PurchaseOrderStatus` (`DRAFT`, `CONFIRMED`, `PARTIALLY_RECEIVED`, `COMPLETED`, `CANCELLED`), and its lines carry a `receivedQuantity` that `PurchaseService` increments/decrements as bills are posted/reversed against it.

## 2. Frontend

### Files and Components

| File | Purpose |
|---|---|
| `frontend/src/pages/purchases/PurchaseHub.tsx` | Landing page for the module; shows `PurchaseSummary` stat cards and links to the 5 sub-sections (Orders, Bills, Kacchi, Payments, Debit Notes). |
| `frontend/src/pages/purchases/PurchaseOrders.tsx` | Purchase Order list with search/supplier/status/date filters, cancel dialog. |
| `frontend/src/pages/purchases/PurchaseOrderForm.tsx` | Create/edit Purchase Order (supplier, items, GST%, discount). |
| `frontend/src/pages/purchases/PurchaseOrderDetail.tsx` | View a PO; actions: Create Purchase Bill, Edit, Cancel. |
| `frontend/src/pages/purchases/PurchaseBills.tsx` | Purchase Bill (`transactionType=PURCHASE`) list; view/edit/payment/delete actions. |
| `frontend/src/pages/purchases/PurchaseBillForm.tsx` | Create/edit a Purchase Bill, from scratch or pre-filled `?orderId=` from a PO; barcode scan add-item; GST/Non-GST toggle, tax-mode suggestion from supplier state. |
| `frontend/src/pages/purchases/PurchaseBillDetail.tsx` | Printable bill view; Edit / Payment / Return (Debit Note) / Delete actions. |
| `frontend/src/pages/purchases/KacchiPurchases.tsx` | Kacchi Purchase list (`transactionType=PURCHASE_CHALLAN` filter); Post action for `DRAFT` rows. |
| `frontend/src/pages/purchases/KacchiPurchaseForm.tsx` | Create/edit a Kacchi Purchase; "Save as Draft" vs "Post Now"/"Save & Post" buttons; always GST-type internally. |
| `frontend/src/pages/purchases/KacchiPurchaseDetail.tsx` | Printable challan view with "GST Calculated: YES · GST Reporting: NO" banner; Post/Edit/Return/Delete actions. |
| `frontend/src/pages/purchases/Payments.tsx` | Payment Entry list; delete action. |
| `frontend/src/pages/purchases/PaymentForm.tsx` | Record a payment: supplier → loads outstanding bills + outstanding credit expenses (`paymentApi.getOutstanding`) → optional per-row checkbox allocation (else server FIFO-allocates). |
| `frontend/src/pages/purchases/PaymentDetail.tsx` | Printable payment voucher; shows allocations; delete action. |
| `frontend/src/pages/purchases/DebitNotes.tsx` | Debit Note list with status filter. |
| `frontend/src/pages/purchases/DebitNoteForm.tsx` | Search/select a `COMPLETED` source purchase, then enter per-line return quantities; "Save as Draft" or "Save and Post". |
| `frontend/src/pages/purchases/DebitNoteDetail.tsx` | Note detail; Post/Cancel actions gated on `user.role === 'ADMIN' || 'STORE_MANAGER'` client-side (`canManage`). |
| `frontend/src/api/purchaseApi.ts` | `purchaseApi` — list/getById/create/update/post/cancel/remove against `/purchases`. |
| `frontend/src/api/purchaseOrderApi.ts` | `purchaseOrderApi` — list/getById/create/update/setStatus/cancel against `/purchase-orders`. |
| `frontend/src/api/paymentApi.ts` | `paymentApi` — list/getById/getOutstanding/create/remove against `/payments`. |
| `frontend/src/api/purchaseSummaryApi.ts` | `purchaseSummaryApi.get()` against `/purchase-summary`. |
| `frontend/src/api/debitNoteApi.ts` | `debitNoteApi` — list/getById/create/post/cancel against `/debit-notes`. |
| `frontend/src/types/purchase.ts` | `Purchase`, `PurchaseItem`, `PurchaseCreatePayload`, `PurchaseUpdatePayload`, `PurchaseStatus`, `PurchaseTransactionType`, `PaymentStatus`, `GstType`, `TaxMode`, `PaymentMode`. |
| `frontend/src/types/purchaseOrder.ts` | `PurchaseOrder`, `PurchaseOrderItem`, `PurchaseOrderPayload`, `PurchaseOrderStatus`. |
| `frontend/src/types/payment.ts` | `Payment`, `PaymentAllocation`, `PaymentPayload`, `SupplierOutstanding`, `OutstandingPurchaseBill`, `OutstandingExpense`, `PurchaseSummary`. |
| `frontend/src/types/note.ts` | `DebitNote`, `DebitNoteItem`, `DebitNoteCreatePayload`, `NoteStatus`, `StockImpactType`, `DebitNoteType` (also `CreditNote*` for the Sales-side mirror). |

### Frontend Flow

**Purchase Order create/edit**: `PurchaseOrderForm` collects supplier + item rows (product, qty, rate, discount, GST%); client computes row/grand totals only for display — the server recomputes and is authoritative. Submits `purchaseOrderApi.create`/`.update` → on success navigates to `PurchaseOrderDetail`. There is no separate "approve" step in the UI; `PurchaseOrderDetail`/`PurchaseOrders` expose a `setStatus` API (`DRAFT`/`CONFIRMED` only) but **no page currently calls `purchaseOrderApi.setStatus`** (`grep` finds no caller) — status otherwise advances automatically to `PARTIALLY_RECEIVED`/`COMPLETED` as bills are posted against it (`PurchaseOrderService.recomputeStatus`). Cancel is only allowed while nothing has been received.

**Purchase Bill create from scratch or from PO**: `PurchaseBillForm` either starts empty or, when navigated to with `?orderId=<id>`, calls `purchaseOrderApi.getById` and pre-fills supplier + only the order's lines with `remainingQuantity > 0`, capping each row's editable quantity at that `remainingQuantity` and tagging it with `purchaseOrderItemId`. GST type/tax-mode toggle recomputes CGST/SGST/IGST client-side for display; server is authoritative. Editing an existing bill that already `hasPayments` locks the whole form (`<fieldset disabled={locked}>`) — the backend rejects such edits outright (see §6).

**Kacchi Purchase create**: `KacchiPurchaseForm` is a near-duplicate of `PurchaseBillForm` but always sends `gstType: 'GST'` and `transactionType: 'PURCHASE_CHALLAN'`, and offers two submit buttons — "Save as Draft" (`saveAsDraft: true`) and "Post Now"/"Save & Post" (posts immediately, with a confirm dialog warning that posting adds stock/ledger/journal and "cannot be undone from here"). Editing only allowed while still `DRAFT` (page redirects otherwise). `KacchiPurchaseDetail` exposes a standalone **Post** button (`purchaseApi.post(id)` → `POST /api/purchases/{id}/post`) for a saved draft.

**Payment entry with multi-bill allocation**: `PaymentForm` picks a supplier, then loads `paymentApi.getOutstanding(supplierId)` which returns both outstanding Purchase Bills and outstanding credit Expenses for that supplier. The user may check zero, some, or all rows and edit each row's "Apply Amount" (defaults to the full payable). If no rows are checked, the payload's `allocations` array is empty and the backend allocates FIFO by date across bills+expenses combined. Client validates each checked row's amount ≤ its payable and the sum of checked amounts ≤ the payment amount.

**Debit Note create from a Bill**: `DebitNoteForm` either receives `?purchaseId=` (from `PurchaseBillDetail`/`KacchiPurchaseDetail` "Return" button) or lets the user search `COMPLETED` purchases by number/supplier. Once a source purchase is selected, the form lists its items with an editable "Return Qty" per line (capped client-side at `item.quantity`; the server further caps at `quantity - alreadyReturned`). Submits either "Save as Draft" (`post: false`) or "Save and Post" (`post: true`).

## 3. API Calls

| Function | Method | URL | Request shape | Response shape |
|---|---|---|---|---|
| `purchaseOrderApi.list` | GET | `/purchase-orders` | query: `search, supplierId, status, fromDate, toDate, page, size` | `PagedResponse<PurchaseOrder>` |
| `purchaseOrderApi.getById` | GET | `/purchase-orders/{id}` | — | `PurchaseOrder` |
| `purchaseOrderApi.create` | POST | `/purchase-orders` | `PurchaseOrderPayload` | `PurchaseOrder` (201) |
| `purchaseOrderApi.update` | PUT | `/purchase-orders/{id}` | `PurchaseOrderPayload` | `PurchaseOrder` |
| `purchaseOrderApi.setStatus` | PATCH | `/purchase-orders/{id}/status?status=` | — | `PurchaseOrder` |
| `purchaseOrderApi.cancel` | PATCH | `/purchase-orders/{id}/cancel` | — | `PurchaseOrder` |
| `purchaseApi.list` | GET | `/purchases` | query: `search, paymentStatus, status, fromDate, toDate, transactionType, page, size` (`storeId` supported server-side, not sent by current frontend query object) | `PagedResponse<Purchase>` |
| `purchaseApi.getById` | GET | `/purchases/{id}` | — | `Purchase` |
| `purchaseApi.create` | POST | `/purchases` | `PurchaseCreatePayload` | `Purchase` (201) |
| `purchaseApi.update` | PUT | `/purchases/{id}` | `PurchaseUpdatePayload` | `Purchase` |
| `purchaseApi.post` | POST | `/purchases/{id}/post` | — | `Purchase` (posts a DRAFT Kacchi challan) |
| `purchaseApi.cancel` | PATCH | `/purchases/{id}/cancel` | — | `Purchase` (soft reverse; not called by any current page — `PurchaseBills`/`KacchiPurchases` use `remove` instead) |
| `purchaseApi.remove` | DELETE | `/purchases/{id}` | — | 204 (hard delete + full reversal) |
| `paymentApi.list` | GET | `/payments` | query: `search, supplierId, fromDate, toDate, page, size` | `PagedResponse<Payment>` |
| `paymentApi.getById` | GET | `/payments/{id}` | — | `Payment` |
| `paymentApi.getOutstanding` | GET | `/payments/outstanding/{supplierId}` | — | `SupplierOutstanding` |
| `paymentApi.create` | POST | `/payments` | `PaymentPayload` | `Payment` (201) |
| `paymentApi.remove` | DELETE | `/payments/{id}` | — | 204 |
| `debitNoteApi.list` | GET | `/debit-notes` | query: `search, status, fromDate, toDate, page, size` | `PagedResponse<DebitNote>` |
| `debitNoteApi.getById` | GET | `/debit-notes/{id}` | — | `DebitNote` |
| `debitNoteApi.create` | POST | `/debit-notes` | `DebitNoteCreatePayload` | `DebitNote` (201) |
| `debitNoteApi.post` | POST | `/debit-notes/{id}/post` | — | `DebitNote` |
| `debitNoteApi.cancel` | PATCH | `/debit-notes/{id}/cancel` | — | `DebitNote` |
| `purchaseSummaryApi.get` | GET | `/purchase-summary` | — | `PurchaseSummary` |

## 4. Backend

### Controllers

**`PurchaseController`** (`/api/purchases`)
- `GET /api/purchases` — `PERM_PURCHASE_VIEW` — search/filter/paginate.
- `GET /api/purchases/{id}` — `PERM_PURCHASE_VIEW`.
- `POST /api/purchases` — `PERM_PURCHASE_CREATE` — create (Bill or Kacchi, draft or posted).
- `PUT /api/purchases/{id}` — `PERM_PURCHASE_EDIT` — full update.
- `POST /api/purchases/{id}/post` — `PERM_PURCHASE_POST` — posts a DRAFT Kacchi Purchase.
- `PATCH /api/purchases/{id}/cancel` — `PERM_PURCHASE_CANCEL` — soft reversal (keeps the row).
- `DELETE /api/purchases/{id}` — `PERM_PURCHASE_CANCEL` — hard delete with full reversal.

**`PurchaseOrderController`** (`/api/purchase-orders`)
- `GET /api/purchase-orders` — `PERM_PURCHASE_VIEW`.
- `GET /api/purchase-orders/{id}` — `PERM_PURCHASE_VIEW`.
- `POST /api/purchase-orders` — `PERM_PURCHASE_CREATE`.
- `PUT /api/purchase-orders/{id}` — `PERM_PURCHASE_EDIT`.
- `PATCH /api/purchase-orders/{id}/status?status=` — `PERM_PURCHASE_EDIT`.
- `PATCH /api/purchase-orders/{id}/cancel` — `PERM_PURCHASE_CANCEL`.

**`PaymentController`** (`/api/payments`)
- `GET /api/payments` — `PERM_PAYMENT_VIEW`.
- `GET /api/payments/{id}` — `PERM_PAYMENT_VIEW`.
- `GET /api/payments/outstanding/{supplierId}` — `PERM_PAYMENT_VIEW`.
- `POST /api/payments` — `PERM_PAYMENT_CREATE`.
- `DELETE /api/payments/{id}` — `PERM_PAYMENT_POST` (note: delete is gated on the POST permission, not a dedicated cancel permission — there is no `PAYMENT_CANCEL`).

**`PurchaseSummaryController`** (`/api/purchase-summary`)
- `GET /api/purchase-summary` — **no `@PreAuthorize`** on the endpoint or class (relies on the global authenticated-request filter chain only). NOT FOUND: any permission check for this endpoint.

**`DebitNoteController`** (`/api/debit-notes`)
- `GET /api/debit-notes` — **no `@PreAuthorize`** (view is open to any authenticated user; unlike `PurchaseController`/`PaymentController` there is no `PERM_DEBIT_NOTE_VIEW` check here despite that permission existing).
- `GET /api/debit-notes/{id}` — **no `@PreAuthorize`**.
- `POST /api/debit-notes` — `PERM_DEBIT_NOTE_CREATE`.
- `POST /api/debit-notes/{id}/post` — `PERM_DEBIT_NOTE_POST`.
- `PATCH /api/debit-notes/{id}/cancel` — `PERM_DEBIT_NOTE_CANCEL`.

### DTOs

- **`PurchaseCreateRequest`**: `supplierId` (`@NotNull`), `storeId` (optional), `purchaseDate` (`@NotNull`), `gstType` (`@NotNull`), `taxMode` (required only when GST — checked in service, not annotation), `supplierPhone`, `supplierGstin`, `billingAddress`, `shippingAddress`, `paymentMode` (`@NotNull`), `paidAmount` (`@NotNull @DecimalMin("0")`), `notes`, `purchaseOrderId`, `items` (`@NotEmpty @Valid List<PurchaseItemRequest>`), `transactionType`, `saveAsDraft` (boolean, Kacchi-only).
- **`PurchaseUpdateRequest`**: same core fields as create plus `status` (`@NotNull PurchaseStatus`); no `storeId`/`purchaseOrderId`/`transactionType`/`saveAsDraft` (store and transaction type are fixed at creation).
- **`PurchaseItemRequest`**: `productId` (`@NotNull`), `quantity` (`@NotNull @Min(1)`), `purchasePrice` (`@NotNull @DecimalMin("0")`), `discount` (`@NotNull @DecimalMin("0")`), `tax` (`@NotNull @DecimalMin("0")` — client-computed, not authoritative), `gstPercent` (`@DecimalMin("0")`), `purchaseOrderItemId` (optional).
- **`PurchaseResponse`** / **`PurchaseItemResponse`**: full read model including derived `subtotalAmount`/`totalDiscount`/`totalTax` (`fromEntity` sums), `hasPayments`, `gstReportingApplicable`, `transactionType`, store fields.
- **`PurchaseOrderRequest`**: `supplierId` (`@NotNull`), `storeId`, `supplierPhone/Gstin`, addresses, `orderDate` (`@NotNull`), `expectedDeliveryDate`, `remarks`, `items` (`@NotEmpty @Valid List<PurchaseOrderItemRequest>`).
- **`PurchaseOrderItemRequest`**: `productId` (`@NotNull`), `quantity` (`@NotNull @Min(1)`), `rate` (`@NotNull @DecimalMin("0")`), `discount` (`@NotNull @DecimalMin("0")`), `gstPercent` (`@NotNull @DecimalMin("0")`).
- **`PurchaseOrderResponse`** / **`PurchaseOrderItemResponse`**: adds derived `receivedAmount`/`remainingAmount` (proportional to `receivedQuantity`), and per-item `remainingQuantity`.
- **`PaymentRequest`**: `supplierId` (`@NotNull`), `storeId`, `paymentDate` (`@NotNull`), `amount` (`@NotNull @DecimalMin("0.01")`), `paymentMode` (`@NotNull`), `remarks`, `allocations` (`@Valid List<PaymentAllocationRequest>`, optional/empty ⇒ server FIFO).
- **`PaymentAllocationRequest`**: exactly one of `purchaseId`/`expenseId` (enforced in service, not by annotation), `amountApplied` (`@NotNull @DecimalMin("0.01")`).
- **`PaymentResponse`** / **`PaymentAllocationResponse`**: includes resolved `purchaseNumber`/`expenseNumber`.
- **`SupplierOutstandingResponse`** / **`OutstandingPurchaseBillResponse`** / **`OutstandingExpenseResponse`**: read models for `GET /payments/outstanding/{supplierId}`.
- **`PurchaseSummaryResponse`**: `totalPurchaseOrders`, `pendingOrders`, `totalPurchaseBills`, `totalPayables`, `todaysPurchases`.
- **`DebitNoteCreateRequest`**: `sourcePurchaseId` (`@NotNull`), `noteType` (`@NotNull DebitNoteType`), `noteDate` (`@NotNull`), `stockImpact` (`@NotNull StockImpactType`), `reason`, `remarks`, `items` (`@NotEmpty @Valid List<DebitNoteItemRequest>`), `post` (boolean — create-then-post in one call).
- **`DebitNoteItemRequest`**: `purchaseItemId` (`@NotNull`), `quantity` (`@NotNull @Min(1)`) — rate/tax are **not** accepted from the client; the service derives them proportionally from the original `PurchaseItem`.
- **`DebitNoteResponse`** / **`DebitNoteItemResponse`**: full read model including `createdBy`/`postedBy`/`postedAt`/`cancelledBy`/`cancelledAt` audit trail fields.

### Services

**`PurchaseService`** (`backend/src/main/java/com/storehub/service/PurchaseService.java`)
- `getPurchases(...)` — store-scoped search via `storeAccessService.resolveViewableStoreId`.
- `getPurchaseById(id)` — asserts caller has access to the purchase's `store` (`storeAccessService.assertStoreAccess`); flags `hasPayments` from `PaymentAllocationRepository.findByPurchaseId`.
- `createPurchase(request)` — validates GST/tax-mode consistency and GSTIN format (`GstinValidator.isValid`), resolves supplier/store (rejecting inactive ones), resolves `storeId` via `storeAccessService.resolveEffectiveStoreId`, builds the `Purchase` with `status = DRAFT` only if `saveAsDraft && transactionType == PURCHASE_CHALLAN` (else `COMPLETED`), computes line items + totals (`applyItems`), computes paid/payable/paymentStatus (`applyPayment`), saves, assigns the voucher number (`voucherNumberService.next(PURCHASE_CHALLAN or PURCHASE, purchaseDate)`), and — **only if not draft** — calls `applyPostingEffects`. Always writes an `AuditAction.CREATE` audit log.
- `postPurchaseChallan(id)` — the only way a `DRAFT` (Kacchi) purchase becomes `COMPLETED`; guards `transactionType == PURCHASE_CHALLAN` and `status == DRAFT`, flips status, then `applyPostingEffects`.
- `applyPostingEffects(saved)` (private) — the single place every "posting" side effect happens: `addStock` (inventory), `ledgerService.recordPurchaseCredit`, `ledgerService.recordInputGstEntry`, `accountingService.postPurchaseJournal`, `consumeOrderQuantities(+1)` (PO received-qty), `gstTransactionSyncService.syncPurchase`, and — if `paidAmount > 0` — `paymentService.createSystemPaymentForPurchase`.
- `updatePurchase(id, request)` — blocks editing a `CANCELLED` purchase, blocks setting status to `CANCELLED` via this endpoint ("Use the delete action to cancel and reverse a purchase"), and **blocks editing entirely once any payment allocation exists** ("Delete the payment first..."). If the old row was `COMPLETED`, first fully reverses it (`restoreStock` + ledger/GST/journal/PO-qty reversal), then rebuilds items/totals from the new request and, if the new status isn't `DRAFT`, re-applies all posting effects (stock add, ledger, journal, PO-qty, GST sync, system payment).
- `cancelPurchase(id)` (soft) — rejects if already `CANCELLED`; `guardNoManualPayments` (rejects if any *manual* — non-system — payment allocation exists); calls `reversePurchaseEffects`, sets `status = CANCELLED` and `payableAmount = 0`; audit-logs `CANCEL`.
- `deletePurchase(id)` (hard) — same manual-payment guard, `reversePurchaseEffects`, then `purchaseRepository.delete`.
- `reversePurchaseEffects(purchase, reason)` (private) — if `COMPLETED`, `restoreStock`; if still `DRAFT`, returns immediately (nothing was ever posted, nothing to reverse); otherwise deletes every system-generated `Payment` tied to it (`paymentService.deleteSystemPayment` per allocation), reverses the supplier-ledger CREDIT, reverses the GST log entry, reverses the accounting journal, decrements PO received-qty, reverses the GST-return sync.
- `applyItems(...)` (private) — rejects duplicate products in one purchase, rejects inactive products (unless already on the purchase being edited), validates a PO-linked line doesn't exceed `orderItem.getRemainingQuantity()`, computes taxable/CGST/SGST/IGST per line via `GstCalculationService.calculateLine`, accumulates purchase-level totals.
- `restoreStock` / `addStock` (private) — call `inventoryService.applyMovement(productId, storeId, ±qty, StockMovementType.PURCHASE_CANCEL|PURCHASE, ReferenceType.PURCHASE, purchaseId, reason)`.
- `findPurchaseOrThrow(id)` — throws `PurchaseNotFoundException`.

**`PurchaseOrderService`** (`backend/src/main/java/com/storehub/service/PurchaseOrderService.java`)
- `search(...)` / `getById(id)` — store-scoped, same `storeAccessService` pattern as Purchase.
- `create(request)` — validates supplier/store active, builds items (`applyItems`, rejects duplicate products), status always starts `DRAFT`, assigns voucher number (`VoucherDocType.PURCHASE_ORDER`).
- `update(id, request)` — blocks editing a `CANCELLED` or `COMPLETED` order; guards that any product with `receivedQuantity > 0` stays present with quantity ≥ what was already received; rebuilds items, `recomputeStatus`.
- `setStatus(id, status)` — only `DRAFT`/`CONFIRMED` allowed as a manual target; rejects if the order already has any received quantity ("status is now managed automatically").
- `cancel(id)` — rejects if already cancelled or if any quantity has been received (i.e., a bill exists against it).
- `applyReceivedQuantityDelta(purchaseOrderItemId, delta)` — called by `PurchaseService.consumeOrderQuantities` on every posted-purchase create/update/reversal; clamps at `[0, orderedQuantity]`, then `recomputeStatus`.
- `recomputeStatus(order)` (private) — `COMPLETED` once every line's `remainingQuantity <= 0`, else `PARTIALLY_RECEIVED` if anything has been received, else left as-is (never auto-downgrades a `CANCELLED` order).
- `countPendingOrders()` / `countTotalOrders()` — used by `PurchaseSummaryService`.
- **No `AuditService` calls anywhere in this service** — Purchase Order create/update/cancel is **not** audit-logged (unlike Purchase, Payment and Debit Note). NOT FOUND: any `auditService.log(...)` call in `PurchaseOrderService`.

**`PaymentService`** (`backend/src/main/java/com/storehub/service/PaymentService.java`) — handles both a Purchase Bill payment *and* a credit (party) Expense payment through the same `PaymentAllocation` mechanism.
- `search(...)` / `getById(id)` — store-scoped.
- `getOutstandingForSupplier(supplierId)` — combines `purchaseRepository.findOutstandingBySupplier` and `expenseRepository.findOutstandingBySupplier` into one `SupplierOutstandingResponse`.
- `create(request)` — resolves supplier/store, builds `Payment` (`systemGenerated=false`), assigns voucher number (`VoucherDocType.PAYMENT`), calls `allocate(...)`, saves, then `ledgerService.recordPaymentDebit` + `ledgerService.recordCashEntryOut` + `accountingService.postPaymentJournal`; audit-logs `PAYMENT`.
- `createSystemPaymentForPurchase(purchase)` — invoked only from `PurchaseService` when a bill records an immediate `paidAmount`; builds a `systemGenerated=true` `Payment` with a single allocation to that purchase for the full `paidAmount`, posts the same ledger/journal effects. No separate audit log (the parent Purchase's audit entry covers it).
- `delete(id)` — rejects deleting a **system-generated** payment directly ("Delete the purchase bill instead to reverse it."); otherwise `reverseAndRemove` + audit-log `CANCEL`.
- `deleteSystemPayment(id)` (package-private) — used only by `PurchaseService` when reversing a purchase; same `reverseAndRemove`, no separate audit entry.
- `reverseAndRemove(payment)` (private) — for each allocation, restores the linked `Purchase`'s or `Expense`'s `paidAmount`/`payableAmount`/`paymentStatus`; reverses ledger debit, reverses cash-out entry, reverses the payment journal; deletes the `Payment` row (allocations cascade).
- `allocate(payment, explicit, supplierId)` (private) — if `explicit` allocations given: validates each references exactly one of purchase/expense, belongs to the same supplier, doesn't exceed that document's `payableAmount`, and the sum doesn't exceed the payment amount. If **no** explicit allocations: FIFO across the supplier's combined outstanding purchases + expenses sorted by date, applying `min(remaining, payable)` to each until the payment amount is exhausted; any leftover becomes an on-account credit (ledger DEBIT only, not tied to a document).
- `findOrThrow(id)` — throws `PaymentNotFoundException`.

**`PurchaseSummaryService`** (`backend/src/main/java/com/storehub/service/PurchaseSummaryService.java`)
- `getSummary()` — a single read-only aggregation: `purchaseOrderRepository.count()`, `purchaseOrderService.countPendingOrders()`, `purchaseRepository.count()`, `ledgerService.getTotalPayables()`, `purchaseRepository.getTotalPurchasesForDate(today)`.

**`DebitNoteService`** (`backend/src/main/java/com/storehub/service/DebitNoteService.java`) — the Purchase-side mirror of `CreditNoteService` (Sales module).
- `search(...)` / `getById(id)` — store access resolved via the **source purchase's** store.
- `create(request)` — rejects a debit note against a `CANCELLED` or still-`DRAFT` (unposted) source purchase; for each requested item, validates the `purchaseItemId` belongs to the source purchase, quantity > 0, and quantity ≤ `purchaseItem.quantity - alreadyReturned` (`debitNoteItemRepository.sumReturnedQuantity`, excluding cancelled notes) — the exact "eligible" message names the product, requested qty, and remaining eligible qty. Each item's taxable/discount/CGST/SGST/IGST is **derived proportionally** from the source `PurchaseItem` (`proportion = totalForLine * returnedQty / purchasedQty`, HALF_UP to 2dp) — never re-entered by the client. Resolves `financialYearId` (`financialYearService.resolveForDate`). Saves, assigns voucher number (`VoucherDocType.DEBIT_NOTE`), audit-logs `CREATE`. If `request.isPost()`, immediately calls `post(id)`.
- `post(id)` — only from `DRAFT`; rejects if the source purchase has since been cancelled. Posts a journal (`DEBIT SUPPLIER_PAYABLE` for `totalAmount`, `CREDIT PURCHASE` for `taxableAmount`, plus `CREDIT` lines for any positive CGST/SGST/IGST) via `accountingService.postJournal(VoucherType.DEBIT_NOTE, ...)`. If `stockImpact == STOCK_RETURN`, calls `inventoryService.applyMovement(..., -quantity, StockMovementType.PURCHASE_RETURN, ReferenceType.DEBIT_NOTE, ...)` per item (store-aware overload when the source purchase has a store). Calls `ledgerService.recordDebitNoteEntry`. Flips `status = POSTED`, stamps `postedBy`/`postedAt`, **then** calls `gstTransactionSyncService.syncDebitNote` (comment: "Must run after the status flip: GstReportingEligibility checks status == POSTED"). Audit-logs `POST`.
- `cancel(id)` — idempotent no-op if already `CANCELLED`. If it was `POSTED`: reverses the journal (`accountingService.reverseJournal`), if `STOCK_RETURN` reverses stock with `StockMovementType.ADJUSTMENT` (positive delta, putting the returned stock back), reverses the ledger entry, reverses the GST sync. Sets `status = CANCELLED`, stamps `cancelledBy`/`cancelledAt`; audit-logs `CANCEL`.
- `proportion(...)` (private) — the shared line-splitting helper described above.
- `findOrThrow(id)` — throws `NoteNotFoundException`.

### Repository (exact method names)

- **`PurchaseRepository`**: `findBySupplierIdOrderByCreatedAtDesc`, `findOutstandingBySupplier`, `findAllOutstanding`, `findCompletedIds`, `findAllIds`, `getTotalPurchasesForDate`, `sumTotalAmountByDateRange`, `countByDateRange`, `sumAndCountByDateRangeAndStore`, `search`, `findEligibleForReconciliation`.
- **`PurchaseItemRepository`**: `hsnSummary`, `taxRateSummary`.
- **`PurchaseOrderRepository`**: `search`, `countByStatusIn`.
- **`PurchaseOrderItemRepository`**: no custom methods (plain `JpaRepository<PurchaseOrderItem, Long>`).
- **`PaymentRepository`**: `findAllIds`, `sumAmountForDate`, `search`.
- **`PaymentAllocationRepository`**: `findByPurchaseId`, `findByExpenseId`.
- **`SupplierLedgerEntryRepository`**: `findBySupplierIdOrderByEntryDateAscCreatedAtAsc`, `getOutstandingForSupplier`, `getTotalOutstanding`, `findByReferenceTypeAndReferenceId`, `sumBySupplier`.
- **`PurchaseGstEntryRepository`**: `findByPurchaseId`.
- **`DebitNoteRepository`**: `search`, `findPostedIds`, `findAllIds`.
- **`DebitNoteItemRepository`**: `sumReturnedQuantity`.

## 5. Database

### Tables

`purchases`, `purchase_items`, `purchase_orders`, `purchase_order_items`, `payments`, `payment_allocations`, `supplier_ledger_entries`, `purchase_gst_entries`, `debit_notes`, `debit_note_items`.

### Relationships

- **Purchase ↔ PurchaseItem**: `purchases.id` ← `purchase_items.purchase_id` (`@OneToMany(mappedBy="purchase", cascade=ALL, orphanRemoval=true)`).
- **Purchase ↔ Supplier**: `purchases.supplier_id` → `suppliers.id` (`@ManyToOne`, `nullable=false`).
- **Purchase ↔ Store**: `purchases.store_id` → `stores.id` (`@ManyToOne`, nullable — fixed at creation, never changed on edit per the class comment).
- **Purchase ↔ PurchaseOrder**: `purchases.purchase_order_id` → `purchase_orders.id` (`@ManyToOne`, nullable — set only when the bill originates from a PO).
- **PurchaseItem ↔ PurchaseOrderItem**: `purchase_items.purchase_order_item_id` → `purchase_order_items.id` (nullable — links a billed line back to the order line it fulfilled, driving `receivedQuantity` bookkeeping).
- **PurchaseOrder ↔ PurchaseOrderItem**: `purchase_orders.id` ← `purchase_order_items.purchase_order_id` (`@OneToMany`, cascade ALL, orphanRemoval).
- **Payment ↔ PaymentAllocation ↔ Purchase**: `payments.id` ← `payment_allocations.payment_id`; `payment_allocations.purchase_id` → `purchases.id` (nullable — exactly one of `purchase_id`/`expense_id` set, enforced in `PaymentService`, not a DB constraint); `payment_allocations.expense_id` → `expenses.id` (nullable, the credit-Expense path).
- **DebitNote ↔ DebitNoteItem ↔ Purchase**: `debit_notes.source_purchase_id` → `purchases.id` (`@ManyToOne`, not null); `debit_notes.id` ← `debit_note_items.debit_note_id`; `debit_note_items.purchase_item_id` → `purchase_items.id` (not null — links each returned line back to its original bill line, used by `sumReturnedQuantity` for the eligible-quantity guard).
- **DebitNote ↔ Supplier**: `debit_notes.supplier_id` → `suppliers.id` (copied from the source purchase at creation).
- **SupplierLedgerEntry**: `supplier_ledger_entries.supplier_id` → `suppliers.id`; polymorphic `reference_type` + `reference_id` (no FK) pointing at whichever document (Purchase, Payment, DebitNote, Expense) posted the entry.
- **PurchaseGstEntry**: `purchase_gst_entries.purchase_id` — plain `Long` column, no FK/`@ManyToOne` (looked up via `findByPurchaseId`).

## 6. Validation

- **Bean validation** (`jakarta.validation` annotations on the DTOs) — see §4 DTOs for the exact constraints (`@NotNull`, `@NotEmpty`, `@Min(1)`, `@DecimalMin`). A violation throws `MethodArgumentNotValidException`, mapped to `400 Bad Request` with `ApiError.fieldErrors` populated from each field's `getDefaultMessage()` (`GlobalExceptionHandler.handleValidation`).
- **Service-level business validation** (throws `BadRequestException`, mapped to `400` with **no** `fieldErrors` — only a top-level `message`):
  - GST purchase without a `taxMode` ("Tax mode (Intra-State or Inter-State) is required for a GST purchase").
  - Malformed `supplierGstin` (`GstinValidator.isValid` — 15-char GSTIN format).
  - Inactive supplier / inactive store used for a new purchase, PO, or edit onto a different supplier.
  - Draft save requested for a non-Kacchi `transactionType`.
  - `saveAsDraft`/challan-only actions on the wrong `transactionType` or wrong current `status`.
  - Duplicate product within one purchase or PO's item list.
  - A PO-linked bill line's quantity exceeding `orderItem.getRemainingQuantity()`.
  - Discount exceeding the line's taxable amount.
  - `paidAmount` exceeding `totalAmount`.
  - Editing a `CANCELLED` purchase, or setting status to `CANCELLED` via `PUT /purchases/{id}` (must use delete/cancel endpoints).
  - Editing a purchase that already has any payment allocation.
  - Cancelling/deleting a purchase that has a **manual** (non-system) payment against it — the payment must be deleted first.
  - PO: editing a `CANCELLED`/`COMPLETED` order; reducing/removing a line already partially received; manually setting status once anything has been received; cancelling an order with any received quantity.
  - Payment: allocation referencing neither/both purchase and expense; allocation amount exceeding the target's payable amount; total explicit allocations exceeding the payment amount; allocation's purchase/expense not belonging to the stated supplier.
  - Payment delete: rejecting deletion of a system-generated payment directly.
  - Debit Note: source purchase `CANCELLED` or still `DRAFT`; a requested `purchaseItemId` not belonging to the stated source purchase; return quantity ≤ 0 or exceeding `quantity - alreadyReturned`; posting anything but a `DRAFT` note; posting when the source purchase has since been cancelled.
- Per this repo's `CLAUDE.md` debugging rule (Login/Registration/User Management scope), the field-error-surfacing pattern (`isInvalid`/`Form.Control.Feedback`-equivalent via React `fieldErrors` state + `parseApiError`) is already followed in `PurchaseOrderForm.tsx`, `PurchaseBillForm.tsx`, `KacchiPurchaseForm.tsx` for the annotated DTO fields (e.g. `supplierId`, `orderDate`, `purchaseDate`, `paidAmount`) — business-rule `BadRequestException` messages (no `fieldErrors`) are shown only via the top-level `error`/`Alert` banner, since the backend does not attach a field key to them.

## 7. Authentication / Authorization

Every endpoint in this module sits behind the application's standard JWT bearer-token filter chain (`backend/src/main/java/com/storehub/config/SecurityConfig.java`); all `PurchaseController`, `PurchaseOrderController`, `PaymentController` and most `DebitNoteController` methods additionally require a specific granted authority via `@PreAuthorize("hasAuthority('PERM_...')")`. Two `DebitNoteController` read endpoints (`GET /api/debit-notes`, `GET /api/debit-notes/{id}`) and the whole `PurchaseSummaryController` carry **no** `@PreAuthorize` — see §4 Controllers. Authorities are granted per `Role` via the fixed map in `backend/src/main/java/com/storehub/service/RolePermissions.java` (see §8).

## 8. Permissions

From `backend/src/main/java/com/storehub/entity/Permission.java` and `RolePermissions.java`:

| Permission | Purpose | Granted to |
|---|---|---|
| `PURCHASE_VIEW` | List/view purchases & purchase orders | ADMIN, STORE_MANAGER, ACCOUNTANT, PURCHASE_USER |
| `PURCHASE_CREATE` | Create a purchase / PO | ADMIN, STORE_MANAGER, PURCHASE_USER |
| `PURCHASE_EDIT` | Update a purchase / PO, PO status | ADMIN, STORE_MANAGER, PURCHASE_USER |
| `PURCHASE_POST` | Post a DRAFT Kacchi challan | ADMIN, STORE_MANAGER, PURCHASE_USER |
| `PURCHASE_CANCEL` | Cancel (soft) / delete (hard) a purchase or PO | ADMIN, STORE_MANAGER only (excluded from PURCHASE_USER's set) |
| `PAYMENT_VIEW` | List/view payments, outstanding | ADMIN, STORE_MANAGER, ACCOUNTANT, PURCHASE_USER |
| `PAYMENT_CREATE` | Record a payment | ADMIN, STORE_MANAGER, PURCHASE_USER |
| `PAYMENT_POST` | Delete/reverse a payment (also gates `DELETE /api/payments/{id}`) | ADMIN, STORE_MANAGER, PURCHASE_USER |
| `DEBIT_NOTE_VIEW` | (defined, but not enforced by `DebitNoteController`'s GET endpoints — see §4/§7) | ADMIN, STORE_MANAGER, ACCOUNTANT, PURCHASE_USER |
| `DEBIT_NOTE_CREATE` | Create a debit note | ADMIN, STORE_MANAGER only |
| `DEBIT_NOTE_POST` | Post a debit note | ADMIN, STORE_MANAGER only |
| `DEBIT_NOTE_CANCEL` | Cancel a debit note | ADMIN, STORE_MANAGER only |

`PURCHASE_USER` therefore cannot cancel/delete a purchase or PO, nor create/post/cancel a debit note (matching `DebitNoteDetail.tsx`'s client-side `canManage = role === 'ADMIN' || 'STORE_MANAGER'` gate — the client and server checks agree for Debit Note actions). `ACCOUNTANT` has view-only access across Purchase/Payment/Debit-Note. There is no `PAYMENT_CANCEL` permission — deletion of a payment reuses `PAYMENT_POST`.

## 9. Transaction Handling

Every mutating method in `PurchaseService`, `PurchaseOrderService`, `PaymentService`, and `DebitNoteService` is `@Transactional` (class-level `@Transactional` is not used; each public mutator is annotated individually) — read paths in `DebitNoteService` use `@Transactional(readOnly = true)`; read paths elsewhere run without an explicit annotation (plain reads under the default propagation of whatever calls them, typically none needed for a single `findById`).

Atomicity boundaries:
- **`createPurchase`**: item/total computation, save, voucher-number assignment, and (if not draft) the *entire* `applyPostingEffects` cascade (stock, ledger CREDIT, GST log, accounting journal, PO received-qty, GST-return sync, and an immediate system `Payment` with its own ledger/journal) all commit or roll back together in one transaction.
- **`postPurchaseChallan`**: status flip + `applyPostingEffects` — one transaction.
- **`updatePurchase`**: full reversal-then-reapply cycle (if the old row was posted) plus the new item/total rebuild — one transaction; a failure partway (e.g. a bad new item) rolls back the reversal too, leaving the original posted state intact.
- **`cancelPurchase`** / **`deletePurchase`**: the manual-payment guard, every system-payment deletion (`paymentService.deleteSystemPayment`, itself `@Transactional` but running inside the caller's transaction due to default `REQUIRED` propagation), ledger/GST/journal/PO-qty reversal, and the status flip or row delete — one transaction.
- **`PaymentService.create`** / **`createSystemPaymentForPurchase`**: allocation loop (which mutates every allocated `Purchase`/`Expense` row), ledger debit, cash-out entry, and accounting journal — one transaction.
- **`PaymentService.delete`** / **`deleteSystemPayment`**: reversing every allocation's target document, ledger/cash/journal reversal, and the `Payment` row delete — one transaction.
- **`DebitNoteService.post`**: journal posting, conditional stock reversal-movement, ledger entry, status flip, and GST sync — one transaction; note the ordering comment that GST sync must run *after* the status flip.
- **`DebitNoteService.cancel`**: journal reversal, conditional stock re-add, ledger reversal, GST-sync reversal, and status flip — one transaction.
- **`VoucherNumberService.next(...)`** runs with `REQUIRES_NEW` propagation (per its own Javadoc, referenced from `PurchaseService`/`PurchaseOrderService`/`PaymentService`/`DebitNoteService`) so a voucher number, once issued, is never rolled back even if the enclosing purchase/payment/note transaction later fails — preventing gaps from being reused, at the cost of a possible gap in the sequence on failure.
- **`AuditService.log(...)`** deliberately runs in the *same* transaction as its caller (default propagation) — an audit entry for an action that rolls back rolls back with it.

## 10. Error Handling

All exceptions are centralized in `backend/src/main/java/com/storehub/exception/GlobalExceptionHandler.java`, returning a uniform `ApiError { timestamp, status, error, message, path, fieldErrors }`:

| Exception | HTTP status | `fieldErrors` |
|---|---|---|
| `MethodArgumentNotValidException` (bean validation) | 400 | populated per invalid field |
| `PurchaseNotFoundException` | 404 | null |
| `PurchaseOrderNotFoundException` | 404 | null |
| `PaymentNotFoundException` | 404 | null |
| `NoteNotFoundException` | 404 | null |
| `BadRequestException` (all business-rule violations in this module) | 400 | null |
| `AccessDeniedException` (a `@PreAuthorize` failure) | 403 | null |
| any other `Exception` | 500, generic "An unexpected error occurred" message (full exception logged server-side) | null |

Frontend: `parseApiError(err, fallbackMessage)` (`frontend/src/utils/apiError.ts`, used throughout every Purchase page) reads `err.response.data.message` and `err.response.data.fieldErrors`, falling back to the caller-supplied message on a network/parse failure. Field-level errors are then wired to `invalid`/`fieldErrors.<field>` props on the relevant `Input`/`Select` in `PurchaseOrderForm`, `PurchaseBillForm`, `KacchiPurchaseForm`; all other pages surface only the top-level `message` via a `sonner` toast or an `Alert` banner.

## 11. Audit Flow

`AuditService.log(action, module, entityType, entityId, documentNumber, oldValue, newValue, description, storeId)` — `oldValue`/`newValue` are passed `null` everywhere in this module (no field-level diff is recorded, only a free-text `description`). Exact call sites:

- `PurchaseService.createPurchase` → `AuditAction.CREATE`, module `"PURCHASE"`, entityType `"Purchase"` — description names whether it was "saved as draft" or "created and posted".
- `PurchaseService.cancelPurchase` → `AuditAction.CANCEL`, `"PURCHASE"`/`"Purchase"` — "stock, ledger, accounting and GST effects reversed".
- `PaymentService.create` → `AuditAction.PAYMENT`, `"PURCHASE"`/`"Payment"`.
- `PaymentService.delete` → `AuditAction.CANCEL`, `"PURCHASE"`/`"Payment"` — "allocations and ledger/accounting effects reversed".
- `DebitNoteService.create` → `AuditAction.CREATE`, `"PURCHASE"`/`"DebitNote"`.
- `DebitNoteService.post` → `AuditAction.POST`, `"PURCHASE"`/`"DebitNote"`.
- `DebitNoteService.cancel` → `AuditAction.CANCEL`, `"PURCHASE"`/`"DebitNote"`.

**Not audit-logged** (confirmed by absence of any `AuditService` reference in the file): `PurchaseService.updatePurchase`, `PurchaseService.deletePurchase` (hard delete), `PurchaseService.postPurchaseChallan`, and the entire `PurchaseOrderService` (create/update/setStatus/cancel). `PaymentService.createSystemPaymentForPurchase`/`deleteSystemPayment` also do not write their own audit entries (the parent Purchase's CREATE/CANCEL entry is treated as covering them).

## 12. Important Side Effects

Triggered from `PurchaseService.applyPostingEffects` (a normal Purchase Bill create, or posting a Kacchi draft), and mirrored (reversed) in `reversePurchaseEffects` / the reversal half of `updatePurchase`:

- **Stock movement**: `inventoryService.applyMovement(productId, storeId, ±quantity, StockMovementType.PURCHASE | PURCHASE_CANCEL, ReferenceType.PURCHASE, purchaseId, reason)` — one call per line item.
- **Accounting journal posting**: `accountingService.postPurchaseJournal(purchase)` — debits `SystemAccountCode.PURCHASE` for the taxable amount, debits `INPUT_CGST`/`INPUT_SGST`/`INPUT_IGST` (if GST), credits `SUPPLIER_PAYABLE` for the full total (always books the full payable regardless of any immediate payment — the Payment's own journal clears it separately); reversed via `accountingService.reversePurchaseJournal` → `reverseJournal(VoucherType.PURCHASE, purchaseId, reason)`.
- **GST transaction sync**: `gstTransactionSyncService.syncPurchase(purchase)` / `.reversePurchase(purchase)` — feeds the GST reporting module; a Kacchi challan still calls this but the purchase's `gstReportingApplicable=false` flag is what actually excludes it from GSTR-1/GSTR-3B downstream.
- **Supplier ledger entry**: `ledgerService.recordPurchaseCredit(purchase)` (CREDIT, increases payable) / `ledgerService.reversePurchaseCredit(purchase, reason)`; plus `ledgerService.recordInputGstEntry(purchase)` / `.reverseInputGstEntry(purchase)` writing to `purchase_gst_entries`.
- **Voucher numbering**: `voucherNumberService.next(VoucherDocType.PURCHASE_CHALLAN | PURCHASE | PURCHASE_ORDER | PAYMENT | DEBIT_NOTE, date)` — assigned once, immediately after the initial save, in a separate `REQUIRES_NEW` transaction so the sequence never rolls back with the enclosing operation.
- **Purchase-Order received-quantity consumption**: `consumeOrderQuantities(items, ±1)` → `PurchaseOrderService.applyReceivedQuantityDelta` — only for lines carrying a `purchaseOrderItemId`; also drives `PurchaseOrderStatus` transitions to `PARTIALLY_RECEIVED`/`COMPLETED`.
- **Immediate payment**: if `paidAmount > 0` at posting time, `paymentService.createSystemPaymentForPurchase(purchase)` creates a `systemGenerated=true` `Payment` with its own ledger-debit, cash-out entry, and payment journal — this payment can only be reversed by reversing/deleting the *purchase* itself (`PaymentService.delete` explicitly refuses to delete a system-generated payment directly).
- **Debit Note posting** additionally: conditional stock reversal (`StockMovementType.PURCHASE_RETURN`, only if `stockImpact == STOCK_RETURN`), its own journal (`DEBIT SUPPLIER_PAYABLE` / `CREDIT PURCHASE` + tax lines), `ledgerService.recordDebitNoteEntry`, and `gstTransactionSyncService.syncDebitNote`.

## 13. Dependencies on Other Modules

- **Product / Inventory** — `ProductService.findProductOrThrow` (line validation, rejects inactive products), `InventoryService.applyMovement` (stock in/out on post/cancel/return, documented in the Inventory module).
- **Supplier (party)** — `SupplierService.findSupplierOrThrow`; every purchase, PO, payment, and debit note is scoped to exactly one `Supplier`; inactive suppliers are rejected for new documents.
- **Store** — `StoreService.findOrThrow`, `StoreAccessService.resolveEffectiveStoreId` (create-time, validated against the caller's own access) / `.resolveViewableStoreId` (list-time) / `.assertStoreAccess` (detail-view-time) — every Purchase/PO/Payment row is fixed to one store at creation (Multi-Store spec, referenced throughout the service Javadoc).
- **Accounting** — `AccountingService.postPurchaseJournal`/`reversePurchaseJournal`, `.postPaymentJournal`/`reversePaymentJournal`, `.postJournal`/`.reverseJournal` (used directly by `DebitNoteService` for the DEBIT_NOTE voucher type); `VoucherNumberService.next(...)` for every document number in this module (documented fully in the Accounting Core module).
- **GST** — `GstCalculationService.calculateLine` (per-line CGST/SGST/IGST split by `TaxMode`), `GstTransactionSyncService.syncPurchase`/`reversePurchase`/`syncDebitNote`/`reverseDebitNote`, `GstinValidator.isValid`.
- **Financial Year** — `FinancialYearService.resolveForDate(noteDate)` used by `DebitNoteService.create` to stamp `financialYearId`; `VoucherNumberService` (Accounting Core module) performs period/sequence validation as part of voucher-number issuance.
- **Expense** (Accounting/Expenses module) — `PaymentService` treats a credit Expense as an alternate allocation target alongside a Purchase Bill, reusing the same `PaymentAllocation` entity and FIFO logic (`ExpenseRepository.findOutstandingBySupplier`).

## 14. Key Operation Flows

**Create Purchase Order**
`PurchaseOrderForm` (submit) → `purchaseOrderApi.create(payload)` → `POST /api/purchase-orders` → `PurchaseOrderController.create` (`PERM_PURCHASE_CREATE`) → `PurchaseOrderRequest` (validated) → `PurchaseOrderService.create` → resolves supplier/store (rejects inactive), builds `PurchaseOrder(status=DRAFT)`, `applyItems` computes per-line taxable/GST/total and rejects duplicate products, `purchaseOrderRepository.save` → `voucherNumberService.next(PURCHASE_ORDER, orderDate)` → save again → `purchase_orders`/`purchase_order_items` rows → `PurchaseOrderResponse.fromEntity` → 201 → UI navigates to `PurchaseOrderDetail`.

**Create Purchase Bill (direct)**
`PurchaseBillForm` (submit, no `orderId`) → `purchaseApi.create(payload)` (`gstType`, `taxMode`, items, `paidAmount`) → `POST /api/purchases` → `PurchaseController.createPurchase` (`PERM_PURCHASE_CREATE`) → `PurchaseCreateRequest` (validated) → `PurchaseService.createPurchase` → GST/tax-mode + GSTIN checks → resolve supplier/store (active) → `purchaseOrder = null` → `type = PURCHASE`, `draft = false` → build `Purchase(status=COMPLETED, gstReportingApplicable=true)` → `applyItems` (per-line `GstCalculationService.calculateLine`, duplicate-product/discount guards) → `applyPayment` (paid/payable/paymentStatus) → save → `voucherNumberService.next(PURCHASE, purchaseDate)` → save → `applyPostingEffects`: `addStock` (`InventoryService.applyMovement` × items), `ledgerService.recordPurchaseCredit` + `recordInputGstEntry`, `accountingService.postPurchaseJournal`, `consumeOrderQuantities` (no-op, no PO link), `gstTransactionSyncService.syncPurchase`, and if `paidAmount>0` `paymentService.createSystemPaymentForPurchase` (its own ledger/journal) → `auditService.log(CREATE)` → `PurchaseResponse.fromEntity(saved, hasPayments)` → 201 → UI navigates to `PurchaseBillDetail`.

**Create Purchase Bill from Purchase Order**
`PurchaseOrderDetail`/`PurchaseOrders` "Create Bill" → navigate `/purchases/bills/new?orderId=<id>` → `PurchaseBillForm` loads the order (`purchaseOrderApi.getById`), pre-fills supplier and only lines with `remainingQuantity>0` (capped, tagged `purchaseOrderItemId`) → submit builds `PurchaseCreatePayload` with `purchaseOrderId` and each item's `purchaseOrderItemId` → `POST /api/purchases` → `PurchaseService.createPurchase` resolves `purchaseOrder = purchaseOrderService.findOrThrow(id)` and stores it on the `Purchase`; `applyItems` additionally validates each PO-linked line's quantity ≤ `orderItem.getRemainingQuantity()` → same posting cascade as above, but `consumeOrderQuantities(items, +1)` now calls `purchaseOrderService.applyReceivedQuantityDelta` per line → `PurchaseOrder`'s `receivedQuantity` increments and `recomputeStatus` may flip it to `PARTIALLY_RECEIVED`/`COMPLETED` → response includes `purchaseOrderId`/`purchaseOrderNumber`.

**Create Kacchi Purchase**
`KacchiPurchaseForm` → "Save as Draft" or "Post Now" → `buildPayload(saveAsDraft)` forces `gstType: 'GST'`, `transactionType: 'PURCHASE_CHALLAN'` → `purchaseApi.create` → `POST /api/purchases` → `PurchaseService.createPurchase` → `draft = request.isSaveAsDraft() && type==PURCHASE_CHALLAN` → if draft: `Purchase(status=DRAFT, gstReportingApplicable=false)` saved with a `PURCHASE_CHALLAN` voucher number, **`applyPostingEffects` is skipped entirely** — no stock/ledger/GST-log/journal/payment yet → 201, UI shows it in `KacchiPurchases` as `DRAFT`. Later, "Post" (`KacchiPurchaseDetail`/`KacchiPurchases`) → `purchaseApi.post(id)` → `POST /api/purchases/{id}/post` → `PurchaseController.postPurchaseChallan` (`PERM_PURCHASE_POST`) → `PurchaseService.postPurchaseChallan` guards `transactionType==PURCHASE_CHALLAN && status==DRAFT`, flips to `COMPLETED`, runs the full `applyPostingEffects` cascade (stock, ledger, GST log, journal, GST sync, system payment) — identical effects to a directly-posted bill, just deferred. If the form instead chose "Post Now"/"Save & Post" (or editing a draft with "Save & Post"), `saveAsDraft=false` skips the draft state and posts immediately in the same create call.

**Cancel a Purchase (and reversal)**
`PurchaseBills`/`PurchaseBillDetail`/`KacchiPurchases`/`KacchiPurchaseDetail` "Delete" (the UI uses hard delete, `purchaseApi.remove`, not the soft `cancel` endpoint — no page currently calls `purchaseApi.cancel`) → confirm dialog → `DELETE /api/purchases/{id}` → `PurchaseController.deletePurchase` (`PERM_PURCHASE_CANCEL`) → `PurchaseService.deletePurchase` → `findPurchaseOrThrow` → `guardNoManualPayments` (rejects if any **manual** payment allocation exists — "Delete those payments first") → `reversePurchaseEffects`:
  - If `status==COMPLETED`: `restoreStock` reverses every line (`StockMovementType.PURCHASE_CANCEL`, negative delta) — **Inventory reversal**.
  - If `status==DRAFT` (never posted): returns immediately — nothing to reverse.
  - Otherwise (posted, not draft): for every `PaymentAllocation` on this purchase, `paymentService.deleteSystemPayment` — reverses that system payment's ledger/cash/journal and deletes it (**system payments are the only ones allowed to exist here, per the manual-payment guard**); `ledgerService.reversePurchaseCredit` + `reverseInputGstEntry` (**Supplier ledger reversal**); `accountingService.reversePurchaseJournal` — **Accounting reversal**; `consumeOrderQuantities(items, -1)` decrements any linked PO's received quantity (`PurchaseOrderStatus` may revert from `COMPLETED`/`PARTIALLY_RECEIVED`); `gstTransactionSyncService.reversePurchase` — **GST resync**.
  - `purchaseRepository.delete(purchase)` removes the row (cascades to `purchase_items`).
  The soft `PATCH /api/purchases/{id}/cancel` (`PurchaseService.cancelPurchase`) performs the identical `reversePurchaseEffects` cascade but **keeps** the row, setting `status=CANCELLED` and `payableAmount=0`, and writes an `AuditAction.CANCEL` log (the hard-delete path writes **no** audit entry — see §11).

**Record a Payment against one or more bills**
`PaymentForm` → select supplier → `paymentApi.getOutstanding(supplierId)` → `GET /api/payments/outstanding/{supplierId}` → `PaymentController.getOutstanding` (`PERM_PAYMENT_VIEW`) → `PaymentService.getOutstandingForSupplier` → `purchaseRepository.findOutstandingBySupplier` + `expenseRepository.findOutstandingBySupplier` → `SupplierOutstandingResponse` renders as checkable rows → user optionally checks bills/expenses and edits amounts → submit → `paymentApi.create(payload)` → `POST /api/payments` → `PaymentController.create` (`PERM_PAYMENT_CREATE`) → `PaymentRequest` (validated) → `PaymentService.create` → resolve supplier/store → build `Payment(systemGenerated=false)` → save → `voucherNumberService.next(PAYMENT, paymentDate)` → `allocate(payment, request.getAllocations(), supplierId)`:
  - if explicit allocations given: validate each (supplier match, amount ≤ payable, sum ≤ payment amount), `applyAllocation`/`applyExpenseAllocation` per row (creates a `PaymentAllocation`, updates the target's `paidAmount`/`payableAmount`/`paymentStatus`);
  - else: FIFO across combined outstanding purchases+expenses sorted by date, applying `min(remaining, payable)` until exhausted; leftover becomes an unallocated on-account amount.
  → save → `ledgerService.recordPaymentDebit` + `recordCashEntryOut` → `accountingService.postPaymentJournal` → `auditService.log(PAYMENT)` → `PaymentResponse.fromEntity` → 201 → UI navigates to `PaymentDetail`, which lists each `PaymentAllocation` under "Applied Against".

**Create a Debit Note against a Bill**
`PurchaseBillDetail`/`KacchiPurchaseDetail` "Return" → navigate `/purchases/debit-notes/new?purchaseId=<id>` → `DebitNoteForm` loads the purchase (`purchaseApi.getById`), lists its items with an editable return-quantity per line → submit ("Save as Draft" or "Save and Post") → `debitNoteApi.create({ sourcePurchaseId, noteType, noteDate, stockImpact, reason, remarks, items, post })` → `POST /api/debit-notes` → `DebitNoteController.create` (`PERM_DEBIT_NOTE_CREATE`) → `DebitNoteCreateRequest` (validated) → `DebitNoteService.create` → `purchaseRepository.findById` or `PurchaseNotFoundException` → rejects if source is `CANCELLED` or still `DRAFT` → builds `DebitNote(status=DRAFT)` copying `gstType`/`gstReportingApplicable` from the source purchase, resolves `financialYearId` → for each item: validates the `purchaseItemId` belongs to this purchase and the return quantity ≤ `purchaseItem.quantity - alreadyReturned` (`debitNoteItemRepository.sumReturnedQuantity`), derives taxable/discount/CGST/SGST/IGST proportionally, appends a `DebitNoteItem` → totals summed → save → `voucherNumberService.next(DEBIT_NOTE, noteDate)` → save → `auditService.log(CREATE)` → if `request.isPost()`, immediately calls `post(saved.getId())`:
  - `post` guards `status==DRAFT` and source purchase not since cancelled → posts a journal (`DEBIT SUPPLIER_PAYABLE` / `CREDIT PURCHASE` + tax credit lines) via `accountingService.postJournal(VoucherType.DEBIT_NOTE, ...)` → if `stockImpact==STOCK_RETURN`, reverses stock per item (`StockMovementType.PURCHASE_RETURN`, negative delta) → `ledgerService.recordDebitNoteEntry` → `status=POSTED`, stamps `postedBy`/`postedAt` → `gstTransactionSyncService.syncDebitNote` (after the status flip) → `auditService.log(POST)`.
  → `DebitNoteResponse.fromEntity` → 201 → UI navigates to `DebitNoteDetail`, which offers a standalone Post button (for a saved draft) and a Cancel button (`debitNoteApi.cancel` → `DebitNoteService.cancel`, reversing journal/stock/ledger/GST-sync if it had been posted).

## 15. Manual Changes

- **Add a new field to `Purchase`**: add the column to `backend/src/main/java/com/storehub/entity/Purchase.java`, then thread it through `PurchaseCreateRequest`/`PurchaseUpdateRequest` (with validation annotations), `PurchaseService.createPurchase`/`.updatePurchase` (builder/setter), and `PurchaseResponse.fromEntity`. If it participates in GST or totals, also update `PurchaseService.applyItems`. Mirror the corresponding frontend edits in `frontend/src/types/purchase.ts`, `PurchaseBillForm.tsx`/`KacchiPurchaseForm.tsx` (and `PurchaseOrderForm.tsx` if the field also belongs on the order), and `purchaseApi.ts` if new query params are needed.
- **Add a new field to `PurchaseOrder`**: same pattern in `backend/src/main/java/com/storehub/entity/PurchaseOrder.java`, `PurchaseOrderRequest`, `PurchaseOrderService.create`/`.update`, `PurchaseOrderResponse.fromEntity`, `frontend/src/types/purchaseOrder.ts`, `PurchaseOrderForm.tsx`.
- **Change the cancel/reversal logic**: the single source of truth is `PurchaseService.reversePurchaseEffects` (private method) — both `cancelPurchase` (soft) and `deletePurchase` (hard) call it, so a fix there fixes both paths. `guardNoManualPayments` is the gate that decides whether a reversal is even allowed; changing what counts as "blocking" payment lives there. The Debit Note equivalent is the reversal half of `DebitNoteService.cancel`; the Payment equivalent is `PaymentService.reverseAndRemove`.
- **Add a new `PurchaseStatus`**: edit `backend/src/main/java/com/storehub/entity/PurchaseStatus.java`, then update every `switch`/`if` that branches on status — `PurchaseService` (`applyPostingEffects` gating, `updatePurchase`'s old/new-status branches, `cancelPurchase`/`reversePurchaseEffects`'s `COMPLETED`/`DRAFT` checks), `PurchaseRepository.search`'s implicit status filter, and the frontend `PurchaseStatus` union type (`frontend/src/types/purchase.ts`) plus every page's `statusVariant()`/status-`Select` options (`PurchaseBills.tsx`, `KacchiPurchases.tsx`, `KacchiPurchaseDetail.tsx`).
- **Change payment allocation logic**: entirely inside `PaymentService.allocate` (private) — the explicit-allocation branch and the FIFO fallback branch are both there, along with `applyAllocation`/`applyExpenseAllocation` for the actual balance mutation. The reversal counterpart (undoing an allocation) is `PaymentService.reverseAndRemove`. Any change to what counts as "outstanding" starts in `PurchaseRepository.findOutstandingBySupplier`/`ExpenseRepository.findOutstandingBySupplier`.
- **Other modules affected by a Purchase-lifecycle change**:
  - **Inventory** — any change to when/how much stock moves must be mirrored in both `PurchaseService.addStock`/`restoreStock` (bill) and `DebitNoteService.post`/`cancel`'s `inventoryService.applyMovement` calls (return), keeping `StockMovementType` (`PURCHASE`, `PURCHASE_CANCEL`, `PURCHASE_RETURN`, `ADJUSTMENT`) consistent with `ReferenceType` so inventory history stays traceable back to the right document.
  - **Accounting** — journal shape changes belong in `AccountingService.postPurchaseJournal`/`postPaymentJournal` (Purchase/Payment) or `DebitNoteService.post`'s inline `JournalLine` list (Debit Note); a reversal always goes through `accountingService.reverseJournal`/`reversePurchaseJournal`/`reversePaymentJournal`, which look up the original journal by `(voucherType, voucherId)` — never hand-roll a reversing entry elsewhere.
  - **GST** — a resync is required (`gstTransactionSyncService.syncPurchase`/`reversePurchase`/`syncDebitNote`/`reverseDebitNote`) any time taxable amounts, GST split, or `gstReportingApplicable` eligibility changes, and must stay ordered *after* any status flip that `GstReportingEligibility` reads.
  - **Supplier ledger** — `ledgerService.recordPurchaseCredit`/`recordInputGstEntry` (bill), `recordPaymentDebit`/`recordCashEntryOut` (payment), `recordDebitNoteEntry` (note) and their `reverse*` counterparts must stay paired 1:1 with the accounting-journal change, since `SupplierLedgerEntryRepository.getOutstandingForSupplier` is what the Payment module's FIFO allocation and the Payables report both read from.
