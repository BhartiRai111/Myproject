# Lookup / Simple Masters Module

## 1. Overview

StoreHub has nine "simple lookup masters" — **Country, State, City, Zone, Currency, Nationality, Unit, Item Group, HSN** — that all follow the exact same CRUD shape:

- List with search + status filter + pagination
- Create
- Edit (full replace via `PUT`)
- Activate / Deactivate (soft status toggle, `PATCH`; no hard delete exists anywhere in this module)

Every one of these masters is a plain JPA `@Entity` with an `id`, a name-ish unique field, a `status` enum (`ACTIVE`/`INACTIVE`), and `createdAt`/`updatedAt` timestamps auto-set by `@PrePersist`/`@PreUpdate`. Four of the nine (State, City, Nationality, Zone) also carry a `ManyToOne` reference to another master in this same group (State→Country, City→State, Nationality→Country, Zone→Country/State optionally) to model a lookup hierarchy.

On the frontend, eight of the nine masters (Country, State, City, Zone, Currency, Nationality, Unit, Item Group) are built by simply configuring the shared generic component `frontend/src/components/masters/MasterCrudPage.tsx` with entity-specific columns, a form renderer, and the four CRUD callbacks — there is no bespoke list/detail/dialog code per master.

**HSN is the one exception.** `HsnMaster.tsx` does **not** use `MasterCrudPage` — it is a hand-written ~480-line page that duplicates most of `MasterCrudPage`'s list/search/pagination/dialog/discard-confirm logic, because HSN needs an extra capability none of the others do: each HSN code owns a **child collection of tax rates** (`HsnTaxRate`, table `hsn_tax_rates`) with its own repeatable add/remove row UI inside the create/edit form, and a read-only "View" dialog showing the full tax-rate history table. On the backend this shows up as `Hsn` having a `@OneToMany(mappedBy = "hsn", cascade = ALL, orphanRemoval = true)` list of `HsnTaxRate`, and `HsnService.update()` fully replacing the child rows (`clearTaxRates()` + `applyTaxRates()`) rather than diffing them.

## 2. Frontend

### The Shared Component: MasterCrudPage.tsx

File: `frontend/src/components/masters/MasterCrudPage.tsx`

`MasterCrudPage<T extends MasterRow, TPayload>` is a generic React function component parameterized by:
- `T extends MasterRow` — the entity/response shape as returned by the API. `MasterRow` requires at minimum `{ id: number; status: string }`.
- `TPayload` — the create/update request-body shape (the form's state type).

**Props (`MasterCrudPageProps<T, TPayload>`):**

| Prop | Type | Purpose |
|---|---|---|
| `icon` | `LucideIcon` | Icon shown in `PageHeader` and the empty state |
| `title` | `string` | Singular display name, e.g. `"Country"` — used in headings, toasts, dialog titles |
| `description` | `string` | Subtitle text under the page header |
| `itemLabel` | `string` | Plural label used in toast/error/empty-state text, e.g. `"countries"` |
| `searchPlaceholder` | `string` | Placeholder text for the search input |
| `columns` | `MasterColumn<T>[]` | `{ header, render(item): ReactNode, className? }[]` — defines the table's data columns (Status and Actions columns are always appended automatically) |
| `emptyValues` | `TPayload` | The blank/default payload used when opening the "Add" dialog |
| `toFormValues` | `(item: T) => TPayload` | Converts a fetched row into edit-form values when opening "Edit" |
| `renderForm` | `(values, setValues, fieldErrors) => ReactNode` | Renders the create/edit form body inside the shared `Dialog` |
| `renderView?` | `(item: T) => ReactNode` | Optional — if provided, adds a "View" dropdown action and a read-only detail `Dialog` (none of the 9 masters that use `MasterCrudPage` currently pass this — HSN needs its own view and bypasses `MasterCrudPage` entirely instead) |
| `fetchList` | `(params: {search, status, page, size}) => Promise<{data: {content: T[]; totalPages: number}}>` | List/search call |
| `create` | `(payload: TPayload) => Promise<any>` | Create call |
| `update` | `(id: number, payload: TPayload) => Promise<any>` | Update call |
| `activate` | `(id: number) => Promise<any>` | Activate call |
| `deactivate` | `(id: number) => Promise<any>` | Deactivate call |
| `extraFilterSlot?` | `ReactNode` | Extra filter control rendered next to the search bar (e.g. State/Country dropdown filters) |
| `reloadToken?` | `unknown` | Any value; changing it re-triggers `load()` (used so an extra filter, e.g. `countryFilter`, forces a reload) |
| `backTo?` | `string` (default `/masters`) | Back-button target |
| `backLabel?` | `string` (default `Back to Masters`) | Back-button label |

**Internal state and behavior:**
- `items`, `loading`, `search`, `statusFilter`, `page`, `totalPages` drive the list/table/pagination.
- `load()` calls `fetchList({search, status: statusFilter, page, size: PAGE_SIZE=10})`, sets `items`/`totalPages`, and on failure shows a toast via `parseApiError(err, "Failed to load {itemLabel} list")`.
- `useEffect` reloads on `[page, statusFilter, reloadToken]` — note: typing in the search box does **not** auto-reload; the user must submit the search form (`handleSearchSubmit`, which resets `page` to 0 and calls `load()`).
- `formModal` state (`{show, mode: 'add'|'edit', item}`) drives a single shared `Dialog` used for both create and edit.
- `openAdd()` resets `values` to `emptyValues` and snapshots it (`initialValuesSnapshot`, JSON-stringified) for dirty-checking.
- `openEdit(item)` calls `toFormValues(item)` to seed `values` and snapshots it the same way.
- `isFormDirty()` compares the current `JSON.stringify(values)` against the snapshot; `requestCloseForm()` opens a `ConfirmDialog` ("Discard unsaved changes?") only if dirty, otherwise closes immediately.
- `handleSubmit()`: calls `create(values)` (mode `add`) or `update(item.id, values)` (mode `edit`), shows a success toast (`"{title} created successfully"` / `"{title} updated successfully"`), closes the dialog and calls `load()`. On error, calls `parseApiError` and sets both the top-level `error` (shown in an `Alert`) and `fieldErrors` (passed into `renderForm` for per-field `invalid`/feedback rendering).
- `toggleStatus(item)`: calls `deactivate(item.id)` if `item.status === 'ACTIVE'`, else `activate(item.id)`; shows a toast; reloads the list. Exposed via a dropdown menu item labeled "Deactivate" (destructive variant) or "Activate".
- Renders: `BackButton` → `PageHeader` (title/description/"Add {title}" button) → search/status-filter `Card` → data `Table` (or `TableSkeleton` while loading, or `EmptyState` when empty) with a per-row dropdown (`View?`, `Edit`, `Activate`/`Deactivate`) → `Pagination` → the create/edit `Dialog` → the discard-confirm `ConfirmDialog` → the optional view `Dialog`.

**Example configuration — `CountryMaster.tsx` (`frontend/src/pages/masters/CountryMaster.tsx`):**
```tsx
<MasterCrudPage<Country, CountryPayload>
  icon={Globe}
  title="Country"
  columns={[
    { header: 'Name', render: (c) => <span className="font-medium">{c.name}</span> },
    { header: 'Code', render: (c) => c.code },
  ]}
  emptyValues={{ name: '', code: '' }}
  toFormValues={(c) => ({ name: c.name, code: c.code })}
  renderForm={(values, setValues, fieldErrors) => (/* Name + Code inputs, each with
      invalid={!!fieldErrors.name} and a <p> showing fieldErrors.name/code */)}
  fetchList={({ search, status, page, size }) => countryApi.list({ search, status, page, size })}
  create={countryApi.create}
  update={countryApi.update}
  activate={countryApi.activate}
  deactivate={countryApi.deactivate}
/>
```
The dependent masters (State, City, Nationality) additionally fetch their parent master's active list in a `useEffect` (e.g. `StateMaster` loads `countryApi.list({status:'ACTIVE', size:1000})` into a `countries` state array), render a `<Select>` of that parent in `renderForm`, and use `extraFilterSlot` + `reloadToken` to add a parent-scoped filter dropdown above the table (e.g. "All Countries" filter on `StateMaster`, which is passed as `countryFilter` into both `fetchList`'s params and `reloadToken`).

