# StockSmart — Retail Inventory Optimization & Supply Chain Management

Day 7 completes **deployment**: Docker images for backend + frontend, a full
compose stack (Angular/Nginx → Spring Boot → Oracle XE), externalized config,
smoke tests, and an expanded Jenkins pipeline. See §15 for the verified status
and the environment limits. Day 6 completed hardening; Days 1–5 built auth,
master data, inventory, orders, barcode/RFID and the KPI dashboard.

> Scope guard: Days 1–7 are complete. There is no forecasting/ML; RFID is
> simulator-backed (no physical hardware is integrated), and reads never change
> stock.

---

## 1. Technology stack

| Layer    | Choice                                                                 |
|----------|------------------------------------------------------------------------|
| Backend  | Java 17, Spring Boot 3.2, Spring Web, Spring Data JPA, Hibernate, Maven |
| Database | Oracle Database (Oracle XE via Docker for local), `ojdbc11` driver     |
| Security | Spring Security, JWT (jjwt), BCrypt, RBAC                              |
| Frontend | Angular 17 (standalone), TypeScript, Router, HttpClient                |
| Docs     | Swagger/OpenAPI (springdoc), Docker-ready                               |

---

## 2. Prerequisites

- Java 17+, Maven 3.9+ (native backend)
- Node.js 20+, npm 10+ (native frontend)
- Docker Engine 24+ with Compose v2 (`docker compose`) for the containerized
  stack — or use the H2 `local` profile with no Docker at all
- Ports free: `8080` (API), `4200` (Angular), `1521` (Oracle)
- Oracle XE container is amd64-only; ARM64 hosts use the override in §15.2

## 3. Oracle setup

**Option A — Docker (recommended, Oracle is the system of record):**

```bash
cp .env.example .env        # fill real values; never commit .env
docker compose up -d oracle # starts Oracle XE, persists in oracle-data volume
```

**Option B — existing Oracle:** point `DB_HOST / DB_PORT / DB_SERVICE /
DB_USERNAME / DB_PASSWORD` at it. Schema objects (`roles`, `users`, sequences)
are created by Hibernate (`ddl-auto: update`); no manual DDL needed for Day 1.

**Option C — no Oracle available:** run the backend on the H2 `local` profile
(zero-dependency; Oracle remains primary):

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## 4. Environment variables

| Variable               | Default (dev)              | Purpose                              |
|------------------------|----------------------------|--------------------------------------|
| `DB_HOST`              | `localhost`                | Oracle host                          |
| `DB_PORT`              | `1521`                     | Oracle listener port                 |
| `DB_SERVICE`           | `XEPDB1`                   | Oracle service name                  |
| `DB_USERNAME`          | `stocksmart`               | DB user                              |
| `DB_PASSWORD`          | _(none — set it)_          | DB password                          |
| `JWT_SECRET`           | dev fallback in config     | **Override in real deployments**     |
| `JWT_EXPIRATION`       | `86400000` (24h)           | Token TTL in ms                      |
| `FRONTEND_URL`         | `http://localhost:4200`    | Frontend origin (CORS fallback)      |
| `CORS_ALLOWED_ORIGINS` | _(falls back to above)_    | Tight allow-list; wins when set      |
| `APP_SEED_ENABLED`     | `true`                     | Seed roles + demo users (`false` in prod) |
| `APP_SEED_PASSWORD`    | `Admin@123`                | Demo password (dev only)             |

## 5. Backend startup

```bash
cd backend
mvn spring-boot:run                                # Oracle (application.yml)
mvn spring-boot:run -Dspring-boot.run.profiles=local  # H2 fallback, no Oracle needed
```

Backend serves on `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`

## 6. Frontend startup

```bash
cd frontend
npm install
npm start                 # ng serve → http://localhost:4200
```

Dev builds call the API directly at `http://localhost:8080` (CORS allows the dev
origin). Production builds are same-origin (`/api/...`, see §15.1) — the origin
switch is an Angular `fileReplacements` entry, not a code change.

## 7. Authentication test (curl)

