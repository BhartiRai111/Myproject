# User & Employee Management Module

## 1. Overview

This module covers two related but distinct record types in StoreHub:

- **User** (`com.storehub.entity.User`, table `users`) — a login account: email + password, a `Role`, a `UserStatus` (ACTIVE/INACTIVE), and optional links to an `Employee` and to a `Store` (`currentStore`).
- **Employee** (`com.storehub.entity.Employee`, table `employees`) — a master/HR record (name, mobile, designation, department, address, city/state, joining date) that need not have any login at all.

A `User` may optionally be linked to one `Employee` (`User.employee`, unique FK `employee_id`), and an `Employee` may optionally have one `User` login pointed at it. The link is enforced unique from the `User` side only — `Employee` itself carries no FK back to `User`; the reverse lookup is done via `UserRepository.findByEmployeeId`.

Two controllers/services back this module:
- `UserController` / `UserService` — login-account CRUD, status, password management, admin-safety rules, store assignment (`/api/users/**`).
- `EmployeeController` / `EmployeeService` — HR master CRUD, employee-code generation, informational store tagging (`/api/masters/employees/**`).

The module never physically deletes a `User` or `Employee` — only ever flips `status` between `ACTIVE`/`INACTIVE`, per the code comment on `UserService`: *"Never physically deletes a user (spec section 52) — only ever ACTIVE/INACTIVE, so historical audit/transaction references stay valid."*

## 2. Frontend

### Files and Components