### Files and Components

| Master | Frontend page | Wires to entity | Wires to API object | Uses `MasterCrudPage`? |
|---|---|---|---|---|
| Country | `frontend/src/pages/masters/CountryMaster.tsx` | `Country` | `countryApi` | Yes |
| State | `frontend/src/pages/masters/StateMaster.tsx` | `StateMaster` (frontend type; backend entity `State`) | `stateApi` (+ `countryApi` for the parent dropdown) | Yes |
| City | `frontend/src/pages/masters/CityMaster.tsx` | `City` | `cityApi` (+ `stateApi` for the parent dropdown) | Yes |
| Zone | `frontend/src/pages/masters/ZoneMaster.tsx` | `Zone` | `zoneApi` (+ `countryApi`, `stateApi` for optional parent dropdowns) | Yes |
| Currency | `frontend/src/pages/masters/CurrencyMaster.tsx` | `Currency` | `currencyApi` | Yes |
| Nationality | `frontend/src/pages/masters/NationalityMaster.tsx` | `Nationality` | `nationalityApi` (+ `countryApi`) | Yes |
| Unit | `frontend/src/pages/masters/UnitMaster.tsx` | `Unit` | `unitApi` | Yes |
| Item Group | `frontend/src/pages/masters/ItemGroupMaster.tsx` | `ItemGroup` | `itemGroupApi` | Yes |
| HSN | `frontend/src/pages/masters/HsnMaster.tsx` | `Hsn` (+ child `HsnTaxRate`) | `hsnApi` | **No — bespoke page** |

Shared infra: `frontend/src/components/masters/MasterCrudPage.tsx` (generic CRUD shell), `frontend/src/pages/MastersDashboard.tsx` (landing grid with per-master record counts), `frontend/src/api/mastersApi.ts` (per-master `*Api` objects), `frontend/src/types/masters.ts` (per-master `interface X` / `XPayload` types).

### Frontend Flow

**Generic flow (all `MasterCrudPage`-based masters):**
1. Page mounts → `useEffect([page, statusFilter, reloadToken])` fires `load()` → `fetchList()` → table renders `items`, or `TableSkeleton` while loading, or `EmptyState` if empty.
2. **Create**: "Add {title}" button → `openAdd()` seeds `emptyValues` into the shared `Dialog` → user fills `renderForm` fields → submit → `create(values)` → on success, toast + close + `load()`; on 400, `parseApiError` fills `error` (top Alert) and `fieldErrors` (inline per-field `<p>` under each `Input`, using `invalid={!!fieldErrors.x}`).
3. **Edit**: row's "⋯" menu → "Edit" → `openEdit(item)` seeds `toFormValues(item)` → same dialog/submit path but calls `update(item.id, values)`.
4. **Activate/Deactivate**: row's "⋯" menu → `toggleStatus(item)` → calls `activate`/`deactivate` directly (no confirmation dialog, no form) → toast → `load()`.
5. Search box requires explicit "Search" submit (resets to page 0); the Status dropdown and any `extraFilterSlot` (parent-master filter) reload immediately on change.

**HSN's extra tax-rate row flow (`HsnMaster.tsx`, self-contained, no `MasterCrudPage`):**
- `HsnPayload.taxRates: HsnTaxRate[]` is part of the same form state as `hsnCode`/`description`.
- `emptyTaxRate()` seeds one blank row (`taxPercent: 0`, `effectiveFrom: today`) whenever "Add HSN Code" is opened, or whenever an edited HSN happens to have zero rows.
- Inside the form, a "Tax Rates" section renders one row per `taxRates[i]` with Tax %/CGST %/SGST %/IGST %/Effective-From inputs plus a per-row delete (`Trash2`) button (disabled when only one row remains) and an "Add Rate" button (`addTaxRateRow`) that appends another `emptyTaxRate()`.
- `updateTaxRateRow(idx, field, value)` updates one field of one row immutably; numeric fields are parsed with `Number(value)` (empty string → `undefined`), `effectiveFrom` stays a raw string.
- On submit, the whole `taxRates` array is sent as-is inside `HsnPayload` to `hsnApi.create`/`hsnApi.update` — the backend fully replaces the child rows (see §4 Services).
- The list table shows only the **latest** rate's `taxPercent` per HSN code (`latestRate()` sorts `taxRates` by `effectiveFrom` descending and takes the first); the "View" dialog shows the full tax-rate history table (Tax %/CGST/SGST/IGST/Cess/Effective From) for the selected HSN.

## 3. API Calls

All 9 masters, from `frontend/src/api/mastersApi.ts`. Every `list()` call sends `{search, status, page, size}` (plus a parent-id filter where noted) as query params via `buildParams()`, which drops empty/undefined values.