```bash
# Health (public)
curl http://localhost:8080/api/v1/health

# Login (demo seed: admin / Admin@123)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')

# Protected (needs any JWT) / admin (needs ADMIN) / management (needs MANAGEMENT)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/test/protected
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/test/admin
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/test/management

# Negative checks
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"wrong"}'  # → 401
curl http://localhost:8080/api/v1/test/protected                                     # → 401 (no token)
```

Automated matrix: `cd backend && mvn test` (`SecurityVerificationTest`: 7 tests —
health, login 200/401, protected 200/401, expired JWT 401, invalid JWT 401,
admin/management RBAC, BCrypt storage, JWT tamper checks).

## 8. API documentation

Swagger UI (`/swagger-ui.html`) documents health, login, and the three test
endpoints with a **Bearer/JWT** security scheme — paste the login JWT via
“Authorize” to call protected endpoints.

## 9. CI/CD

The root `Jenkinsfile` is the pipeline: backend `mvn -B -ntp clean verify`
(67 tests, H2), frontend `npm ci` + production build (+ `ng test` only when
Chrome exists on the agent, otherwise an explicit skip), Docker image builds,
compose-config validation, and an opt-in local deploy + smoke test. Details,
prerequisites and the exact verified/unverified status: §15.7–§15.8.
`.dockerignore` files keep build contexts small. Docker is not required to run
or test Days 1–6 locally.

## 10. Day 2 — Master Data Management

Four modules, same conventions as Day 1 (Controller → Service → Repository →
Oracle, DTOs only on the wire, constructor injection, centralized errors).

| Module | Key fields | Business rules |
|---|---|---|
| Categories | code (unique), name, description, active | duplicate codes → 409; delete blocked (409) while products reference the category |
| Products | SKU (unique), name, category FK, brand, unit, cost/selling price, active | valid category required (else 404); duplicate SKU → 409; prices ≥ 0; filter by `categoryId`/`active`; search SKU/name |
| Warehouses | code (unique), name, address/city/state/postal, contact person/email/phone, active | duplicate codes → 409; email/phone validated; search code/name/city |
| Suppliers | code (unique), name, contact person/email/phone, address/city/state/postal, active | duplicate codes → 409; email/phone validated; search code/name/contact info |

Tables: `categories`, `products` (FK `fk_products_category` → categories),
`warehouses`, `suppliers` — sequence PKs, unique business codes, audit
timestamps. See `design.md` for the full data model.

### 10.1 Endpoints (`/api/v1/...`, JWT required)

Each group supports `GET` (paged list) / `GET /{id}` / `POST` (201) /
`PUT /{id}` / `DELETE` (204), with `search`, `active`, `page`, `size`, `sort`
params (products add `categoryId`):

- `/api/v1/categories`
- `/api/v1/products`
- `/api/v1/warehouses`
- `/api/v1/suppliers`

RBAC baseline (backend-enforced, filter + `@PreAuthorize`):

| Role | Categories | Products | Warehouses | Suppliers |
|---|---|---|---|---|
| ADMIN | RW | RW | RW | RW |
| INVENTORY_MANAGER | RW | RW | RW | R |
| PROCUREMENT_USER | R | R | R | RW |
| WAREHOUSE_STAFF | R | R | R | — |
| SALES_USER | R | R | — | — |
| MANAGEMENT | R | R | R | R |

Example:

```bash
# Categories (admin)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/categories?search=elec
curl -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"code":"ELEC","name":"Electronics"}' http://localhost:8080/api/v1/categories

# Products in a category
curl -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/v1/products?categoryId=1&active=true'
```

### 10.2 Angular screens

Guarded routes `/categories`, `/products`, `/warehouses`, `/suppliers`
(linked from Home): tables with search + filters, create/edit forms
(product form loads categories from the API), validation, loading/empty
states, API error display, delete confirmation, role-aware write buttons
(backend remains the security boundary).

### 10.3 Tests

`cd backend && mvn test` — Day 1 `SecurityVerificationTest` (6 tests) plus
Day 2 `MasterDataIntegrationTest` (8 tests: CRUD per module, duplicate-code/SKU
409s, invalid-category 404, search/filter matrix, referenced-category delete
409, validation 400s, missing-record 404s, full RBAC matrix).

## 11. Day 3 — Inventory Management

Stock balances per product-warehouse plus an append-only movement history.
Same conventions (Controller → Service → Repository → Oracle, DTOs only,
centralized errors).

