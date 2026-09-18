# Authentication & Authorization Module

## 1. Overview

This module handles account registration, login, session identity (JWT), and permission-based
authorization for the whole StoreHub application. It exists to answer two questions on every
request: "who is this?" and "what is this user allowed to do?"

- **Identity/login** is handled by `AuthController`/`AuthService` (`backend/src/main/java/com/storehub/controller/AuthController.java`,
  `backend/src/main/java/com/storehub/service/AuthService.java`): register a new `User`, verify
  credentials on login, and issue a signed JWT.
- **Authorization** is a fixed, code-defined Role → Permission map (`RolePermissions.java`), not a
  database table. Every request's Spring Security `Authentication` carries both a legacy
  `ROLE_<name>` authority and a `PERM_<name>` authority per permission the user's role has
  (`UserPrincipal.getAuthorities()`), and controllers across the whole app gate endpoints with
  `@PreAuthorize("hasAuthority('PERM_...')")`.
- **Read-only catalog endpoints** (`PermissionController`, `RoleController`) expose the permission
  catalog and role → permission mapping for the admin "Roles & Permissions" screen; they are
  browsing endpoints only — nothing here lets an admin edit a role's permissions at runtime.

## 2. Frontend

### Files and Components

| File | Component/Purpose |
|---|---|
| `frontend/src/pages/Login.tsx` | Login form: email + password, calls `AuthContext.login`. |
| `frontend/src/pages/Register.tsx` | Registration form: name/email/mobile/password/role (STORE_MANAGER or STAFF only), calls `AuthContext.register`. |
| `frontend/src/context/AuthContext.tsx` | Global auth state: current `user`, `permissions` set, `myStores`; hydrates from `localStorage` + `/auth/me` on load; exposes `login`, `register`, `logout`, `switchStore`, `hasPermission`. |
| `frontend/src/api/authApi.ts` | Axios wrapper for `/api/auth/*` endpoints. |
| `frontend/src/api/roleApi.ts` | Axios wrapper for `/api/roles` and `/api/permissions`. |
| `frontend/src/api/userApi.ts` | Includes `getEffectivePermissions(id)` used by `AuthContext` to populate the permission set (backed by `UserController`, outside this module's own controllers but part of the auth flow). |
| `frontend/src/components/ProtectedRoute.tsx` | Route guard: redirects to `/login` if `user` is null (after `loading` resolves). |
| `frontend/src/components/AdminRoute.tsx` | Route guard: only `user.role === 'ADMIN'` passes, else redirect to `/unauthorized`. |
| `frontend/src/components/ManagerRoute.tsx` | Route guard: `ADMIN`/`STORE_MANAGER` plus an optional `extraRoles` list pass, else redirect to `/unauthorized`. |
| `frontend/src/pages/Unauthorized.tsx` | Static "Access denied" page shown by the route guards. |
| `frontend/src/pages/admin/RolesAndPermissions.tsx` | Admin-only, read-only browser: lists roles (`roleApi.list()`) and, per selected role, its permissions grouped by module (`roleApi.getPermissions(role)`). |
| `frontend/src/types/user.ts` | `Role`, `User`, `AuthResponse`, `LoginPayload`, `RegisterPayload`, `RoleInfo`, `ApiErrorResponse`, etc. |
| `frontend/src/utils/apiError.ts` | `parseApiError()` — turns an axios error into a message + per-field `fieldErrors` map for inline form display. |
| `frontend/src/App.tsx` | Route wiring: `/login`, `/register` are public; everything else sits under `<ProtectedRoute>`, with `<AdminRoute>`/`<ManagerRoute>` nested for role-restricted branches. |

### Frontend Flow

**Login:**
1. User submits `Login.tsx`'s form → `handleSubmit` calls `useAuth().login({ email, password })`.
2. `AuthContext.login` calls `authApi.login(payload)` → `POST /api/auth/login`.
3. On success: JWT stored as `localStorage['storehub_token']`, user JSON as `localStorage['storehub_user']`, `setUser(res.data.user)`, then `loadPermissions(user)` and `loadMyStores(user)` are fired (not awaited) to populate `permissions` and `myStores`.
4. `Login.tsx` navigates to `/dashboard` on success; on failure, `parseApiError` fills `error` and `fieldErrors` for inline display.

**Registration:**
1. User submits `Register.tsx`'s form → client first checks `form.password !== form.confirmPassword` locally (shows `confirmPassword: 'Passwords do not match'` without a network call).
2. `handleSubmit` calls `useAuth().register(form)` → `AuthContext.register` calls `authApi.register(payload)` → `POST /api/auth/register`.
3. On success, `Register.tsx` shows a success alert and `setTimeout`s to `navigate('/login')` after 1500ms (registration does **not** log the user in or return a token — see DTOs below).
4. On failure, `parseApiError` fills `error`/`fieldErrors` (e.g. backend's `mobile: "Mobile number must be 10 digits"`).

**AuthContext hydration on load (`useEffect` in `AuthProvider`):**
1. Reads `storehub_token` and `storehub_user` from `localStorage`.
2. If both present: optimistically `setUser(parsed)`, then fires `loadPermissions(parsed)` and `loadMyStores(parsed)` immediately (using the possibly-stale cached user), and in parallel calls `authApi.me()` (`GET /api/auth/me`, authenticated via the stored JWT) to refresh the user from the server.
3. On `me()` success: `setUser(res.data)`, refresh `localStorage['storehub_user']`, reload permissions from the fresh user.
4. On `me()` failure (e.g. expired/invalid token → 401): clears both `localStorage` keys, resets `user`/`permissions`/`myStores` to empty — effectively logs the user out client-side.
5. `loadMyStores`: calls `authApi.getMyStores()` (`GET /api/auth/my-stores`); if the user has exactly one accessible store and no `currentStoreId` yet, it silently calls `authApi.setCurrentStore(id)` to auto-select it.
6. `loading` is set to `false` once this resolves; `ProtectedRoute` shows a spinner until then.

## 3. API Calls

| Frontend function (file:function) | HTTP Method | URL | Request body shape | Response shape |
|---|---|---|---|---|
| `authApi.ts:login` | POST | `/api/auth/login` | `LoginPayload { email, password }` | `AuthResponse { token, user: User }` |
| `authApi.ts:register` | POST | `/api/auth/register` | `RegisterPayload { firstName, lastName, email, mobile, password, confirmPassword, role }` | `User` (the created `UserResponse`) |
| `authApi.ts:logout` | POST | `/api/auth/logout` | none | `{ message: string }` |
| `authApi.ts:me` | GET | `/api/auth/me` | none (JWT in `Authorization` header) | `User` |
| `authApi.ts:getMyStores` | GET | `/api/auth/my-stores` | none | `Store[]` |
| `authApi.ts:setCurrentStore` | PUT | `/api/auth/current-store` | `{ storeId: number }` | `User` |
| `roleApi.ts:list` | GET | `/api/roles` | none | `RoleInfo[] { name, description, active, permissionCount }` |
| `roleApi.ts:getPermissions` | GET | `/api/roles/{role}/permissions` | none | `PermissionsByModule` (`Record<module, PermissionEntry[]>`) |
| `roleApi.ts:permissionApi.list` | GET | `/api/permissions` | none | `PermissionsByModule` |
| `userApi.ts:getEffectivePermissions` | GET | `/api/users/{id}/effective-permissions` | none | `Permission[]` (string names, used by `AuthContext.hasPermission`) |

## 4. Backend

### Controllers

**`AuthController`** — `backend/src/main/java/com/storehub/controller/AuthController.java`, `@RequestMapping("/api/auth")`, no class-level `@PreAuthorize` (endpoints are `permitAll()` or protected via `SecurityConfig`'s `anyRequest().authenticated()`):

| Method | Annotation | Signature | Calls |
|---|---|---|---|
| `register` | `@PostMapping("/register")` | `register(@Valid @RequestBody RegisterRequest request)` → `ResponseEntity<UserResponse>` (201) | `authService.register(request)` |
| `login` | `@PostMapping("/login")` | `login(@Valid @RequestBody LoginRequest request)` → `ResponseEntity<AuthResponse>` | `authService.login(request)` |
| `logout` | `@PostMapping("/logout")` | `logout()` → `ResponseEntity<Map<String,String>>` | none — stateless JWT, purely client-side token discard; returns a static success message |
| `getCurrentUser` | `@GetMapping("/me")` | `getCurrentUser(@AuthenticationPrincipal UserPrincipal principal)` → `ResponseEntity<UserResponse>` | `authService.getCurrentUser(principal.getUsername())` |
| `getMyStores` | `@GetMapping("/my-stores")` | `getMyStores(@AuthenticationPrincipal UserPrincipal principal)` → `ResponseEntity<List<StoreResponse>>` | `userService.getAccessibleStores(principal.getUsername())` |
| `setCurrentStore` | `@PutMapping("/current-store")` | `setCurrentStore(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody SetCurrentStoreRequest request)` → `ResponseEntity<UserResponse>` | `userService.setCurrentStore(principal.getUsername(), request.getStoreId())` |

`/register` and `/login` are reachable unauthenticated because `SecurityConfig` permits `/api/auth/**`; `/me`, `/my-stores`, `/current-store` still require a valid JWT since the filter chain requires authentication for everything not explicitly permitted, and rely on `@AuthenticationPrincipal` being populated by `JwtAuthenticationFilter`.

**`PermissionController`** — `backend/src/main/java/com/storehub/controller/PermissionController.java`, `@RequestMapping("/api/permissions")`, class-level `@PreAuthorize("hasAuthority('PERM_PERMISSION_VIEW')")`:

| Method | Annotation | Signature | Calls |
|---|---|---|---|
| `list` | `@GetMapping` (inherits class `@PreAuthorize`) | `list()` → `ResponseEntity<Map<String, List<PermissionResponse>>>` | `Permission.values()` mapped to `PermissionResponse.fromPermission`, grouped by `PermissionResponse::getModule` |

**`RoleController`** — `backend/src/main/java/com/storehub/controller/RoleController.java`, `@RequestMapping("/api/roles")`, class-level `@PreAuthorize("hasAuthority('PERM_ROLE_VIEW')")`:

| Method | Annotation | Signature | Calls |
|---|---|---|---|
| `list` | `@GetMapping` (inherits class `@PreAuthorize`) | `list()` → `ResponseEntity<List<RoleResponse>>` | `Role.values()` mapped to `RoleResponse.fromRole` |
| `permissionsFor` | `@GetMapping("/{role}/permissions")` (inherits class `@PreAuthorize`) | `permissionsFor(@PathVariable Role role)` → `ResponseEntity<Map<String, List<PermissionResponse>>>` | `RolePermissions.forRole(role)` mapped/grouped |

Per the class Javadoc: "Roles are a fixed code enum here (see `Role`), not a DB table, so there is nothing to create/rename/delete — only `RolePermissions` ... is out of scope for this step's read API."

### DTOs

**`LoginRequest`** — `backend/src/main/java/com/storehub/dto/LoginRequest.java`
- `email: String` — `@NotBlank(message = "Email is required")`, `@Email(message = "Email must be valid")`
- `password: String` — `@NotBlank(message = "Password is required")`

**`RegisterRequest`** — `backend/src/main/java/com/storehub/dto/RegisterRequest.java`
- `firstName: String` — `@NotBlank(message = "First name is required")`
- `lastName: String` — `@NotBlank(message = "Last name is required")`
- `email: String` — `@NotBlank(message = "Email is required")`, `@Email(message = "Email must be valid")`
- `mobile: String` — `@NotBlank(message = "Mobile number is required")`, `@Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")`
- `password: String` — `@NotBlank(message = "Password is required")`, `@Size(min = 6, message = "Password must be at least 6 characters")`
- `confirmPassword: String` — `@NotBlank(message = "Confirm password is required")`
- `role: Role` — `@NotNull(message = "Role is required")`

**`AuthResponse`** — `backend/src/main/java/com/storehub/dto/AuthResponse.java`
- `token: String`
- `user: UserResponse`

**`UserResponse`** — `backend/src/main/java/com/storehub/dto/UserResponse.java`
- `id: Long`, `firstName: String`, `lastName: String`, `email: String`, `mobile: String`, `role: Role`, `status: UserStatus`
- `employeeId: Long`, `employeeName: String`, `employeeCode: String` (from linked `Employee`, else null)
- `mustChangePassword: boolean`, `lastLogin: LocalDateTime`
- `allStoresAccess: boolean` — computed as `RolePermissions.has(user.getRole(), Permission.STORE_ACCESS_ALL)`
- `currentStoreId: Long`, `currentStoreName: String`, `currentStoreCode: String`
- `createdAt: LocalDateTime`, `updatedAt: LocalDateTime`
- No validation annotations (response DTO); `fromEntity(User user)` is the mapping factory.

**`PermissionResponse`** — `backend/src/main/java/com/storehub/dto/PermissionResponse.java`
- `name: String`, `module: String`; `fromPermission(Permission permission)` maps `permission.name()` / `permission.getModule()`.

**`RoleResponse`** — `backend/src/main/java/com/storehub/dto/RoleResponse.java`
- `name: String`, `description: String`, `active: boolean`, `permissionCount: int`; `fromRole(Role role)` maps from `RoleDescriptions.of(role)` and `RolePermissions.forRole(role).size()`. `active` is always hardcoded `true` (no soft-disable of roles exists).

**`UserCreateRequest`** (used by `UserController`, module-adjacent) — `backend/src/main/java/com/storehub/dto/UserCreateRequest.java`
- `firstName`, `lastName` — `@NotBlank`
- `email` — `@NotBlank`, `@Email`
- `mobile` — `@NotBlank`, `@Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")`
- `password` — `@NotBlank`, `@Size(min = 6, message = "Password must be at least 6 characters")`
- `role: Role` — `@NotNull`
- `status: UserStatus` (optional, no annotation)
- `employeeId: Long` (optional)

**`UserUpdateRequest`** — `backend/src/main/java/com/storehub/dto/UserUpdateRequest.java`
- `firstName`, `lastName` — `@NotBlank`
- `email` — `@NotBlank`, `@Email`
- `mobile` — `@NotBlank`, `@Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")`
- `role: Role` — `@NotNull`
- `status: UserStatus` — `@NotNull(message = "Status is required")`
- `employeeId: Long` (optional, "null clears the link" per comment)

### Services

**`AuthService`** — `backend/src/main/java/com/storehub/service/AuthService.java` (`@Service`, depends on `UserRepository`, `PasswordEncoder`, `JwtUtil`, `AuditService`):

- **`register(RegisterRequest request)`** — `@Transactional`. Business rules enforced, in order:
  1. `if (!request.getPassword().equals(request.getConfirmPassword())) throw new BadRequestException("Password and confirm password do not match");`
  2. `if (userRepository.existsByEmail(request.getEmail())) throw new DuplicateEmailException(request.getEmail());`
  3. `if (request.getRole() == Role.ADMIN) throw new BadRequestException("Public registration cannot create an ADMIN account");` — the codebase's exact ADMIN self-registration guard.
  4. Builds a `User` with `status(UserStatus.ACTIVE)` and BCrypt-encoded password (`passwordEncoder.encode(...)`), saves via `userRepository.save(user)`, returns `UserResponse.fromEntity(saved)`. Note this method never logs the user in and never calls `AuditService` (no audit log is written for registration).

- **`login(LoginRequest request)`** — `@Transactional`.
  1. `User user = userRepository.findByEmail(request.getEmail()).orElse(null);`
  2. If `user == null` or `!passwordEncoder.matches(request.getPassword(), user.getPassword())`: logs a failed-login audit entry (`AuditAction.LOGIN`, description `"Failed login attempt for " + request.getEmail() + ": invalid credentials"`) and throws `InvalidCredentialsException("Invalid email or password")`.
  3. If `user.getStatus() == UserStatus.INACTIVE`: logs a failed-login audit entry (description `"Failed login attempt for " + user.getEmail() + ": account is inactive"`) and throws `InvalidCredentialsException("Your account is inactive. Please contact an administrator")`.
  4. On success: `user.setLastLogin(LocalDateTime.now()); userRepository.save(user);`, logs a success audit entry (description `"User " + user.getEmail() + " logged in"`), generates the JWT via `jwtUtil.generateToken(user.getEmail(), user.getRole().name())`, returns `AuthResponse{ token, user: UserResponse.fromEntity(user) }`.

- **`getCurrentUser(String email)`** — not transactional. `userRepository.findByEmail(email).orElseThrow(() -> new InvalidCredentialsException("User not found"))` → `UserResponse.fromEntity(user)`. Backs `GET /api/auth/me`.

**`PermissionService`** — `backend/src/main/java/com/storehub/service/PermissionService.java` (`@Service`, no repository dependency — pure logic over `RolePermissions`):
- `hasPermission(User user, Permission permission)` → `user != null && RolePermissions.has(user.getRole(), permission)`.
- `currentUserHasPermission(Permission permission)` → `hasPermission(SecurityUtil.currentUserOrNull(), permission)`.
- `effectivePermissions(Role role)` → `RolePermissions.forRole(role)`.
- Per its Javadoc, this exists for the handful of checks that need a plain Java call rather than a `@PreAuthorize` SpEL expression (admin-safety checks, the effective-permissions endpoint, self-service guards) — but the primary enforcement mechanism everywhere else remains `@PreAuthorize("hasAuthority('PERM_...')")` backed by the same `RolePermissions` map.

**`RolePermissions`** (not a Spring bean — `final` class with static methods) — `backend/src/main/java/com/storehub/service/RolePermissions.java`:
- `forRole(Role role)` → `BY_ROLE.getOrDefault(role, Collections.emptySet())`.
- `has(Role role, Permission permission)` → `forRole(role).contains(permission)`.
- `build()` constructs the fixed `Map<Role, Set<Permission>>` (see section 8 below for full contents). This is described in its own Javadoc as "the single source of truth" and "the one place authorization logic is ever duplicated from."

**`RoleDescriptions`** (also static-only, not a bean) — `backend/src/main/java/com/storehub/service/RoleDescriptions.java`: `of(Role role)` returns a hardcoded human-readable description per role, "display only, not authorization."

### Repository

**`UserRepository`** — `backend/src/main/java/com/storehub/repository/UserRepository.java` (`extends JpaRepository<User, Long>`), methods used by this module:
- `findByEmail(String email): Optional<User>` — used by `AuthService.login`, `getCurrentUser`, and `CustomUserDetailsService.loadUserByUsername`.
- `existsByEmail(String email): boolean` — used by `AuthService.register` for the duplicate-email check.
- (Also present, used elsewhere in the user-management module: `findByEmployeeId`, `existsByEmployeeId`, `existsByEmployeeIdAndIdNot`, `countByRoleAndStatus`, and a JPQL `search(...)` query.)

## 5. Database

### Tables

**`users`** (entity `User`, `backend/src/main/java/com/storehub/entity/User.java`, `@Table(name = "users")`):

| Column | Type/Constraint |
|---|---|
| `id` | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` |
| `first_name` | `@Column(name = "first_name", nullable = false, length = 50)` |
| `last_name` | `@Column(name = "last_name", nullable = false, length = 50)` |
| `email` | `@Column(nullable = false, unique = true, length = 100)` |
| `mobile` | `@Column(nullable = false, length = 15)` |
| `password` | `@Column(nullable = false)` (BCrypt hash) |
| `role` | `@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)` |
| `status` | `@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)` (`ACTIVE`/`INACTIVE`) |
| `employee_id` | `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "employee_id", unique = true)` — nullable, unique (at most one `User` per `Employee`) |
| `must_change_password` | `@Column(name = "must_change_password", nullable = false)`, default `false` |
| `last_login` | `@Column(name = "last_login")` — nullable |
| `current_store_id` | `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "current_store_id")` — nullable |
| `created_at` | `@Column(name = "created_at", nullable = false, updatable = false)` — set in `@PrePersist onCreate()` |
| `updated_at` | `@Column(name = "updated_at", nullable = false)` — refreshed in `@PreUpdate onUpdate()` |

`@PrePersist onCreate()` also defaults `status` to `UserStatus.ACTIVE` if null (belt-and-braces alongside `AuthService.register` explicitly setting it).

`Role` and `Permission` (`backend/src/main/java/com/storehub/entity/Role.java`, `Permission.java`) are plain Java enums — not database tables — per their own Javadoc.

### Relationships

- `User.role` — enum column, no FK (not a separate `roles` table).
- `User.employee` → `Employee` — `@ManyToOne`, nullable, unique on `employee_id` (one Employee has at most one User login).
- `User.currentStore` → `Store` — `@ManyToOne`, nullable; validated by `StoreAccessService` (per the entity's own Javadoc) before ever being set, not trusted blindly from the frontend request.

## 6. Validation

**Bean Validation (Jakarta):**
- `LoginRequest.email` — `@NotBlank("Email is required")`, `@Email("Email must be valid")`.
- `LoginRequest.password` — `@NotBlank("Password is required")`.
- `RegisterRequest` — see full field list in section 4 DTOs; notably `mobile` requires `^[0-9]{10}$` ("Mobile number must be 10 digits") and `password` requires `@Size(min = 6, ...)` ("Password must be at least 6 characters").
- `UserCreateRequest` / `UserUpdateRequest` — same `mobile` pattern and `password` size rule (create only; update has no password field).

**Custom business validation (in `AuthService`, thrown as exceptions, not annotations):**
- Password/confirm-password mismatch on register → `BadRequestException("Password and confirm password do not match")`.
- Duplicate email on register → `DuplicateEmailException` (message: `"A user with email '" + email + "' already exists"`).
- Public self-registration attempting `Role.ADMIN` → `BadRequestException("Public registration cannot create an ADMIN account")`.
- Login with unknown email or wrong password → `InvalidCredentialsException("Invalid email or password")` (deliberately the same message for both cases, so failed login never reveals whether the email exists).
- Login while `UserStatus.INACTIVE` → `InvalidCredentialsException("Your account is inactive. Please contact an administrator")`.
- `getCurrentUser` on a since-deleted user → `InvalidCredentialsException("User not found")`.

## 7. Authentication / Authorization

**JWT issuance** — `JwtUtil.generateToken(String email, String role)` (`backend/src/main/java/com/storehub/security/JwtUtil.java`): builds a JWT with `setSubject(email)`, a single custom claim `claim("role", role)`, `setIssuedAt(now)`, `setExpiration(now + expirationMs)`, signed HS256 with a `SecretKey` built from `${jwt.secret}` (`Keys.hmacShaKeyFor(secret.getBytes(UTF_8))`). `expirationMs` comes from `${jwt.expiration-ms}`, both injected via `@Value` in the constructor.

**JWT validation** — `JwtUtil.isTokenValid(token)` parses the claims and checks `claims.getExpiration().after(new Date())`, catching `JwtException`/`IllegalArgumentException` as invalid. `extractEmail`/`extractRole` read the subject/`role` claim respectively (note: the `role` claim is extractable but is **not** what drives authorization at request time — see below).

**Filter chain wiring** — `JwtAuthenticationFilter` (`OncePerRequestFilter`) reads the `Authorization: Bearer <token>` header; if `jwtUtil.isTokenValid(token)`, extracts the email, and if no `Authentication` is already set in the `SecurityContextHolder`, loads a fresh `UserDetails` via `CustomUserDetailsService.loadUserByUsername(email)` and sets a `UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())` into the context. `SecurityConfig` (`backend/src/main/java/com/storehub/config/SecurityConfig.java`) registers this filter with `.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`, disables CSRF, sets `SessionCreationPolicy.STATELESS`, and authorizes requests with:
```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/**").permitAll()
        .requestMatchers("/actuator/health").permitAll()
        .anyRequest().authenticated())
```
`@EnableMethodSecurity` on `SecurityConfig` is what makes `@PreAuthorize` annotations on controllers take effect.

**Authority derivation** — `UserPrincipal.getAuthorities()` (`backend/src/main/java/com/storehub/security/UserPrincipal.java`) is rebuilt fresh from the database `User` on every request (via `CustomUserDetailsService`, called per-request by the filter above — never from a claim baked into the JWT itself). It returns:
- `ROLE_<user.role.name()>` (the legacy authority every pre-existing `hasRole(...)`/`hasAnyRole(...)` check used), plus
- one `PERM_<permission.name()>` `SimpleGrantedAuthority` per entry in `RolePermissions.forRole(user.getRole())`.

Because this is derived per-request from the current DB row rather than the JWT payload, an ADMIN changing a user's role takes effect on that user's very next request, even with an old-but-still-valid token in hand (explicitly called out in the class Javadoc).

`UserPrincipal` also implements the `UserDetails` account-status flags: `isEnabled()` returns `user.getStatus() == UserStatus.ACTIVE`, so an INACTIVE user's existing token, if authenticated, would still be rejected downstream by any check relying on `isEnabled()` (Spring Security's own auth flow), though the primary INACTIVE gate in this codebase is the explicit check in `AuthService.login`.

**`@PreAuthorize`/`hasAuthority` usage in this module**: `PermissionController` (class-level `hasAuthority('PERM_PERMISSION_VIEW')`), `RoleController` (class-level `hasAuthority('PERM_ROLE_VIEW')`). `AuthController` has none — it relies on `SecurityConfig`'s path-level `permitAll()`/`authenticated()` instead.

## 8. Permissions

Source: `RolePermissions.java` (`build()` method). Full permission catalog is in `Permission.java`, grouped by module there; the module value only affects UI grouping (`PermissionResponse.getModule()`), not authorization.

| Role | Permissions (EnumSet contents) |
|---|---|
| `ADMIN` | `EnumSet.allOf(Permission.class)` — every permission in the system. |
| `STORE_MANAGER` | All of ADMIN's set **minus**: `USER_VIEW`, `USER_CREATE`, `USER_EDIT`, `USER_DEACTIVATE`, `ROLE_VIEW`, `ROLE_MANAGE`, `PERMISSION_VIEW`, `FY_MANAGE`, `AUDIT_VIEW`, `GST_CONFIG`, `STORE_CREATE`, `STORE_EDIT`, `STORE_ASSIGN`, `STORE_ACCESS_ALL`. |
| `ACCOUNTANT` | `DASHBOARD_VIEW`; `PARTY_VIEW`, `ITEM_VIEW`, `MASTER_VIEW`, `EMPLOYEE_VIEW`, `STORE_VIEW`; `SALES_VIEW`, `RECEIPT_VIEW`, `CREDIT_NOTE_VIEW`; `PURCHASE_VIEW`, `PAYMENT_VIEW`, `DEBIT_NOTE_VIEW`; `ACCOUNT_VIEW`, `JOURNAL_VIEW`, `JOURNAL_CREATE`, `JOURNAL_POST`; `EXPENSE_VIEW`, `EXPENSE_CREATE`, `EXPENSE_POST`, `EXPENSE_CANCEL`; `REPORT_VIEW`, `REPORT_EXPORT`, `CASH_MANAGE`; `GST_VIEW`, `GST_REPORT`, `GST_EXPORT`, `GST_CONFIG`; `INVENTORY_VIEW`. |
| `SALES_USER` | `DASHBOARD_VIEW`; `SALES_VIEW`, `SALES_CREATE`, `SALES_EDIT`, `SALES_POST`; `RECEIPT_VIEW`, `RECEIPT_CREATE`, `RECEIPT_POST`; `CREDIT_NOTE_VIEW`; `POS_ACCESS`; `PARTY_VIEW`, `ITEM_VIEW`, `MASTER_VIEW`, `STORE_VIEW`. |
| `PURCHASE_USER` | `DASHBOARD_VIEW`; `PURCHASE_VIEW`, `PURCHASE_CREATE`, `PURCHASE_EDIT`, `PURCHASE_POST`; `PAYMENT_VIEW`, `PAYMENT_CREATE`, `PAYMENT_POST`; `DEBIT_NOTE_VIEW`; `PARTY_VIEW`, `ITEM_VIEW`, `MASTER_VIEW`, `STORE_VIEW`. |
| `INVENTORY_USER` | `DASHBOARD_VIEW`; `ITEM_VIEW`, `INVENTORY_VIEW`, `INVENTORY_ADJUST`; `STOCK_TRANSFER_VIEW`, `STOCK_TRANSFER_CREATE`, `STOCK_TRANSFER_DISPATCH`, `STOCK_TRANSFER_RECEIVE`; `MASTER_VIEW`, `STORE_VIEW`. |
| `STAFF` | `DASHBOARD_VIEW`, `POS_ACCESS`; `SALES_VIEW`, `SALES_CREATE`; `RECEIPT_VIEW`, `RECEIPT_CREATE`; `ITEM_VIEW`, `PARTY_VIEW`, `MASTER_VIEW`, `STORE_VIEW`. |

Permissions relevant specifically to *this* module's own screens: `PERMISSION_VIEW` (gates `GET /api/permissions`), `ROLE_VIEW` (gates `GET /api/roles` and `GET /api/roles/{role}/permissions`) — both held only by `ADMIN` (they are in the ADMIN-minus set removed from `STORE_MANAGER`). `STORE_ACCESS_ALL` (also ADMIN-only) drives `UserResponse.allStoresAccess`.

## 9. Transaction Handling

- `AuthService.register(RegisterRequest)` — `@Transactional`.
- `AuthService.login(LoginRequest)` — `@Transactional` (covers both the `lastLogin` update/save and the audit log write inside the same transaction as the login check).
- `AuthService.getCurrentUser(String)` — not annotated (read-only, single repository call).
- `AuditService.log(...)` (the overload actually taking `storeId`) is `@Transactional`, with **default propagation** (not `REQUIRES_NEW`) — per its own Javadoc, "an audit entry for an action that later rolls back should roll back with it," i.e. it deliberately joins the caller's existing transaction rather than committing independently.
- `PermissionService`, `RolePermissions`, `RoleDescriptions` have no transactional methods (no persistence involved).

## 10. Error Handling

`GlobalExceptionHandler` (`backend/src/main/java/com/storehub/exception/GlobalExceptionHandler.java`) maps this module's exceptions as follows, always returning an `ApiError { timestamp, status, error, message, path, fieldErrors }` body (`fieldErrors` omitted from JSON when null, via `@JsonInclude(NON_NULL)`):

| Exception | HTTP Status | Message source | `fieldErrors` |
|---|---|---|---|
| `MethodArgumentNotValidException` (bean validation failure on `@Valid` DTOs) | 400 `BAD_REQUEST` | `"Validation failed"` | populated from `ex.getBindingResult().getFieldErrors()`, keyed by DTO field name |
| `DuplicateEmailException` | 409 `CONFLICT` | exception message | none |
| `InvalidCredentialsException` | 401 `UNAUTHORIZED` | exception message | none |
| `BadCredentialsException` (Spring Security's own) | 401 `UNAUTHORIZED` | hardcoded `"Invalid email or password"` | none |
| `BadRequestException` | 400 `BAD_REQUEST` | exception message | none |
| `AccessDeniedException` (from `@PreAuthorize` failures) | 403 `FORBIDDEN` | hardcoded `"You do not have permission to access this resource"` | none |
| any other `Exception` | 500 `INTERNAL_SERVER_ERROR` | hardcoded `"An unexpected error occurred"` | none (also logged via `log.error`) |

This is the map the frontend's `parseApiError` (`frontend/src/utils/apiError.ts`) relies on: it prefers `fieldErrors` (rendering one line per field, e.g. `"Mobile: Mobile number must be 10 digits"`) and falls back to the top-level `message` when `fieldErrors` is empty, per this project's CLAUDE.md debugging-rule pattern.

## 11. Audit Flow

Yes — `AuthService.login` calls `AuditService.log(...)` directly (backend/src/main/java/com/storehub/service/AuthService.java, lines 64-78):

```java
// failed login, unknown email or wrong password
auditService.log(AuditAction.LOGIN, "AUTH", "User", user != null ? user.getId() : null,
        null, null, null, "Failed login attempt for " + request.getEmail() + ": invalid credentials");

// failed login, inactive account
auditService.log(AuditAction.LOGIN, "AUTH", "User", user.getId(), null,
        null, null, "Failed login attempt for " + user.getEmail() + ": account is inactive");

// successful login
auditService.log(AuditAction.LOGIN, "AUTH", "User", user.getId(), null,
        null, null, "User " + user.getEmail() + " logged in");
```

Each call logs: `action = AuditAction.LOGIN`, `module = "AUTH"`, `entityType = "User"`, `entityId` = the user's id (or `null` if the email didn't match any user), `documentNumber = null`, `oldValue = null`, `newValue = null`, and a human-readable `description`. `AuditService.log` (the 8-arg overload, `backend/src/main/java/com/storehub/service/AuditService.java`) delegates to the 9-arg `@Transactional` version with `storeId = null`, which additionally captures `userId`/`username` from `SecurityUtil.currentUserOrNull()` (typically `null`/`"System"` for a login attempt, since there's no authenticated principal yet), plus `ipAddress` (from `X-Forwarded-For` or `request.getRemoteAddr()`) and `userAgent` (from the `User-Agent` header).

`AuthService.register` does **not** call `AuditService` — no audit log is written for a new registration.

## 12. Important Side Effects

- **Login updates `lastLogin`**: `AuthService.login` sets `user.setLastLogin(LocalDateTime.now())` and saves it on every successful login (not on failed attempts).
- **Registration auto-assigns status**: `AuthService.register` always sets `status(UserStatus.ACTIVE)` — a newly self-registered account is active immediately, no email verification or admin approval step exists in this codebase.
- **Registration does not log the user in**: it returns a `UserResponse` (201 Created), not an `AuthResponse`/token; `Register.tsx` explicitly redirects to `/login` after success rather than storing a session.
- **JWT has no server-side revocation**: `/api/auth/logout` (`AuthController.logout`) is a no-op that returns a static success message — it does not blacklist the token server-side. "Logout" is purely the frontend discarding `localStorage['storehub_token']`/`storehub_user`. A previously issued token remains valid (and accepted by `JwtAuthenticationFilter`) until its `expirationMs` lapses, regardless of client-side logout.
- **JWT expiry affects the whole app**: since `SecurityConfig` requires authentication for `anyRequest()` beyond `/api/auth/**` and `/actuator/health`, every other module's API calls fail with 401 once the token in `${jwt.expiration-ms}` expires; `AuthContext`'s `me()` failure handler is what clears local session state client-side in that case.
- **Role/permission changes take effect immediately, not on next login**: because `UserPrincipal.getAuthorities()` is derived fresh per-request from the DB `User.role` (not a JWT claim), changing a user's role via user management is live on their very next API call, even with their existing token still valid.
- **Single-store auto-selection**: `AuthContext.loadMyStores` silently calls `authApi.setCurrentStore` when the user has exactly one accessible store and no `currentStoreId` set yet — a side effect of the login/hydration flow, not something the user explicitly requested.
- **Same generic message for "no such email" and "wrong password"**: `InvalidCredentialsException("Invalid email or password")` is thrown identically in both cases, a deliberate anti-enumeration side effect of `AuthService.login`'s logic.

## 13. Dependencies on Other Modules

**This module depends on:**
- `Employee` entity (`User.employee` FK) — optional link surfaced in `UserResponse.employeeId/employeeName/employeeCode`.
- `Store` entity (`User.currentStore` FK, and `Store`-scoped `getMyStores`/`setCurrentStore` endpoints on `AuthController`) — multi-store access is validated elsewhere (`StoreAccessService`, per `User.java`'s own Javadoc) but surfaced through this module's `AuthResponse`/`UserResponse`.
- `AuditService`/`AuditLogRepository`/`Store` (via `AuditService.storeRepository`) — for writing login audit entries.
- `UserService` — `AuthController` delegates `getMyStores`/`setCurrentStore` to `UserService.getAccessibleStores`/`setCurrentStore` (not itself part of this module's ground-truth files, but directly called from `AuthController`).

**What depends on this module:** effectively every other module in the application. Every other controller's `@PreAuthorize("hasAuthority('PERM_...')")` check depends on `RolePermissions`/`UserPrincipal.getAuthorities()` defined here; every authenticated request depends on `JwtAuthenticationFilter`/`JwtUtil`/`CustomUserDetailsService`; every frontend route beyond `/login` and `/register` depends on `ProtectedRoute`/`AdminRoute`/`ManagerRoute` and `AuthContext.user`/`hasPermission`.

## 14. Key Operation Flows

**Login:**
```
UI (Login.tsx:Login/handleSubmit) → Function (AuthContext.tsx:login) → API (POST /api/auth/login) → Controller (AuthController.login) → DTO (LoginRequest) → Service (AuthService.login) → Repository (UserRepository.findByEmail) → Database (users) → Response (AuthResponse{token, user: UserResponse}) → UI (AuthContext sets user/token in state + localStorage, Login.tsx navigates to /dashboard)
```

**Register:**
```
UI (Register.tsx:Register/handleSubmit) → Function (AuthContext.tsx:register) → API (POST /api/auth/register) → Controller (AuthController.register) → DTO (RegisterRequest) → Service (AuthService.register) → Repository (UserRepository.existsByEmail, UserRepository.save) → Database (users) → Response (UserResponse, HTTP 201) → UI (Register.tsx shows success alert, redirects to /login after 1500ms)
```

**Session hydration on app load (not strictly "login"/"register" but the third core flow of this module):**
```
UI (AuthContext.tsx:AuthProvider useEffect) → API (GET /api/auth/me, Bearer token from localStorage) → Controller (AuthController.getCurrentUser) → Service (AuthService.getCurrentUser) → Repository (UserRepository.findByEmail) → Database (users) → Response (UserResponse) → UI (AuthContext refreshes user + permissions, or clears session on 401)
```

**Password Change/Reset:** NOT FOUND IN CURRENT CODEBASE within this module's ground-truth files — `frontend/src/types/user.ts` declares `PasswordChangePayload`/`AdminPasswordResetPayload` and `userApi.ts` exposes `changeOwnPassword`/`resetPassword` (`PUT /api/users/me/password`, `POST /api/users/{id}/reset-password`), but the controller/service backing these live in the User Management module (`UserController`/`UserService`), not in `AuthController`/`AuthService`/`PermissionController`/`RoleController` covered here.

## 15. Manual Changes

**Add a new field to registration/login:**
- Add the field to `RegisterRequest` (`backend/src/main/java/com/storehub/dto/RegisterRequest.java`) with its validation annotation, and to `LoginRequest` (`backend/src/main/java/com/storehub/dto/LoginRequest.java`) if it belongs to login.
- Add the same field to the `User` entity (`backend/src/main/java/com/storehub/entity/User.java`) with its `@Column`, and set it in `AuthService.register`'s `User.builder()` call.
- Surface it on the response side in `UserResponse` (`backend/src/main/java/com/storehub/dto/UserResponse.java`, both the field and `fromEntity`).
- Add the form field to `Register.tsx` (state in the `form` object, `<Input>`, and error display) and/or `Login.tsx`.
- Add it to `RegisterPayload`/`LoginPayload`/`User` in `frontend/src/types/user.ts`.
- Affected: a DB migration/schema change for the new `users` column; any admin `UserCreateRequest`/`UserUpdateRequest` DTOs and their forms (`UserFormModal.tsx`, per CLAUDE.md) if the field should also be editable by an admin.

**Change JWT expiry or claims:**
- Expiry: change the `jwt.expiration-ms` property (application config, e.g. `application.yml`/`application.properties`) consumed by `JwtUtil`'s constructor (`backend/src/main/java/com/storehub/security/JwtUtil.java`).
- Claims: add to `JwtUtil.generateToken(String email, String role)` (add another `.claim("x", value)` call before `.compact()`), then add a corresponding `extractX(token)` helper if the claim needs to be read back. Note authorization does **not** currently trust the `role` claim at request time (it's re-derived from the DB via `CustomUserDetailsService`/`UserPrincipal`), so a new claim used for authorization would be a deliberate architecture change, not just a JWT tweak.
- Affected: nothing else in the authorization path needs to change since authorities are DB-derived per request, but any code relying on `JwtUtil.extractRole` (if any exists elsewhere) would need review.

**Change password validation rules:**
- Update `@Size(min = 6, ...)` on `RegisterRequest.password` (`backend/src/main/java/com/storehub/dto/RegisterRequest.java`) and the equivalent field on `UserCreateRequest` (`backend/src/main/java/com/storehub/dto/UserCreateRequest.java`) — keep both in sync, since they currently duplicate the same rule.
- Update the frontend's `minLength={6}` on `Register.tsx`'s password `<Input>` to match.
- Any password-change/reset DTOs (outside this module's read files, per section 14) would also need the same rule applied for consistency.

**Add a new Permission or Role:**
- New `Permission`: add the enum constant (with its module string) to `Permission.java` (`backend/src/main/java/com/storehub/entity/Permission.java`), then add it to every `Role`'s `EnumSet` in `RolePermissions.build()` (`backend/src/main/java/com/storehub/service/RolePermissions.java`) that should carry it. `ADMIN` gets it automatically via `EnumSet.allOf(Permission.class)`.
- New `Role`: add the enum constant to `Role.java` (`backend/src/main/java/com/storehub/entity/Role.java`), add its entry to `RolePermissions.build()`'s map, and add a description in `RoleDescriptions.build()` (`backend/src/main/java/com/storehub/service/RoleDescriptions.java`).
- Affected: (1) `RolePermissions.java` — the authorization source of truth; (2) `UserPrincipal.getAuthorities()` needs no code change (it already iterates `RolePermissions.forRole(...)` generically) but any `@PreAuthorize("hasAuthority('PERM_...')")` annotation that should now reference the new permission must be added/updated on the relevant controller; (3) frontend `Role` type in `frontend/src/types/user.ts` (add the new literal to the `Role` union); (4) `Register.tsx`'s `REGISTERABLE_ROLES` array if the new role should be self-registerable; (5) `ManagerRoute`/`AdminRoute` usages in `App.tsx` (the `extraRoles` arrays) if the new role should access existing manager-gated routes.

**Change which roles can self-register:**
- Backend gate: `AuthService.register`'s single check `if (request.getRole() == Role.ADMIN) throw new BadRequestException(...)` (`backend/src/main/java/com/storehub/service/AuthService.java`) — currently the only backend restriction is "not ADMIN"; every other role is backend-permitted to self-register.
- Frontend gate: `Register.tsx`'s `REGISTERABLE_ROLES` constant (`frontend/src/pages/Register.tsx`, lines 14-17) currently limits the dropdown to `STORE_MANAGER` and `STAFF` only — this is a UI-only restriction; a request crafted directly against `POST /api/auth/register` with, say, `role: "ACCOUNTANT"` would currently succeed since the backend does not block it. Any tightening of self-registerable roles should be enforced in `AuthService.register` (backend), not just in `Register.tsx` (frontend-only restrictions are bypassable).

**Every one of these changes should be cross-checked against:** `RolePermissions.java` (authorization source of truth), `UserPrincipal.getAuthorities()` (only if authority-derivation logic itself changes, not for adding within existing structure), the specific `@PreAuthorize` annotations on affected controllers, and `frontend/src/types/user.ts`'s `Role`/`Permission` types plus any frontend route guards (`AdminRoute.tsx`, `ManagerRoute.tsx`) or role-gated UI (`Register.tsx`, `RolesAndPermissions.tsx`) that assume a fixed role/permission set.