| Master | Function | Method | URL | Request shape | Response shape |
|---|---|---|---|---|---|
| Country | `countryApi.list` | GET | `/masters/countries` | query: `search?, status?, page, size` | `PagedResponse<Country>` |
| | `countryApi.getById` | GET | `/masters/countries/{id}` | — | `Country` |
| | `countryApi.create` | POST | `/masters/countries` | `CountryPayload {name, code}` | `Country` |
| | `countryApi.update` | PUT | `/masters/countries/{id}` | `CountryPayload` | `Country` |
| | `countryApi.activate` | PATCH | `/masters/countries/{id}/activate` | — | `Country` |
| | `countryApi.deactivate` | PATCH | `/masters/countries/{id}/deactivate` | — | `Country` |
| State | `stateApi.list` | GET | `/masters/states` | query: `search?, status?, countryId?, page, size` | `PagedResponse<StateMaster>` |
| | `stateApi.getById` | GET | `/masters/states/{id}` | — | `StateMaster` |
| | `stateApi.create` | POST | `/masters/states` | `StatePayload {name, code?, countryId}` | `StateMaster` |
| | `stateApi.update` | PUT | `/masters/states/{id}` | `StatePayload` | `StateMaster` |
| | `stateApi.activate` / `deactivate` | PATCH | `/masters/states/{id}/activate` \| `/deactivate` | — | `StateMaster` |
| City | `cityApi.list` | GET | `/masters/cities` | query: `search?, status?, stateId?, countryId?, page, size` | `PagedResponse<City>` |
| | `cityApi.getById` | GET | `/masters/cities/{id}` | — | `City` |
| | `cityApi.create` | POST | `/masters/cities` | `CityPayload {name, stateId}` | `City` |
| | `cityApi.update` | PUT | `/masters/cities/{id}` | `CityPayload` | `City` |
| | `cityApi.activate` / `deactivate` | PATCH | `/masters/cities/{id}/activate` \| `/deactivate` | — | `City` |
| Zone | `zoneApi.list` | GET | `/masters/zones` | query: `search?, status?, page, size` | `PagedResponse<Zone>` |
| | `zoneApi.getById` | GET | `/masters/zones/{id}` | — | `Zone` |
| | `zoneApi.create` | POST | `/masters/zones` | `ZonePayload {name, code?, countryId?, stateId?}` | `Zone` |
| | `zoneApi.update` | PUT | `/masters/zones/{id}` | `ZonePayload` | `Zone` |
| | `zoneApi.activate` / `deactivate` | PATCH | `/masters/zones/{id}/activate` \| `/deactivate` | — | `Zone` |
| Currency | `currencyApi.list` | GET | `/masters/currencies` | query: `search?, status?, page, size` | `PagedResponse<Currency>` |
| | `currencyApi.getById` | GET | `/masters/currencies/{id}` | — | `Currency` |
| | `currencyApi.create` | POST | `/masters/currencies` | `CurrencyPayload {name, code, symbol, decimalPlaces}` | `Currency` |
| | `currencyApi.update` | PUT | `/masters/currencies/{id}` | `CurrencyPayload` | `Currency` |
| | `currencyApi.activate` / `deactivate` | PATCH | `/masters/currencies/{id}/activate` \| `/deactivate` | — | `Currency` |
| Nationality | `nationalityApi.list` | GET | `/masters/nationalities` | query: `search?, status?, countryId?, page, size` | `PagedResponse<Nationality>` |
| | `nationalityApi.getById` | GET | `/masters/nationalities/{id}` | — | `Nationality` |
| | `nationalityApi.create` | POST | `/masters/nationalities` | `NationalityPayload {name, countryId}` | `Nationality` |
| | `nationalityApi.update` | PUT | `/masters/nationalities/{id}` | `NationalityPayload` | `Nationality` |
| | `nationalityApi.activate` / `deactivate` | PATCH | `/masters/nationalities/{id}/activate` \| `/deactivate` | — | `Nationality` |
| Unit | `unitApi.list` | GET | `/masters/units` | query: `search?, status?, page, size` | `PagedResponse<Unit>` |
| | `unitApi.getById` | GET | `/masters/units/{id}` | — | `Unit` |
| | `unitApi.create` | POST | `/masters/units` | `UnitPayload {name, symbol}` | `Unit` |
| | `unitApi.update` | PUT | `/masters/units/{id}` | `UnitPayload` | `Unit` |
| | `unitApi.activate` / `deactivate` | PATCH | `/masters/units/{id}/activate` \| `/deactivate` | — | `Unit` |
| Item Group | `itemGroupApi.list` | GET | `/masters/item-groups` | query: `search?, status?, page, size` | `PagedResponse<ItemGroup>` |
| | `itemGroupApi.getById` | GET | `/masters/item-groups/{id}` | — | `ItemGroup` |
| | `itemGroupApi.create` | POST | `/masters/item-groups` | `ItemGroupPayload {name, description?}` | `ItemGroup` |
| | `itemGroupApi.update` | PUT | `/masters/item-groups/{id}` | `ItemGroupPayload` | `ItemGroup` |
| | `itemGroupApi.activate` / `deactivate` | PATCH | `/masters/item-groups/{id}/activate` \| `/deactivate` | — | `ItemGroup` |
| HSN | `hsnApi.list` | GET | `/masters/hsn` | query: `search?, status?, page, size` | `PagedResponse<Hsn>` |
| | `hsnApi.getById` | GET | `/masters/hsn/{id}` | — | `Hsn` |
| | `hsnApi.create` | POST | `/masters/hsn` | `HsnPayload {hsnCode, description?, taxRates: HsnTaxRate[]}` | `Hsn` |
| | `hsnApi.update` | PUT | `/masters/hsn/{id}` | `HsnPayload` | `Hsn` |
| | `hsnApi.activate` / `deactivate` | PATCH | `/masters/hsn/{id}/activate` \| `/deactivate` | — | `Hsn` |

`PagedResponse<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number }`. All URLs above are relative to the axios instance's `baseURL` (`/api` — see `frontend/src/api/axios.ts`), i.e. the full path is `/api/masters/...`.

## 4. Backend

### Controllers

All 9 controllers follow the identical shape: `list` (GET, no `@PreAuthorize`), `getById` (GET, no `@PreAuthorize`), `create`/`update`/`activate`/`deactivate` (all `@PreAuthorize`-guarded). Base path is `/api/masters/...` (from class-level `@RequestMapping`).

| Master | Controller | Base path | `create`/`update`/`activate`/`deactivate` permission |
|---|---|---|---|
| Country | `CountryController` | `/api/masters/countries` | `hasAuthority('PERM_MASTER_MANAGE')` |
| State | `StateController` | `/api/masters/states` | `hasAuthority('PERM_MASTER_MANAGE')` |
| City | `CityController` | `/api/masters/cities` | `hasAuthority('PERM_MASTER_MANAGE')` |
| Zone | `ZoneController` | `/api/masters/zones` | `hasAuthority('PERM_MASTER_MANAGE')` |
| Currency | `CurrencyController` | `/api/masters/currencies` | `hasAuthority('PERM_MASTER_MANAGE')` |
| Nationality | `NationalityController` | `/api/masters/nationalities` | `hasAuthority('PERM_MASTER_MANAGE')` |
| Unit | `UnitController` | `/api/masters/units` | `hasAuthority('PERM_MASTER_MANAGE')` |
| Item Group | `ItemGroupController` | `/api/masters/item-groups` | `hasAuthority('PERM_MASTER_MANAGE')` |
| **HSN** | `HsnController` | `/api/masters/hsn` | **`hasAuthority('PERM_HSN_EDIT')`** (different permission from the other 8) |

Exact endpoints per controller (identical pattern, `{base}` = the base path above):
- `GET {base}?search=&status=&page=0&size=10` → `list(...)`
- `GET {base}/{id}` → `getById(id)`
- `POST {base}` (`@Valid @RequestBody {X}Request`) → `create(...)`, returns `201 CREATED`
- `PUT {base}/{id}` (`@Valid @RequestBody {X}Request`) → `update(...)`
- `PATCH {base}/{id}/activate` → `setStatus(id, {X}Status.ACTIVE)`
- `PATCH {base}/{id}/deactivate` → `setStatus(id, {X}Status.INACTIVE)`

Extra query params beyond `search/status/page/size`: `StateController.list` also takes `countryId`; `CityController.list` also takes `stateId` and `countryId`; `NationalityController.list` also takes `countryId`.

### DTOs

| Master | Request DTO (validation) | Response DTO (extra fields beyond id/status/timestamps) |
|---|---|---|
| Country | `CountryRequest`: `name` `@NotBlank`, `code` `@NotBlank` | `CountryResponse`: `name, code` |
| State | `StateRequest`: `name` `@NotBlank`, `code` (no constraint), `countryId` `@NotNull` | `StateResponse`: `name, code, countryId, countryName` |
| City | `CityRequest`: `name` `@NotBlank`, `stateId` `@NotNull` | `CityResponse`: `name, stateId, stateName, countryId, countryName` (country is resolved transitively via `city.getState().getCountry()`) |
| Zone | `ZoneRequest`: `name` `@NotBlank`, `code` (no constraint), `countryId`/`stateId` (both optional, no constraint) | `ZoneResponse`: `name, code, countryId, countryName, stateId, stateName` (country/state name are `null` when not set) |
| Currency | `CurrencyRequest`: `name` `@NotBlank`, `code` `@NotBlank`, `symbol` `@NotBlank`, `decimalPlaces` `@NotNull @Min(0) @Max(4)` | `CurrencyResponse`: `name, code, symbol, decimalPlaces` |
| Nationality | `NationalityRequest`: `name` `@NotBlank`, `countryId` `@NotNull` | `NationalityResponse`: `name, countryId, countryName` |
| Unit | `UnitRequest`: `name` `@NotBlank`, `symbol` `@NotBlank` | `UnitResponse`: `name, symbol` |
| Item Group | `ItemGroupRequest`: `name` `@NotBlank`, `description` (no constraint) | `ItemGroupResponse`: `name, description` |
| HSN | `HsnRequest`: `hsnCode` `@NotBlank`, `description` (no constraint), `taxRates: List<HsnTaxRateRequest>` `@Valid` (defaults to empty list) | `HsnResponse`: `hsnCode, description, taxRates: List<HsnTaxRateResponse>` |
| HSN tax rate | `HsnTaxRateRequest`: `taxPercent` `@NotNull`, `cgstPercent`/`sgstPercent`/`igstPercent`/`cessPercent` (no constraint), `effectiveFrom` `@NotNull` | `HsnTaxRateResponse`: `id, taxPercent, cgstPercent, sgstPercent, igstPercent, cessPercent, effectiveFrom` |

