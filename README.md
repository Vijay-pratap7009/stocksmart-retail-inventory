# StockSmart — Retail Inventory Optimization & Supply Chain Management

Day 2 master data: **Category, Product, Warehouse, Supplier CRUD** with
search/filter/pagination, RBAC, and Angular screens — on top of the Day 1
**Oracle connectivity, JWT authentication, RBAC, and Angular login** foundation.

> Scope guard: Day 2 contains **no** inventory quantities / adjustments /
> transfers, purchase/sales orders, barcode / RFID, KPI / reporting /
> forecasting logic. Those belong to Day 3+.

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
later without redesign. Docker is not required to run Day 2.

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

## 11. Scope checklist

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
- [ ] Day 3+ (inventory, orders, barcode/RFID, KPIs, forecasting) — **not started by design**
