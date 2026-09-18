# GST Module

## 1. Overview

The GST module has three layers:

1. **GST calculation** — `GstCalculationService.calculateLine(...)` is the single central engine that every financial module (Sale, Purchase, Expense; Kacchi variants reuse Sale/Purchase directly) must call to compute a line's taxable value, GST amount, and CGST/SGST/IGST split. Before this service existed, `SaleService`, `PurchaseService`, and `ExpenseService` each carried an independently-maintained copy of the same arithmetic; the spec explicitly calls this out as a "one central engine" requirement. The caller is responsible for subtracting discount before calling (`taxableAmount` = gross − discount) and for zeroing the rate when the parent document is NON_GST or the item's tax treatment is EXEMPT/NIL_RATED/ZERO_RATED (there is also an item-taxability-aware overload that does this zeroing itself).

2. **GST transaction sync (`gst_transactions`)** — `GstTransactionSyncService` is the *only* place that writes to the `gst_transactions` table. It is a **normalized, synced mirror/audit log of already-computed GST data** on posted Sale/Purchase/CreditNote/DebitNote/Expense records — architecturally the same pattern as `StockHistory` mirroring posted stock movements. It never recalculates tax: every amount is copied verbatim from the source transaction, which remains the single source of truth. A cancelled/reversed source transaction flips its `GstTransaction` row's `status` to `REVERSED` in place (never deleted), so GST reports (which only read `ACTIVE` rows) stop counting it while the record stays auditable — mirroring the `JournalHeader` reversal convention.

3. **GST reporting** — `GstReportingService` reads *only* from `gst_transactions` (already scoped to `ACTIVE` + synced-at-post-time eligibility) or from `SaleItem`/`PurchaseItem` with the same explicit filters, to produce the full statutory report suite: GSTR-1 (B2B/B2C/HSN), Purchase GST Report (with ITC eligibility), GSTR-3B summary, Output GST report, Input GST report (Purchase + Expense), HSN Summary, Tax Rate Summary, GST Liability, and Reconciliation. GST tax calculation is deliberately kept separate from GST reporting eligibility: a Kacchi transaction still gets a fully correct CGST/SGST/IGST split from `GstCalculationService`, but is structurally excluded from ever having a `GstTransaction` row (see §4 `GstReportingEligibility`), so it can never appear in any report. All these reports are explicitly documented as return **preparation aids** built from StoreHub's own data — none of it is an actual GSTN filing integration.

**Business GST config vs per-store GSTIN override:** `BusinessGstConfig` is a business-wide singleton (always id `1`) holding the seller's own GST registration (legal name, GSTIN, PAN, address, state, pincode) — the "seller state" anchor `GstCalculationService.suggestTaxMode` compares a party's state against. Multi-Store adds a per-store override: `Store.gstin` (and related fields), resolved through `StoreService.resolveGstContext`, which returns the store's own GSTIN/state when configured, and falls back to the business-wide config otherwise (see §4/§14).

## 2. Frontend

### Files and Components

| File | Purpose |
|---|---|
| `frontend/src/pages/gst/GstReportsHub.tsx` | Landing page linking to all 9 GST reports (static `SECTIONS` array of cards). |
| `frontend/src/pages/gst/Gstr1Report.tsx` | GSTR-1 — tabs for B2B, B2C, HSN Summary, and a permanently-empty Credit/Debit Notes tab. |
| `frontend/src/pages/gst/PurchaseGstReport.tsx` | Paginated Purchase GST Report with ITC-eligibility badge per row. |
| `frontend/src/pages/gst/Gstr3bSummary.tsx` | GSTR-3B summary (Outward Supplies / ITC / Net Liability), by return period (month picker). |
| `frontend/src/pages/gst/OutputGstReport.tsx` | Paginated Output GST report (Sale side), by date range. |
| `frontend/src/pages/gst/InputGstReport.tsx` | Paginated Input GST report (Purchase + Expense), by date range, with ITC badge. |
| `frontend/src/pages/gst/HsnSummaryReport.tsx` | HSN Summary — tabs for Outward/Inward, by date range. |
| `frontend/src/pages/gst/TaxRateSummaryReport.tsx` | Tax Rate Summary — tabs for Outward/Inward, by date range. |
| `frontend/src/pages/gst/GstLiabilityReport.tsx` | Output/Input/Net liability table, by return period. |
| `frontend/src/pages/gst/GstReconciliation.tsx` | Reconciliation table (MATCHED/MISMATCHED/MISSING/DUPLICATE counts + row list), by date range. |
| `frontend/src/pages/masters/BusinessGstConfigMaster.tsx` | View/edit form for the singleton `BusinessGstConfig` (Masters section). |
| `frontend/src/api/gstReportApi.ts` | Typed API client for all 9 report endpoints. |
| `frontend/src/api/mastersApi.ts` (`businessGstConfigApi`, lines ~204-207) | `GET`/`PUT` client for Business GST Config. |
| `frontend/src/types/gstReport.ts` | TypeScript interfaces mirroring every report DTO. |

### Frontend Flow

**Viewing a report:** each report page holds its own `fromDate`/`toDate` (default: first day of current month → today, via `firstDayOfMonthIso()`/`todayIso()` in `./gstFormat`) or `returnPeriod` (default: `currentReturnPeriod()`, an HTML `<input type="month">`) as local React state, fetches on mount via `useEffect`, and re-fetches on an explicit "Apply" button click (not on every keystroke). Paginated reports (Purchase, Output, Input) also carry a `page` state reset to `0` on Apply and re-fetched via a `page`-only `useEffect`. Errors go through `parseApiError(...)` and a `toast.error(...)`; there is no page-level field-error surfacing since these are all read-only GET reports.

