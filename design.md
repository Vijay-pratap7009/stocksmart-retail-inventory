# DESIGN.md — StockSmart Data Model & API Design (Day 6 actual)

> Living design record. Day 1 = auth, Day 2 = master data, Day 3 = inventory,
> Day 4 = purchase/sales orders with inventory integration,
> Day 5 = barcode operations, RFID tag mapping + reader simulation, inventory KPI dashboard,
> Day 6 = hardening & release verification (see §6 for limits that remain).
> Day 7 (Docker/Jenkins) remains out of scope.

## 1. Entity-relationship overview

```
roles 1 — N users            (Day 1, unchanged)

categories 1 — N products    (Day 2: FK products.category_id → categories.category_id)

products 1 — N inventory N — 1 warehouses   (Day 3: unique (product_id, warehouse_id))

products 1 — N stock_transactions           (Day 3: FK stock_transactions.product_id)
warehouses 1 — N stock_transactions         (Day 3: FK stock_transactions.warehouse_id)
users 1 — N stock_transactions              (Day 3: FK stock_transactions.performed_by_user_id)

suppliers                    (Day 2 table; Day 4 purchase orders reference it)

Day 4 order graphs (see §2.7–2.8):

```
suppliers 1 — N purchase_orders 1 — N po_items N — 1 products
warehouses 1 — N purchase_orders (destination)

purchase_orders 1 — N purchase_receipts 1 — N purchase_receipt_items
po_items 1 — N purchase_receipt_items (which line was received)

warehouses 1 — N sales_orders 1 — N so_items N — 1 products
users 1 — N sales_orders (created_by)

