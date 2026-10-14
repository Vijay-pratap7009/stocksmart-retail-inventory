# DESIGN.md — StockSmart Data Model & API Design (Day 2 actual)

> Living design record. Day 1 = auth foundation (`users`, `roles`).
> Day 2 adds four master-data tables. Inventory / orders / barcode / RFID /
> KPIs are explicitly out of scope — see §6.

## 1. Entity-relationship overview

```
roles 1 — N users            (Day 1, unchanged)

categories 1 — N products    (Day 2: FK products.category_id → categories.category_id)

warehouses                   (Day 2: standalone master table, no dependents yet)
suppliers                    (Day 2: standalone master table, no dependents yet)
```

Future (not implemented): Day 3 inventory references `products` + `warehouses`;
Day 4 purchase orders reference `suppliers` (+ `products`/`warehouses`).

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

## 3. Conventions

- PKs: `GenerationType.SEQUENCE` (`allocationSize = 1`) per table — Oracle-friendly.
- Schema: `ddl-auto: update` (Oracle) / `create-drop` (H2 local/test). No Flyway/Liquibase yet.
- Codes/SKUs normalized to UPPERCASE + trimmed on write; uniqueness checked
  case-insensitively in the service layer AND by DB unique constraints.
- Money: `BigDecimal`, `NUMBER(19,2)`, `@Digits(integer=15, fraction=2)`, `@DecimalMin("0.00")`.
- Blank optional strings stored as NULL.
- Layering: Controller → Service → Repository → Oracle. JPA entities never
  leave the service layer (mapped to `*Response` DTOs via `*Mapper` components).
- Errors: 400 validation, 404 `ResourceNotFoundException`, 409 `ConflictException`
  (duplicates, referenced-category delete) + `DataIntegrityViolationException` safety net,
  401/403 via Day 1 handlers (+ `AccessDeniedException` → 403 safety net for
  method-level denials past the filter chain).

## 4. REST API (`/api/v1/...`, paged `Page<DTO>`, `size=20` default)

| Group | Operations | Query params |
|---|---|---|
| `/categories` | GET (page), GET /{id}, POST 201, PUT /{id}, DELETE 204 | `search` (name/code), `active`, `page/size/sort` |
| `/products` | GET (page), GET /{id}, POST 201, PUT /{id}, DELETE 204 | `search` (sku/name), `active`, `categoryId`, `page/size/sort` |
| `/warehouses` | GET (page), GET /{id}, POST 201, PUT /{id}, DELETE 204 | `search` (code/name/city), `active`, `page/size/sort` |
| `/suppliers` | GET (page), GET /{id}, POST 201, PUT /{id}, DELETE 204 | `search` (code/name/contact/email/phone), `active`, `page/size/sort` |

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

## 6. Deferred (do not build until its phase)

- Day 3: inventory balances/adjustments/transfers referencing products + warehouses.
- Day 4: purchase/sales orders referencing suppliers (+ products/warehouses).
- Day 5: barcode/RFID/KPI/reporting. Day 6: hardening. Day 7: Docker/Jenkins runs.
- Warehouse/supplier delete guards must be revisited when Day 3/4 FKs land
  (currently direct delete — no dependents exist yet).