### 11.1 Rules

- `inventory` holds one row per `(product_id, warehouse_id)` (unique).
  Quantities are `BigDecimal` (`NUMBER(19,4)`), never negative.
- Receipts create the row on first use (`OPENING_BALANCE`) and add
  afterwards (`RECEIPT`) — no purchase order required.
- Adjustments need direction + reason; decreases beyond stock → 409 and
  leave no balance change and no transaction row.
- Transfers validate everything before writing (different warehouses,
  positive quantity, sufficient source stock), then commit source/dest
  updates plus paired `TRANSFER_OUT`/`TRANSFER_IN` rows sharing one
  reference — atomically. Failed transfers change nothing.
- Concurrency: pessimistic row locks + deterministic lock order;
  first-row creation races retry once in a fresh transaction.
- Products/warehouses with inventory rows cannot be deleted (409).

### 11.2 Endpoints (`/api/v1/...`, JWT required)

- `GET /inventory` (search/productId/warehouseId, paged), `GET /inventory/{id}`,
  `GET /inventory/product/{productId}`, `GET /inventory/warehouse/{warehouseId}`
- `POST /inventory/receipts`, `POST /inventory/adjustments`, `POST /inventory/transfers` (201)
- `GET /stock-transactions` (productId/warehouseId/type/reference/from/to, newest first),
  `GET /stock-transactions/{id}`

RBAC: reads for every authenticated role; receipts/adjustments/transfers for
ADMIN, INVENTORY_MANAGER, WAREHOUSE_STAFF (others → 403).

Example:

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"productId":1,"warehouseId":1,"quantity":50}' \
  http://localhost:8080/api/v1/inventory/receipts
curl -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"productId":1,"sourceWarehouseId":1,"destinationWarehouseId":2,"quantity":10}' \
  http://localhost:8080/api/v1/inventory/transfers
```

### 11.3 Angular screens

Guarded routes `/inventory` (overview + filters + paging), `/inventory/operations`
(receipt + adjustment forms with confirmation), `/inventory/transfers`
(same-warehouse blocked client-side), `/inventory/history` (type/reference/
product/warehouse filters) — linked from Home.

### 11.4 Tests

`cd backend && mvn test` — as of Day 3: 26 tests: Day 1 (6) + Day 2 (8) +
`InventoryIntegrationTest` (12: receipts, validation, adjustments, negative-stock
rejection with zero side effects, atomic paired transfers, rejection matrix,
history search, delete guards, 6-role RBAC, concurrent withdrawals staying exact).

## 12. Day 4 — Purchase & Sales Orders

Procurement and sales workflows wired into Day 3 inventory. Same conventions
(services own the rules, DTOs only, centralized errors).

### 12.1 Purchase orders

Lifecycle `DRAFT → SUBMITTED → PARTIALLY_RECEIVED → RECEIVED`, plus `CANCELLED`
from any open state (received stock is kept, never reversed). Lines editable
while DRAFT; submission needs ≥1 line; receipts only on submitted/partial
orders, never beyond remaining quantity. Each receipt records a durable
`purchase_receipts` row (unique reference = idempotency key), increases stock
through the Day 3 service (`RECEIPT` movements sharing the reference), and
updates received quantities + status — all in one transaction.

### 12.2 Sales orders

Lifecycle `DRAFT → CONFIRMED → PARTIALLY_FULFILLED → FULFILLED`, plus
`CANCELLED`. Confirming does **not** reserve stock. Fulfillment checks
remaining quantity and live stock, issues through the Day 3 service (`ISSUE`
movements sharing the fulfillment reference), and updates fulfilled
quantities + status — all in one transaction. Failed fulfillment changes
nothing.

### 12.3 Endpoints (`/api/v1/...`, JWT required)

- `/purchase-orders`: `GET` (search/status/supplierId, paged), `GET /{id}`,
  `GET /{id}/receipts`, `POST` 201, `PUT /{id}` (draft),
  `POST /{id}/submit`, `POST /{id}/receipts` 201, `POST /{id}/cancel`
- `/sales-orders`: `GET` (search/status/warehouseId, paged), `GET /{id}`,
  `GET /{id}/fulfillments`, `POST` 201, `PUT /{id}` (draft),
  `POST /{id}/confirm`, `POST /{id}/fulfillments` 201, `POST /{id}/cancel`

RBAC: PO reads (ADMIN/IM/PROCUREMENT/WH/MGMT), PO writes (ADMIN/PROCUREMENT),
receipts (+IM/WH); SO reads (ADMIN/IM/SALES/WH/MGMT), SO writes
(ADMIN/SALES), fulfillments (+IM/WH). No cross-access (sales↔purchase).

Example:

```bash
# Purchase: create → submit → receive (procurement user)
curl -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"supplierId":1,"warehouseId":1,"orderDate":"2026-10-14","items":[{"productId":1,"orderedQuantity":100,"unitCost":90}]}' \
  http://localhost:8080/api/v1/purchase-orders