sales_orders 1 — N sales_fulfillments 1 — N sales_fulfillment_items
so_items 1 — N sales_fulfillment_items (which line was issued)
```

Receipts write `RECEIPT` and fulfillments write `ISSUE` rows in
`stock_transactions` sharing the receipt/fulfillment reference.

Day 5 additions (see §2.9–2.10):

```
products 1 — N rfid_tags                    (EPC → product mapping; uk epc_code)
users    1 — N rfid_tags                    (registered_by)
rfid_tags 1 — N rfid_read_events            (nullable link: unknown EPCs log without a tag)
products 1 — N rfid_read_events             (resolved product, nullable)
users    1 — N rfid_read_events             (performed_by)
```

`products` also gain `barcode` (unique when present) and `minimum_stock_level`
(§2.2). Barcode lookups and RFID reads are informational — they NEVER change
stock; only `InventoryService`/order workflows move quantities.

## 2. Tables (Oracle system of record; H2 `local` profile mirrors via JPA)

### 2.1 `categories`

| Column | Type (Oracle) | Constraints |
|---|---|---|
| `category_id` | NUMBER(19), `categories_seq` | PK |
| `category_code` | VARCHAR2(30) | NOT NULL, UNIQUE (`uk_categories_code`); stored UPPERCASE |
| `category_name` | VARCHAR2(100) | NOT NULL |
| `description` | VARCHAR2(500) | nullable |
| `active` | NUMBER(1) | NOT NULL, default true |
| `created_at` / `updated_at` | TIMESTAMP | set by entity |

Delete rule: blocked with 409 when `products.category_id` references the row.

### 2.2 `products`

| Column | Type (Oracle) | Constraints |
|---|---|---|
| `product_id` | NUMBER(19), `products_seq` | PK |
| `sku` | VARCHAR2(50) | NOT NULL, UNIQUE (`uk_products_sku`); stored UPPERCASE |
| `barcode` | VARCHAR2(60) | nullable, UNIQUE when set (`uk_products_barcode`); stored UPPERCASE; pattern `^[A-Za-z0-9.\-+/]{4,60}$` (Day 5) |
| `product_name` | VARCHAR2(150) | NOT NULL |
| `description` | VARCHAR2(1000) | nullable |
| `category_id` | NUMBER(19) | NOT NULL, FK `fk_products_category` → `categories` |
| `brand` | VARCHAR2(100) | nullable |
| `unit` | VARCHAR2(20) | NOT NULL (e.g. PCS, KG, LTR, BOX) |
| `cost_price` / `selling_price` | NUMBER(19,2) | NOT NULL, ≥ 0 |
| `minimum_stock_level` | NUMBER(19,4) | nullable column (migration-safe on existing rows); app treats null as 0, never negative; drives low-stock KPI (Day 5) |
| `active` | NUMBER(1) | NOT NULL, default true |
| `created_at` / `updated_at` | TIMESTAMP | set by entity |

No stock-quantity columns (Day 3). Barcode duplicates → 409; blank barcode is
stored as NULL (many products may be unscannable). Product deletes are blocked
(409) while `inventory`, `stock_transactions`, order lines, or `rfid_tags`
reference the row.

### 2.3 `warehouses`

| Column | Type (Oracle) | Constraints |
|---|---|---|
| `warehouse_id` | NUMBER(19), `warehouses_seq` | PK |
| `warehouse_code` | VARCHAR2(30) | NOT NULL, UNIQUE (`uk_warehouses_code`); UPPERCASE |
| `warehouse_name` | VARCHAR2(150) | NOT NULL |
| `address` | VARCHAR2(255) | nullable |
| `city` / `state` | VARCHAR2(100) | nullable |
| `postal_code` | VARCHAR2(20) | nullable |
| `contact_person` | VARCHAR2(120) | nullable |
| `contact_email` | VARCHAR2(120) | nullable, email format |
| `contact_phone` | VARCHAR2(30) | nullable, loose intl pattern |
| `active` | NUMBER(1) | NOT NULL, default true |
| `created_at` / `updated_at` | TIMESTAMP | set by entity |

### 2.4 `suppliers`

Same shape as warehouses with `supplier_id` / `suppliers_seq`,
`supplier_code` UNIQUE (`uk_suppliers_code`), `supplier_name`,
`contact_person`, `email`, `phone`, address fields, `active`, timestamps.

### 2.5 `inventory` (Day 3)

| Column | Type (Oracle) | Constraints |
|---|---|---|
| `inventory_id` | NUMBER(19), `inventory_seq` | PK |
| `product_id` | NUMBER(19) | NOT NULL, FK `fk_inventory_product` → `products` |
| `warehouse_id` | NUMBER(19) | NOT NULL, FK `fk_inventory_warehouse` → `warehouses` |
| `quantity_on_hand` | NUMBER(19,4) | NOT NULL, ≥ 0 (fractional units allowed) |
| `created_at` / `updated_at` | TIMESTAMP | set by entity |

Unique `(product_id, warehouse_id)` (`uk_inventory_product_warehouse`) —
one balance row per product-warehouse pair. Quantities change only via
`InventoryService` (receipt/adjust/transfer), always with an audit row.

Delete rules (Day 3, new): product/warehouse deletes are blocked with 409
while `inventory` rows reference them.

### 2.6 `stock_transactions` (Day 3, append-only)

| Column | Type (Oracle) | Constraints |
|---|---|---|
| `transaction_id` | NUMBER(19), `stock_transactions_seq` | PK |
| `product_id` / `warehouse_id` | NUMBER(19) | NOT NULL, FKs to `products` / `warehouses` |
| `transaction_type` | VARCHAR2(30) | NOT NULL, `TransactionType` enum |
| `quantity` | NUMBER(19,4) | NOT NULL, always positive (direction in type) |
| `quantity_before` / `quantity_after` | NUMBER(19,4) | NOT NULL, balance snapshot |
| `reference_number` | VARCHAR2(60) | NOT NULL (`RCT-`/`ADJ-`/`TRF-` prefixed; shared by transfer legs) |
| `reason` | VARCHAR2(500) | mandatory for adjustments |
| `performed_by_user_id` | NUMBER(19) | NOT NULL, FK → `users` |
| `notes` | VARCHAR2(1000) | nullable |
| `created_at` | TIMESTAMP | movement timestamp, immutable |

No update/delete API exists — corrections are new movements. First-ever
receipt for a pair is recorded as `OPENING_BALANCE`, later ones as `RECEIPT`.
`ISSUE` rows are written by Day 4 sales fulfillments (reference = fulfillment
number); purchase receipts write `RECEIPT` rows (reference = receipt number).
Transfers write paired `TRANSFER_OUT` + `TRANSFER_IN` rows sharing one reference.

### 2.7 Purchase orders (Day 4)

`purchase_orders`: `po_id` (`purchase_orders_seq` PK), `po_number` UNIQUE
(`uk_po_number`, uppercased, generated `PO-xxxxxxxx` when absent),
`supplier_id` FK (`fk_po_supplier`), `warehouse_id` FK (`fk_po_warehouse`,
destination), `order_date` DATE, `expected_delivery_date` DATE nullable,
`status` (`DRAFT/SUBMITTED/PARTIALLY_RECEIVED/RECEIVED/CANCELLED`),
`notes`, `created_by_user_id` FK, audit timestamps. Lines editable while DRAFT
(replaced wholesale via PUT, orphanRemoval).

`po_items`: `po_item_id` PK, `po_id` FK, `product_id` FK,
`ordered_quantity` NUMBER(19,4) > 0, `received_quantity` NUMBER(19,4) ≥ 0 and
≤ ordered (app-enforced), `unit_cost` NUMBER(19,2) ≥ 0 captured at order time.

`purchase_receipts`: `receipt_id` PK, `receipt_number` UNIQUE
(`uk_por_number`; client idempotency key or generated `POR-xxxxxxxx`),
`po_id` FK, `received_by_user_id` FK, `received_at`, `notes`.
`purchase_receipt_items`: receipt FK, `po_item_id` FK (which line),
`product_id` FK, `quantity` > 0.

Lifecycle: DRAFT → SUBMITTED (needs ≥1 line) → PARTIALLY_RECEIVED → RECEIVED;
CANCELLED from any open state (received stock is kept, never reversed).
Receipts only on SUBMITTED/PARTIALLY_RECEIVED; never beyond remaining quantity.

### 2.8 Sales orders (Day 4)

`sales_orders`: `so_id` (`sales_orders_seq` PK), `so_number` UNIQUE
(`uk_so_number`, generated `SO-xxxxxxxx` when absent), `warehouse_id` FK,
`customer_name` (minimal, no CRM), `customer_contact`, `order_date` DATE,
`status` (`DRAFT/CONFIRMED/PARTIALLY_FULFILLED/FULFILLED/CANCELLED`), `notes`,
`created_by_user_id` FK, timestamps.

`so_items`: like PO lines with `fulfilled_quantity` and `unit_price`
NUMBER(19,2) captured at order time.

`sales_fulfillments` / `sales_fulfillment_items`: mirror the receipt
structures (`uk_sof_number`, generated `SOF-xxxxxxxx`, `so_item_id` FK).

Lifecycle: DRAFT → CONFIRMED (needs ≥1 line; does NOT reserve stock) →
PARTIALLY_FULFILLED → FULFILLED; CANCELLED from any open state.
Fulfillment checks remaining quantity AND live stock, issues via the Day 3
service (`ISSUE` type), and updates fulfilled quantities atomically.

### 2.9 `rfid_tags` (Day 5)

| Column | Type (Oracle) | Constraints |
|---|---|---|
| `tag_id` | NUMBER(19), `rfid_tags_seq` | PK |
| `epc_code` | VARCHAR2(96) | NOT NULL, UNIQUE (`uk_rfid_tags_epc`); stored UPPERCASE; pattern `^[A-Za-z0-9.\-:]{4,96}$` |
| `product_id` | NUMBER(19) | NOT NULL, FK `fk_rfid_tag_product` → `products` |
| `active` | NUMBER(1) | NOT NULL, default true |
| `registered_by_user_id` | NUMBER(19) | NOT NULL, FK `fk_rfid_tag_user` → `users` |
| `created_at` / `updated_at` | TIMESTAMP | set by entity |

One row per physical tag; a tag maps to exactly one product. Stores NO
quantities. Deactivation (`active=false`) is preferred over deletion; product
deletes are blocked (409) while tags reference them. Registration/remap/
(de)activation are ADMIN-only.

### 2.10 `rfid_read_events` (Day 5, append-only)

| Column | Type (Oracle) | Constraints |
|---|---|---|
| `read_event_id` | NUMBER(19), `rfid_read_events_seq` | PK |
| `event_reference` | VARCHAR2(60) | NOT NULL, UNIQUE (`uk_rfid_read_reference`); client idempotency key or generated `RFD-xxxxxxxx` — replay → 409 |
| `epc_code` | VARCHAR2(96) | NOT NULL; raw scanned EPC, kept even when unknown |
| `tag_id` | NUMBER(19) | nullable, FK `fk_rfid_read_tag` (null = unknown EPC) |
| `product_id` | NUMBER(19) | nullable, FK `fk_rfid_read_product` (resolved via tag) |
| `status` | VARCHAR2(30) | NOT NULL, `RESOLVED` / `INACTIVE_TAG` / `UNKNOWN_TAG` |
| `reader_name` | VARCHAR2(60) | nullable (simulator or hardware bridge name) |
| `simulated` | NUMBER(1) | NOT NULL; server-controlled — a simulated read can never be labeled hardware |
| `event_at` | TIMESTAMP | when the read happened (client-supplied or now) |
| `received_at` | TIMESTAMP | server receipt time, immutable |
| `performed_by_user_id` | NUMBER(19) | NOT NULL, FK → `users` |

No update/delete API. Processing a read NEVER changes stock — stock moves only
through `InventoryService` and the order workflows.

## 3. Conventions

- PKs: `GenerationType.SEQUENCE` (`allocationSize = 1`) per table — Oracle-friendly.
- Schema: `ddl-auto: update` (Oracle) / `create-drop` (H2 local/test). No Flyway/Liquibase yet.
- Codes/SKUs normalized to UPPERCASE + trimmed on write; uniqueness checked
  case-insensitively in the service layer AND by DB unique constraints.
- Money: `BigDecimal`, `NUMBER(19,2)`, `@Digits(integer=15, fraction=2)`, `@DecimalMin("0.00")`.
- Blank optional strings stored as NULL.
- Layering: Controller → Service → Repository → Oracle. JPA entities never
  leave the service layer (mapped to `*Response` DTOs via `*Mapper` components).
- Errors: 400 validation (incl. malformed JSON, same-warehouse transfers),
  404 `ResourceNotFoundException`, 409 `ConflictException`
  (duplicates, referenced deletes) + `InsufficientStockException` (over-draws,
  failed transfers leave zero state change) + `DataIntegrityViolationException`
  safety net, 401/403 via Day 1 handlers (+ `AccessDeniedException` → 403
  safety net for method-level denials past the filter chain).
- Concurrency: `PESSIMISTIC_WRITE` row locks on stock changes, deterministic
  lock order (warehouse id ascending) for transfers, single-`@Transactional`
  movement + audit writes, retry-once in a fresh transaction on first-row
  creation races.
- Orders: receipt/fulfillment run in ONE transaction joining the Day 3
  inventory hooks (`receiveForOrder`/`issueForOrder`, no separate commit);
  order rows are locked (`lockById`) so concurrent receipts/fulfillments on
  one order serialize. Receipt/fulfillment references are unique idempotency
  keys — replays are rejected, never double-applied.
- Identification (Day 5): barcodes and EPCs normalized to UPPERCASE + trimmed;
  blank barcode → NULL. Reads are append-only and idempotent via unique
  `event_reference` (replay → 409). `simulated` is server-controlled. Neither
  barcode nor RFID paths touch inventory — they only resolve products/log events.

## 4. REST API (`/api/v1/...`, paged `Page<DTO>`, `size=20` default)

| Group | Operations | Query params |
|---|---|---|
| `/categories` | GET (page), GET /{id}, POST 201, PUT /{id}, DELETE 204 | `search` (name/code), `active`, `page/size/sort` |
| `/products` | GET (page), GET /{id}, GET /barcode/{barcode}, GET /sku/{sku}, POST 201, PUT /{id}, DELETE 204 | `search` (sku/name), `active`, `categoryId`, `page/size/sort` |
| `/warehouses` | GET (page), GET /{id}, POST 201, PUT /{id}, DELETE 204 | `search` (code/name/city), `active`, `page/size/sort` |
| `/suppliers` | GET (page), GET /{id}, POST 201, PUT /{id}, DELETE 204 | `search` (code/name/contact/email/phone), `active`, `page/size/sort` |
| `/inventory` | GET (page), GET /{id}, GET /product/{id}, GET /warehouse/{id}, POST /receipts 201, POST /adjustments 201, POST /transfers 201 | `search` (sku/name), `productId`, `warehouseId`, `page/size/sort` |
| `/stock-transactions` | GET (page, newest first), GET /{id} | `productId`, `warehouseId`, `type`, `reference`, `from`, `to` (ISO-8601), `page/size/sort` |
| `/purchase-orders` | GET (page), GET /{id}, GET /{id}/receipts, POST 201, PUT /{id} (draft), POST /{id}/submit, POST /{id}/receipts 201, POST /{id}/cancel | `search` (poNumber), `status`, `supplierId`, `page/size/sort` |
| `/sales-orders` | GET (page), GET /{id}, GET /{id}/fulfillments, POST 201, PUT /{id} (draft), POST /{id}/confirm, POST /{id}/fulfillments 201, POST /{id}/cancel | `search` (soNumber/customer), `status`, `warehouseId`, `page/size/sort` |
| `/rfid` (Day 5) | GET /tags (page), GET /tags/{epcCode}, POST /tags 201 (ADMIN), PUT /tags/{epcCode} (ADMIN), GET /reader, POST /reads 201, GET /reads (page) | `/tags`: `search` (epc/sku/name), `active`, `productId`, `page/size/sort`; `/reads`: `epcCode`, `status`, `simulated`, `from`, `to`, `page/size/sort` (default `receivedAt,desc`) |
| `/dashboard` (Day 5) | GET /summary, GET /low-stock (page), GET /stockouts (page), GET /purchase-orders, GET /sales-fulfillment, GET /recent-transactions | `warehouseId` (validated; unknown → 404) on all but recent; `/recent-transactions`: `productId`, `warehouseId`, `limit` (1–50, default 10); lists: `page/size/sort` |

## 5. RBAC baseline (filter-level matchers + `@PreAuthorize`, backend is the boundary)

| Role | Categories | Products | Warehouses | Suppliers |
|---|---|---|---|---|
| ADMIN | RW | RW | RW | RW |
| INVENTORY_MANAGER | RW | RW | RW | R |
| PROCUREMENT_USER | R | R | R | RW |
| WAREHOUSE_STAFF | R | R | R | — |
| SALES_USER | R | R | — | — |
| MANAGEMENT | R | R | R | R |

R = GET, W = POST/PUT/DELETE, — = no access (403). No token → 401.

Day 3 inventory baseline (filter-level matchers + `@PreAuthorize`):

| Role | Inventory reads + history | Receipts / adjustments / transfers |
|---|---|---|
| ADMIN | R | W |
| INVENTORY_MANAGER | R | W |
| WAREHOUSE_STAFF | R | W |
| PROCUREMENT_USER | R | — |
| SALES_USER | R | — |
| MANAGEMENT | R | — |

Day 4 order baseline (filter-level matchers + `@PreAuthorize`):

| Role | PO reads | PO writes | PO receipts | SO reads | SO writes | SO fulfillments |
|---|---|---|---|---|---|---|
| ADMIN | R | W | W | R | W | W |
| PROCUREMENT_USER | R | W | W | — | — | — |
| SALES_USER | — | — | — | R | W | W |
| INVENTORY_MANAGER | R | — | W | R | — | W |
| WAREHOUSE_STAFF | R | — | W | R | — | W |
| MANAGEMENT | R | — | — | R | — | — |

Day 5 barcode/RFID baseline (filter-level matchers + `@PreAuthorize`):

| Role | Barcode/SKU lookup | RFID reads (tags/reader/events) | RFID tag writes | RFID read processing |
|---|---|---|---|---|
| ADMIN | R | R | W | W |
| INVENTORY_MANAGER | R | R | — | W |
| WAREHOUSE_STAFF | R | R | — | W |
| PROCUREMENT_USER | R | R | — | W |
| SALES_USER | R | R | — | W |
| MANAGEMENT | R | R | — | — |

Day 5 dashboard baseline: `/summary`, `/low-stock`, `/stockouts`,
`/recent-transactions` = any authenticated role. `/purchase-orders` excludes
SALES_USER (mirrors Day 4 PO reads); `/sales-fulfillment` excludes
PROCUREMENT_USER (mirrors Day 4 SO reads). Frontend hides a card a role cannot
read, but the backend 403 is the boundary.

KPI definitions (computed from persisted rows, no caching, no fake data):
- Low stock: `0 < quantity_on_hand < COALESCE(minimum_stock_level, 0)` per
  product-warehouse row; `deficit = minimum − on_hand`. Exactly-at-minimum is
  NOT low; zero is a stockout.
- Stockout: `quantity_on_hand = 0` (the row exists and is reported).
- `inventoryValue = SUM(quantity_on_hand × product.cost_price)`, scale 2
  HALF_UP — an operational cost-basis estimate, not an accounting valuation.
- Pending purchase orders = `SUBMITTED + PARTIALLY_RECEIVED`; pending sales
  orders = `CONFIRMED + PARTIALLY_FULFILLED`; remaining-to-fulfill units =
  `SUM(ordered − fulfilled)` over those orders' lines (each line once).

## 6. Deferred / known limits

- Days 1–7 are complete. Day 6 (hardening) added duplicate-line guards on
  receipts/fulfillments, order-line + stock-history-aware delete guards,
  client-error status mapping (400/404/405/415), JWT signature/claim-tampering
  regressions, the JWT dev-secret startup warning, auth-interceptor
  session-expiry handling, and `@BatchSize` N+1 mitigation. Day 7 adds the
  container deployment (§7) — no forecasting/ML, no reporting module beyond the
  Day 5 KPI dashboard.
- RFID is **tag mapping + simulator only** (`RfidReader` abstraction,
  `SimulatedRfidReader`): no physical reader hardware is integrated; the
  interface is the extension point for a real bridge (read-only integration —
  reads still never change stock).
- Dashboard is a live query view (no materialized aggregates); acceptable at
  current data volumes, revisit with snapshots if needed.
- JWT is stateless: user deactivation/deletion does not revoke already-issued
  tokens before expiry (24h default). A deny-list or shorter TTL would be the
  path if revocation becomes a requirement.
- Oracle runtime verification is blocked in the current environment (no
  Docker/Oracle): automated suites run on H2; Oracle is the primary
  config/driver/dialect and the schema is JPA-managed. The compose stack was
  validated at the configuration level only (§7.6).

## 7. Deployment architecture (Day 7)

### 7.1 Topology

```text
host :4200 ─► frontend (nginx:1.27-alpine, non-root base image)
                · serves the Angular prod bundle (dist/frontend/browser)
                · /api/* ──proxy──► http://backend:8080 (BACKEND_URL, envsubst)
                · SPA fallback: non-file routes → /index.html
host :8080 ─► backend (eclipse-temurin:17-jre, user stocksmart uid 1001)
                · Spring Boot jar; /api/v1/health is the container healthcheck
                · SIGTERM → graceful shutdown (server.shutdown=graceful, 30 s)
host :1521 ─► oracle (gvenzl/oracle-xe:21-slim-faststart; arm64: oracle-free:23)
                · healthcheck.sh <PDB> gates backend start (service_healthy)
                · data in named volume `oracle-data`
             all three on user-defined bridge network `stocksmart-net`
```

### 7.2 Frontend routing and origins

- Production Angular uses `environment.prod.ts` (`apiUrl: ''`) via the
  `fileReplacements` entry in `angular.json` — the browser only ever calls
  same-origin `/api/...`, so there is no CORS dependency and no internal
  hostname leakage into the bundle.
- nginx resolves the upstream via `${BACKEND_URL}` in
  `frontend/nginx/default.conf.template` (rendered by the image's envsubst
  entrypoint), so the API location is configurable per environment.

### 7.3 Configuration & secrets

- `docker-compose.yml` requires `DB_PASSWORD` and `JWT_SECRET` (`:?` fail-fast);
  `.env` is git-ignored, `.env.example` holds placeholders. There are no weak
  defaults in the compose file, and the backend additionally warns at startup if
  the built-in dev JWT secret is in use.
- The seed user/password remain dev conveniences (`APP_SEED_ENABLED`).

### 7.4 Startup ordering & lifecycle

- Health-based `depends_on` chain: oracle (PDB OPEN READ WRITE) → backend
  (`/api/v1/health`) → frontend. No fixed sleeps anywhere.
- Both images define `HEALTHCHECK`s; graceful shutdown is enabled so container
  stop drains in-flight requests.

### 7.5 ARM64 / persistence caveats

- Oracle XE 21c is amd64-only; `docker-compose.arm64.yml` swaps to Oracle
  Database Free 23c (`FREEPDB1`). The two images are not volume-compatible —
  use a fresh volume (destructive `down -v`) or an external Oracle.
- `ddl-auto: update` is additive for existing databases; the schema is
  JPA-managed on first start.

### 7.6 Verification status (honest split)

- Config-level (verified here): compose renders for base + arm64 and fails
  fast on missing required vars (Compose v5.6.0 `config`, no daemon);
  production bundle contains zero `localhost:8080` references; 50/50 native
  smoke checks on H2; graceful SIGTERM shutdown observed.
- Runtime container/Oracle/nginx/Jenkins execution is unverified in this
  environment (no Docker daemon, no browser) — tracked in `agent.md` §6g.
