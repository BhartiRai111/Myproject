# Operations Guide (Phase 6)

Covers production configuration, backup/restore, and health monitoring for the
StoreHub backend. Written for whoever deploys and operates this application,
not for end users of the app itself.

## 1. Production configuration

All secrets and environment-specific values are read from environment
variables with **dev-only fallback defaults** in
`backend/src/main/resources/application.properties`:

| Variable | Purpose | Dev default (never use in production) |
|---|---|---|
| `DB_USERNAME` | MySQL username | `root` |
| `DB_PASSWORD` | MySQL password | `root` |
| `JWT_SECRET` | HMAC signing key for JWTs | a fixed placeholder string committed in this repo |
| `JWT_EXPIRATION_MS` | JWT lifetime in ms | `86400000` (24h) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins | `http://localhost:5173` |

**Before deploying to production:**
- Set `DB_USERNAME` / `DB_PASSWORD` to a dedicated, least-privilege MySQL
  account (not `root`).
- Set `JWT_SECRET` to a long, random, unique value — the committed default is
  a known placeholder and must never be used outside local development. There
  is no automatic warning if it's left unset in production; this is a manual
  deployment checklist item.
- Set `CORS_ALLOWED_ORIGINS` to the real frontend origin(s) only.
- No secrets are committed to source control as literal production values —
  everything above is overridden via environment variables in the deployment
  environment (container env vars, systemd `EnvironmentFile`, etc.), never
  hardcoded in `application.properties` itself.

### Logging

The application uses Spring Boot's default structured console logging
(Logback). No request/response body logging, no password or JWT logging is
present anywhere in the codebase — `spring.jpa.show-sql` is `false` by
default, so SQL parameter values (which could include customer/GST data)
are not written to logs either. If SQL logging is temporarily enabled for
debugging, turn it back off before returning to production.

### Centralized system settings

**Not implemented in Phase 6.** There is no in-app "System Settings" screen
for admins to change things like currency symbol, business name, or GST
default rates at runtime — these remain either hardcoded (₹ / INR) or
configured through the existing Masters (HSN, Unit, etc.) and environment
variables above. This is a disclosed gap, not a partial/broken feature.

## 2. Health endpoint

`GET /actuator/health` (added in Phase 6) is public (no JWT required — the
security filter chain explicitly permits only this one actuator path) and
returns only `{"status":"UP"}` or `{"status":"DOWN"}` —
`management.endpoint.health.show-details=never` ensures no database
connection details, disk space, or other internals are ever exposed to an
unauthenticated caller. No other `/actuator/**` endpoint is exposed; all of
them fall through to the default `anyRequest().authenticated()` rule and
return 403 without a valid JWT — confirmed by manual test against a running
instance (`/actuator/env`, `/actuator/beans`, and the actuator index page
all 403; `/actuator/health` 200 with the minimal body above).

Use this endpoint for container/load-balancer liveness checks.

## 3. Backup strategy

**There is no in-app backup or restore feature**, and this is a deliberate
choice, not an oversight. Phase 6 explicitly restricts what's safe to build
here: no one-click destructive restore endpoint, since a bug or a
compromised admin session in an in-app restore path could silently and
irreversibly overwrite the live financial ledger with no way back. Database
backup and restore are operated **outside** the application, using MySQL's
own tooling, by whoever has infrastructure/DB access — the same person who
already holds `DB_USERNAME`/`DB_PASSWORD`.

### Taking a backup

```bash
mysqldump -h <host> -u <user> -p \
  --single-transaction --routines --triggers \
  storehub_db > storehub_backup_$(date +%Y%m%d_%H%M%S).sql
```

`--single-transaction` takes a consistent snapshot without locking tables
(safe to run against the live database on InnoDB, which this schema uses).
Run this on a schedule (e.g. daily via cron) and store the dumps somewhere
with its own access control and retention policy — this document does not
prescribe where, since that's an infrastructure decision outside the app's
scope.

### Restoring a backup

Restoring is **destructive to whatever is currently in the target
database** and must only be done deliberately, by an operator with DB
access, never by the application itself:

```bash
mysql -h <host> -u <user> -p storehub_db < storehub_backup_20260101_020000.sql
```

Before restoring into a database that has live data: take a fresh backup of
the current state first (so the restore itself is reversible), and confirm
the target database name and host — there is no confirmation prompt built
into a raw `mysql` restore.

### What Phase 6 added instead: catalog-level export

Product and Inventory CSV export (`GET /api/products/export`,
`GET /api/inventory/export`, both ADMIN/STORE_MANAGER-only) let an operator
pull the current product catalog and stock levels into a spreadsheet. This
is **not** a database backup — it covers only Products and current
Inventory, has no restore counterpart, and is meant for reporting/audit
use, not disaster recovery. See section 4 for the one write path
(`POST /api/products/import`) it pairs with.

## 4. Product CSV import safety

`POST /api/products/import` (ADMIN/STORE_MANAGER-only, multipart CSV
upload) is the only bulk-write path added in Phase 6, and it is
deliberately narrow:

- It only ever creates or updates rows in the `products`/`inventory` tables
  through the normal `ProductService`/`InventoryService` code paths — never
  raw SQL, never a table truncate-and-reload.
- Opening stock (an optional `openingStock` column) is applied only to
  **newly created** products, and only through
  `InventoryService.applyMovement(...)` — the same centralized stock-in
  movement every Purchase uses — so it always produces a `StockHistory` row
  and can be audited like any other stock change. Existing products
  matched by SKU are updated on their catalog fields only; their current
  stock is never touched by import, so re-importing the same file twice is
  safe and won't double-count stock.
- A row with an unknown category, a duplicate name, or an invalid number is
  skipped with a specific error message returned to the caller — the import
  is row-by-row best-effort, not all-or-nothing, so one bad row never blocks
  the rest of the file.