**IMPORTANT finding:** none of the 9 frontend report pages actually pass a `storeId` — `gstReportApi.ts`'s param objects never include it, even though every backend report endpoint accepts an optional `storeId` query parameter and every `GstReportingService` method takes a `storeId` argument (see §3/§4). `Store.resolveViewableStoreId` on the backend therefore always resolves to `null` (all-store aggregate) unless the current user only has access to exactly one store, or has a `currentStore` set — see §4 Services. There is no store-selector UI control anywhere under `frontend/src/pages/gst/`.

**Editing Business GST Config:** `BusinessGstConfigMaster.tsx` loads the config via `businessGstConfigApi.get()` (any authenticated user may view) and the active `State` list. Only `user?.role === 'ADMIN'` sees an enabled form and a Save button (all inputs are `disabled={!isAdmin}` otherwise, matching the backend's `@PreAuthorize("hasAuthority('PERM_GST_CONFIG')")` gate on `PUT`). On submit it does a client-side required check on `legalName` only, then calls `businessGstConfigApi.update(...)`; on a 400 it surfaces `parsed.fieldErrors` per-field (`invalid={!!fieldErrors.gstin}` + inline `<p>` message) and `parsed.message` in a top-level `Alert`, per the codebase's field-error surfacing convention.

## 3. API Calls

| Function | Method | URL | Request shape | Response shape |
|---|---|---|---|---|
| `gstReportApi.gstr1` | GET | `/api/gst-reports/gstr1` | `fromDate?, toDate?, returnPeriod?` (no `storeId` sent by frontend; backend accepts `storeId?`) | `Gstr1Response` |
| `gstReportApi.purchaseGstReport` | GET | `/api/gst-reports/purchase` | `fromDate?, toDate?, returnPeriod?, page=0, size=20` (backend also accepts `storeId?`) | `PurchaseGstReportResponse` |
| `gstReportApi.gstr3b` | GET | `/api/gst-reports/gstr3b` | `returnPeriod` (required; backend also accepts `storeId?`) | `Gstr3bResponse` |
| `gstReportApi.outputGst` | GET | `/api/gst-reports/output` | `fromDate?, toDate?, page=0, size=20` (backend also accepts `storeId?`) | `GstReportListResponse` |
| `gstReportApi.inputGst` | GET | `/api/gst-reports/input` | `fromDate?, toDate?, page=0, size=20` (backend also accepts `storeId?`) | `GstReportListResponse` |
| `gstReportApi.hsnSummary` | GET | `/api/gst-reports/hsn` | `fromDate?, toDate?` (backend also accepts `storeId?`) | `HsnSummaryReportResponse` |
| `gstReportApi.taxRateSummary` | GET | `/api/gst-reports/tax-rate` | `fromDate?, toDate?` (backend also accepts `storeId?`) | `TaxRateSummaryReportResponse` |
| `gstReportApi.liability` | GET | `/api/gst-reports/liability` | `returnPeriod` (required; backend also accepts `storeId?`) | `GstLiabilityResponse` |
| `gstReportApi.reconciliation` | GET | `/api/gst-reports/reconciliation` | `fromDate?, toDate?` (backend also accepts `storeId?`) | `ReconciliationResponse` |
| `businessGstConfigApi.get` | GET | `/api/masters/business-gst-config` | none | `BusinessGstConfigResponse` |
| `businessGstConfigApi.update` | PUT | `/api/masters/business-gst-config` | `BusinessGstConfigRequest` body | `BusinessGstConfigResponse` |
| (Store module, used for GST context) | GET | `/api/masters/stores/{id}/gst-context` | path `id` | `StoreGstContextResponse` |

## 4. Backend

### Controllers

**`GstReportController`** (`@RequestMapping("/api/gst-reports")`) — no `@PreAuthorize` on any method (protected only by the global `anyRequest().authenticated()` rule; see §7/§8):

| Endpoint | Params | Returns |
|---|---|---|
| `GET /gstr1` | `fromDate?, toDate?, returnPeriod?, storeId?` | `Gstr1Response` |
| `GET /purchase` | `fromDate?, toDate?, returnPeriod?, storeId?, page=0, size=20` (sorted `voucherDate ASC`) | `PurchaseGstReportResponse` |
| `GET /gstr3b` | `returnPeriod` (required), `storeId?` | `Gstr3bResponse` |
| `GET /output` | `fromDate?, toDate?, storeId?, page=0, size=20` (sorted `voucherDate ASC`) | `GstReportListResponse` |
| `GET /input` | `fromDate?, toDate?, storeId?, page=0, size=20` (sorted `voucherDate ASC`) | `GstReportListResponse` |
| `GET /hsn` | `fromDate?, toDate?, storeId?` | `HsnSummaryReportResponse` |
| `GET /tax-rate` | `fromDate?, toDate?, storeId?` | `TaxRateSummaryReportResponse` |
| `GET /liability` | `returnPeriod` (required), `storeId?` | `GstLiabilityResponse` |
| `GET /reconciliation` | `fromDate?, toDate?, storeId?` | `ReconciliationResponse` |

**`BusinessGstConfigController`** (`@RequestMapping("/api/masters/business-gst-config")`) — class comment: "Any authenticated user may VIEW the business's GST configuration (Sales/Purchase forms need it for tax-mode suggestion); only ADMIN may edit it":

| Endpoint | Auth | Returns |
|---|---|---|
| `GET` | any authenticated user | `BusinessGstConfigResponse` |
| `PUT` | `@PreAuthorize("hasAuthority('PERM_GST_CONFIG')")`, `@Valid @RequestBody BusinessGstConfigRequest` | `BusinessGstConfigResponse` |

**`StoreController`** (in Store module, but the one relevant endpoint) — `GET /api/masters/stores/{id}/gst-context`, unguarded (matches other Store GETs), calls `storeService.resolveGstContext(id)` → `StoreGstContextResponse`.

### DTOs

- **`BusinessGstConfigRequest`** — `legalName` (`@NotBlank`, `@Size(max=200)`), `tradeName` (`@Size(max=200)`), `gstin` (no annotation — validated manually in the service via `GstinValidator.isValid`), `pan` (`@Size(max=10)`), `address`, `stateId`, `pincode` (`@Size(max=10)`).
- **`BusinessGstConfigResponse`** — `id, legalName, tradeName, gstin, pan, address, stateId, stateName, stateCode, pincode, configured (boolean, true iff legalName and gstin are both non-blank), createdAt, updatedAt`.
- **`GstTransactionRow`** — one report row built directly from a `GstTransaction`: `gstTransactionId, sourceTransactionType, sourceTransactionId, voucherNumber, voucherDate, partyType, partyId, partyName, partyGstin, placeOfSupplyStateCode, b2b, itcEligible (Boolean, meaningful only for PURCHASE rows; null for SALE), taxableAmount, cgstAmount, sgstAmount, igstAmount, totalTax, totalValue, returnPeriod`.
- **`GstSummaryTotals`** — `taxableAmount, cgstAmount, sgstAmount, igstAmount, totalTax, totalValue, transactionCount`; has a static `.zero()` factory used when an aggregate query returns no rows.
- **`Gstr1Response`** — `returnPeriod, fromDate, toDate, b2bTransactions[], b2cTransactions[], creditNotes[] (always empty — no Credit/Debit Note voucher type exists structurally in the GSTR-1 aggregation path), debitNotes[] (always empty), hsnSummary[], totals`.
- **`PurchaseGstReportResponse`** — `returnPeriod, fromDate, toDate, transactions (PagedResponse<GstTransactionRow>), totals, eligibleItcTotal`.
- **`Gstr3bResponse`** — `returnPeriod, outwardSupplies, inputTaxCredit, netLiability, note` (a fixed disclaimer string that this is not a GSTN filing integration).
- **`GstReportListResponse`** — shared shape for Output/Input GST report pages: `fromDate, toDate, transactions (PagedResponse<GstTransactionRow>), totals`.
- **`HsnSummaryReportResponse`** — `fromDate, toDate, outward[], inward[]` of `HsnSummaryRow{ hsnCode, description, unit, totalQuantity, taxableAmount, cgstAmount, sgstAmount, igstAmount, totalValue }`.
- **`TaxRateSummaryReportResponse`** — `fromDate, toDate, outward[], inward[]` of `TaxRateSummaryRow{ gstPercent, taxableAmount, cgstAmount, sgstAmount, igstAmount, totalValue }`.
- **`GstLiabilityResponse`** — `returnPeriod, outputCgst/Sgst/Igst/Total, inputCgst/Sgst/Igst/Total, netCgst/Sgst/Igst/Total`.
- **`ReconciliationResponse`** — `fromDate, toDate, rows[], matchedCount, mismatchedCount, missingCount, duplicateCount`.
- **`ReconciliationRow`** — `sourceTransactionType, sourceTransactionId, voucherNumber, voucherDate, status ("MATCHED"|"MISMATCHED"|"MISSING", string not enum on the backend — frontend adds "DUPLICATE" as a 4th possible type value but the backend never actually emits a per-row DUPLICATE status; duplicates are only ever a top-level `duplicateCount`), sourceTaxableAmount, reportedTaxableAmount (nullable), sourceTotalTax, reportedTotalTax (nullable), remarks`.
- **`StoreGstContextResponse`** — `storeId, gstin, legalName, stateCode, source ("STORE" | "BUSINESS")`.
- **`PagedResponse<T>`** — generic `content[], page, size, totalElements, totalPages` (a `.fromPage(Page<T>)` factory).

### Services

**`GstCalculationService`** — the exact split logic in `calculateLine(taxableAmount, gstPercent, taxMode, taxTreatment)`:
1. `effectivePercent` = `gstPercent` (nvl'd to `ZERO` if null) **unless** `taxTreatment` is non-null and not `TAXABLE` (i.e. EXEMPT/NIL_RATED/ZERO_RATED), in which case `effectivePercent = ZERO`. `taxTreatment == null` is treated as `TAXABLE` (legacy/unset items are not silently zeroed).
2. `gstAmount = taxableAmount * effectivePercent / 100`, rounded `HALF_UP` to 2 decimal places (via `BigDecimal.divide(..., 2, RoundingMode.HALF_UP)`), or `ZERO` if `effectivePercent.signum() <= 0`.
3. Split: if `gstAmount.signum() > 0` and `taxMode == TaxMode.INTER_STATE` → `igst = gstAmount`, `cgst = sgst = 0`. Otherwise (`INTRA_STATE`, the CGST+SGST path) → `cgst = gstAmount / 2` rounded `HALF_UP` to 2 decimals, `sgst = gstAmount - cgst` (so any odd-paisa remainder from the halving lands on `sgst`, not lost).
4. Returns a `LineTaxResult{ taxableAmount, gstPercent (the *effective* rate used), gstAmount, cgstAmount, sgstAmount, igstAmount, totalAmount = taxableAmount + gstAmount }`.
- A 3-arg overload (`taxTreatment` omitted) delegates to the 4-arg one with `TaxTreatment.TAXABLE`.
- `suggestTaxMode(sellerState, partyState)` returns `null` if either is null/blank (so callers keep their existing default rather than guessing), else `INTRA_STATE` if the trimmed, case-insensitive states match, else `INTER_STATE`. This is a **suggestion only** — it never overrides an explicitly chosen tax mode (the code comment calls out exports/SEZ as legitimate cases needing a human decision).
- Called from: `SaleService` (line ~452), `PurchaseService` (line ~401), `ExpenseService` (line ~225) — each per line-item, in its own item-application loop.

**`GstReportingEligibility`** — the single source of truth for "does this transaction belong in GST return reporting" (kept deliberately separate from tax calculation):
- `Sale` — eligible iff `gstReportingApplicable == TRUE` **AND** `status == SaleStatus.COMPLETED`.
- `Purchase` — eligible iff `gstReportingApplicable == TRUE` **AND** `status == PurchaseStatus.COMPLETED`.
- `CreditNote` — eligible iff `gstReportingApplicable == TRUE` (copied verbatim from the source Sale at note creation, never re-derived) **AND** `status == NoteStatus.POSTED`.
- `DebitNote` — eligible iff `gstReportingApplicable == TRUE` **AND** `status == NoteStatus.POSTED`.
- `Expense` — eligible iff `taxMode != null` (GST was actually applied) **AND** `status == ExpenseStatus.POSTED`. (`itcEligible` on the expense controls whether it's counted as ITC in reports — see `GstTransactionSyncService.syncExpense` — it does not gate reporting inclusion itself.)
- Explicitly never inferred from `taxAmount > 0` or `transactionType == SALE` alone: a Kacchi transaction (SALE_CHALLAN/PURCHASE_CHALLAN) calculates GST in full via `GstCalculationService` but must never appear in reporting; a DRAFT transaction — Kacchi or not — has no accounting/stock effects yet and must never appear either.

**`GstTransactionSyncService`** — the ONLY writer to `gst_transactions`. Every sync/reverse pair:

| Sync method | Reverse method | Guard (no-op if false) | Caller (module) |
|---|---|---|---|
| `syncSale(Sale)` | `reverseSale(Sale)` | `GstReportingEligibility.isEligibleForGstReporting(sale)` | `SaleService.applyPostingEffects` (create/post, line 220) and re-post after edit (line 303); `reverseSale` called from `SaleService` cancel/void flow (line 270) and `reverseSaleEffects` (line 378) |
| `syncPurchase(Purchase)` | `reversePurchase(Purchase)` | eligibility check on the purchase | `PurchaseService.applyPostingEffects` (line 187) and re-post after edit (line 265); `reversePurchase` called on cancel (line 230) and `reversePurchaseEffects` (line 333) |
| `syncCreditNote(CreditNote)` | `reverseCreditNote(CreditNote)` | eligibility check on the note | `CreditNoteService` post flow (line 233); reverse on cancel (line 266) |
| `syncDebitNote(DebitNote)` | `reverseDebitNote(DebitNote)` | eligibility check on the note | `DebitNoteService` post flow (line 222); reverse on cancel (line 254) |
| `syncExpense(Expense)` | `reverseExpense(Expense)` | eligibility check on the expense | `ExpenseService` post flow (line 302); reverse on cancel (line 328) |

Sync-method mechanics (identical shape across all five):
1. `findOrNew(voucherType, sourceId)` — looks up an existing `GstTransaction` by `(sourceTransactionType, sourceTransactionId)` (the row is unique on that pair), or builds a fresh one. This makes re-syncing idempotent — editing and re-posting a transaction *refreshes* its existing row rather than creating a duplicate.
2. Copies voucher number/date, party type/id/name, the party's GSTIN, `placeOfSupplyStateCode = GstinValidator.extractStateCode(gstin)`, and `b2b = GstinValidator.isValid(gstin)` — **except** `syncExpense`, where `b2b` is instead set directly to `expense.isItcEligible()` (the expense's own explicit ITC flag, not a GSTIN-validity proxy), so ITC-side reports only ever include what the user explicitly marked eligible.
3. Copies amounts verbatim via `applyAmounts(...)`: `taxableAmount, cgstAmount, sgstAmount, igstAmount` (each null-coalesced to `ZERO`), `totalTax = cgst+sgst+igst`, `totalValue`.
4. Sets `returnPeriod = voucherDate.format("yyyy-MM")`, `status = ACTIVE`, `createdBy` = current user's full name (or `"System"` if no authenticated principal), then `save(txn)`.

Reverse-method mechanics (identical shape across all five): look up the existing row by `(sourceTransactionType, sourceTransactionId)`; if present, flip `status` to `REVERSED` and save; if absent (never-synced, e.g. Kacchi/ineligible), it's a no-op.

**`GstReportingService`** — 9 public report methods, all `@Transactional(readOnly = true)`, all first resolve `resolvedStoreId = storeAccessService.resolveViewableStoreId(currentUser, storeId)`:

1. `gstr1(fromDate, toDate, returnPeriod, storeId)` — pulls all `ACTIVE` SALE `GstTransaction`s via `findActiveForSummary`, splits into `b2bTransactions`/`b2cTransactions` by each row's `isB2b()`, adds HSN summary from `saleItemRepository.hsnSummary(...)`, and totals via `gstTransactionRepository.aggregateTotals(SALE, ...)`. `creditNotes`/`debitNotes` are always empty lists (no such voucher type feeds this aggregation).
2. `taxRateSummary(fromDate, toDate, storeId)` — `outward` from `saleItemRepository.taxRateSummary(...)`, `inward` from `purchaseItemRepository.taxRateSummary(...)` — reads line-item data directly, not `gst_transactions`.
3. `hsnSummary(fromDate, toDate, storeId)` — same shape, `saleItemRepository.hsnSummary(...)` / `purchaseItemRepository.hsnSummary(...)`.
4. `purchaseGstReport(fromDate, toDate, returnPeriod, storeId, pageable)` — paginated `gstTransactionRepository.search(PURCHASE, ...)`, each row mapped with `itcEligible = txn.isB2b()`; totals via `aggregateTotals(PURCHASE, ...)`; `eligibleItcTotal` via `aggregateB2bTotals(PURCHASE, ...).getTotalTax()`.
5. `outputGstReport(fromDate, toDate, storeId, pageable)` — paginated `search(SALE, null returnPeriod, ...)`, `itcEligible` left `null` on every row (not meaningful for sales); totals via `aggregateTotals(SALE, ...)`.
6. `inputGstReport(fromDate, toDate, storeId, pageable)` — paginated `searchByTypes([PURCHASE, EXPENSE], ...)` (the "generic ITC-side view" combining both voucher types, unlike `purchaseGstReport` which is Purchase-only); `itcEligible = txn.isB2b()`; totals via `aggregateTotalsByTypes([PURCHASE, EXPENSE], ...)`.
7. `gstr3bSummary(returnPeriod, storeId)` — `outward = aggregateTotals(SALE, returnPeriod, null, null, ...)`, `itc = aggregateB2bTotalsByTypes([PURCHASE, EXPENSE], returnPeriod, ...)`, `net = netOf(outward, itc)` (component-wise subtraction, `transactionCount` taken from `outward`'s count).
8. `gstLiability(returnPeriod, storeId)` — same underlying `outward`/`input` aggregates as `gstr3bSummary`, laid out as explicit `outputCgst/Sgst/Igst/Total`, `inputCgst/Sgst/Igst/Total`, and `netCgst/Sgst/Igst/Total = output - input` per component.
9. `reconciliation(fromDate, toDate, storeId)` — for each of SALE and PURCHASE: pulls every source-side transaction eligible for reconciliation (`saleRepository.findEligibleForReconciliation` / `purchaseRepository.findEligibleForReconciliation` — raw `Object[]` rows of id/voucherNumber/voucherDate/taxableAmount/cgst/sgst/igst) and every currently-`ACTIVE` `GstTransaction` of that type, then for each source row: **MISSING** if no active `GstTransaction` row exists for that source id ("Eligible for GST reporting but no active GstTransaction row was found — re-sync needed"); **MATCHED** if `taxableAmount` and `totalTax` both compare equal (`BigDecimal.compareTo == 0`) to the reported row; **MISMATCHED** otherwise ("Amounts on the source transaction differ from the synced GST reporting row"). `duplicateCount` is computed separately via `countActiveBySource` (groups `ACTIVE` rows by `sourceTransactionId`, counts groups with `>1` row) — documented as a **defensive check only**, since the DB `uk_gst_transaction_source` unique constraint on `(source_transaction_type, source_transaction_id)` makes a true duplicate structurally impossible.

**`BusinessGstConfigService`** — singleton row always at id `1` (`findOrCreate()`):
- `get()` — safe to call before any ADMIN has configured anything; returns an empty/unconfigured row rather than 404, so callers like the suggest-tax-mode flow never need a special branch.
- `update(request)` — validates `gstin` via `GstinValidator.isValid` if non-blank (throws `BadRequestException("GSTIN '<value>' is not a valid 15-character GSTIN")` otherwise), resolves `State` via `stateService.findOrThrow(stateId)` if `stateId` given, blank-to-null normalizes `tradeName/gstin/pan/address/pincode`, saves, and logs an audit entry (see §11).

**`StoreService.resolveGstContext(Long id)`** (the only method in scope from this file): finds the `Store`, and if `StringUtils.hasText(store.getGstin())`, returns a `StoreGstContextResponse` built from the store's own `gstin`/`legalName` (fallback to `storeName`)/`state.code`, `source = "STORE"`. Otherwise calls `businessGstConfigService.get()` and returns a response built from the business config's `gstin`/`legalName`/`stateCode`, `source = "BUSINESS"`.

### Repository

**`GstTransactionRepository`**:
- `findBySourceTransactionTypeAndSourceTransactionId(type, id)` → `Optional<GstTransaction>` — the sync/reverse lookup key.
- `findAllBySourceTransactionTypeAndSourceTransactionId(type, id)` → `List<GstTransaction>`.
- `search(type, returnPeriod, fromDate, toDate, b2b, storeId, pageable)` → `Page<GstTransaction>` — `ACTIVE` only; **storeId-filtered** (`:storeId IS NULL OR g.store.id = :storeId`).
- `findActiveForSummary(type, returnPeriod, fromDate, toDate, storeId)` → `List<GstTransaction>` — same filter set as `search` minus `b2b`/paging; **storeId-filtered**.
- `aggregateTotals(type, returnPeriod, fromDate, toDate, storeId)` → `List<Object[]>` (single summed row); **storeId-filtered**.
- `aggregateB2bTotals(type, returnPeriod, fromDate, toDate, storeId)` → same shape restricted to `b2b = true`; **storeId-filtered**.
- `searchByTypes(types, returnPeriod, fromDate, toDate, storeId, pageable)` → `Page<GstTransaction>`, `sourceTransactionType IN :types`; **storeId-filtered**.
- `aggregateTotalsByTypes(types, returnPeriod, fromDate, toDate, storeId)` → summed row across a type set; **storeId-filtered**.
- `aggregateB2bTotalsByTypes(types, returnPeriod, fromDate, toDate, storeId)` → same, `b2b = true` only; **storeId-filtered**.
- `countActiveBySource(type)` → `List<Object[]>` of `(sourceTransactionId, count)` for duplicate detection — **not** storeId-filtered (reconciliation duplicate check is deliberately store-agnostic).
- `findBySourceTransactionTypeAndStatus(type, status)` → `List<GstTransaction>` — used by reconciliation to build the `activeById` lookup map.

**`BusinessGstConfigRepository`** — plain `JpaRepository<BusinessGstConfig, Long>`, no custom methods.

## 5. Database

### Tables

- **`gst_transactions`** — backs `GstTransaction`. Unique constraint `uk_gst_transaction_source` on `(source_transaction_type, source_transaction_id)`. Columns: `id, source_transaction_type, source_transaction_id, store_id (FK, nullable — null for pre-migration data), voucher_number, voucher_date, party_type, party_id, party_name, party_gstin, place_of_supply_state_code, b2b, taxable_amount, cgst_amount, sgst_amount, igst_amount, total_tax, total_value, return_period, status, created_by, created_at, updated_at`.
- **`business_gst_config`** — backs `BusinessGstConfig`. Singleton (`id` always `1`, no auto-generation). Columns: `id, legal_name, trade_name, gstin, pan, address, state_id (FK), pincode, created_at, updated_at`.

### Relationships

- `GstTransaction.sourceTransactionType` (enum `VoucherType`: SALE/PURCHASE/CREDIT_NOTE/DEBIT_NOTE/EXPENSE) + `sourceTransactionId` together identify the source `Sale`/`Purchase`/`CreditNote`/`DebitNote`/`Expense` row — a polymorphic reference by convention, not a JPA foreign key/join (no `@ManyToOne` to any of those entities).
- `GstTransaction.store` — `@ManyToOne` (lazy) to `Store`, nullable (Multi-Store; null for pre-migration data).
- `BusinessGstConfig.state` — `@ManyToOne` (lazy) to `State` (the state's own `code` is exposed as the business's GST state code — no duplicated state-code column).
- `Store.gstin`/state fields (Store module) are the per-store override consulted by `StoreService.resolveGstContext` before falling back to `BusinessGstConfig`.

## 6. Validation

**`GstinValidator`** (`backend/src/main/java/com/storehub/util/GstinValidator.java`) — the single reusable GSTIN validator for the whole app:

```java
private static final Pattern GSTIN_PATTERN =
        Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");
```

`isValid(gstin)` trims and uppercases before matching (null → `false`). A 15-character GSTIN is: 2-digit state code + 10-character PAN + 1-digit entity code + literal `Z` + 1 checksum character. `extractStateCode(gstin)` returns the first 2 characters of a valid GSTIN (its embedded state code) or `null` if invalid — used to populate `GstTransaction.placeOfSupplyStateCode` and to classify a party as B2B/B2C.

`BusinessGstConfigService.update(...)` calls `GstinValidator.isValid(...)` on a non-blank `gstin` and throws `BadRequestException` with the exact message `"GSTIN '<value>' is not a valid 15-character GSTIN"` if it fails. `BusinessGstConfigRequest.gstin` itself carries no Bean Validation annotation — the format check happens in the service, not via `@Pattern`.

`BusinessGstConfigRequest` bean validation: `legalName` `@NotBlank` + `@Size(max=200)`; `tradeName` `@Size(max=200)`; `pan` `@Size(max=10)`; `pincode` `@Size(max=10)`.

## 7. Authentication / Authorization

- All `/api/gst-reports/**` endpoints and `GET /api/masters/business-gst-config` require only a valid authenticated session (global rule `anyRequest().authenticated()` in `SecurityConfig`, `/api/auth/**` and `/actuator/health` are the only `permitAll()` paths) — **no** `@PreAuthorize` gate is present on any `GstReportController` method or on the config `GET`.
- `PUT /api/masters/business-gst-config` requires `@PreAuthorize("hasAuthority('PERM_GST_CONFIG')")`.
- `GET /api/masters/stores/{id}/gst-context` requires only authentication (no `@PreAuthorize`).

## 8. Permissions

From `RolePermissions.java` / `Permission.java` (module tag `"GST"`): `GST_VIEW`, `GST_REPORT`, `GST_EXPORT`, `GST_CONFIG`.

- `ADMIN` holds all four (holds every `Permission`).
- `STORE_MANAGER` holds `GST_VIEW`/`GST_REPORT`/`GST_EXPORT` (inherited from the admin set) but **not** `GST_CONFIG` — explicitly excluded by name in `RolePermissions.build()` with the comment "GST config ... is system-level ... pre-existing controllers already restrict these to ADMIN only, so STORE_MANAGER never had them."
- `ACCOUNTANT` holds all four: `GST_VIEW, GST_REPORT, GST_EXPORT, GST_CONFIG`.
- `SALES_USER`, `PURCHASE_USER`, `INVENTORY_USER` — none of the four GST permissions appear in their permission sets (not read in full here beyond the grep hits; confirmed absent from the two role blocks read).

**Important:** none of `GST_VIEW`/`GST_REPORT`/`GST_EXPORT` is actually enforced by any `@PreAuthorize` check anywhere in the codebase (grepped for `PERM_GST` across the whole backend — the only hit is `PERM_GST_CONFIG` on the config `PUT`). They exist in the permission model/role map but the report endpoints themselves are open to any authenticated user regardless of these three permissions.

## 9. Transaction Handling

- All 9 `GstReportingService` report methods are `@Transactional(readOnly = true)`.
- `BusinessGstConfigService.update(...)` is `@Transactional`.
- Every `GstTransactionSyncService` sync/reverse method is individually `@Transactional`. Because each is called synchronously from within the caller's own posting/reversal method (e.g. `SaleService.applyPostingEffects`), it participates in — and is covered by — that caller's own transaction boundary (Spring's default `REQUIRED` propagation), so a GST sync failure rolls back the whole Sale/Purchase/Note/Expense posting operation, and vice versa. No explicit `@Transactional(propagation = ...)` override was found on any sync method.

## 10. Error Handling

- `MethodArgumentNotValidException` (bean-validation failures, e.g. `BusinessGstConfigRequest.legalName` blank) → `GlobalExceptionHandler.handleValidation` → HTTP 400 with `ApiError.fieldErrors` populated per DTO field name.
- `BadRequestException` (e.g. `BusinessGstConfigService.update`'s manual GSTIN-format check, or `StoreAccessService.resolveViewableStoreId`'s "Please select a store"/"You do not have access to any store") → `GlobalExceptionHandler.handleBadRequest` → HTTP 400, `message = ex.getMessage()`, `fieldErrors = null` (this path does **not** populate per-field errors — only the top-level message).
- `AccessDeniedException` (a `@PreAuthorize` failure, e.g. a non-ADMIN hitting `PUT /business-gst-config`) → HTTP 403, generic message "You do not have permission to access this resource".
- Any other unhandled exception → `GlobalExceptionHandler.handleGeneral` → HTTP 500, generic message, full exception logged server-side.
- `MasterNotFoundException` (e.g. `StoreService.findOrThrow` inside `resolveGstContext` for an unknown store id) — handled by the generic Master-not-found mapping in `GlobalExceptionHandler` (not itself part of the GST module's own code, standard 404 convention shared across Masters).

## 11. Audit Flow

- `BusinessGstConfigService.update(...)` is the **only** audit call site in this module: `auditService.log(AuditAction.UPDATE, "GST_CONFIG", "BusinessGstConfig", saved.getId(), null, oldGstin, saved.getGstin(), "Business GST configuration updated")` — logs the module as `"GST_CONFIG"`, entity type `"BusinessGstConfig"`, entity id `1` (the singleton), `documentNumber = null`, `oldValue`/`newValue` = the GSTIN before/after the update, and a fixed description. No `storeId` is passed (this overload defaults it to `null` — a global/system-level action, consistent with the config being business-wide, not per-store).
- No audit call exists inside `GstTransactionSyncService` — sync/reverse of `gst_transactions` rows is not itself separately audited; it is a downstream side effect of the Sale/Purchase/Note/Expense posting action, which is audited by its own owning service.
- No audit call exists inside `GstReportingService` (read-only reports are not audited).

## 12. Important Side Effects

Every sync/reverse call is an automatic side effect of posting or cancelling a transaction in another module — none of it is user-visible or separately triggered. Complete list of call sites found via `grep "gstTransactionSyncService\."` across `backend/src/main/java/com/storehub/service/`:

- **`SaleService.java`**: `syncSale` in `applyPostingEffects(Sale saved)` (line 220, called on initial post) and again after an edit-and-repost (line 303); `reverseSale` in the sale-cancel flow (line 270) and in `reverseSaleEffects(Sale sale, String reason)` (line 378).
- **`PurchaseService.java`**: `syncPurchase` in `applyPostingEffects(Purchase saved)` (line 187) and after edit-and-repost (line 265); `reversePurchase` on cancel (line 230) and in `reversePurchaseEffects(Purchase purchase, String reason)` (line 333).
- **`CreditNoteService.java`**: `syncCreditNote` on note post (line 233); `reverseCreditNote` on note cancel (line 266).
- **`DebitNoteService.java`**: `syncDebitNote` on note post (line 222); `reverseDebitNote` on note cancel (line 254).
- **`ExpenseService.java`**: `syncExpense` on expense post (line 302); `reverseExpense` on expense cancel (line 328).

Every call site is a `private void applyPostingEffects(...)` / `private void reverse*Effects(...)`-style internal method, always invoked from the owning service's own create/post/cancel/edit public methods — GST sync is never invoked directly by a controller.

## 13. Dependencies on Other Modules

- **Sale** — `SaleService` calls `GstCalculationService.calculateLine` per line item, and `GstTransactionSyncService.syncSale`/`reverseSale` on post/cancel/repost. Source of `taxableAmount, cgstAmount, sgstAmount, igstAmount, totalAmount, customerGstin, saleDate, invoiceNumber, gstReportingApplicable, status`.
- **Purchase** — `PurchaseService` calls `GstCalculationService.calculateLine` per line item, and `GstTransactionSyncService.syncPurchase`/`reversePurchase`. Source of the equivalent purchase-side fields plus `supplierGstin`.
- **Credit Note** — `CreditNoteService` calls `GstTransactionSyncService.syncCreditNote`/`reverseCreditNote`; `gstReportingApplicable` is copied verbatim from the source Sale.
- **Debit Note** — `DebitNoteService` calls `GstTransactionSyncService.syncDebitNote`/`reverseDebitNote`.
- **Expense** — `ExpenseService` calls `GstCalculationService.calculateLine` and `GstTransactionSyncService.syncExpense`/`reverseExpense`; supplies its own `itcEligible` flag (used as `GstTransaction.b2b` instead of a GSTIN-validity proxy).
- **Store** (Multi-Store) — `StoreService.resolveGstContext` reads `Store.gstin`/state as an override before falling back to `BusinessGstConfig`; `GstTransaction.store` and every `GstTransactionRepository` query/aggregate accept a `storeId` filter resolved via `StoreAccessService.resolveViewableStoreId`.
- **Party / Customer / Supplier** — `Sale.customer`/`Purchase.supplier` (and their GSTIN/state) are the source of the party name, GSTIN, and — via `GstinValidator.extractStateCode` — the place-of-supply state code used to determine B2B/B2C classification and, upstream at billing time, `GstCalculationService.suggestTaxMode`'s CGST+SGST vs IGST suggestion.
- **State** — both `BusinessGstConfig.state` and party addresses ultimately resolve to the shared `State` master for state-code comparison.

## 14. Key Operation Flows

**A. Sale GST calculation → sync → GSTR-1 reporting (end-to-end):**
1. User posts a Sale in the Sales module. `SaleService`'s line-item application loop calls `gstCalculationService.calculateLine(taxableAmount, gstPercent, taxMode, taxTreatment)` per `SaleItem` (line ~452), which returns the CGST/SGST/IGST split described in §4, and the totals are stored on the `Sale` entity (`taxableAmount, cgstAmount, sgstAmount, igstAmount, totalAmount`) together with `gstReportingApplicable` (set elsewhere in Sale's own business rules, e.g. false for a Kacchi Sale Challan) and `status`.
2. `SaleService.applyPostingEffects(saved)` (line 220) calls `gstTransactionSyncService.syncSale(saved)`. `syncSale` checks `GstReportingEligibility.isEligibleForGstReporting(sale)` (`gstReportingApplicable == TRUE && status == COMPLETED`) — if false (Kacchi or not-yet-posted), it's a no-op and no `GstTransaction` row is ever created. If eligible, it finds-or-creates the row keyed on `(SALE, sale.getId())`, copies party/GSTIN/place-of-supply/amounts verbatim, sets `b2b = GstinValidator.isValid(customerGstin)`, `returnPeriod = saleDate.format("yyyy-MM")`, `status = ACTIVE`, and saves.
3. Later, a user opens GSTR-1 (`Gstr1Report.tsx` → `gstReportApi.gstr1(...)` → `GET /api/gst-reports/gstr1`). `GstReportingService.gstr1(...)` calls `gstTransactionRepository.findActiveForSummary(SALE, returnPeriod, fromDate, toDate, resolvedStoreId)`, splits the rows into `b2bTransactions`/`b2cTransactions` by each row's stored `b2b` flag, adds an HSN summary from `saleItemRepository.hsnSummary(...)`, and totals from `aggregateTotals(SALE, ...)` — all read straight from the `gst_transactions` mirror, never recomputed from the live `Sale`/`SaleItem` rows.
4. If the sale is later cancelled, `SaleService`'s cancel flow calls `gstTransactionSyncService.reverseSale(sale)`, which flips the existing row's `status` to `REVERSED` in place — it then silently drops out of every report above (`findActiveForSummary`/`search`/`aggregate*` all filter `status = ACTIVE`), while the row itself remains in the table for audit/reconciliation purposes.

**B. `resolveGstContext` flow (store GSTIN override):**
1. A billing form (Sale/Purchase UI, store-aware) requests `GET /api/masters/stores/{id}/gst-context`.
2. `StoreController.gstContext(id)` calls `storeService.resolveGstContext(id)`.
3. `StoreService.resolveGstContext` loads the `Store` via `findOrThrow`. If `StringUtils.hasText(store.getGstin())` (the store has its own GSTIN configured), it returns `StoreGstContextResponse{ gstin: store.gstin, legalName: store.legalName ?? store.storeName, stateCode: store.state?.code, source: "STORE" }`.
4. Otherwise it calls `businessGstConfigService.get()` and returns `StoreGstContextResponse{ gstin: business.gstin, legalName: business.legalName, stateCode: business.stateCode, source: "BUSINESS" }` — the business-wide singleton fallback.
5. The frontend uses `source` to label which anchor was used (store-level vs business-wide) when suggesting the seller line/tax mode for that store's transactions.

## 15. Manual Changes

- **To change the GST calculation/split logic** (rate application, HALF_UP rounding, CGST/SGST vs IGST split): edit `GstCalculationService.calculateLine` in `backend/src/main/java/com/storehub/service/GstCalculationService.java`. This is the single central engine — changing it affects every caller: `SaleService`, `PurchaseService`, `ExpenseService` (and transitively any Kacchi variant, since those reuse Sale/Purchase directly).
- **To change which transactions are GST-eligible for reporting** (e.g. adding a new eligible status, or a new source transaction type): edit `GstReportingEligibility` in `backend/src/main/java/com/storehub/service/GstReportingEligibility.java`. A new source type also requires a new sync/reverse method pair in `GstTransactionSyncService`, a new call site in that module's own post/cancel service methods, and (if it should aggregate anywhere) inclusion in the relevant `GstTransactionRepository` type list (e.g. `INPUT_GST_TYPES` in `GstReportingService`).
- **To add a new GST report**: add a method to `GstReportingService` (reading only `ACTIVE` `GstTransaction` rows or `SaleItem`/`PurchaseItem` with equivalent explicit filters — never re-derive eligibility ad hoc), a new DTO in `backend/src/main/java/com/storehub/dto/`, a new endpoint in `GstReportController`, a matching function in `frontend/src/api/gstReportApi.ts`, a type in `frontend/src/types/gstReport.ts`, a new page under `frontend/src/pages/gst/`, and a card entry in `GstReportsHub.tsx`'s `SECTIONS` array.
- **To change the GSTIN validation regex**: edit `GSTIN_PATTERN` in `backend/src/main/java/com/storehub/util/GstinValidator.java`. This is the single reusable validator — changing it affects GSTIN format checks in `BusinessGstConfigService.update`, and B2B/B2C classification plus place-of-supply state-code extraction in every `GstTransactionSyncService.sync*` method (`isValid`/`extractStateCode` are both called there for Sale/Purchase/CreditNote/DebitNote/Expense).
- **Modules affected by any `GstCalculationService` change**: `SaleService`, `PurchaseService`, `ExpenseService`.
- **Modules affected by any `GstTransactionSyncService` change**: `SaleService`, `PurchaseService`, `CreditNoteService`, `DebitNoteService`, `ExpenseService` (every module listed in §12/§13 that calls a sync/reverse method).
- **Frontend note**: if per-store GST report filtering is ever required in the UI, the backend already supports it end-to-end (`storeId` query param on every `GstReportController` endpoint, `storeId`-filtered repository queries) — only the frontend `gstReportApi.ts` param objects and each report page's UI would need a store selector added (see §2 IMPORTANT finding).
