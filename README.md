# StockSmart — Retail Inventory Optimization & Supply Chain Management

Day 5 adds **barcode operations, RFID tag mapping + reader simulation, and an
inventory KPI dashboard** on top of Day 4 purchase/sales orders, Day 3
inventory balances, Day 2 master data, and the Day 1 auth foundation.

> Scope guard: Days 1–5 are complete. Day 6 = hardening, Day 7 = Docker/Jenkins.
> There is no forecasting/ML; RFID is simulator-backed (no physical hardware is
> integrated), and reads never change stock.

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

- Java 17+, Maven 3.9+
- Node.js 20+, npm 10+
- Docker + Docker Compose (for Oracle XE), **or** use the H2 `local` profile
- Ports free: `8080` (API), `4200` (Angular), `1521` (Oracle)

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
npm start                 # ng serve → http://localhost:4200 (proxy.conf.json forwards /api)
```

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

Automated matrix: `cd backend && mvn test` (`SecurityVerificationTest`: 6 tests —
health, login 200/401, protected 200/401, expired JWT 401, invalid JWT 401,
admin/management RBAC 403/200, BCrypt storage).

## 8. API documentation

Swagger UI (`/swagger-ui.html`) documents health, login, and the three test
endpoints with a **Bearer/JWT** security scheme — paste the login JWT via
“Authorize” to call protected endpoints.

## 9. CI readiness

A minimal `Jenkinsfile` (backend `mvn test` + frontend `ng build`) and
`.dockerignore` files are present so CI/containerization can be introduced
later without redesign. Docker is not required to run Days 1–5.

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

`cd backend && mvn test` — now 61 tests: Day 1 (6) + Day 2 (8) + Day 3 (12) +
Day 4 (8) + `BarcodeRfidIntegrationTest` (15: barcode/SKU lookups + stock
unchanged proof, duplicate/invalid barcode 409/400, tag lifecycle + duplicate
EPC, simulated/external reads, unknown/inactive statuses, event-reference
dedup, RFID RBAC) + `DashboardIntegrationTest` (12: exact KPI values, low-stock
boundaries, warehouse filter/paging, cost-basis rounding, empty dataset,
pending counts, no-double-count, recent movements, auth matrix).

## 14. Scope checklist

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
- [ ] Day 6+ (hardening, Docker/Jenkins, forecasting) — **not started by design**