curl -X POST -H "Authorization: Bearer $TOKEN" -d '{}' \
  http://localhost:8080/api/v1/purchase-orders/1/submit
curl -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"lines":[{"poItemId":1,"quantity":60}]}' \
  http://localhost:8080/api/v1/purchase-orders/1/receipts
```

### 12.4 Angular screens

Guarded routes `/purchase-orders`, `/purchase-orders/new|/:id` (draft editor,
submit/cancel, receipt form with per-line quantities, receipt history) and the
`/sales-orders` equivalents (confirm/fulfill, fulfillment history) — linked
from Home, with validation, confirmations, status badges, and role-aware actions.

### 12.5 Tests

`cd backend && mvn test` — as of Day 4: 34 tests: Day 1 (6) + Day 2 (8) + Day 3 (12) +
`OrderIntegrationTest` (8: PO lifecycle with partial receipts, PO validation +
rollback, receipt idempotency, SO lifecycle with partial fulfillment, SO
validation + rollback, fulfillment idempotency, concurrent fulfillments never
double-issuing, cross-role RBAC).

## 13. Day 5 — Barcode, RFID & KPI Dashboard

### 13.1 Barcode operations

`products` gain an optional `barcode` (unique when present, stored UPPERCASE,
pattern `^[A-Za-z0-9.\-+/]{4,60}$`) and a `minimum_stock_level` (optional,
default 0) that drives low-stock KPIs. Resolve a scan with
`GET /api/v1/products/barcode/{barcode}` (or `.../sku/{sku}`). **Lookups never
change stock** — the Angular `/scan` screen shows the product and links to the
receipt/adjustment workflow for real movements.

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/products/barcode/8901234567890
```

### 13.2 RFID (tag mapping + simulator)

- `rfid_tags` (EPC → product, unique EPC, deactivate-instead-of-delete) and
  append-only `rfid_read_events` (unique `event_reference` idempotency key —
  replays → 409).
- Endpoints: `GET/POST/PUT /api/v1/rfid/tags`, `GET /api/v1/rfid/reader`,
  `POST/GET /api/v1/rfid/reads`.
- Reader abstraction `RfidReader` with the built-in `SimulatedRfidReader`
  (`SIMULATED-READER-01`). Processed reads are `RESOLVED`, `INACTIVE_TAG` or
  `UNKNOWN_TAG` and are always labeled simulated/hardware by the server.
  **No physical reader is integrated; reads never change stock.**
- RBAC: tag writes ADMIN; read processing ADMIN/INVENTORY_MANAGER/
  WAREHOUSE_STAFF/PROCUREMENT_USER/SALES_USER; RFID reads any authenticated
  role; MANAGEMENT read-only.
- Angular `/rfid`: reader card, tag registry (register/remap/deactivate for
  ADMIN), simulate/process read form, processed-events log.

```bash
# Register a tag (admin), then simulate a read
curl -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"epcCode":"3034F9C2A1B0","productId":1}' http://localhost:8080/api/v1/rfid/tags
curl -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"epcCode":"3034F9C2A1B0","eventReference":"dock-in-001","simulate":true}' \
  http://localhost:8080/api/v1/rfid/reads
```

### 13.3 Inventory KPI dashboard

`GET /api/v1/dashboard/summary | low-stock | stockouts | purchase-orders |
sales-fulfillment | recent-transactions` — all computed from persisted rows
(no caching, no fake metrics), optionally scoped by `warehouseId` (unknown →
404). Definitions:

