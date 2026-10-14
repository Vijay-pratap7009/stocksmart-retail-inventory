# DESIGN.md — StockSmart Data Model & API Design (Day 4 actual)

> Living design record. Day 1 = auth, Day 2 = master data, Day 3 = inventory,
> Day 4 = purchase/sales orders with inventory integration.
> Barcode / RFID / KPIs are explicitly out of scope — see §6.

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
| `product_name` | VARCHAR2(150) | NOT NULL |
| `description` | VARCHAR2(1000) | nullable |
| `category_id` | NUMBER(19) | NOT NULL, FK `fk_products_category` → `categories` |
| `brand` | VARCHAR2(100) | nullable |
| `unit` | VARCHAR2(20) | NOT NULL (e.g. PCS, KG, LTR, BOX) |
| `cost_price` / `selling_price` | NUMBER(19,2) | NOT NULL, ≥ 0 |
| `active` | NUMBER(1) | NOT NULL, default true |
| `created_at` / `updated_at` | TIMESTAMP | set by entity |

No stock-quantity columns (Day 3).

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

## 4. REST API (`/api/v1/...`, paged `Page<DTO>`, `size=20` default)

| Group | Operations | Query params |
|---|---|---|
| `/categories` | GET (page), GET /{id}, POST 201, PUT /{id}, DELETE 204 | `search` (name/code), `active`, `page/size/sort` |
| `/products` | GET (page), GET /{id}, POST 201, PUT /{id}, DELETE 204 | `search` (sku/name), `active`, `categoryId`, `page/size/sort` |
| `/warehouses` | GET (page), GET /{id}, POST 201, PUT /{id}, DELETE 204 | `search` (code/name/city), `active`, `page/size/sort` |
| `/suppliers` | GET (page), GET /{id}, POST 201, PUT /{id}, DELETE 204 | `search` (code/name/contact/email/phone), `active`, `page/size/sort` |
| `/inventory` | GET (page), GET /{id}, GET /product/{id}, GET /warehouse/{id}, POST /receipts 201, POST /adjustments 201, POST /transfers 201 | `search` (sku/name), `productId`, `warehouseId`, `page/size/sort` |
| `/stock-transactions` | GET (page, newest first), GET /{id} | `productId`, `warehouseId`, `type`, `reference`, `from`, `to` (ISO-8601), `page/size/sort` |
| `/purchase-orders` | GET (page), GET /{id}, GET /{id}/receipts, POST 201, PUT /{id} (draft), POST /{id}/submit, POST /{id}/receipts 201, POST /{id}/cancel | `search` (poNumber), `status`, `supplierId`, `page/size/sort` |
| `/sales-orders` | GET (page), GET /{id}, GET /{id}/fulfillments, POST 201, PUT /{id} (draft), POST /{id}/confirm, POST /{id}/fulfillments 201, POST /{id}/cancel | `search` (soNumber/customer), `status`, `warehouseId`, `page/size/sort` |

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

## 6. Deferred (do not build until its phase)

- Day 5: barcode/RFID/KPI/reporting. Day 6: hardening. Day 7: Docker/Jenkins runs.
- Supplier delete guard still open (purchase orders reference suppliers —
  revisit when deletion of suppliers with orders must be blocked; currently the
  FK constraint is the backstop).