All list endpoints return `PagedResponse<T> { content, page, size, totalElements, totalPages }` (`backend/src/main/java/com/storehub/dto/PagedResponse.java`), built via `PagedResponse.fromPage(springDataPage)`.

Validation failures on `create`/`update` (any `@Valid` violation) are caught globally by `GlobalExceptionHandler.handleValidation` (`MethodArgumentNotValidException`) and returned as `400` `ApiError` with a `fieldErrors: Map<fieldName, message>` populated from each DTO field's own `message=` text above (e.g. `"Country name is required"`).

### Services

All 9 services (`CountryService`, `StateService`, `CityService`, `ZoneService`, `CurrencyService`, `NationalityService`, `UnitService`, `ItemGroupService`, `HsnService` in `backend/src/main/java/com/storehub/service/`) share the same shape: `search(...)`, `getById`, `create` (`@Transactional`), `update` (`@Transactional`), `setStatus` (`@Transactional`), and a `findOrThrow(id)` helper that throws `MasterNotFoundException(entityName, id)` → mapped by `GlobalExceptionHandler` to `404`.

Per-master business rules:

- **Country** (`CountryService`): uniqueness on `name` (case-insensitive) and `code` (case-insensitive), each checked with `existsBy...IgnoreCase` on create and `existsBy...IgnoreCaseAndIdNot` on update (self-exclusion), each throwing `BadRequestException` with a message naming the exact duplicate value, e.g. `"A country named '{name}' already exists"`.
- **State** (`StateService`): injects `CountryService` to `findOrThrow` the parent country. Rejects create/update with `BadRequestException` if the resolved country's `status == CountryStatus.INACTIVE` — **except** on update, this rejection is skipped if the country being set is the *same* country the state already belonged to (`!country.getId().equals(oldCountryId)`), i.e. you cannot move a state onto an inactive country, but an already-inactive-country state can still be edited without being forced to switch countries. Uniqueness is scoped to the country: `existsByNameIgnoreCaseAndCountryId(name, countryId)` (and `...AndIdNot` on update), so the same state name is allowed under two different countries.
- **City** (`CityService`): identical pattern to State but one level down — validates the parent `State` (via `StateService.findOrThrow`) is not `INACTIVE` (same same-parent exception on update), and uniqueness is `existsByNameIgnoreCaseAndStateId` (scoped per state).
- **Zone** (`ZoneService`): `country`/`state` are **both optional** — resolved only `if (request.getCountryId() != null)` / `if (request.getStateId() != null)` (no inactive-parent check for zones, unlike State/City/Nationality). Uniqueness on `name` only (global, case-insensitive), via `existsByNameIgnoreCase(AndIdNot)`.
- **Currency** (`CurrencyService`): uniqueness on both `code` and `name` (case-insensitive), each with the same create/update pattern as Country.
- **Nationality** (`NationalityService`): parent `Country` required and must not be `INACTIVE` (same same-parent-on-update exception as State/City). Uniqueness on `name` only, global (not scoped to country) — `existsByNameIgnoreCase(AndIdNot)`.
- **Unit** (`UnitService`): uniqueness on both `name` and `symbol` (case-insensitive), same pattern as Country/Currency.
- **Item Group** (`ItemGroupService`): uniqueness on `name` only. `description` is free text with no uniqueness or format constraint.
- **HSN** (`HsnService`): uniqueness on `hsnCode` (case-insensitive), same create/update pattern. **Tax-rate handling** (the distinguishing behavior):
  - `create()`: builds the `Hsn`, then calls `applyTaxRates(hsn, request.getTaxRates())`, which iterates every `HsnTaxRateRequest` in the payload, builds a new `HsnTaxRate` entity from it, and calls `hsn.addTaxRate(rate)` (which both appends to the list and sets the back-reference `rate.setHsn(this)`, required because the FK is on `HsnTaxRate.hsn_id`). `cascade = ALL` on the `@OneToMany` means saving the `Hsn` also persists every `HsnTaxRate` row in one transaction.
  - `update()`: **full replace**, not a diff/merge — calls `hsn.clearTaxRates()` (which nulls out each existing rate's `hsn` back-reference and clears the Java list; combined with `orphanRemoval = true` this causes Hibernate to `DELETE` every previously-stored `hsn_tax_rates` row for that HSN on flush) and then `applyTaxRates(hsn, request.getTaxRates())` again to re-insert whatever the request body currently contains. **Consequence**: any tax-rate row not resent in the `taxRates` array on an update is permanently deleted, including its `id` and history — there is no per-row create/update/delete distinction on the wire, the frontend always sends the complete current array.
  - No uniqueness or overlap validation exists across `effectiveFrom` dates within one HSN's tax rates — multiple rates with the same or overlapping `effectiveFrom` are accepted; the frontend's `latestRate()` picks the one with the lexicographically-latest `effectiveFrom` string for list-table display.

### Repository

Standard Spring Data `JpaRepository<Entity, Long>` interfaces in `backend/src/main/java/com/storehub/repository/`, each with hand-written `existsBy...` uniqueness checks and one `@Query`-annotated `search(...)` method (JPQL, case-insensitive `LIKE` on the searchable text field(s), `:status IS NULL OR ... = :status` to make status optional):

| Repository | `existsBy...` methods | `search(...)` params | Joins |
|---|---|---|---|
| `CountryRepository` | `existsByNameIgnoreCase`, `existsByNameIgnoreCaseAndIdNot`, `existsByCodeIgnoreCase`, `existsByCodeIgnoreCaseAndIdNot` | `search, status` | — |
| `StateRepository` | `existsByNameIgnoreCaseAndCountryId`, `existsByNameIgnoreCaseAndCountryIdAndIdNot` | `search, countryId, status` | `JOIN s.country c` |
| `CityRepository` | `existsByNameIgnoreCaseAndStateId`, `existsByNameIgnoreCaseAndStateIdAndIdNot` | `search, stateId, countryId, status` | `JOIN ci.state s JOIN s.country c` |
| `ZoneRepository` | `existsByNameIgnoreCase`, `existsByNameIgnoreCaseAndIdNot` | `search, status` | — |
| `CurrencyRepository` | `existsByCodeIgnoreCase(AndIdNot)`, `existsByNameIgnoreCase(AndIdNot)` | `search, status` | — |
| `NationalityRepository` | `existsByNameIgnoreCase(AndIdNot)` | `search, countryId, status` | `JOIN n.country c` |
| `UnitRepository` | `existsByNameIgnoreCase(AndIdNot)`, `existsBySymbolIgnoreCase(AndIdNot)` | `search, status` | — |
| `ItemGroupRepository` | `existsByNameIgnoreCase(AndIdNot)` | `search, status` | — |
| `HsnRepository` | `existsByHsnCodeIgnoreCase(AndIdNot)` | `search, status` (search matches `hsnCode` OR `description`) | — |

All `search()` queries sort by `Sort.by("name").ascending()` at the service layer (`Sort.by("hsnCode").ascending()` for HSN specifically, since it has no `name` field).

## 5. Database

### Tables

| Entity | `@Table` name | Key columns (from entity `@Column`) |
|---|---|---|
| `Country` | `countries` | `id, name (unique, len 100), code (unique, len 10), status (len 20), created_at, updated_at` |
| `State` | `states` | `id, name (len 100), code (len 10, nullable), country_id (FK, not null), status, created_at, updated_at` |
| `City` | `cities` | `id, name (len 100), state_id (FK, not null), status, created_at, updated_at` |
| `Zone` | `zones` | `id, name (unique, len 100), code (len 20, nullable), country_id (FK, nullable), state_id (FK, nullable), status, created_at, updated_at` |
| `Currency` | `currencies` | `id, name (unique, len 100), code (unique, len 10), symbol (len 5), decimal_places, status, created_at, updated_at` |
| `Nationality` | `nationalities` | `id, name (unique, len 100), country_id (FK, not null), status, created_at, updated_at` |
| `Unit` | `units` | `id, name (unique, len 50), symbol (unique, len 10), status, created_at, updated_at` |
| `ItemGroup` | `item_groups` | `id, name (unique, len 100), description (len 255, nullable), status, created_at, updated_at` |
| `Hsn` | `hsn_codes` | `id, hsn_code (unique, len 20), description (len 255, nullable), status, created_at, updated_at` |
| `HsnTaxRate` | `hsn_tax_rates` | `id, hsn_id (FK, not null), tax_percent (decimal 5,2, not null), cgst_percent, sgst_percent, igst_percent, cess_percent (all decimal 5,2, nullable), effective_from (date, not null), created_at` (no `updated_at` — rows are replace-only, never individually edited in place) |

Note the entity class is `Hsn` but the physical table is `hsn_codes`, not `hsns` or `hsn`.

### Relationships

Within this module:
- `State.country_id` → `countries.id` (`@ManyToOne` required)
- `City.state_id` → `states.id` (`@ManyToOne` required)
- `Zone.country_id` → `countries.id` (`@ManyToOne` **optional**)
- `Zone.state_id` → `states.id` (`@ManyToOne` **optional**)
- `Nationality.country_id` → `countries.id` (`@ManyToOne` required)
- `HsnTaxRate.hsn_id` → `hsn_codes.id` (`@ManyToOne` required; owning side of `Hsn.taxRates` `@OneToMany(cascade=ALL, orphanRemoval=true)`)

Downstream — other modules that reference these masters by FK (grepped across `backend/src/main/java/com/storehub/entity`):

| This master | Referenced by | FK column | Relationship |
|---|---|---|---|
| `Country` | `Store` | `store.country_id` | `@ManyToOne` |
| | `Party` | `party.country_id` | `@ManyToOne` |
| `State` | `Store` | `store.state_id` | `@ManyToOne` |
| | `Party` | `party.state_id` | `@ManyToOne` |
| | `Employee` | `employee.state_id` | `@ManyToOne` |
| | `PartyAddress` | `party_addresses.state_id` | `@ManyToOne` |
| | `BusinessGstConfig` | `business_gst_config.state_id` | `@ManyToOne` |
| `City` | `Store` | `store.city_id` | `@ManyToOne` |
| | `Party` | `party.city_id` | `@ManyToOne` |
| | `Employee` | `employee.city_id` | `@ManyToOne` |
| | `PartyAddress` | `party_addresses.city_id` | `@ManyToOne` |
| `Zone` | `Store` | `store.zone_id` | `@ManyToOne` |
| `Unit` | `Product` | `product.purchase_unit_id` and `product.sale_unit_id` (**two separate FKs**, not one) | `@ManyToOne` |
| `ItemGroup` | `Product` | `product.item_group_id` | `@ManyToOne` |
| `Hsn` | `Product` | `product.hsn_id` | `@ManyToOne` |
| `Currency` | *(none found)* | — | `Currency` has **no** downstream FK reference anywhere in the entity package — it is a standalone lookup not yet wired into `Product`/`Store`/`Party`/anything else (`NOT FOUND IN CURRENT CODEBASE`) |
| `Nationality` | *(none found)* | — | Same — no other entity has a `nationality_id` FK (`NOT FOUND IN CURRENT CODEBASE`) |

## 6. Validation

- Field-level (Bean Validation, enforced by `@Valid @RequestBody` on every `create`/`update` controller method): see the DTO table in §4 — `@NotBlank` / `@NotNull` on required fields, `@Min(0)`/`@Max(4)` on `Currency.decimalPlaces`. A violation throws `MethodArgumentNotValidException`, caught globally and returned as `400` with `fieldErrors`.
- Business-rule validation (in the service layer, throwing `BadRequestException` → `400`, message-only, no `fieldErrors` map — the field this refers to is implicit from the message text, not a DTO field name):
  - Duplicate name/code/symbol checks (all 9 masters, case-insensitive, self-excluded on update) — see §4 Services for the exact scope of each (global vs. parent-scoped).
  - Inactive-parent checks: State/City/Nationality reject a create/update that would attach to an `INACTIVE` parent (Country for State/Nationality, State for City), with an exception carved out for editing a record without changing its existing (already inactive) parent.
- No cross-field validation exists beyond the above (e.g. no check that `HsnTaxRate.cgstPercent + sgstPercent` sums to `taxPercent`, and no check preventing duplicate/overlapping `effectiveFrom` dates within one HSN's tax rates — `NOT FOUND IN CURRENT CODEBASE`).

## 7. Authentication / Authorization

- **Baseline**: `SecurityConfig.filterChain` requires `anyRequest().authenticated()` for everything except `/api/auth/**` and `/actuator/health` — so every one of these endpoints, including the unguarded `list`/`getById`, requires a valid JWT (`JwtAuthenticationFilter`).
- **`list` / `getById`**: no `@PreAuthorize` on any of the 9 controllers — any authenticated user, regardless of role/permission, can view. (In practice every `Role` in `RolePermissions` that has any master-related access also has `MASTER_VIEW`, but that permission is never actually checked by these two endpoints — it is enforced only implicitly by every role granting it.)
- **`create` / `update` / `activate` / `deactivate`**: `@PreAuthorize("hasAuthority('PERM_MASTER_MANAGE')")` on all 8 non-HSN controllers; **HSN uniquely uses `@PreAuthorize("hasAuthority('PERM_HSN_EDIT')")` instead** — a different permission, so a role that has `MASTER_MANAGE` but not `HSN_EDIT` (or vice versa) would be able to manage the other 8 masters but not HSN, or vice versa. In the current `RolePermissions` mapping, only `ADMIN` and `STORE_MANAGER` hold `MASTER_MANAGE`; `HSN_EDIT` is not granted to any role in `RolePermissions.build()` except implicitly via `ADMIN`'s `EnumSet.allOf(Permission.class)` and `STORE_MANAGER`'s copy of it (HSN_EDIT is not in `STORE_MANAGER`'s removal list, so `STORE_MANAGER` retains it too).
- `PERM_<name>` authorities are minted per-request from `UserPrincipal.getAuthorities()`, which iterates `RolePermissions.forRole(user.getRole())` and adds a `SimpleGrantedAuthority("PERM_" + permission.name())` per permission the user's role has (`backend/src/main/java/com/storehub/security/UserPrincipal.java`).
- **Frontend route gate** (`frontend/src/App.tsx`, `frontend/src/components/ManagerRoute.tsx`): all `/masters/*` routes, including all 9 of these master pages, are wrapped in `<Route element={<ManagerRoute />}>` with no `extraRoles`, so `ManagerRoute` only allows `user.role` to be `ADMIN` or `STORE_MANAGER` — any other authenticated role is redirected to `/unauthorized` before it can even reach the page, **regardless of what `MASTER_VIEW`/`MASTER_MANAGE`/`HSN_EDIT` the backend would otherwise allow them**. This means the backend's more granular view-only access for ACCOUNTANT/SALES_USER/PURCHASE_USER/INVENTORY_USER/STAFF (all of whom hold `MASTER_VIEW` per `RolePermissions`) is currently unreachable through this UI — those roles can never navigate to a master page at all, only ADMIN/STORE_MANAGER can.

## 8. Permissions

From `backend/src/main/java/com/storehub/entity/Permission.java` and `backend/src/main/java/com/storehub/service/RolePermissions.java`:

- `MASTER_VIEW` (module `"Master"`) — doc comment on the enum literal itself states: *"Simple lookup masters not individually named by the spec (Currency, Country, State, City, Zone, Nationality, Unit, Item Group, Payment Method, Business GST Config, Expense Category, Supplier, Customer)."* Held by `ADMIN`, `STORE_MANAGER`, `ACCOUNTANT`, `SALES_USER`, `PURCHASE_USER`, `INVENTORY_USER`, `STAFF` — i.e. every role. (Not actually enforced by any `@PreAuthorize` in this module — see §7.)
- `MASTER_MANAGE` (module `"Master"`) — governs create/update/activate/deactivate for the 8 non-HSN masters. Held by `ADMIN` and `STORE_MANAGER` only.
- `HSN_VIEW` / `HSN_EDIT` (module `"Master"`) — `HSN_EDIT` governs HSN's create/update/activate/deactivate specifically (see §7); `HSN_VIEW` is defined but **not referenced by any `@PreAuthorize` in `HsnController`** (`NOT FOUND IN CURRENT CODEBASE` as an enforced check — HSN's `list`/`getById` are unguarded, same as the other 8). Both held by `ADMIN` and (via its `EnumSet.copyOf(admin)` minus an explicit removal list that does not name `HSN_VIEW`/`HSN_EDIT`) `STORE_MANAGER`.

## 9. Transaction Handling

- Every mutating service method (`create`, `update`, `setStatus`) across all 9 services is annotated `@Transactional` (Spring, class-level import `org.springframework.transaction.annotation.Transactional`, method-level use).
- `search`/`getById` (read paths) are **not** annotated `@Transactional` — they run in default (non-transactional, or DataSource-autocommit) read mode. Lazy-loaded relations (`State.country`, `City.state`, `Zone.country`/`state`, `Nationality.country`) are still safely dereferenced inside `XxxResponse.fromEntity(...)` because that mapping happens inside the (still-open, request-scoped, OSIV-backed — `spring.jpa.open-in-view` default) Hibernate session; no explicit `@Transactional(readOnly = true)` is used anywhere in this module (`NOT FOUND IN CURRENT CODEBASE`).
- HSN's `create`/`update` transaction is the one case where multiple entities are written atomically in one call: saving the parent `Hsn` (via `hsnRepository.save(hsn)`) cascades (`CascadeType.ALL`) to insert/delete every `HsnTaxRate` child row in the same transaction — if any part fails, the whole HSN + its tax rates roll back together.
- No manual/programmatic transaction management (`TransactionTemplate`, `@Transactional(propagation=...)`, etc.) is used anywhere in this module — plain declarative `@Transactional` only.

## 10. Error Handling

Centralized in `backend/src/main/java/com/storehub/exception/GlobalExceptionHandler.java` (`@RestControllerAdvice`), producing a uniform `ApiError { timestamp, status, error, message, path, fieldErrors? }` body (`fieldErrors` is omitted from JSON via `@JsonInclude(NON_NULL)` when null):

| Exception | Thrown by | HTTP status | `fieldErrors`? |
|---|---|---|---|
| `MethodArgumentNotValidException` | Spring, from `@Valid` DTO failures on `create`/`update` | `400 BAD_REQUEST` | Yes — one entry per invalid field, DTO field name → its `message=` |
| `BadRequestException` | Service-layer business rules (duplicate name/code, inactive parent) | `400 BAD_REQUEST` | No — message only |
| `MasterNotFoundException` | `findOrThrow(id)` in every service, for a nonexistent `id` on `getById`/`update`/`activate`/`deactivate` | `404 NOT_FOUND` | No — message is `"{EntityName} not found with id: {id}"` |
| `AccessDeniedException` | Spring Security, when `@PreAuthorize` fails | `403 FORBIDDEN` | No — fixed message `"You do not have permission to access this resource"` |
| Any other unhandled `Exception` | — | `500 INTERNAL_SERVER_ERROR` | No — fixed generic message; full exception is logged server-side via `log.error(...)` |

On the frontend, `frontend/src/utils/apiError.ts`'s `parseApiError(err, fallbackMessage)` turns this into a `{message, fieldErrors}` pair: if the response carries `fieldErrors`, it builds one combined sentence (`"Field Name: reason | Field Name2: reason2"`, field names humanized by inserting spaces before capitals) for the top-level `Alert`, while also returning the raw `fieldErrors` map so `MasterCrudPage`'s `renderForm` callback can mark each `Input` `invalid` and show its own inline `<p>` message. If the request never reached the server at all (`!err.response`), it instead returns a fixed "Could not reach the server..." message with no field errors.

## 11. Audit Flow

**NOT FOUND IN CURRENT CODEBASE.** None of the 9 services in this module (`CountryService`, `StateService`, `CityService`, `ZoneService`, `CurrencyService`, `NationalityService`, `UnitService`, `ItemGroupService`, `HsnService`) import or call `AuditService` — confirmed by grepping every file that references `AuditService` across `backend/src/main/java/com/storehub`, which returns 21 files (e.g. `ProductService`, `InventoryService`, `SaleService`, `UserService`, …) and none of the 9 master services. Create/update/activate/deactivate on these lookup masters produce **no audit log entry** (`AuditLog` / `audit_log` table is untouched by this module).

## 12. Important Side Effects

- **No hard delete anywhere.** There is no `DELETE` endpoint or repository `deleteById` call exposed for any of these 9 masters — the only way to "remove" a record from active use is `PATCH .../deactivate`, which just flips `status` to `INACTIVE`. Deactivated records remain fully queryable (via `?status=INACTIVE` or omitting the filter) and are never physically removed.
- **Deactivating a parent does not cascade-deactivate or block existing children.** E.g. deactivating a Country does not touch its States, and an existing State already pointing at that now-inactive Country is left as-is — the inactive-parent check in `StateService`/`CityService`/`NationalityService` only fires on a *new* create or on *changing* a record's parent via update, never retroactively.
- **HSN update fully replaces the tax-rate child rows** (see §4 Services) — any row omitted from the `taxRates` array on a `PUT` is hard-deleted (`orphanRemoval = true`), including its database `id` and its `created_at`. There is no soft-delete or history retention for individual tax-rate rows; the only "history" that exists is whatever rows the caller chooses to keep resending.
- `Currency` and `Nationality` are the two masters in this group with zero downstream FK consumers found anywhere else in the codebase (§5) — creating/deactivating/editing them has no effect on any other module's data today.
- Activating/deactivating any of these 8 non-HSN masters or HSN itself does **not** validate or affect records elsewhere that already reference it by FK (e.g. deactivating a `Unit` that a `Product.saleUnit` still points to does not warn, block, or update that `Product` — `NOT FOUND IN CURRENT CODEBASE`).

## 13. Dependencies on Other Modules

Consumers of these 9 masters, grepped from `backend/src/main/java/com/storehub/entity` (FK) and cross-referenced with frontend master pages using the same `*Api` clients:

| This master | Consuming module / entity | How |
|---|---|---|
| Country, State, City | **Store** (`Store` entity / Store master) | `store.country_id`, `store.state_id`, `store.city_id` — a Store's address is built from these three masters |
| Zone | **Store** | `store.zone_id` — a Store can be assigned a distribution/sales zone |
| Country, State, City | **Party** (`Party` entity — Supplier/Customer master) | `party.country_id`, `party.state_id`, `party.city_id` for the party's primary address |
| State, City | **PartyAddress** (`Party`'s multi-address child table) | `party_addresses.state_id`, `party_addresses.city_id` — each of a party's additional addresses can independently pick a state/city |
| State, City | **Employee** (`Employee` master) | `employee.state_id`, `employee.city_id` for the employee's address |
| State | **BusinessGstConfig** (seller GST registration singleton) | `business_gst_config.state_id`, used for GST place-of-supply determination |
| Unit | **Product** (Item Master) | `product.purchase_unit_id` and `product.sale_unit_id` — a product's purchase and sale units are independently selectable from the `units` table |
| ItemGroup | **Product** | `product.item_group_id` — a product's classification group |
| Hsn | **Product** | `product.hsn_id` — a product's HSN code, which in turn determines its `HsnTaxRate` history for GST calculations |
| Country, State, City, Zone, Currency, Nationality, Unit, Item Group | **`MastersDashboard.tsx`** | Reads each master's `totalElements` via `list({size: 1})` to show a record count on its dashboard card |

No module outside this one writes to any of these 9 masters' tables — all mutation goes through the 9 services above.

## 14. Key Operation Flows

### Country — Create → Edit → Activate/Deactivate

**Create:**
1. UI: `CountryMaster.tsx` → "Add Country" → `openAdd()` (from `MasterCrudPage`) opens the shared `Dialog` with `emptyValues = {name:'', code:''}`.
2. User fills Name/Code → submits → `MasterCrudPage.handleSubmit` → `create(values)`, which is `countryApi.create`.
3. API: `countryApi.create(payload)` → `POST /api/masters/countries` with body `CountryPayload {name, code}`.
4. Controller: `CountryController.create(@Valid @RequestBody CountryRequest request)` — `@PreAuthorize("hasAuthority('PERM_MASTER_MANAGE')")` checked first by Spring Security; then `@Valid` runs `CountryRequest`'s `@NotBlank` checks on `name`/`code`.
5. DTO → Service: `countryService.create(request)`.
6. Service: `CountryService.create` — `countryRepository.existsByNameIgnoreCase(name)` and `existsByCodeIgnoreCase(code)`; if either is true, throws `BadRequestException("A country named '...' already exists")` / `"...code '...' already exists"`. Otherwise builds `Country.builder().name(...).code(...).build()` (status left `null` so `@PrePersist` defaults it to `ACTIVE`) and calls `countryRepository.save(country)`.
7. Repository/Database: Hibernate `INSERT INTO countries (name, code, status, created_at, updated_at) VALUES (...)` (`status='ACTIVE'`, both timestamps = now).
8. Response: `CountryResponse.fromEntity(saved)` → controller wraps it `ResponseEntity.status(201).body(...)`.
9. UI: `MasterCrudPage` gets the resolved promise → `toast.success("Country created successfully")` → closes dialog → `load()` refetches the list, showing the new row with an `ACTIVE` green `Badge`.

**Edit:** row "⋯" → "Edit" → `openEdit(item)` seeds the form from `toFormValues(c) = {name: c.name, code: c.code}` → submit → `update(item.id, values)` = `countryApi.update(id, payload)` → `PUT /api/masters/countries/{id}` → `CountryController.update` (`@PreAuthorize PERM_MASTER_MANAGE`, `@Valid`) → `CountryService.update(id, request)`: `findOrThrow(id)` (404 `MasterNotFoundException` if missing) → re-checks name/code uniqueness with `...AndIdNot(value, id)` (skipped entirely if the value didn't change, via the `!country.getName().equalsIgnoreCase(...)` guard) → mutates the managed entity's `name`/`code` → `save()` → `@PreUpdate` bumps `updated_at` → same `CountryResponse` shape back → UI toasts `"Country updated successfully"` and reloads.

**Activate/Deactivate:** row "⋯" → "Deactivate" (shown when `status==='ACTIVE'`) or "Activate" → `toggleStatus(item)` → `deactivate(item.id)`/`activate(item.id)` = `countryApi.deactivate`/`activate(id)` → `PATCH /api/masters/countries/{id}/deactivate` (or `/activate`) → `CountryController.deactivate`/`activate` (`@PreAuthorize PERM_MASTER_MANAGE`) → `countryService.setStatus(id, CountryStatus.INACTIVE|ACTIVE)` → `findOrThrow` → `country.setStatus(...)` → `save()` (no uniqueness/business check at all on this path) → `CountryResponse` back → UI toasts and reloads, row's `Badge` flips to `muted`/`INACTIVE` or `success`/`ACTIVE`.

### HSN — Create → Edit → Activate/Deactivate (child tax-rate pattern)

**Create:**
1. UI: `HsnMaster.tsx` (its own page, not `MasterCrudPage`) → "Add HSN Code" → `openAdd()` seeds `EMPTY = {hsnCode:'', description:'', taxRates:[emptyTaxRate()]}` (one blank rate row, `taxPercent:0`, `effectiveFrom` = today).
2. User fills HSN Code/Description, then one or more tax-rate rows (Tax %/CGST %/SGST %/IGST %/Effective From), using "Add Rate"/trash-icon to grow/shrink `values.taxRates` — all client-side state, no network call per row.
3. Submit → `handleSubmit` → `hsnApi.create(values)` → `POST /api/masters/hsn` with body `HsnPayload {hsnCode, description, taxRates: HsnTaxRate[]}`.
4. Controller: `HsnController.create` — **`@PreAuthorize("hasAuthority('PERM_HSN_EDIT')")`** (not `MASTER_MANAGE`, unlike the other 8) → `@Valid` validates `HsnRequest.hsnCode` `@NotBlank` and, via `@Valid` cascading onto `taxRates`, each `HsnTaxRateRequest.taxPercent`/`effectiveFrom` `@NotNull`.
5. Service: `HsnService.create(request)` — `hsnRepository.existsByHsnCodeIgnoreCase(...)` uniqueness check (`BadRequestException` if duplicate) → builds `Hsn.builder().hsnCode(...).description(...).build()` → `applyTaxRates(hsn, request.getTaxRates())`: for each `HsnTaxRateRequest`, builds an `HsnTaxRate` and calls `hsn.addTaxRate(rate)` (appends to the in-memory list **and** sets `rate.setHsn(hsn)` so the FK is populated) → `hsnRepository.save(hsn)`.
6. Database: because `Hsn.taxRates` is `@OneToMany(cascade = CascadeType.ALL)`, one `save()` call produces both `INSERT INTO hsn_codes (...)` and one `INSERT INTO hsn_tax_rates (hsn_id, tax_percent, cgst_percent, sgst_percent, igst_percent, cess_percent, effective_from, created_at)` per tax-rate row, all in the same transaction (`@Transactional` on the service method).
7. Response: `HsnResponse.fromEntity(saved)` maps `taxRates` via `HsnTaxRateResponse::fromEntity` for each child row, including its generated `id`.
8. UI: toast `"HSN code created successfully"`, dialog closes, list reloads; the new row shows the *first* rate's `taxPercent` in the "Current Tax %" column (via `latestRate()` picking the max `effectiveFrom`).

**Edit (the child-replace step):** "Edit" → `openEdit(item)` seeds `taxRates: item.taxRates.length > 0 ? item.taxRates.map(r => ({...r})) : [emptyTaxRate()]` (shallow-copies existing rows, including their `id`s, so they can be edited in place client-side) → user edits/add/removes rows → submit → `hsnApi.update(id, values)` → `PUT /api/masters/hsn/{id}` → `HsnController.update` (`PERM_HSN_EDIT`) → `HsnService.update(id, request)`: `findOrThrow` → uniqueness re-check on `hsnCode` (`AndIdNot`) → sets `hsnCode`/`description` → **`hsn.clearTaxRates()`** (nulls each existing `HsnTaxRate.hsn` and empties the Java list — combined with `orphanRemoval=true` this schedules a `DELETE FROM hsn_tax_rates WHERE hsn_id = ?` for every row that was there) → **`applyTaxRates(hsn, request.getTaxRates())`** again, which re-inserts a brand-new `HsnTaxRate` row (new auto-generated `id`) for every entry currently in the submitted array — even ones that look unchanged from the client's perspective get a new database row/id. → `save()`. Net effect: the request body's `taxRates` array is the single source of truth for that HSN's entire tax-rate history after every update; nothing is preserved that wasn't resent.

**Activate/Deactivate:** identical mechanics to Country's (`toggleStatus` → `hsnApi.activate`/`deactivate(id)` → `PATCH /api/masters/hsn/{id}/activate|deactivate`, `PERM_HSN_EDIT` → `HsnService.setStatus` → `save()`), and does **not** touch `taxRates` at all — deactivating an HSN code leaves its tax-rate rows completely untouched in the database.

## 15. Manual Changes

**To add a brand-new simple master following this exact pattern** (e.g. a hypothetical "Warehouse Type" master with just a unique `name`), touch these files in this order:

1. **Entity**: `backend/src/main/java/com/storehub/entity/WarehouseType.java` — copy the shape of `ItemGroup.java` (id, unique `name`, `status`, `createdAt`/`updatedAt`, `@PrePersist`/`@PreUpdate` with `status` defaulting to `ACTIVE`). Set `@Table(name = "warehouse_types")`.
2. **Status enum**: `backend/src/main/java/com/storehub/entity/WarehouseTypeStatus.java` — `enum { ACTIVE, INACTIVE }`, copy `ItemGroupStatus.java`.
3. **Repository**: `backend/src/main/java/com/storehub/repository/WarehouseTypeRepository.java` — `extends JpaRepository<WarehouseType, Long>` with `existsByNameIgnoreCase`/`existsByNameIgnoreCaseAndIdNot` and a `@Query`-based `search(search, status, pageable)`, copy `ItemGroupRepository.java`.
4. **DTOs**: `backend/src/main/java/com/storehub/dto/WarehouseTypeRequest.java` (`@NotBlank name`) and `WarehouseTypeResponse.java` (`fromEntity` static factory), copy `ItemGroupRequest`/`ItemGroupResponse`.
5. **Service**: `backend/src/main/java/com/storehub/service/WarehouseTypeService.java` — `search`/`getById`/`create`/`update`/`setStatus`/`findOrThrow`, copy `ItemGroupService.java` (or `CountryService.java` if it needs a parent-master FK, or `HsnService.java` if it needs a child collection).
6. **Controller**: `backend/src/main/java/com/storehub/controller/WarehouseTypeController.java` — `@RequestMapping("/api/masters/warehouse-types")`, copy `ItemGroupController.java` exactly, including `@PreAuthorize("hasAuthority('PERM_MASTER_MANAGE')")` on the 4 mutating endpoints (reuse `MASTER_MANAGE`/`MASTER_VIEW` unless the new master needs its own dedicated permission the way HSN does with `HSN_EDIT` — if so, add a new `Permission` enum constant and grant it to the appropriate roles in `RolePermissions.build()`).
7. **Frontend type**: add `WarehouseType`/`WarehouseTypePayload` interfaces to `frontend/src/types/masters.ts`, copying the `ItemGroup`/`ItemGroupPayload` block.
8. **Frontend API client**: add a `warehouseTypeApi` export to `frontend/src/api/mastersApi.ts`, copying the `itemGroupApi` block (`list`/`getById`/`create`/`update`/`activate`/`deactivate`).
9. **Frontend page**: `frontend/src/pages/masters/WarehouseTypeMaster.tsx` — copy `ItemGroupMaster.tsx` (or `CountryMaster.tsx`/`StateMaster.tsx` if a parent-master dropdown is needed), configuring `<MasterCrudPage<WarehouseType, WarehouseTypePayload>>` with the new icon/title/columns/`renderForm`/api callbacks.
10. **MastersDashboard card**: add one entry to the `CARDS` array in `frontend/src/pages/MastersDashboard.tsx` (`key, name, description, icon, path: '/masters/warehouse-types', loadCount: () => warehouseTypeApi.list({size:1}).then(r => r.data.totalElements)`).
11. **App.tsx route**: add `import WarehouseTypeMaster from './pages/masters/WarehouseTypeMaster';` and `<Route path="/masters/warehouse-types" element={<WarehouseTypeMaster />} />` inside the existing `<Route element={<ManagerRoute />}>` block that wraps `/masters/*` (around line 259 of `frontend/src/App.tsx`).
12. **Nav**: individual masters are **not** listed in the sidebar directly — `frontend/src/components/layout/nav-items.ts` has exactly one entry for the whole module, `{ label: 'Masters', path: '/masters', icon: Landmark, allowedRoles: ['ADMIN', 'STORE_MANAGER'] }`, which links to `MastersDashboard`'s card grid (step 10's `CARDS` array is what actually surfaces the new master to users). No nav change is needed for a new simple master beyond step 10, as long as it's reached via a dashboard card. (`allowedRoles` here mirrors `ManagerRoute`'s `ADMIN`/`STORE_MANAGER` gate — if that ever changes, update both in sync.)

**To add a field to an existing master** (e.g. adding `phoneCode` to `Country`): add the `@Column` to `Country.java` → add it to `CountryRequest` (with any `@NotBlank`/`@NotNull`/etc. if required) and `CountryResponse` (+ its `fromEntity` mapping) → add it to `CountryService.create`/`update` (setting it on the builder/entity) → add it to the frontend `Country`/`CountryPayload` interfaces in `types/masters.ts` → add the corresponding input to `CountryMaster.tsx`'s `renderForm` (and to its `toFormValues`/`emptyValues` if it's editable) and, if it should be visible in the list, to its `columns` array.

**To change HSN's tax-rate handling** (e.g. to preserve tax-rate history instead of full-replace-on-update): the only place to change is `HsnService.update()` in `backend/src/main/java/com/storehub/service/HsnService.java` — replace the `hsn.clearTaxRates(); applyTaxRates(hsn, request.getTaxRates());` full-replace with per-row diff logic (match incoming `HsnTaxRateRequest`s that carry an existing `id` against `hsn.getTaxRates()` to update in place, add new ones without an `id`, and only remove ones genuinely dropped from the request). The DTOs (`HsnTaxRateRequest`/`HsnTaxRateResponse`) already carry `id` end-to-end (frontend `HsnTaxRate.id?: number` in `types/masters.ts`, sent back as-is on edit via `openEdit`'s `item.taxRates.map(r => ({...r}))`), so no frontend or DTO shape change would be needed for that specific improvement — only the service-layer replace logic and (if row-level immutability of `createdAt`/`effectiveFrom` matters) possibly adding an `updatedAt` column to `HsnTaxRate`/`hsn_tax_rates`.