- Low stock: `0 < quantity_on_hand < COALESCE(minimum_stock_level, 0)`
  (deficit = minimum − on-hand; exactly-at-minimum is not low; zero is a stockout).
- Stockout: `quantity_on_hand = 0`.
- `inventoryValue = SUM(quantity_on_hand × cost_price)` scale 2 HALF_UP —
  a cost-basis estimate, not an accounting valuation.
- Pending POs = `SUBMITTED + PARTIALLY_RECEIVED`; pending SOs =
  `CONFIRMED + PARTIALLY_FULFILLED` with remaining units summed per line.

RBAC mirrors the Day 4 read matrix (PO summary excludes SALES_USER, SO summary
excludes PROCUREMENT_USER; core KPIs readable by every role) — the backend is
the boundary, the Angular `/dashboard` card just hides itself on a 403.

### 13.4 Tests

`cd backend && mvn test` — as of Day 5: 61 tests: Day 1 (6) + Day 2 (8) + Day 3 (12) +
Day 4 (8) + `BarcodeRfidIntegrationTest` (15: barcode/SKU lookups + stock
unchanged proof, duplicate/invalid barcode 409/400, tag lifecycle + duplicate
EPC, simulated/external reads, unknown/inactive statuses, event-reference
dedup, RFID RBAC) + `DashboardIntegrationTest` (12: exact KPI values, low-stock
boundaries, warehouse filter/paging, cost-basis rounding, empty dataset,
pending counts, no-double-count, recent movements, auth matrix). Day 6 expands
the suite to 67 — see §14.

## 14. Day 6 — Hardening & release verification

### 14.1 Defects found and fixed

| Finding | Fix |
|---|---|
| Duplicate line ids in one goods receipt / fulfillment passed the per-line remaining-quantity check and could over-receive / over-fulfill | `PurchaseOrderService.receive` / `SalesOrderService.fulfill` reject duplicate line ids up front (400), before any stock or quantity write |
| `DELETE` on suppliers / warehouses / products could orphan purchase / sales order lines or stock history | Delete guards now return 409 with a clear message when POs, SOs, order lines, or stock movements reference the record (deactivate instead) |
| Client input mistakes rendered 500: wrong path-variable / query types, unknown URLs, wrong method, wrong media type | `GlobalExceptionHandler` maps them to 400 / 404 / 405 / 415 JSON responses |
| Angular: a 401 (expired / revoked token) left screens stuck on error banners | `authInterceptor` session-expiry policy — clear session + redirect to `/login` on any non-login 401 |
| Signing with the public dev JWT default was silent | `JwtService` logs a startup warning when `JWT_SECRET` is the documented dev fallback |
| N+1 lazy loads on order / receipt / fulfillment item collections | `@BatchSize(50)` on order aggregate entities |

### 14.2 Verification evidence (executed 2026-10-17)

- `cd backend && mvn test` → **67/67 pass** (H2 test profile):
  `SecurityVerificationTest` (7 — includes new signature / claim-tampering 401
  tests) + `MasterDataIntegrationTest` (8) + `InventoryIntegrationTest` (12) +
  `OrderIntegrationTest` (8) + `BarcodeRfidIntegrationTest` (15) +
  `DashboardIntegrationTest` (12) + `HardeningIntegrationTest` (5 — duplicate
  receipt line, duplicate fulfillment line, client-error 400/404/405/415 matrix,
  delete guards, CORS allow-list).
- `cd frontend && npm run build` → production build succeeds (~470 kB initial).
- Angular unit tests: **not executed** — Karma needs a browser; none is
  available in this environment (pre-existing project limitation).
- **Oracle runtime: not verified here** — no Docker / Oracle in this
  environment. Automated tests run on the H2 test profile; Oracle remains the
  primary config / driver / dialect (schema is JPA-managed).

### 14.3 Known limitations

- JWT is stateless: deactivating / deleting a user does not revoke an
  already-issued token before expiry (24h default). Acceptable for this phase;
  revocation would need a deny-list or a shorter TTL.
- The dev fallback `JWT_SECRET` and default DB password in `application.yml`
  are overridable dev-only defaults. Real deployments must override both (the
  app warns when the default JWT secret is in use).