| File | Role |
|---|---|
| `frontend/src/pages/Users.tsx` | User Management page: search/filter/paginate users, table with actions (View, Edit, Reset Password, Store Access, Activate/Deactivate). Hosts `UserFormModal`, `UserViewModal`, `ResetPasswordDialog`, and `StoreAccessDialog` (store-assignment dialog, not in this module's required file set but wired from here). |
| `frontend/src/components/UserFormModal.tsx` | Add/Edit User modal. Fields: firstName, lastName, email, mobile, password (add-mode only), role, status, employeeId (dropdown of ACTIVE employees). Surfaces backend `fieldErrors` inline per field. |
| `frontend/src/components/UserViewModal.tsx` | Read-only detail view: mobile, role, status, linked employee, mustChangePassword, lastLogin, createdAt/updatedAt. |
| `frontend/src/components/ResetPasswordDialog.tsx` | ADMIN-initiated password reset for another user: newPassword + confirmPassword only (no current password). |
| `frontend/src/api/userApi.ts` | Axios wrapper for all `/api/users/**` endpoints. |
| `frontend/src/types/user.ts` | `Role`, `UserStatus`, `User`, `UserCreatePayload`, `UserUpdatePayload`, `AdminPasswordResetPayload`, `PasswordChangePayload`, `PagedResponse<T>`, `ApiErrorResponse`. |
| `frontend/src/pages/masters/EmployeeMaster.tsx` | Employee master CRUD page, built on the generic `MasterCrudPage<Employee, EmployeePayload>` component (list/create/edit/activate/deactivate), with a "Generate Code" button calling `employeeApi.generateCode()`. |
| `frontend/src/api/mastersApi.ts` (`employeeApi` export) | Axios wrapper for `/api/masters/employees/**` (list, getById, generateCode, create, update, activate, deactivate). |
| `frontend/src/types/masters.ts` (`Employee`, `EmployeePayload`) | Employee response/request shapes for the frontend. |
| `frontend/src/utils/apiError.ts` | `parseApiError` — turns an axios error's `ApiErrorResponse` into a display message + `fieldErrors` map, used by every form above. |

### Frontend Flow

**List users** — `Users.tsx` `loadUsers()` calls `userApi.list({ search, role, status, page, size })` on mount and whenever `page`/`roleFilter`/`statusFilter` change, or the search form is submitted; renders into the table with `TableSkeleton` while loading and `EmptyState` when empty.

**Create user** — "Add User" button opens `UserFormModal` in `mode: 'add'` with `EMPTY_FORM` (role defaults `STAFF`, status `ACTIVE`). On submit, `Users.tsx handleFormSubmit` calls `userApi.create(...)` with `employeeId` coerced from `''` to `null`. Validation errors thrown by the API surface as `error`/`fieldErrors` inside the modal (caught in `UserFormModal.handleSubmit`); success shows a toast, closes the modal, and reloads the list.

**Edit user** — "Edit" menu item opens the same modal in `mode: 'edit'` pre-filled from the selected `User` (password field is omitted/not sent). Submit calls `userApi.update(id, ...)`.

**Deactivate/reactivate user** — "Deactivate"/"Activate" menu item calls `toggleStatus(user)`, which calls `userApi.updateStatus(user.id, newStatus)` (flips ACTIVE↔INACTIVE) and reloads on success; errors are toasted via `parseApiError`.

**Reset password** — "Reset Password" menu item opens `ResetPasswordDialog` for that user; submit calls `userApi.resetPassword(user.id, { newPassword, confirmPassword })`; success toast tells the admin the user must change it at next login.

**Assign stores** — "Store Access" menu item opens `StoreAccessDialog` (component file not included in this module's read set, but its API calls are `userApi.getAssignedStores(id)` / `userApi.assignStores(id, storeIds)`), replace-all semantics.

**Self password change** — `userApi.changeOwnPassword(payload)` exists in `userApi.ts` (`PUT /users/me/password`) but no calling UI component was found under the read files for this module — NOT FOUND IN CURRENT CODEBASE (the calling page, if any, lives outside this module's file set).

**Employee — list** — `EmployeeMaster.tsx` passes `fetchList={({search,status,page,size}) => employeeApi.list(...)}` into the generic `MasterCrudPage`, which handles the paging/search UI itself.

**Employee — create** — `MasterCrudPage`'s create flow calls `employeeApi.create` with the form built in `renderForm`; a "Generate Code" icon button calls `employeeApi.generateCode()` and fills `employeeCode` into the form before submit (does not auto-save).

**Employee — edit** — `MasterCrudPage`'s edit flow calls `employeeApi.update(id, payload)`; `toFormValues` maps an `Employee` response back into `EmployeePayload` for the form.

**Employee — deactivate/activate** — `MasterCrudPage` wires `activate={employeeApi.activate}` / `deactivate={employeeApi.deactivate}`.

**Employee — assign stores** — `EmployeeController` exposes `GET/PUT /api/masters/employees/{id}/stores`, but `employeeApi` in `mastersApi.ts` does **not** export `assignStores`/`getAssignedStores` — NOT FOUND IN CURRENT CODEBASE (backend endpoint exists, no frontend caller wired in the read files).

## 3. API Calls

| Function | HTTP Method | URL | Request shape | Response shape |
|---|---|---|---|---|
| `userApi.list` | GET | `/users` | query params `search?`, `role?`, `status?`, `page`, `size` | `PagedResponse<User>` |
| `userApi.getById` | GET | `/users/{id}` | — | `User` |
| `userApi.create` | POST | `/users` | `UserCreatePayload` | `User` |
| `userApi.update` | PUT | `/users/{id}` | `UserUpdatePayload` | `User` |
| `userApi.updateStatus` | PATCH | `/users/{id}/status` | `{ status: UserStatus }` | `User` |
| `userApi.resetPassword` | POST | `/users/{id}/reset-password` | `AdminPasswordResetPayload` (`{ newPassword, confirmPassword }`) | `User` |
| `userApi.changeOwnPassword` | PUT | `/users/me/password` | `PasswordChangePayload` (`{ currentPassword, newPassword, confirmPassword }`) | `void` (204) |
| `userApi.getEffectivePermissions` | GET | `/users/{id}/effective-permissions` | — | `Permission[]` |
| `userApi.getAssignedStores` | GET | `/users/{id}/stores` | — | `number[]` |
| `userApi.assignStores` | PUT | `/users/{id}/stores` | `{ storeIds: number[] }` | `User` |
| `employeeApi.list` | GET | `/masters/employees` | query params via `buildParams`: `search?`, `status?`, `page`, `size` | `PagedResponse<Employee>` |
| `employeeApi.getById` | GET | `/masters/employees/{id}` | — | `Employee` |
| `employeeApi.generateCode` | POST | `/masters/employees/generate-code` | — | `{ employeeCode: string }` |
| `employeeApi.create` | POST | `/masters/employees` | `EmployeePayload` | `Employee` |
| `employeeApi.update` | PUT | `/masters/employees/{id}` | `EmployeePayload` | `Employee` |
| `employeeApi.activate` | PATCH | `/masters/employees/{id}/activate` | — | `Employee` |
| `employeeApi.deactivate` | PATCH | `/masters/employees/{id}/deactivate` | — | `Employee` |

Note: axios instance is `api` from `./axios`, and all URLs above are relative to its base (`/api`), e.g. `userApi.list` really hits `/api/users`.

## 4. Backend

### Controllers

**`UserController`** — `@RequestMapping("/api/users")`, class-level `@PreAuthorize("hasRole('ADMIN')")` (every endpoint below requires ADMIN unless a method-level override is noted):

| Method | Endpoint | Notes |
|---|---|---|
| `getUsers` | `GET /api/users` | params `search`, `role`, `status`, `page` (default 0), `size` (default 10) |
| `getUserById` | `GET /api/users/{id}` | |
| `getEffectivePermissions` | `GET /api/users/{id}/effective-permissions` | comment: "Troubleshooting/access review (spec section 39)" |
| `createUser` | `POST /api/users` | `@Valid @RequestBody UserCreateRequest`; returns 201 |
| `updateUser` | `PUT /api/users/{id}` | `@Valid @RequestBody UserUpdateRequest` |
| `updateStatus` | `PATCH /api/users/{id}/status` | `@Valid @RequestBody UserStatusUpdateRequest` |
| `resetPassword` | `POST /api/users/{id}/reset-password` | `@Valid @RequestBody AdminPasswordResetRequest`; comment: "never requires or reveals the existing one" |
| `getAssignedStores` | `GET /api/users/{id}/stores` | ADMIN-only via class-level guard |
| `assignStores` | `PUT /api/users/{id}/stores` | `@Valid @RequestBody AssignStoresRequest` |
| `changeOwnPassword` | `PUT /api/users/me/password` | **method-level `@PreAuthorize("isAuthenticated()")` overrides the class-level ADMIN guard** — reachable by every authenticated role, not just ADMIN; returns 204 |

**`EmployeeController`** — `@RequestMapping("/api/masters/employees")`, no class-level `@PreAuthorize` (each endpoint sets its own):

| Method | Endpoint | `@PreAuthorize` |
|---|---|---|
| `list` | `GET /api/masters/employees` | none (open to any authenticated caller reachable by Spring Security filter chain) |
| `getById` | `GET /api/masters/employees/{id}` | none |
| `generateCode` | `POST /api/masters/employees/generate-code` | `hasAuthority('PERM_EMPLOYEE_CREATE')` |
| `create` | `POST /api/masters/employees` | `hasAuthority('PERM_EMPLOYEE_CREATE')`; returns 201 |
| `update` | `PUT /api/masters/employees/{id}` | `hasAuthority('PERM_EMPLOYEE_EDIT')` |
| `activate` | `PATCH /api/masters/employees/{id}/activate` | `hasAuthority('PERM_EMPLOYEE_EDIT')` |
| `deactivate` | `PATCH /api/masters/employees/{id}/deactivate` | `hasAuthority('PERM_EMPLOYEE_EDIT')` |
| `getAssignedStores` | `GET /api/masters/employees/{id}/stores` | `hasAuthority('PERM_EMPLOYEE_VIEW')` |
| `assignStores` | `PUT /api/masters/employees/{id}/stores` | `hasAuthority('PERM_EMPLOYEE_EDIT')` |

(`PERM_<name>` authorities are granted by `UserPrincipal.getAuthorities()`, which adds `"PERM_" + permission.name()` for every `Permission` the user's `Role` carries per `RolePermissions`.)

### DTOs

**`UserCreateRequest`**
```
@NotBlank(message = "First name is required") firstName
@NotBlank(message = "Last name is required") lastName
@NotBlank(message = "Email is required") @Email(message = "Email must be valid") email
@NotBlank(message = "Mobile number is required") @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits") mobile
@NotBlank(message = "Password is required") @Size(min = 6, message = "Password must be at least 6 characters") password
@NotNull(message = "Role is required") role
status               // no validation, optional — defaults to ACTIVE in service
employeeId           // optional — links this login to an existing Employee record (spec section 7)
```

**`UserUpdateRequest`**
```
@NotBlank(message = "First name is required") firstName
@NotBlank(message = "Last name is required") lastName
@NotBlank(message = "Email is required") @Email(message = "Email must be valid") email
@NotBlank(message = "Mobile number is required") @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits") mobile
@NotNull(message = "Role is required") role
@NotNull(message = "Status is required") status
employeeId           // optional — null clears the link
```
(no `password` field — password is never updated here)

**`UserStatusUpdateRequest`**
```
@NotNull(message = "Status is required") status
```

**`AdminPasswordResetRequest`**
```
@NotBlank(message = "New password is required") @Size(min = 6, message = "Password must be at least 6 characters") newPassword
@NotBlank(message = "Confirm password is required") confirmPassword
```

**`PasswordChangeRequest`**
```
@NotBlank(message = "Current password is required") currentPassword
@NotBlank(message = "New password is required") @Size(min = 6, message = "Password must be at least 6 characters") newPassword
@NotBlank(message = "Confirm password is required") confirmPassword
```

**`AssignStoresRequest`**
```
@NotNull(message = "storeIds is required (use an empty array to clear all store access)") storeIds  // Set<Long>
```

**`UserResponse`** fields: `id, firstName, lastName, email, mobile, role, status, employeeId, employeeName, employeeCode, mustChangePassword, lastLogin, allStoresAccess, currentStoreId, currentStoreName, currentStoreCode, createdAt, updatedAt`. `allStoresAccess` is computed via `RolePermissions.has(user.getRole(), Permission.STORE_ACCESS_ALL)`, not stored on the entity.

**`EmployeeRequest`**
```
@NotBlank(message = "Employee code is required") employeeCode
@NotBlank(message = "Employee name is required") name
@NotBlank(message = "Mobile number is required") @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits") mobile
@Email(message = "Email must be valid") email        // optional (no @NotBlank)
designation, department, address, cityId, stateId, joiningDate, notes   // no validation annotations
```

**`EmployeeResponse`** fields: `id, employeeCode, name, mobile, email, designation, department, address, cityId, cityName, stateId, stateName, joiningDate, status, notes, linkedUserId, linkedUserEmail, createdAt, updatedAt`. `linkedUserId`/`linkedUserEmail` are populated by the caller passing a `User` found via `UserRepository.findByEmployeeId(id)` — `Employee` entity itself has no such field.

**`GenerateEmployeeCodeResponse`**: `{ employeeCode: String }`.

**`PagedResponse<T>`**: `content: List<T>, page, size, totalElements, totalPages`, built via `PagedResponse.fromPage(Page<T>)`.

### Services

**`UserService`**
- `getUsers(search, role, status, page, size)` — delegates to `UserRepository.search(...)`, sorted by `createdAt` descending, maps to `UserResponse`.
- `getUserById(id)` — `findUserOrThrow` + map.
- `createUser(request)` (`@Transactional`) — throws `DuplicateEmailException` if `existsByEmail`; resolves optional `employeeId` via `resolveEmployee(employeeId, null)`; builds `User` with `passwordEncoder.encode(request.getPassword())`, status defaults to `UserStatus.ACTIVE` if request status is null; saves; audit-logs `CREATE`.
- `updateUser(id, request)` (`@Transactional`) — throws `DuplicateEmailException` if email changed to one that already exists (case-insensitive compare on current email); re-resolves `employeeId` excluding this user's own id; **admin-safety guard**: `if (user.getRole() == Role.ADMIN && request.getRole() != Role.ADMIN) guardNotLastActiveAdmin(...)` and `if (user.getRole() == Role.ADMIN && request.getStatus() == UserStatus.INACTIVE) guardNotLastActiveAdmin(...)`; audit-logs `PERMISSION_CHANGE` if role changed, else `UPDATE`.
- `updateStatus(id, request)` (`@Transactional`) — same admin-safety guard for `ADMIN → INACTIVE`; audit-logs `UPDATE` with old/new status.
- `adminResetPassword(id, request)` (`@Transactional`) — throws `BadRequestException("New password and confirm password do not match")` if mismatched; encodes and sets password; sets `mustChangePassword = true`; audit-logs `UPDATE` with description "Password reset for user ... by an administrator" (comment: "Never log the password itself — only that a reset happened").
- `changeOwnPassword(currentUserEmail, request)` (`@Transactional`) — mismatch check same as above; looks up user by `currentUserEmail` (throws `BadRequestException("User not found")` if absent); throws `BadRequestException("Current password is incorrect")` if `!passwordEncoder.matches(currentPassword, user.getPassword())`; sets new password, `mustChangePassword = false`; audit-logs `UPDATE`.
- `effectivePermissions(id)` — `findUserOrThrow` then `RolePermissions.forRole(user.getRole())`.
- `assignStores(id, storeIds)` (`@Transactional`) — delegates to `StoreAccessService.assignStores`; audit-logs `UPDATE` with count of stores.
- `getAssignedStoreIds(id)` — `findUserOrThrow` (404 guard) then `StoreAccessService.getAssignedStoreIds(id)`.
- `setCurrentStore(currentUserEmail, storeId)` (`@Transactional`) — validates via `StoreAccessService.assertStoreAccess`; audit-logs `UPDATE` under module `"AUTH"`.
- `getAccessibleStores(currentUserEmail)` — returns every store the caller may act on.
- `resolveEmployee(employeeId, excludeUserId)` (private) — if `employeeId` null, returns null; else looks up `Employee` (404 `MasterNotFoundException` if missing); checks `existsByEmployeeIdAndIdNot`/`existsByEmployeeId` depending on create vs update; throws `BadRequestException("Employee '" + name + "' is already linked to another user login")` if already linked.
- `guardNotLastActiveAdmin(user, message)` (private) — **exact condition**: *"Throws if `user` is the only ACTIVE ADMIN in the system"*. If `user.getStatus() != ACTIVE`, returns (no-op — an already-inactive admin can be freely changed). Else counts `userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)`; if `<= 1`, throws `BadRequestException(message)`.
- `findUserOrThrow(id)` (private) — `UserRepository.findById` or throw `UserNotFoundException(id)`.

**`EmployeeService`**
- `search(search, status, page, size)` — sorted by `name` ascending; maps each `Employee` to `EmployeeResponse.fromEntity(e, userRepository.findByEmployeeId(e.getId()).orElse(null))` (N+1 reverse lookup per row).
- `getById(id)` — `findOrThrow` + same reverse-lookup mapping.
- `generateCode()` — delegates entirely to `EmployeeCodeGeneratorService.generateNext()`; comment: *"The ONLY place an auto-generated employee code is produced (spec section 5) — a manually-entered code bypasses this."*
- `create(request)` (`@Transactional`) — three uniqueness checks in order: `existsByEmployeeCodeIgnoreCase` → `BadRequestException("An employee with code '...' already exists")`; `existsByMobile` → `BadRequestException("An employee with mobile number '...' already exists")`; if email present, `existsByEmailIgnoreCase` → `BadRequestException("An employee with email '...' already exists")`. Resolves optional `City`/`State` via `cityService.findOrThrow`/`stateService.findOrThrow`. Builds and saves; audit-logs `CREATE` under module `"MASTER"`.
- `update(id, request)` (`@Transactional`) — same three uniqueness checks but scoped with `...AndIdNot` and only re-checked when the value actually changed; audit-logs `UPDATE`.
- `setStatus(id, status)` (`@Transactional`) — comment: *"Deactivating an employee never touches a linked User account (spec section 33) — that is a separate, explicit action."* Sets status, saves, audit-logs `UPDATE` with old/new status values.
- `assignStores(id, storeIds)` (`@Transactional`) — comment: *"purely informational (spec section 10), distinct from `StoreAccessService.assignStores` which is the actual login access control on `User`."* Replace-all: `employeeStoreRepository.deleteByEmployeeId(id)` then re-inserts one `EmployeeStore` row per id (404 `MasterNotFoundException("Store", storeId)` if a store doesn't exist); audit-logs `UPDATE`.
- `getAssignedStoreIds(id)` — `findOrThrow` then maps `EmployeeStoreRepository.findByEmployeeId(id)` to store ids.
- `findOrThrow(id)` (public) — `EmployeeRepository.findById` or throw `MasterNotFoundException("Employee", id)`.

**`EmployeeCodeGeneratorService`** — *"The ONLY place an auto-generated employee code is produced ... Format: `EMP-000001`."* `generateNext()` runs `@Transactional(propagation = Propagation.REQUIRES_NEW)`: calls `sequenceRepository.ensureRowExists(1L)` (native upsert-if-absent), then `sequenceRepository.lockForUpdate(1L)` (`@Lock(PESSIMISTIC_WRITE)`) — throws `IllegalStateException` if the row is somehow still missing. Loops: increments `lastNumber`, formats as `"EMP-" + String.format("%06d", number)`, and re-checks `employeeRepository.existsByEmployeeCodeIgnoreCase(candidate)` in a `do…while` loop to skip any number a manually-typed code already occupies. Saves the sequence row and returns the candidate. Comment: *"mirrors `SkuGeneratorService` exactly ... two simultaneous 'Generate Code' calls can never collide"*, and *"A generated code is never reused even if the caller never saves an employee with it (gaps are fine, duplicates are not)."*

### Repository

**`UserRepository`**: `findByEmail`, `existsByEmail`, `findByEmployeeId`, `existsByEmployeeId`, `existsByEmployeeIdAndIdNot`, `countByRoleAndStatus`, and a JPQL `@Query search(search, role, status, pageable)` doing case-insensitive `LIKE` on `firstName`/`lastName`/`email` plus optional `role`/`status` equality filters.

**`EmployeeRepository`**: `existsByEmployeeCodeIgnoreCase`, `existsByEmployeeCodeIgnoreCaseAndIdNot`, `existsByMobile`, `existsByMobileAndIdNot`, `existsByEmailIgnoreCase`, `existsByEmailIgnoreCaseAndIdNot`, and a JPQL `@Query search(search, status, pageable)` doing case-insensitive `LIKE` on `name`/`employeeCode` and plain `LIKE` on `mobile`, plus optional `status` equality.

**`EmployeeCodeSequenceRepository`**: `lockForUpdate(id)` (`@Lock(PESSIMISTIC_WRITE)` JPQL select), `ensureRowExists(id)` (native `INSERT ... ON DUPLICATE KEY UPDATE id = id`, `@Modifying`).

**`EmployeeStoreRepository`**: `findByEmployeeId(employeeId)`, `deleteByEmployeeId(employeeId)`.

## 5. Database

### Tables

**`users`** (from `User` entity `@Table(name = "users")`):
| Column | Notes |
|---|---|
| `id` | PK, IDENTITY |
| `first_name` | `nullable=false, length=50` |
| `last_name` | `nullable=false, length=50` |
| `email` | `nullable=false, unique=true, length=100` |
| `mobile` | `nullable=false, length=15` |
| `password` | `nullable=false` (bcrypt hash) |
| `role` | enum string, `nullable=false, length=20` |
| `status` | enum string, `nullable=false, length=20` |
| `employee_id` | FK → `employees.id`, `unique=true` (nullable) |
| `must_change_password` | `nullable=false`, default `false` |
| `last_login` | nullable |
| `current_store_id` | FK → `stores.id`, nullable |
| `created_at` | `nullable=false, updatable=false` |
| `updated_at` | `nullable=false` |

**`employees`** (`@Table(name = "employees")`):
| Column | Notes |
|---|---|
| `id` | PK, IDENTITY |
| `employee_code` | `nullable=false, unique=true, length=30` |
| `name` | `nullable=false, length=100` |
| `mobile` | `nullable=false, length=10` |
| `email` | `length=100`, nullable |
| `designation` | `length=100`, nullable |
| `department` | `length=100`, nullable |
| `address` | `TEXT`, nullable |
| `city_id` | FK → `cities.id`, nullable |
| `state_id` | FK → `states.id`, nullable |
| `joining_date` | nullable |
| `status` | enum string, `nullable=false, length=20` |
| `notes` | `TEXT`, nullable |
| `created_at` | `nullable=false, updatable=false` |
| `updated_at` | `nullable=false` |

**`employee_code_sequence`** (`@Table(name = "employee_code_sequence")`): `id` (PK, always `1`), `last_number` (`nullable=false`) — single global counter row.

**`employee_stores`** (`@Table(name = "employee_stores", uniqueConstraints = @UniqueConstraint(name="uk_employee_store", columnNames={"employee_id","store_id"})`): `id` (PK, IDENTITY), `employee_id` (FK → `employees.id`, `nullable=false`), `store_id` (FK → `stores.id`, `nullable=false`), `created_at` (`nullable=false, updatable=false`).

**`user_stores`** (referenced dependency, `@Table(name = "user_stores", uniqueConstraints = @UniqueConstraint(name="uk_user_store", columnNames={"user_id","store_id"})`): `id`, `user_id` (FK → `users.id`), `store_id` (FK → `stores.id`), `created_at`.

### Relationships

- `User.employee` → `Employee` (`@ManyToOne`, `employee_id` unique): at most one User per Employee, enforced by the DB unique constraint **and** pre-checked in `UserService.resolveEmployee` for a clean 400 instead of a raw DB constraint violation.
- `Employee` → `User`: no FK on the `Employee` side; the reverse link is resolved at read time via `UserRepository.findByEmployeeId(employeeId)`.
- `User.currentStore` → `Store` (`@ManyToOne`, `current_store_id`, nullable): the store the user is currently working in; validated through `StoreAccessService` before being set, never trusted directly from a request.
- `User` ↔ `Store` via `UserStore` (`user_stores`): explicit ASSIGNED_STORES access rows, managed by `StoreAccessService.assignStores`/`UserService.assignStores`. This is the actual, backend-enforced login access control.
- `Employee` ↔ `Store` via `EmployeeStore` (`employee_stores`): purely informational tagging of which store(s) an employee is associated with, managed by `EmployeeService.assignStores` — explicitly **not** an access-control mechanism (see code comment on `EmployeeService.assignStores`).
- `Employee.city` / `Employee.state` → `City`/`State` (Master module dependency, not documented in depth here).

## 6. Validation

- Mobile (`User` and `Employee`): `^[0-9]{10}$`, message `"Mobile number must be 10 digits"` — applied to `UserCreateRequest.mobile`, `UserUpdateRequest.mobile`, `EmployeeRequest.mobile`.
- Email: `@Email` (`UserCreateRequest.email`, `UserUpdateRequest.email` both also `@NotBlank`; `EmployeeRequest.email` is `@Email` only — optional).
- Password: `@Size(min = 6)` on `UserCreateRequest.password`, `AdminPasswordResetRequest.newPassword`, `PasswordChangeRequest.newPassword`.
- Required fields (`@NotBlank`/`@NotNull`): first/last name, email, mobile, role (create/update), status (`UserUpdateRequest`, `UserStatusUpdateRequest`), employeeCode/name/mobile (`EmployeeRequest`), storeIds (`AssignStoresRequest`, allows empty array but not null), currentPassword/newPassword/confirmPassword (`PasswordChangeRequest`), newPassword/confirmPassword (`AdminPasswordResetRequest`).
- Cross-field password match: `newPassword.equals(confirmPassword)` checked manually in `UserService.adminResetPassword` and `changeOwnPassword` (not a bean-validation annotation) — throws `BadRequestException("New password and confirm password do not match")`.
- Uniqueness (service-layer, not annotation-based): `User.email` unique (`DuplicateEmailException`); `Employee.employeeCode`, `Employee.mobile`, `Employee.email` all unique (`BadRequestException` with specific messages); one `User` per `Employee` (`BadRequestException`).
- Bean Validation triggers `MethodArgumentNotValidException`, handled globally.

## 7. Authentication / Authorization

| Endpoint | Requirement |
|---|---|
| All `/api/users/**` except `me/password` | `hasRole('ADMIN')` (class-level `@PreAuthorize` on `UserController`) |
| `PUT /api/users/me/password` | `isAuthenticated()` (method-level override — any logged-in role) |
| `GET/POST /api/masters/employees`, `GET /api/masters/employees/{id}` | No explicit `@PreAuthorize` (reachable by any authenticated caller passing the global security filter chain) |
| `POST /api/masters/employees/generate-code`, `POST /api/masters/employees` | `hasAuthority('PERM_EMPLOYEE_CREATE')` |
| `PUT /api/masters/employees/{id}`, `PATCH .../activate`, `PATCH .../deactivate`, `PUT .../{id}/stores` | `hasAuthority('PERM_EMPLOYEE_EDIT')` |
| `GET /api/masters/employees/{id}/stores` | `hasAuthority('PERM_EMPLOYEE_VIEW')` |

Depends on the Authentication & Authorization module for `UserPrincipal`, JWT filter, `Role`/`Permission` enums and `RolePermissions` — referenced here, not re-documented.

## 8. Permissions

Relevant `Permission` enum values (module `"Security"` unless noted) and which roles hold them per `RolePermissions.build()`:

| Permission | ADMIN | STORE_MANAGER | ACCOUNTANT | SALES_USER | PURCHASE_USER | INVENTORY_USER | STAFF |
|---|---|---|---|---|---|---|---|
| `USER_VIEW` / `USER_CREATE` / `USER_EDIT` / `USER_DEACTIVATE` | yes | no (explicitly removed) | no | no | no | no | no |
| `EMPLOYEE_VIEW` (module `"Master"`) | yes | yes | yes | no | no | no | no |
| `EMPLOYEE_CREATE` / `EMPLOYEE_EDIT` (module `"Master"`) | yes | yes | no | no | no | no | no |
| `STORE_ASSIGN` | yes | no (explicitly removed) | no | no | no | no | no |
| `STORE_ACCESS_ALL` | yes | no (explicitly removed) | no | no | no | no | no |
| `ROLE_VIEW` / `ROLE_MANAGE` / `PERMISSION_VIEW` | yes | no | no | no | no | no | no |
| `AUDIT_VIEW` | yes | no (explicitly removed) | no | no | no | no | no |

(ADMIN holds `EnumSet.allOf(Permission.class)`; STORE_MANAGER is a copy of ADMIN's set with USER_*, ROLE_*, PERMISSION_VIEW, FY_MANAGE, AUDIT_VIEW, GST_CONFIG, and STORE_CREATE/STORE_EDIT/STORE_ASSIGN/STORE_ACCESS_ALL explicitly removed. ACCOUNTANT/SALES_USER/PURCHASE_USER/INVENTORY_USER/STAFF are each an explicit `EnumSet.of(...)` that includes `EMPLOYEE_VIEW` only for ACCOUNTANT among the non-admin/non-manager roles, per the table's `EMPLOYEE_VIEW` list above — SALES_USER/PURCHASE_USER/INVENTORY_USER/STAFF do not include `EMPLOYEE_VIEW`.)

## 9. Transaction Handling

- `UserService`: `createUser`, `updateUser`, `updateStatus`, `adminResetPassword`, `changeOwnPassword`, `assignStores`, `setCurrentStore` are all `@Transactional` (default propagation). Read methods (`getUsers`, `getUserById`, `effectivePermissions`, `getAssignedStoreIds`, `getAccessibleStores`) are not annotated.
- `EmployeeService`: `create`, `update`, `setStatus`, `assignStores` are `@Transactional`. `search`, `getById`, `generateCode`, `getAssignedStoreIds`, `findOrThrow` are not.
- `EmployeeCodeGeneratorService.generateNext()` runs `@Transactional(propagation = Propagation.REQUIRES_NEW)` deliberately — code comment: *"running in its own `REQUIRES_NEW` transaction so the single counter row is locked/released independently of the caller's transaction and two simultaneous 'Generate Code' calls can never collide."*
- `AuditService.log(...)` uses the **default** propagation (joins the caller's transaction), not `REQUIRES_NEW`, per its class comment: *"an audit entry for an action that later rolls back should roll back with it."* A logging failure itself is caught internally and only logged (`log.warn`), never rethrown, so it cannot fail the surrounding business transaction.

## 10. Error Handling

All handled centrally by `GlobalExceptionHandler` (`@RestControllerAdvice`), building a uniform `ApiError { timestamp, status, error, message, path, fieldErrors }` (via `buildResponse`):

| Exception | HTTP Status |
|---|---|
| `MethodArgumentNotValidException` (bean validation) | 400, `message="Validation failed"`, `fieldErrors` populated field→message from `ex.getBindingResult().getFieldErrors()` |
| `DuplicateEmailException` | 409 Conflict |
| `UserNotFoundException` | 404 |
| `MasterNotFoundException` (covers Employee/Store/City/State not-found in this module) | 404 |
| `BadRequestException` (admin-safety guard, password mismatch, uniqueness on Employee, employee-already-linked, current-password-incorrect, etc.) | 400 |
| `AccessDeniedException` (e.g. `StoreAccessService.assertStoreAccess`) | 403, fixed message `"You do not have permission to access this resource"` |
| Any other `Exception` | 500, `"An unexpected error occurred"`, full stack trace logged server-side |

`ApiError` uses `@JsonInclude(JsonInclude.Include.NON_NULL)`, so `fieldErrors` is omitted from the JSON entirely when null (only present on validation failures).

## 11. Audit Flow

Every mutation in this module writes through `AuditService.log(...)`. Exact call sites:

`UserService.createUser`:
```java
auditService.log(AuditAction.CREATE, "ADMIN", "User", saved.getId(), null,
        null, null, "User " + saved.getEmail() + " created with role " + saved.getRole());
```

`UserService.updateUser` (role changed):
```java
auditService.log(AuditAction.PERMISSION_CHANGE, "ADMIN", "User", saved.getId(), null,
        oldRole, saved.getRole().name(), "User " + saved.getEmail() + " role changed from " + oldRole + " to " + saved.getRole());
```
(role unchanged):
```java
auditService.log(AuditAction.UPDATE, "ADMIN", "User", saved.getId(), null,
        null, null, "User " + saved.getEmail() + " updated");
```

`UserService.updateStatus`:
```java
auditService.log(AuditAction.UPDATE, "ADMIN", "User", saved.getId(), null,
        oldStatus.name(), saved.getStatus().name(), "User " + saved.getEmail() + " status changed to " + saved.getStatus());
```

`UserService.adminResetPassword`:
```java
// Never log the password itself — only that a reset happened.
auditService.log(AuditAction.UPDATE, "ADMIN", "User", saved.getId(), null,
        null, null, "Password reset for user " + saved.getEmail() + " by an administrator");
```

`UserService.changeOwnPassword`:
```java
auditService.log(AuditAction.UPDATE, "ADMIN", "User", user.getId(), null,
        null, null, "User " + user.getEmail() + " changed their own password");
```

`UserService.assignStores`:
```java
auditService.log(AuditAction.UPDATE, "ADMIN", "User", id, null,
        null, null, "Store access for user " + user.getEmail() + " set to " + storeIds.size() + " store(s)");
```

`UserService.setCurrentStore`:
```java
auditService.log(AuditAction.UPDATE, "AUTH", "User", saved.getId(), store.getStoreCode(),
        null, null, "User " + saved.getEmail() + " switched current store to " + store.getStoreCode());
```

`EmployeeService.create`:
```java
auditService.log(AuditAction.CREATE, "MASTER", "Employee", saved.getId(), saved.getEmployeeCode(),
        null, null, "Employee " + saved.getEmployeeCode() + " (" + saved.getName() + ") created");
```

`EmployeeService.update`:
```java
auditService.log(AuditAction.UPDATE, "MASTER", "Employee", saved.getId(), saved.getEmployeeCode(),
        null, null, "Employee " + saved.getEmployeeCode() + " updated");
```

`EmployeeService.setStatus`:
```java
auditService.log(AuditAction.UPDATE, "MASTER", "Employee", saved.getId(), saved.getEmployeeCode(),
        oldStatus.name(), status.name(), "Employee " + saved.getEmployeeCode() + " status changed to " + status);
```

`EmployeeService.assignStores`:
```java
auditService.log(AuditAction.UPDATE, "MASTER", "Employee", id, employee.getEmployeeCode(),
        null, null, "Store assignment for employee " + employee.getEmployeeCode() + " set to " + storeIds.size() + " store(s)");
```

All entries are persisted via `AuditLogRepository`, capturing `userId`/`username` of the actor (via `SecurityUtil.currentUserOrNull()`), `action`, `module`, `entityType`, `entityId`, optional `store`, `documentNumber`, `oldValue`/`newValue`, `ipAddress` (from `X-Forwarded-For` or `remoteAddr`), `userAgent`, and `description`.

## 12. Important Side Effects

- **Password hashing**: every password write (`createUser`, `adminResetPassword`, `changeOwnPassword`) runs through `passwordEncoder.encode(...)` — plaintext is never stored.
- **`mustChangePassword` flag**: set `true` by `adminResetPassword` (forces the user to change it at next login); set `false` by `changeOwnPassword` once the user sets their own new password. Not touched by `createUser`/`updateUser` (defaults to `false` via `@Builder.Default`).
- **Employee↔User link enforcement**: `UserService.resolveEmployee` checks `existsByEmployeeId`/`existsByEmployeeIdAndIdNot` before allowing a link — prevents two `User`s pointing at the same `Employee`. The reverse direction (`Employee → User`) is never stored, only computed at read time via `UserRepository.findByEmployeeId`.
- **`lastLogin`**: field exists on `User` but is set outside this module (in the Authentication module's login flow) — NOT FOUND IN CURRENT CODEBASE within the files read for this module.
- **Admin-safety (last active ADMIN)**: enforced in `UserService.guardNotLastActiveAdmin`, triggered from `updateUser` (role change away from ADMIN, or status change to INACTIVE while role is ADMIN) and `updateStatus` (status change to INACTIVE while role is ADMIN). Only counts/blocks when the target user is currently `ACTIVE`; an already-`INACTIVE` admin can be freely edited.
- **Employee deactivation never cascades to the linked User**: explicit code comment on `EmployeeService.setStatus` — deactivating an `Employee` leaves any linked `User` login untouched; the two must be deactivated independently.
- **Replace-all store assignment**: both `StoreAccessService.assignStores` (User→Store, login access) and `EmployeeService.assignStores` (Employee→Store, informational) delete all existing rows for the id and re-insert from the given set — no incremental add/remove semantics.
- **Stale current-store cleanup**: `StoreAccessService.assignStores` clears `user.currentStore` if the new assignment no longer includes it (and the user isn't ALL_STORES).
- **Employee code collision guard**: `EmployeeCodeGeneratorService.generateNext()` loops past any number a manually-entered code already occupies, so a hand-typed `EMP-000005` can never collide with a subsequently generated code.

## 13. Dependencies on Other Modules

- **Authentication & Authorization module**: `Role`, `Permission`, `RolePermissions`, `UserPrincipal`, `SecurityUtil`, `PermissionService`, JWT filter. This module reads `Role`/`Permission` and calls `RolePermissions.forRole`/`RolePermissions.has`, but does not redefine or re-document that mapping's internals beyond what's listed in section 8.
- **Store module**: `Store`, `StoreRepository`, `StoreService`, `StoreAccessService`, `UserStore`/`UserStoreRepository`. `UserService` delegates all store-access-list logic to `StoreAccessService` (`assignStores`, `getAssignedStoreIds`, `assertStoreAccess`, `getAccessibleStoreIds`) rather than reimplementing it. `User.currentStore` and `EmployeeStore` also depend on `Store`.
- **City/State masters**: `EmployeeService` depends on `CityService.findOrThrow` / `StateService.findOrThrow` to resolve `Employee.city`/`Employee.state`.
- **Audit module**: `AuditService`, `AuditAction`, `AuditLogRepository` — every mutation logs through here (section 11).

## 14. Key Operation Flows

**Create User**: `UserFormModal` (mode `add`) → `Users.tsx handleFormSubmit` → `userApi.create(payload)` → `POST /api/users` → `UserController.createUser` (`@Valid UserCreateRequest`) → `UserService.createUser` (checks `existsByEmail`, resolves optional `employeeId` via `resolveEmployee`, encodes password, builds `User`, `userRepository.save`, `auditService.log(CREATE,...)`) → `UserRepository.save` → `users` table INSERT → `UserResponse.fromEntity(saved)` → 201 JSON → `Users.tsx` toasts success, closes modal, reloads list.

**Edit User**: `UserFormModal` (mode `edit`, pre-filled) → `userApi.update(id, payload)` → `PUT /api/users/{id}` → `UserController.updateUser` (`@Valid UserUpdateRequest`) → `UserService.updateUser` (email-change/duplicate check, `resolveEmployee` excluding self, admin-safety guards on role/status change, field updates, `save`, conditional `PERMISSION_CHANGE`/`UPDATE` audit log) → `users` table UPDATE → `UserResponse` → UI toast + reload.

**Deactivate/Reactivate User**: dropdown action → `toggleStatus` → `userApi.updateStatus(id, {status})` → `PATCH /api/users/{id}/status` → `UserController.updateStatus` (`@Valid UserStatusUpdateRequest`) → `UserService.updateStatus` (admin-safety guard if `role==ADMIN && status==INACTIVE`, sets status, `save`, `UPDATE` audit log with old/new status) → `users` table UPDATE → `UserResponse` → UI toast + reload.

**Admin Password Reset**: `ResetPasswordDialog` → `userApi.resetPassword(id, {newPassword, confirmPassword})` → `POST /api/users/{id}/reset-password` → `UserController.resetPassword` (`@Valid AdminPasswordResetRequest`) → `UserService.adminResetPassword` (match check, `findUserOrThrow`, `passwordEncoder.encode`, `mustChangePassword=true`, `save`, `UPDATE` audit log without the password) → `users` table UPDATE → `UserResponse` → UI toast telling admin the user must change it at next login.

**Self Password Change**: caller not found among the read frontend files (`userApi.changeOwnPassword` exists but has no known UI trigger in this module's file set) → `PUT /api/users/me/password` (`isAuthenticated()`) → `UserController.changeOwnPassword` (`@AuthenticationPrincipal UserPrincipal`, `@Valid PasswordChangeRequest`) → `UserService.changeOwnPassword` (match check, `findByEmail`, `passwordEncoder.matches(currentPassword,...)` check, encode new password, `mustChangePassword=false`, `save`, `UPDATE` audit log) → `users` table UPDATE → `204 No Content`.

**Create Employee**: `EmployeeMaster.tsx` (via `MasterCrudPage`, optionally pre-filled via "Generate Code" → `employeeApi.generateCode()` → `POST /api/masters/employees/generate-code` → `EmployeeController.generateCode` (`PERM_EMPLOYEE_CREATE`) → `EmployeeService.generateCode()` → `EmployeeCodeGeneratorService.generateNext()`, its own `REQUIRES_NEW` transaction, row-locks `employee_code_sequence`, increments, dedupes against `employees.employee_code`) → form submit → `employeeApi.create(payload)` → `POST /api/masters/employees` → `EmployeeController.create` (`@Valid EmployeeRequest`, `PERM_EMPLOYEE_CREATE`) → `EmployeeService.create` (uniqueness checks on code/mobile/email, resolves `City`/`State`, builds and saves `Employee`, `CREATE` audit log) → `employees` table INSERT → `EmployeeResponse.fromEntity(saved)` → 201 JSON → UI toast + list reload.

**Edit Employee**: `MasterCrudPage` edit form (`toFormValues` maps `Employee`→`EmployeePayload`) → `employeeApi.update(id, payload)` → `PUT /api/masters/employees/{id}` (`PERM_EMPLOYEE_EDIT`) → `EmployeeService.update` (uniqueness re-checks only for changed code/mobile/email, resolves `City`/`State`, field updates, `save`, `UPDATE` audit log) → `employees` table UPDATE → `EmployeeResponse` → UI reload.

**Deactivate Employee**: `MasterCrudPage` deactivate action → `employeeApi.deactivate(id)` → `PATCH /api/masters/employees/{id}/deactivate` (`PERM_EMPLOYEE_EDIT`) → `EmployeeController.deactivate` → `EmployeeService.setStatus(id, EmployeeStatus.INACTIVE)` (no cascade to any linked `User`; `UPDATE` audit log with old/new status) → `employees` table UPDATE → `EmployeeResponse.fromEntity(saved, userRepository.findByEmployeeId(id).orElse(null))` → UI reload.

## 15. Manual Changes

- **Add a new field to `User`**: add the `@Column` to `backend/src/main/java/com/storehub/entity/User.java`; add it to `UserCreateRequest`/`UserUpdateRequest` (with any `jakarta.validation` annotations needed) and to `UserResponse` (+ `fromEntity` mapping); a DB migration is expected wherever this project's schema migrations live (not read in this module — check the migration tool/folder used by the Store/Masters modules for the convention, e.g. Flyway/Liquibase under `backend/src/main/resources`); update `UserFormModal.tsx` (`UserFormValues`, form fields) and `frontend/src/types/user.ts` (`User`, `UserCreatePayload`, `UserUpdatePayload`); if the field should render in the read-only view, also update `UserViewModal.tsx`. If it participates in the users search/filter, extend `UserRepository.search` and `UserController.getUsers`/`UserService.getUsers`.

- **Add a new field to `Employee`**: same pattern against `Employee.java`, `EmployeeRequest`/`EmployeeResponse` (+ `fromEntity`), a DB migration, and `EmployeeMaster.tsx` (`EMPTY`, `toFormValues`, `renderForm`, `renderView`) plus `frontend/src/types/masters.ts` (`Employee`, `EmployeePayload`). If it should be searchable, extend `EmployeeRepository.search`.

- **Change password rules**: minimum length is `@Size(min = 6)` on `UserCreateRequest.password`, `AdminPasswordResetRequest.newPassword`, `PasswordChangeRequest.newPassword` — change all three together to keep create/admin-reset/self-change consistent. The confirm-password match check is hand-written in `UserService.adminResetPassword` and `UserService.changeOwnPassword` (not a bean-validation annotation) — update both if match logic changes. Hashing algorithm is whatever `PasswordEncoder` bean is configured (Authentication module) — not overridden here.

- **Change `employeeCode` generation format**: the only place is `EmployeeCodeGeneratorService.format(long number)` (`PREFIX = "EMP-"` + `%06d`). Changing the prefix or digit-width there is sufficient — `EmployeeService.generateCode()` and the frontend's "Generate Code" button need no changes. Be aware the `do…while` uniqueness loop in `generateNext()` depends on `EmployeeRepository.existsByEmployeeCodeIgnoreCase`, so any format change should keep candidates checkable by that same method.

- **Add a new admin-safety rule** (beyond "never leave zero active ADMINs"): add the new guard inside `UserService` alongside `guardNotLastActiveAdmin`, and call it from every user-mutating method that could violate it (`updateUser`, `updateStatus`, and any new endpoint that changes role/status) — the codebase's existing pattern is to enforce this in the service layer, not the controller, so a single guard method covers all admin-facing endpoints. Also consider whether `assignStores`/store-access changes should be constrained similarly (currently they are not).

- **Modules affected by any User/Employee schema change**: Authentication module (`User.role`/`status` feed `UserPrincipal`/JWT claims), Store module (`User.currentStore`, `UserStore`, `EmployeeStore` all FK into `users`/`employees`), Audit module (every mutation here writes an `AuditLog` row referencing `entityType "User"`/`"Employee"` and `entityId`), and the Masters dashboard/navigation that lists `EmployeeMaster.tsx` under `/masters/employees` (see `frontend/src/pages/MastersDashboard.tsx`, `App.tsx` route table — not modified as part of this documentation task).
