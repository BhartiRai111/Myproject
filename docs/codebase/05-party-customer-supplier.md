# Party / Customer / Supplier Module

## 1. Overview

Contrary to the "Party is a generic superset used by Sale/Purchase, Customer/Supplier are legacy" assumption, **the actual code shows the opposite**: `Party` is an **isolated, standalone master** with no foreign-key relationship to any transactional entity anywhere in the backend, while `Customer` and `Supplier` are the entities that are actually wired into every transaction (Sale, Purchase, Orders, Payments, Receipts, Notes, Ledgers).

Evidence:

- **`Party`** (`backend/src/main/java/com/storehub/entity/Party.java`, table `parties`) has a `partyType` enum (`SUPPLIER`, `CUSTOMER`, `BOTH`), its own GSTIN/PAN, city/state/country FKs, a `dealsInCategories` many-to-many to `Category`, and a one-to-many to `PartyAddress` (additional addresses). It is managed via `/api/masters/parties` (`PartyController` → `PartyService` → `PartyRepository`).
- **Grep across the whole `entity` package for `private Party `** returns exactly one hit outside `Party` itself: `PartyAddress.java` (`private Party party;`, the address's own owning-side FK). **No other entity** (`Sale`, `Purchase`, `SalesOrder`, `PurchaseOrder`, `Payment`, `Receipt`, `CreditNote`, `DebitNote`, `CustomerLedgerEntry`, `SupplierLedgerEntry`, etc.) references `Party`. `PartyService`/`PartyRepository` are also not referenced by any other backend service (`grep "partyRepository"` across `com.storehub` only matches `PartyService.java`).
- **Grep for `private Customer ` / `private Supplier `** across `entity/` shows these are the ones actually used downstream:
  - `Sale.java:44` → `private Customer customer;`
  - `SalesOrder.java:37` → `private Customer customer;`
  - `Receipt.java:37` → `private Customer customer;`
  - `CreditNote.java:52` → `private Customer customer;`
  - `CustomerLedgerEntry.java:35` → `private Customer customer;`
  - `Purchase.java:34` → `private Supplier supplier;`
  - `PurchaseOrder.java:37` → `private Supplier supplier;`
  - `Payment.java:37` → `private Supplier supplier;`
  - `Expense.java:62` → `private Supplier supplier;`
  - `DebitNote.java:43` → `private Supplier supplier;`
  - `SupplierLedgerEntry.java:36` → `private Supplier supplier;`
- On the frontend, `partyApi` (`frontend/src/api/mastersApi.ts`) is imported **only** by `pages/MastersDashboard.tsx` and `pages/masters/PartyMaster.tsx`. It is never imported by any Sales/Purchase form. In contrast, `supplierApi`/`customerApi` (and their quick-add modals) are wired directly into `SalesBillForm.tsx`, `SalesOrderForm.tsx`, `KacchiSaleForm.tsx`, `PurchaseBillForm.tsx`, `PurchaseOrderForm.tsx`, `KacchiPurchaseForm.tsx`.

**Conclusion**: `Customer` and `Supplier` are the two entities that actually back every sales/purchase/accounting transaction in this codebase. `Party` is a separate, self-contained "Party Master" (under Masters → Parties) that models a generic business party (supplier, customer, or both, with GSTIN/PAN, categories, multiple addresses) but as of the current codebase is **not consumed by any other module** — it has no downstream FK usage at all. It functions as a dormant/parallel master, not a superset that Sale/Purchase resolve against. `Customer` and `Supplier` also have **no relationship to each other or to `Party`** at the entity/FK level — they are three fully independent JPA entities/tables that happen to model overlapping real-world concepts.

Also notable: `PartyLedgerService.java` (accounting ledger, not part of this module's own service set) resolves ledger party names by looking up `CustomerRepository`/`SupplierRepository` directly (an `AccountingPartyType` discriminator), **not** `PartyRepository` — reinforcing that the accounting subsystem treats Customer/Supplier, not Party, as the real party masters.

## 2. Frontend

### Files and Components

| File | Role |
|---|---|
| `frontend/src/pages/masters/PartyMaster.tsx` | Full CRUD page for `Party` master (list/create/edit/activate/deactivate), built on the generic `MasterCrudPage` component. Tabs: Basic Info, Location & Tax, Categories & Addresses. |
| `frontend/src/pages/Suppliers.tsx` | Supplier list page: search, status filter, sort, activate/deactivate, links to `SupplierForm`, opens `SupplierViewModal`. |
| `frontend/src/pages/SupplierForm.tsx` | Create/edit form for `Supplier` (routed as `/suppliers/new` and `/suppliers/:id/edit`). |
| `frontend/src/components/SupplierViewModal.tsx` | Read-only detail modal for a supplier, including its purchase history (`GET /api/suppliers/{id}/purchases`). |
| `frontend/src/components/SupplierQuickAddModal.tsx` | Minimal inline "Add Supplier" modal (name, mobile, email, address) used from Purchase forms. |
| `frontend/src/components/CustomerQuickAddModal.tsx` | Minimal inline "Add Customer" modal (firstName, lastName, mobile, email, state) used from Sale forms. |
| `frontend/src/api/supplierApi.ts` | Axios wrapper for `/api/suppliers`. |
| `frontend/src/api/customerApi.ts` | Axios wrapper for `/api/customers`. |
| `frontend/src/api/mastersApi.ts` (`partyApi`) | Axios wrapper for `/api/masters/parties`. |
| `frontend/src/types/supplier.ts` | `Supplier`, `SupplierPayload`, `SupplierStatus`, `SupplierPurchaseSummary` types. |
| `frontend/src/types/sale.ts` | `Customer`, `CustomerCreatePayload`, `CustomerStatus` types (Customer type lives with Sale, not with a "customer" file). |
| `frontend/src/types/masters.ts` (lines ~204–262) | `Party`, `PartyType`, `PartyAddress`, `PartyPayload` types. |
| There is **no** `Customers.tsx` / `CustomerForm.tsx` / `CustomerViewModal.tsx` page. | NOT FOUND IN CURRENT CODEBASE |

### Frontend Flow

**Party** (`PartyMaster.tsx`, via `MasterCrudPage`):
- List: `partyApi.list({search, status, page, size})` → paginated table (Code, Name, Mobile, Type badge).
- Create/Edit: tabbed form (Basic Info / Location & Tax / Categories & Addresses) submitted through `partyApi.create` / `partyApi.update`; per-field errors shown via `fieldErrors` (`invalid` prop + `<p>` feedback), matching the `fieldErrors`-map pattern from `Register.tsx`/`UserFormModal.tsx`.
- Deactivate/Activate: `partyApi.deactivate` / `partyApi.activate` (PATCH), presumably wired through `MasterCrudPage`'s generic action buttons.
- Deals-in categories: checkbox grid populated from `categoryApi.list()`.
- Additional addresses: repeatable rows (label, address line, pincode; city/state selects also present in payload but not exposed in the visible inputs beyond label/line/pincode in the current UI code).

**Supplier** (`Suppliers.tsx` + `SupplierForm.tsx`):
- List: `supplierApi.list({search, status, page, size, sortBy, sortDir})`, with Name/City/Newest sort options and a status filter, gated by `canManage` (`user.role === 'ADMIN' | 'STORE_MANAGER'`) for Add/Edit/Deactivate/Activate actions; View is available to everyone.
- Create/Edit: `SupplierForm.tsx` at `/suppliers/new` and `/suppliers/:id/edit`; client-side validation duplicates backend rules (`name` required, `mobile` exactly `^[0-9]{10}$`, `pincode` exactly `^[0-9]{6}$` if present) before submit; on 400, `parseApiError` extracts `message` + `fieldErrors` and populates per-field `invalid`/feedback text.
- View: `SupplierViewModal.tsx`, opened from the row's dropdown; lazily loads `supplierApi.getPurchaseHistory(id)`.
- Deactivate: confirmation `Dialog` ("It will no longer be selectable for new purchases, but existing purchase history will keep showing it") → `supplierApi.deactivate`.
- Activate: `supplierApi.activate`, no confirmation dialog.

**Customer**: there is no dedicated Customer master page in the frontend (`NOT FOUND IN CURRENT CODEBASE` for list/edit/deactivate UI). The only Customer UI is the **quick-add modal** used from Sales.

**Quick-Add flow** (used from Sales/Purchase forms, e.g. `SalesBillForm.tsx`, `PurchaseBillForm.tsx`, and their Order/Kacchi variants):
- `SupplierQuickAddModal`: minimal fields (name, mobile, email, address) → `supplierApi.create(...)` → `onCreated(supplier)` callback lets the parent form immediately select the new supplier without leaving the page.
- `CustomerQuickAddModal`: minimal fields (firstName, lastName, mobile, email, state) → `customerApi.create(...)` → `onCreated(customer)`. Note: this modal has **no per-field `fieldErrors` handling** (unlike `SupplierQuickAddModal`) — it only shows the top-level `parseApiError(...).message` in an `Alert`, so a 400 from the backend (e.g. a duplicate or malformed field) is not surfaced field-by-field here, only as a generic banner message.

## 3. API Calls

| Function | Method | URL | Request shape | Response shape |
|---|---|---|---|---|
| `partyApi.list` | GET | `/api/masters/parties` | query: `search?, partyType?, status?, page, size` | `PagedResponse<Party>` |
| `partyApi.getById` | GET | `/api/masters/parties/{id}` | — | `Party` |
| `partyApi.create` | POST | `/api/masters/parties` | `PartyPayload` (partyCode, partyName, mobile, partyType, ...) | `Party` (201) |
| `partyApi.update` | PUT | `/api/masters/parties/{id}` | `PartyPayload` | `Party` |
| `partyApi.activate` | PATCH | `/api/masters/parties/{id}/activate` | — | `Party` |
| `partyApi.deactivate` | PATCH | `/api/masters/parties/{id}/deactivate` | — | `Party` |
| `supplierApi.list` | GET | `/api/suppliers` | query: `search?, status?, page, size, sortBy, sortDir` | `PagedResponse<Supplier>` |
| `supplierApi.getById` | GET | `/api/suppliers/{id}` | — | `Supplier` |
| `supplierApi.getPurchaseHistory` | GET | `/api/suppliers/{id}/purchases` | — | `SupplierPurchaseSummary[]` |
| `supplierApi.create` | POST | `/api/suppliers` | `SupplierPayload` (name, mobile, contactPerson?, email?, address?, city?, state?, pincode?, gstNumber?, notes?) | `Supplier` (201) |
| `supplierApi.update` | PUT | `/api/suppliers/{id}` | `SupplierPayload` | `Supplier` |
| `supplierApi.activate` | PATCH | `/api/suppliers/{id}/activate` | — | `Supplier` |
| `supplierApi.deactivate` | PATCH | `/api/suppliers/{id}/deactivate` | — | `Supplier` |
| `customerApi.list` | GET | `/api/customers` | — (no pagination/search/filter params) | `Customer[]` |
| `customerApi.create` | POST | `/api/customers` | `CustomerCreatePayload` (firstName, lastName?, mobile, email?, state?) | `Customer` (201) |

There is no `customerApi.update`, `customerApi.getById`, `customerApi.activate`/`deactivate`, or `/api/customers/{id}/...` route in the frontend or backend — confirmed by `CustomerController.java` only exposing `GET /api/customers` and `POST /api/customers`.

## 4. Backend

### Controllers

- `PartyController` (`/api/masters/parties`): `list` (GET, unauthenticated-beyond-JWT — no `@PreAuthorize`), `getById` (GET, same), `create` (`PERM_PARTY_CREATE`), `update` (`PERM_PARTY_EDIT`), `activate`/`deactivate` (`PERM_PARTY_EDIT`).
- `SupplierController` (`/api/suppliers`): `getSuppliers`, `getSupplierById`, `getSupplierPurchaseHistory` — all unguarded beyond authentication; `createSupplier`, `updateSupplier`, `activateSupplier`, `deactivateSupplier` — all require `PERM_MASTER_MANAGE` (there is no dedicated `SUPPLIER_*` permission).
- `CustomerController` (`/api/customers`): `getAllCustomers`, `createCustomer` — **neither method has `@PreAuthorize`**; any authenticated user can list or create customers (see §7/§8).

### DTOs (fields + validation)

- **`PartyRequest`**: `partyCode` (`@NotBlank`), `partyName` (`@NotBlank`), `contactPerson`, `mobile` (`@NotBlank`, `@Pattern(^[0-9]{10}$)`), `email` (`@Email`), `partyType` (`@NotNull`), `gstNumber` (no annotation — validated in `PartyService` via `GstinValidator.isValid`, not `@Pattern`), `panNumber`, `address`, `cityId`/`stateId`/`countryId` (Long FKs), `pincode`, `notes`, `dealsInCategoryIds` (`Set<Long>`), `addresses` (`@Valid List<PartyAddressRequest>`).
- **`PartyAddressRequest`**: `label` (`@NotBlank`), `addressLine` (`@NotBlank`), `cityId`, `stateId`, `pincode`.
- **`PartyResponse`**: mirrors the entity plus resolved `cityName`/`stateName`/`countryName`, `dealsInCategoryNames`, and a list of `PartyAddressResponse`.
- **`SupplierCreateRequest`** / **`SupplierUpdateRequest`** (identical field sets): `name` (`@NotBlank`), `contactPerson`, `mobile` (`@NotBlank`, `@Pattern(^[0-9]{10}$)`), `email` (`@Email`), `address`, `city`, `state` (both **free-text strings**, not FK IDs — unlike Party's `cityId`/`stateId`), `pincode` (`@Pattern(^[0-9]{6}$)`), `gstNumber` (`@Pattern` for full 15-char GSTIN format `^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$`), `notes`.
- **`SupplierResponse`**: mirrors the entity 1:1 including `status`, `createdAt`, `updatedAt`.
- **`CustomerCreateRequest`**: `firstName` (`@NotBlank`), `lastName`, `mobile` (`@NotBlank` **only — no `@Pattern` digit-count check**, unlike Supplier/Party), `email` (`@Email`), `state` (free text; this is the GST "Place of Supply" anchor field — see §12).
- **`CustomerResponse`**: mirrors the entity.
- GSTIN/state relevance: Party validates GSTIN via `GstinValidator.isValid(...)` (service-layer, not a DTO annotation) and stores structured `stateId`/`countryId` FKs; Supplier validates GSTIN via a DTO-level regex and stores `state` as free text; Customer has **no GSTIN field at all** and stores `state` as free text used purely for tax-mode suggestion, not for GST registration purposes.

### Services (uniqueness checks, deals-in categories, addresses)

- **`PartyService`**:
  - Uniqueness: `partyCode` unique case-insensitively (`existsByPartyCodeIgnoreCase[AndIdNot]`) → `BadRequestException`. GSTIN, if supplied, validated with `GstinValidator.isValid` and unique case-insensitively (`existsByGstNumberIgnoreCase[AndIdNot]`) → `BadRequestException`.
  - `dealsInCategories`: resolved from `dealsInCategoryIds` via `CategoryService.findCategoryOrThrow` into a `Set<Category>` (many-to-many), rebuilt in full on every create/update.
  - Additional addresses: on update, `party.clearAddresses()` (orphan-removal wipe) then `applyAddresses(...)` rebuilds the full list from the request — i.e. addresses are fully replaced, not diffed/merged.
  - `setStatus(id, PartyStatus)` toggles `ACTIVE`/`INACTIVE` (used for both activate and deactivate).
- **`CustomerService`**: **no uniqueness checks at all** — `createCustomer` does not check for a duplicate mobile or email before insert (contrast with Supplier and Party, both of which enforce uniqueness). No update or status-change method exists.
- **`SupplierService`**:
  - Uniqueness: `email` (if non-blank) unique case-insensitively (`existsByEmailIgnoreCase[AndIdNot]`); `gstNumber` (if non-blank) validated via `GstinValidator.isValid` **and** unique case-insensitively (`existsByGstNumberIgnoreCase[AndIdNot]`) — both → `BadRequestException`. No uniqueness check on `mobile`.
  - `searchSuppliers` whitelists sortable fields to `{"name","city","createdAt"}`, defaulting to `"name"` for anything else.
  - `getPurchaseHistory(supplierId)` looks up the supplier (404 if missing) then `purchaseRepository.findBySupplierIdOrderByCreatedAtDesc(...)`.
  - `setStatus` toggles `ACTIVE`/`INACTIVE`.

### Repository

- `PartyRepository extends JpaRepository<Party, Long>`: `existsByPartyCodeIgnoreCase[AndIdNot]`, `existsByGstNumberIgnoreCase[AndIdNot]`, and a JPQL `search(search, partyType, status, pageable)` matching `partyName`/`partyCode`/`mobile` (LIKE, case-insensitive) plus optional `partyType`/`status` filters.
- `PartyAddressRepository extends JpaRepository<PartyAddress, Long>`: no custom methods (addresses are always accessed through the owning `Party`).
- `CustomerRepository extends JpaRepository<Customer, Long>`: **no custom query methods at all** — just the inherited CRUD (`findAll`, `findById`, `save`, ...). No search/filter/sort support.
- `SupplierRepository extends JpaRepository<Supplier, Long>`: `existsByEmailIgnoreCase[AndIdNot]`, `existsByGstNumberIgnoreCase[AndIdNot]`, and a JPQL `search(search, status, pageable)` matching `name`/`mobile`/`email` (LIKE, case-insensitive) plus optional `status` filter.

## 5. Database

### Tables

- `parties` (entity `Party`) — PK `id`, unique `party_code`.
- `party_addresses` (entity `PartyAddress`) — PK `id`, FK `party_id` (NOT NULL).
- `party_deals_in_categories` — join table for `Party` ↔ `Category` (`party_id`, `category_id`).
- `customers` (entity `Customer`) — PK `id`.
- `suppliers` (entity `Supplier`) — PK `id`; note column `phone` maps to Java field `mobile` (`@Column(name = "phone")`).

### Relationships

- `Party` 1—* `PartyAddress` (`mappedBy = "party"`, `CascadeType.ALL`, `orphanRemoval = true`): additional/alternate addresses beyond the party's primary `address`/`city`/`state`/`country`/`pincode` fields.
- `Party` *—* `Category` via `party_deals_in_categories` (deals-in categories).
- `Party` also has `ManyToOne` FKs to `City`, `State`, `Country` (masters) for its primary address — `PartyAddress` rows each independently reference their own `City`/`State`.
- `Party` itself has **no incoming FK from any other entity** (see §1 for the full grep evidence) — it is a leaf/standalone master.
- Downstream dependents of `Customer` (grepped across the entire `entity/` package for `private Customer `):
  - `Sale.customer`, `SalesOrder.customer`, `Receipt.customer`, `CreditNote.customer`, `CustomerLedgerEntry.customer`.
- Downstream dependents of `Supplier` (grepped for `private Supplier `):
  - `Purchase.supplier`, `PurchaseOrder.supplier`, `Payment.supplier`, `Expense.supplier`, `DebitNote.supplier`, `SupplierLedgerEntry.supplier`.

## 6. Validation

- **Mobile**: Party and Supplier both enforce `^[0-9]{10}$` via `@Pattern` at the DTO layer (consistent with the project-wide 10-digit rule). **Customer does not** — `CustomerCreateRequest.mobile` only has `@NotBlank`, so e.g. an 11-digit or non-numeric mobile is accepted by the backend for Customer creation. This is a real, code-confirmed inconsistency versus the documented 10-digit mobile rule for the rest of the app.
- **Pincode**: Supplier enforces `^[0-9]{6}$` via `@Pattern`. Party's `PartyRequest.pincode` and `PartyAddressRequest.pincode` have **no format annotation** — any string is accepted at the DTO layer.
- **GSTIN**: Supplier enforces the full 15-char GSTIN shape via DTO `@Pattern`. Party validates GSTIN in `PartyService` via `GstinValidator.isValid(...)` (service-layer, not annotation-based) — same effective format, different enforcement point. Customer has no GSTIN field.
- **Email**: `@Email` on Party, Supplier (create+update), and Customer.
- **Uniqueness** (service-layer, not annotation-based): Party — `partyCode` and `gstNumber` (if present) unique. Supplier — `email` (if present) and `gstNumber` (if present) unique. Customer — **none**.
- All field errors from `@Valid` failures are captured by `GlobalExceptionHandler.handleValidation` (on `MethodArgumentNotValidException`) into `ApiError.fieldErrors` (`Map<fieldName, message>`); service-thrown `BadRequestException`s (uniqueness/GSTIN-format failures) return only a top-level `message` with `fieldErrors: null` — the frontend forms for Supplier and Party surface these via the top-level `Alert`, since there's no field to attach them to individually.

## 7. Authentication / Authorization

All `/api/**` routes require an authenticated (JWT-bearing) request (`SecurityConfig`: `anyRequest().authenticated()`, with only `/api/auth/**` and `/actuator/health` permitted anonymously). Beyond that base requirement:
- Party: `list`/`getById` need only authentication; `create`/`update`/`activate`/`deactivate` additionally need `PERM_PARTY_CREATE` or `PERM_PARTY_EDIT`.
- Supplier: `list`/`getById`/`getPurchaseHistory` need only authentication; all mutating endpoints need `PERM_MASTER_MANAGE`.
- Customer: **every** endpoint (`list`, `create`) needs only authentication — there is no permission check at all on the Customer controller.

## 8. Permissions

From `RolePermissions.java` / `Permission.java` (`backend/src/main/java/com/storehub/entity/Permission.java` and `.../service/RolePermissions.java`):
- `PARTY_VIEW` ("Master" group) — granted to multiple roles (appears in at least 4 role permission sets alongside `ITEM_VIEW`, `MASTER_VIEW`, `STORE_VIEW`, etc.) but is **never actually checked** by `PartyController` — its GET endpoints have no `@PreAuthorize` at all, so `PARTY_VIEW` is currently a defined-but-unenforced permission for read access.
- `PARTY_CREATE`, `PARTY_EDIT` ("Master" group) — enforced via `@PreAuthorize("hasAuthority('PERM_PARTY_CREATE')")` / `PERM_PARTY_EDIT` on Party's mutating endpoints.
- `MASTER_MANAGE` — the single permission gating all Supplier mutations (create/update/activate/deactivate); there is no `SUPPLIER_VIEW`/`SUPPLIER_CREATE`/`SUPPLIER_EDIT`.
- `CUSTOMER_VIEW` / `CUSTOMER_CREATE` / etc.: NOT FOUND IN CURRENT CODEBASE — no Customer-specific permission exists, and the Customer controller checks no permission at all.

## 9. Transaction Handling

- `PartyService.create`, `.update`, `.setStatus` are `@Transactional` (address replacement + category resolution happen atomically with the party save).
- `CustomerService.createCustomer` is `@Transactional`.
- `SupplierService.createSupplier`, `.updateSupplier`, `.setStatus` are `@Transactional`. `searchSuppliers`, `getSupplierById`, `getPurchaseHistory` are read-only (no `@Transactional`, plain repository calls).

## 10. Error Handling

- `PartyNotFound` → `MasterNotFoundException("Party", id)` (generic, entity-name-parameterized) → `GlobalExceptionHandler.handleMasterNotFound` → HTTP 404.
- `SupplierNotFoundException(id)` → `handleSupplierNotFound` → HTTP 404.
- `CustomerNotFoundException(id)` → `handleCustomerNotFound` → HTTP 404 (thrown by `CustomerService.findCustomerOrThrow`, but note this method is **never called by `CustomerController`** — it exists only for other services, e.g. potentially Sales, to resolve a customer FK; `CustomerController` itself has no `getById` endpoint that could trigger it).
- Uniqueness/GSTIN-format violations → `BadRequestException` → `handleBadRequest` → HTTP 400, top-level `message` only.
- `@Valid` DTO failures → `MethodArgumentNotValidException` → `handleValidation` → HTTP 400 with `fieldErrors` map.
- Unhandled exceptions fall through to `handleGeneral` → HTTP 500 with a generic message (and full stack trace logged server-side).

## 11. Audit Flow

NOT FOUND IN CURRENT CODEBASE — `PartyService`, `CustomerService`, and `SupplierService` contain **no calls to `AuditService`** (confirmed by grepping `Party|Customer|Supplier` inside `AuditService.java` — zero matches — and grepping `AuditService|auditService` inside `PartyService.java` — zero matches). Creates, updates, activations, and deactivations of Party/Customer/Supplier records are not written to the audit log by this module, unlike modules such as User Management that do call `AuditService` explicitly.

## 12. Important Side Effects

- **State-based GST tax-mode auto-suggestion**: `Customer.state` (free text) and `Supplier.state` (free text) are the fields that drive the seller/counterparty "Place of Supply" comparison in the Sales and Purchase bill forms. `frontend/src/utils/gst.ts` exports `suggestTaxMode(sellerState, partyState)`, documented as mirroring the backend's `GstCalculationService#suggestTaxMode` — it returns `'INTRA_STATE'` if the two state strings match case-insensitively (trimmed), `'INTER_STATE'` if they differ, or `null` if either is missing. `SalesBillForm.tsx` calls `suggestTaxMode(sellerState, customer?.state)` to pre-select `taxMode` (line ~208); `PurchaseBillForm.tsx` calls `suggestTaxMode(sellerState, supplier?.state)` analogously. This is explicitly a **pre-select hint only** — the backend does not re-derive or trust this value; the user's dropdown selection remains authoritative for the actual GST calculation.
- Because `Customer.state`/`Supplier.state` are free-text strings (not FK'd to the `State` master the way Party's `stateId` is), a typo or inconsistent casing/naming between the seller's configured state and a customer's/supplier's entered state can silently defeat the auto-suggestion (it just returns `null` or the wrong mode) — there is no dropdown constraining these fields to the canonical state list.
- Deactivating a Supplier does not cascade or block anything at the DB level — `SupplierService.setStatus` is a plain field flip; the frontend's confirmation copy ("no longer selectable for new purchases, but existing purchase history will keep showing it") is a UI-level convention, not something enforced by a DB constraint or service-layer check in this module (whatever excludes inactive suppliers from new-purchase pickers lives in the Purchase module, not here).

## 13. Dependencies on Other Modules

- **Sales module** (`Sale.customer`, `SalesOrder.customer`, `KacchiSaleForm.tsx`, `SalesBillForm.tsx`, `SalesOrderForm.tsx`): resolves/selects `Customer`, uses `CustomerQuickAddModal` and `customerApi`.
- **Purchase module** (`Purchase.supplier`, `PurchaseOrder.supplier`, `KacchiPurchaseForm.tsx`, `PurchaseBillForm.tsx`, `PurchaseOrderForm.tsx`): resolves/selects `Supplier`, uses `SupplierQuickAddModal` and `supplierApi`.
- **Payments/Receipts**: `Payment.supplier`, `Receipt.customer`.
- **Credit/Debit Notes**: `CreditNote.customer`, `DebitNote.supplier`.
- **Expenses**: `Expense.supplier` (an expense can optionally be attributed to a supplier).
- **Accounting / Ledgers**: `CustomerLedgerEntry.customer`, `SupplierLedgerEntry.supplier`; `PartyLedgerService` (accounting ledger reporting) resolves ledger-row party display names via `CustomerRepository`/`SupplierRepository` directly (keyed by an `AccountingPartyType` discriminator), independent of `PartyService`/`PartyRepository`.
- **GST**: tax-mode auto-suggestion (see §12) consumes `Customer.state`/`Supplier.state` from the Sales/Purchase forms.
- **Masters dashboard** (`MastersDashboard.tsx`) and **Categories** (`categoryApi`, for `dealsInCategoryIds`) are the only consumers of `Party`/`partyApi`.

## 14. Key Operation Flows

**Create Customer** (via quick-add, the only creation path that exists):
UI (`CustomerQuickAddModal.tsx`, form fields firstName/lastName/mobile/email/state) → `customerApi.create(payload)` → `POST /api/customers` → `CustomerController.createCustomer(@Valid CustomerCreateRequest)` → Spring validates `@NotBlank firstName`, `@NotBlank mobile` (no digit-pattern check), `@Email email` → `CustomerService.createCustomer(request)` → builds `Customer.builder()...build()` (no uniqueness check) → `customerRepository.save(customer)` → `@PrePersist` sets `createdAt`/`updatedAt` and defaults `status = ACTIVE` → `CustomerResponse.fromEntity(customer)` → HTTP 201 → modal's `onCreated(customer)` callback lets the parent Sale form select the new customer immediately.

**Create Supplier** (full form, `SupplierForm.tsx`, `isEdit = false`):
UI (name/contactPerson/mobile/email/address/city/state/pincode/gstNumber/notes) → client-side `validate()` (name required, mobile `^[0-9]{10}$`, pincode `^[0-9]{6}$` if present) → `supplierApi.create(payload)` → `POST /api/suppliers` (requires `PERM_MASTER_MANAGE`) → `SupplierController.createSupplier(@Valid SupplierCreateRequest)` → DTO validation (`@NotBlank name`, `@Pattern mobile`, `@Email email`, `@Pattern pincode`, `@Pattern gstNumber`) → `SupplierService.createSupplier(request)`: checks `email` uniqueness (if present) and, if `gstNumber` present, validates via `GstinValidator.isValid` then checks GSTIN uniqueness → both failures throw `BadRequestException` (400, top-level message) → builds `Supplier.builder()...build()` (blank email/GST normalized to `null`) → `supplierRepository.save(...)` → `@PrePersist` sets timestamps, defaults `status = ACTIVE` → `SupplierResponse.fromEntity(...)` → HTTP 201 → frontend `toast.success(...)`, `navigate('/suppliers')`.

**Deactivate Supplier**:
UI (`Suppliers.tsx` row dropdown → "Deactivate", `canManage` only) → confirmation `Dialog` → `handleDeactivateConfirm` → `supplierApi.deactivate(id)` → `PATCH /api/suppliers/{id}/deactivate` (requires `PERM_MASTER_MANAGE`) → `SupplierController.deactivateSupplier` → `SupplierService.setStatus(id, SupplierStatus.INACTIVE)` → `findSupplierOrThrow(id)` (404 via `SupplierNotFoundException` if missing) → `supplier.setStatus(INACTIVE)` → `supplierRepository.save(...)` → `@PreUpdate` bumps `updatedAt` → `SupplierResponse.fromEntity(...)` → HTTP 200 → frontend `toast.success(...)`, `loadSuppliers()` refresh; the row now shows the `INACTIVE` badge and an "Activate" action instead.

**Quick-Add-from-another-form flow** (e.g. adding a new customer while creating a Sale, in `SalesBillForm.tsx`/`SalesOrderForm.tsx`/`KacchiSaleForm.tsx`):
Sale form renders a "+ Add Customer" trigger → opens `CustomerQuickAddModal` (`show=true`) → user fills firstName/mobile/(lastName/email/state optional) → submit → `customerApi.create({firstName, lastName, mobile, email, state})` → `POST /api/customers` → same backend path as "Create Customer" above (`CustomerController` → `CustomerService` → `CustomerRepository`, no uniqueness check, no permission check) → HTTP 201 `CustomerResponse` → modal calls `onCreated(customer)` → the Sale form's handler (in the parent page) adds this customer to its in-memory customer list/select and auto-selects it, and also feeds `customer.state` into `suggestTaxMode(sellerState, customer.state)` to pre-select the sale's `taxMode` — all without the user leaving the Sale form. The Supplier equivalent (`SupplierQuickAddModal` from Purchase forms) follows the identical shape against `POST /api/suppliers`, except it does surface `fieldErrors` per-field (unlike the Customer modal) since `SupplierCreateRequest` has stricter validation that is more likely to trigger 400s (e.g. the GSTIN pattern, though GST isn't collected in the quick-add form itself, so in practice only `mobile`/`email` pattern failures surface here).

## 15. Manual Changes

- **Add a new field to `Customer`**: add the column to `backend/src/main/java/com/storehub/entity/Customer.java`, add it to `CustomerCreateRequest`/`CustomerResponse` (`backend/src/main/java/com/storehub/dto/`), wire it through `CustomerService.createCustomer`'s builder, and add the input to `CustomerQuickAddModal.tsx` and the `Customer`/`CustomerCreatePayload` types in `frontend/src/types/sale.ts`. There is no edit form to update, since none exists; if update support is later added, `CustomerController`/`CustomerService` would need new `PUT`/`update` methods first (they don't currently exist).
- **Add a new field to `Supplier`**: add the column to `Supplier.java`; add it to `SupplierCreateRequest` and `SupplierUpdateRequest` (keep both in sync — they are currently duplicated, not shared via a base class) and `SupplierResponse`; wire it through `SupplierService.createSupplier`/`updateSupplier`; add the input to `SupplierForm.tsx` (and optionally `SupplierQuickAddModal.tsx` if it should be quick-addable) and `SupplierViewModal.tsx`; update `Supplier`/`SupplierPayload` in `frontend/src/types/supplier.ts`.
- **Add a new field to `Party`**: add the column to `Party.java`; add to `PartyRequest`/`PartyResponse`; wire through `PartyService.create`/`update`; add the input to the appropriate tab in `PartyMaster.tsx`'s `renderForm`/`renderView`; update `Party`/`PartyPayload` in `frontend/src/types/masters.ts`.
- **Change GSTIN validation**: for Party, edit `backend/src/main/java/com/storehub/util/GstinValidator.java` (`isValid(...)`), used by `PartyService`. For Supplier, edit the `@Pattern` regex directly on `SupplierCreateRequest.gstNumber` and `SupplierUpdateRequest.gstNumber` (must be changed in both files identically since they are not shared). Customer has no GSTIN field to change.
- **Change state validation / add a state dropdown for Customer or Supplier**: today `Customer.state` and `Supplier.state` are unconstrained free-text strings (no `@Pattern`, no FK to the `State` master). To constrain them, either add a `@Pattern`/enum-style validation at the DTO level, or — for stronger consistency with `Party` (which already uses `stateId`/`State` FK) — change the field to a `stateId` FK the way `PartyRequest`/`Party` already do, which would additionally require updating `frontend/src/utils/gst.ts#suggestTaxMode` (currently compares raw strings) and every call site of it (`SalesBillForm.tsx`, `PurchaseBillForm.tsx`, and their Order/Kacchi variants) plus the seller's own state source, since the whole GST tax-mode suggestion pipeline depends on string equality of these two free-text fields today.
- **Add a new "deals-in category"**: this concept exists **only on `Party`** (`dealsInCategoryIds`/`dealsInCategoryNames`, backed by `party_deals_in_categories` and the shared `Category` master/`CategoryService`). Since Party is not consumed by Sales/Purchase (see §1), adding or changing categories here has no effect on Customer/Supplier or on Sales/Purchase forms — it only affects `PartyMaster.tsx`'s "Categories & Addresses" tab and whatever (if anything) elsewhere in the app reads `Category`/`categoryApi` (Products/Items, per `frontend/src/types/product.ts`'s `Category` import in `PartyMaster.tsx`). Supplier and Customer have no equivalent "deals-in" concept at all.
- **Modules affected by Customer/Supplier field changes**: Sales forms (`SalesBillForm.tsx`, `SalesOrderForm.tsx`, `KacchiSaleForm.tsx`, `CustomerQuickAddModal.tsx`) for Customer; Purchase forms (`PurchaseBillForm.tsx`, `PurchaseOrderForm.tsx`, `KacchiPurchaseForm.tsx`, `SupplierQuickAddModal.tsx`) for Supplier; GST tax-mode suggestion (`utils/gst.ts`) for both `state` fields; Accounting/ledger reporting (`PartyLedgerService`, `CustomerLedgerEntry`/`SupplierLedgerEntry`) for any field that ledger reports display; and `SupplierPurchaseSummary`/purchase-history display for Supplier-side purchase fields.