- RFID is simulator-only; no physical hardware integration.

## 15. Day 7 — Deployment & CI/CD

### 15.1 Architecture

```text
Browser ──► frontend container (nginx :80 → host :4200)
                │  serves the Angular production bundle
                │  /api/*  reverse-proxied to BACKEND_URL (default backend:8080)
                ▼
            backend container (Spring Boot :8080 → host :8080)
                │  JPA / HikariCP
                ▼
            oracle container (gvenzl/oracle-xe:21 → host :1521)
                └ named volume `oracle-data` (persistent)
```

- The production bundle calls the API **same-origin** (`/api/...`):
  `environment.prod.ts` has `apiUrl: ''` and nginx proxies to the backend —
  no CORS, no internal hostname in the browser bundle, portable behind any
  host/domain. The dev/prod switch is an Angular `fileReplacements` entry.
- Oracle is the system of record; H2 (`local` profile / tests) is a fallback.

### 15.2 Prerequisites & platform notes

- Docker Engine 24+ / Docker Desktop with **Compose v2** (`docker compose`).
  No Docker? Use the native H2 path (§5) — nothing else changes.
- Oracle XE 21c is **amd64-only**. On Apple Silicon / ARM64 hosts:
  `docker compose -f docker-compose.yml -f docker-compose.arm64.yml up -d --build`
  (Oracle Database Free 23c, PDB `FREEPDB1`). The two images are **not**
  volume-compatible — use a fresh volume or an external Oracle when switching.
- First start pulls ~2 GB and initializes the DB (several minutes); give the
  Docker VM ≥ 4 GB RAM. Ports: `4200`, `8080`, `1521`.

### 15.3 Quick start

```bash
cp .env.example .env               # set DB_PASSWORD + JWT_SECRET (both required:)
docker compose up -d --build       # oracle → healthy → backend → frontend
docker compose ps                  # wait until services report healthy
```

- App: http://localhost:4200 · API: http://localhost:8080 ·
  Swagger: http://localhost:8080/swagger-ui.html
- First login: `admin / Admin@123` (seed users; set `APP_SEED_ENABLED=false`
  and change the password for anything non-demo — §14)
- Logs: `docker compose logs -f backend` (also `oracle`, `frontend`)
- Stop: `docker compose stop` · Remove containers, keep data: `docker compose down`
- **Reset DB (destructive):** `docker compose down -v` deletes `oracle-data`

Startup ordering is health-based, never a fixed sleep: the backend waits for the
Oracle PDB to be OPEN READ WRITE (`healthcheck.sh`), the frontend waits for
`/api/v1/health`. Compose fails fast with actionable errors when required
variables are missing (there are no weak defaults in the compose file).

### 15.4 Persistence — fresh setup vs existing data

- **Fresh setup:** first `up` initializes the DB in the `oracle-data` volume;
  Hibernate (`ddl-auto: update`) creates/updates the schema on backend start.
- **Existing database:** keep the volume (never `down -v`) or point
  `DB_HOST/DB_PORT/DB_SERVICE/DB_USERNAME/DB_PASSWORD` at an external Oracle —
  `ddl-auto: update` is additive, it does not drop data.

### 15.5 Configuration

Required: `DB_PASSWORD`, `JWT_SECRET` (`openssl rand -base64 48`).
Optional: `DB_USERNAME` (stocksmart), `DB_SERVICE` (XEPDB1), `DB_PORT` (1521),
`FRONTEND_PORT` (4200), `IMAGE_TAG` (dev), `BACKEND_URL` (backend:8080),
`APP_SEED_ENABLED`/`APP_SEED_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `FRONTEND_URL`.
`.env` is git-ignored; `.env.example` contains placeholders only. Full table §4.

### 15.6 Smoke tests

```bash
scripts/smoke-test.sh http://localhost:4200   # containerized stack (via nginx)
scripts/smoke-test.sh http://localhost:8080   # native backend (H2 or Oracle)
```

Covers: health, login/JWT, RBAC (401/403), master data, receipt/transfer/
adjustment with ledger assertions, barcode lookup, RFID resolve + duplicate
rejection + **no stock change**, PO receipt, SO fulfillment + insufficient-stock
rejection, dashboard-vs-ledger agreement, and SPA deep-link fallback when the
target serves HTML. Prints `N passed, M failed`; exits non-zero on any failure.

### 15.7 Jenkins CI/CD

Pipeline lives in the root `Jenkinsfile`. Agent prerequisites: tools named
`jdk-17` and `node-20` (matching the `tools` block), Maven, Docker Engine +
Compose v2 for the image stages, and the repo checkout. No registry credentials
are needed — images are built locally; publishing/deploy targets are
intentionally not configured.

Stages: Checkout → Backend `mvn -B -ntp clean verify` (H2; JUnit published) →
Frontend `npm ci` + production build (`ng test` only when Chrome exists on the
agent, otherwise an explicit skip is logged) → Build Docker images
(`:${BUILD_NUMBER}`, `:latest`) → Validate Compose config (placeholder env, no
secrets) → opt-in `DEPLOY_LOCAL` stage (`docker compose up -d --build` + smoke
test through :4200). The build fails on any failed stage.

### 15.8 Verified Day 7 results and environment limits

- `mvn -B -ntp clean package` → **67/67 tests**, jar built ✅
- `npm ci && ng build` → ✅; production bundle contains **zero** references to
  `localhost:8080` (relative `/api` + nginx proxy) ✅
- `docker compose config`: base file, ARM64 override, and fail-fast on missing
  required variables all validated with the standalone Compose v5.6.0 binary ✅
  (no daemon needed for config validation)
- Native runtime smoke: **50/50 passed** against the packaged jar on the H2
  `local` profile; `SIGTERM` triggered Spring **graceful shutdown** ✅
- **Not executable in this environment (no Docker daemon, no browser):**
  `docker build`, `docker compose up`, container startup, Oracle connectivity,
  nginx SPA routing, and Jenkins pipeline execution. Unverified here by
  design — tracked in `agent.md` §6g rather than claimed.

### 15.9 Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `required variable DB_PASSWORD is missing` | `.env` missing/incomplete — `cp .env.example .env`, set `DB_PASSWORD`+`JWT_SECRET` |
| Backend up but DB errors | Oracle still initializing: `docker compose ps`, `logs oracle`. Compose already waits for `healthcheck.sh`; on slow machines raise the Oracle `healthcheck.retries` |
| `ORA-12514` / `ORA-01017` from backend | Wrong `DB_SERVICE` (arm64 override → `FREEPDB1`), credentials, or uninitialized volume — `docker compose logs oracle` |
| Port 4200/8080/1521 in use | Change `FRONTEND_PORT` / free the port; the backend listens on 8080 inside the network and on the host |
| Angular loads, API calls fail | You served `dist/` without the nginx proxy, or `BACKEND_URL` is unreachable. In compose it must be container-to-container (`backend:8080`) |
| Browser still calls `localhost:8080` | Stale bundle — rebuild (`ng build` now applies `environment.prod.ts`); hard-refresh |
| `exec format error` on Apple Silicon | Oracle XE is amd64-only → add the ARM64 override file (§15.2) |
| Jenkins: `docker: command not found` / permission denied | Install Docker on the agent and add its user to the `docker` group; do not mount the socket without a documented reason |
| Tests pass locally, fail in CI | Verify the `jdk-17`/`node-20` tool names and that `npm ci` uses the committed lockfile |
| Data "disappeared" after a restart | You ran `docker compose down -v` — that deletes the `oracle-data` volume |

## 16. Scope checklist

### Day 1 (done)

- [x] Backend + Angular project structure
- [x] Oracle connectivity (env-driven) + H2 `local` fallback profile
- [x] `ROLES` / `USERS` model (FK, uniques, audit timestamps)
- [x] JWT login, filter, 401/403 JSON handlers, BCrypt
- [x] RBAC foundation (ADMIN, INVENTORY_MANAGER, WAREHOUSE_STAFF, PROCUREMENT_USER, SALES_USER, MANAGEMENT)
- [x] Protected verification endpoints + global exception handler + CORS + OpenAPI
- [x] Angular login / interceptor / guard / landing page
- [x] README, `.env.example`, `.gitignore`, Docker-ready

### Day 2 (done)

- [x] `categories` / `products` (FK) / `warehouses` / `suppliers` tables + constraints
- [x] CRUD REST APIs with pagination, search/filter, DTOs, 400/404/409 handling
- [x] RBAC matrix (filter-level + `@PreAuthorize`; `AccessDeniedException` → 403 safety net)
- [x] Angular list + create/edit screens for all four modules
- [x] `MasterDataIntegrationTest` (8 tests) + live-curl verification
- [x] `design.md` data model, README Day 2 docs

### Day 3 (done)

- [x] `inventory` (unique product-warehouse) + append-only `stock_transactions` tables
- [x] Receipt / adjustment / transfer services (transactional, locked, audited)
- [x] Inventory + history REST APIs with filters, pagination, DTOs
- [x] RBAC (reads for all roles; writes for ADMIN/INVENTORY_MANAGER/WAREHOUSE_STAFF)
- [x] Angular overview, receipt/adjust, transfer, history screens
- [x] `InventoryIntegrationTest` (12 tests incl. concurrency) + live-curl verification
- [x] `design.md` + README Day 3 docs

### Day 4 (done)

- [x] `purchase_orders`/`po_items`/`purchase_receipts` + `sales_orders`/`so_items`/`sales_fulfillments` tables
- [x] Draft → submit/confirm → partial → complete lifecycles with cancel rules
- [x] Atomic receipts/fulfillments via Day 3 service hooks (`RECEIPT`/`ISSUE` audit)
- [x] Unique order + operation references (duplicate numbers/replays rejected)
- [x] RBAC per order side (no sales↔purchase cross-access)
- [x] Angular order list + detail screens with receipt/fulfillment workflows
- [x] `OrderIntegrationTest` (8 tests incl. concurrency) + live-curl verification
- [x] `design.md` + README Day 4 docs

### Day 5 (done)

- [x] `barcode` + `minimum_stock_level` on products (unique when set, validated, migration-safe column)
- [x] `GET /products/barcode/{barcode}` + `GET /products/sku/{sku}` lookups — read-only, never change stock
- [x] `rfid_tags` + `rfid_read_events` tables; `RfidReader` abstraction + `SimulatedRfidReader`
- [x] RFID tag register/remap/deactivate + read processing (idempotent, RBAC-enforced)
- [x] Dashboard KPIs (summary, low stock, stockouts, PO/SO summaries, recent movements) with warehouse filter
- [x] Angular `/scan`, `/rfid`, `/dashboard` screens + product form fields + Home nav
- [x] `BarcodeRfidIntegrationTest` (15) + `DashboardIntegrationTest` (12); Day 2–4 test set-ups made RFID-FK-safe
- [x] `design.md` + README Day 5 docs

### Day 6 (done — hardening & release verification, see §14)

- [x] Duplicate receipt/fulfillment line ids rejected before any stock write
- [x] Supplier/warehouse/product delete guards extended to order lines and stock history
- [x] Client-error status codes (400/404/405/415) instead of 500
- [x] Auth interceptor session-expiry handling (401 → login)
- [x] JWT dev-secret warning; JWT signature/claim-tampering regression tests
- [x] `HardeningIntegrationTest` (5) + full suite 67/67 on H2; `ng build` clean

### Day 7 (done — deployment & CI/CD, see §15)

- [x] Backend + frontend Dockerfiles (multi-stage, non-root, healthchecks, graceful shutdown)
- [x] Nginx-served Angular production bundle with same-origin `/api` proxy (`environment.prod.ts` + `fileReplacements`)
- [x] `docker-compose.yml` (oracle → backend → frontend, health-based ordering, named volume, fail-fast secrets) + ARM64 override
- [x] Externalized configuration (`.env.example`; required `DB_PASSWORD`/`JWT_SECRET`)
- [x] `scripts/smoke-test.sh` — 50 checks incl. ledger and RFID no-stock-change
- [x] Expanded `Jenkinsfile` (verify, build, compose config, opt-in deploy + smoke, JUnit)
- [x] Verified: 67/67 tests, clean `ng build` with relative API URL, compose config valid, 50/50 native smoke, graceful SIGTERM shutdown
- [ ] Container/Oracle/Jenkins execution — blocked in this environment (no Docker/browser); documented as unverified
