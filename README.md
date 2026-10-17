# StockSmart — Retail Inventory Optimization & Supply Chain Management

A full-stack **inventory and supply-chain management system** for multi-warehouse
retail operations: product/warehouse master data, stock receiving, adjustments
and transfers with an auditable ledger, purchase & sales order lifecycles,
barcode scanning, RFID tag simulation, and a live KPI dashboard — secured with
JWT authentication and six-role RBAC.

Built as a production-style monorepo: **Angular 17** SPA → **Spring Boot 3.2**
REST API → **Oracle Database** (H2 fallback), fully containerized with Docker
Compose and ready for a Jenkins CI/CD pipeline.

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?logo=springboot&logoColor=white)
![Angular](https://img.shields.io/badge/Angular-17-DD0031?logo=angular&logoColor=white)
![Oracle](https://img.shields.io/badge/Oracle-Database_21c_%2F_23c-F80000?logo=oracle&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose_v2-2496ED?logo=docker&logoColor=white)
![Tests](https://img.shields.io/badge/backend_tests-67%2F67_passing-brightgreen)

---

## Table of contents

- [What is StockSmart?](#what-is-stocksmart)
- [Features](#features)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Quick start](#quick-start)
  - [Option A — Docker Compose (recommended)](#option-a--docker-compose-recommended)
  - [Option B — Run natively (no Docker required)](#option-b--run-natively-no-docker-required)
  - [First login](#first-login)
- [Configuration](#configuration)
- [API overview](#api-overview)
- [Testing](#testing)
- [CI/CD](#cicd)
- [Project structure](#project-structure)
- [Troubleshooting](#troubleshooting)
- [Security notes & known limits](#security-notes--known-limits)
- [Development history](#development-history)
- [Documentation map](#documentation-map)

---

## What is StockSmart?

Retail businesses juggle stock across warehouses, suppliers, and sales channels.
StockSmart models the whole loop in one system:

1. **Master data** — categories, products (SKU + barcode), warehouses, suppliers.
2. **Inventory operations** — receipts, adjustments and warehouse-to-warehouse
   transfers that keep one authoritative balance per product/warehouse, backed
   by an **append-only transaction ledger** (nothing changes without an audit row).
3. **Procurement & sales** — purchase orders (`DRAFT → SUBMITTED → … → RECEIVED`)
   and sales orders (`DRAFT → CONFIRMED → … → FULFILLED`) whose receipts and
   fulfillments hit inventory atomically, with idempotency references.
4. **Scanning & tracking** — barcode/SKU lookups and an RFID tag registry driven
   by a **simulated reader** (reads never change stock).
5. **Insight** — a live KPI dashboard (stock value, low stock, stockouts, pending
   orders, recent movements) computed directly from persisted rows.

Every write path is role-checked on the **backend** (the security boundary), runs
in a transaction, and is covered by integration tests on a real database schema.

## Features

- **Authentication & RBAC** — JWT login, BCrypt-hashed passwords, stateless
  filter, JSON 401/403 handling, six roles: `ADMIN`, `INVENTORY_MANAGER`,
  `WAREHOUSE_STAFF`, `PROCUREMENT_USER`, `SALES_USER`, `MANAGEMENT`.
- **Master data CRUD** — paged lists, search/filter/sort, unique business codes,
  delete guards (409) when records are referenced, validation with clear 400s.
- **Inventory ledger** — receipts, direction+reason adjustments, atomic paired
  transfers with pessimistic locking and deterministic lock order; negative
  stock and over-transfer are impossible; failures leave zero side effects.
- **Purchase orders** — draft editing, submit, partial receiving, cancel;
  receive beyond remaining quantity rejected; idempotent receipt references.
- **Sales orders** — confirm, partial fulfillment, cancel; insufficient stock
  rejected atomically; idempotent fulfillment references.
- **Barcode & RFID** — `GET /products/barcode/{code}` resolution; EPC tag
  registry (register/remap/deactivate); simulated reads with
  `RESOLVED / UNKNOWN_TAG / INACTIVE_TAG` statuses and duplicate-read 409s.
- **KPI dashboard** — summary, low stock, stockouts, pending POs/SOs, recent
  transactions; optional warehouse scoping; cost-basis inventory valuation.
- **Production readiness** — Dockerfiles (non-root, healthchecks, graceful
  shutdown), Docker Compose stack with health-based startup ordering, externalized
  config with fail-fast secrets, ARM64 override, smoke-test script, Jenkinsfile.

## Tech stack

| Layer     | Technology                                                                 |
|-----------|----------------------------------------------------------------------------|
| Backend   | Java 17, Spring Boot 3.2 (Web, Data JPA, Security, Validation), Maven      |
| Database  | Oracle Database 21c XE / 23c Free (`ojdbc11`, HikariCP), H2 for local/tests |
| Security  | Spring Security, JWT (jjwt, HS384), BCrypt, method + URL-level RBAC        |
| Frontend  | Angular 17 (standalone components), TypeScript, RxJS, Angular Router       |
| API docs  | springdoc-openapi (Swagger UI)                                             |
| Dev/Ops   | Docker Compose v2, Nginx (prod static + reverse proxy), Jenkins pipeline   |

## Architecture

```text
┌──────────┐        ┌──────────────────────────────────────────┐
│ Browser  │        │            Docker network                │
│          │        │                                          │
│ Angular  │  :4200 │  ┌───────────────┐   /api/*   ┌─────────┐│
│   SPA    ├────────┼─►│   frontend    ├───────────►│ backend ││
│          │        │  │ nginx + dist  │  (proxy)   │ Spring  ││
└──────────┘        │  └───────────────┘            │  Boot   ││
     :8080          │                               └────┬────┘│
  (direct API /     │                                    │     │
   Swagger)         │                              JDBC │     │
                    │                               ┌────▼────┐│
                    │                               │ oracle  ││
                    │                               │ XE/Free ││
                    │                               └─────────┘│
                    └──────────────────────────────────────────┘
```

- **Production Angular build is same-origin**: the bundle calls `/api/...` and
  Nginx reverse-proxies to the backend (`BACKEND_URL`) — no CORS, portable
  behind any host. In dev, the SPA calls `http://localhost:8080` directly
  (CORS allow-listed). The origin switch is an Angular `fileReplacements` entry.
- **Oracle is the system of record**; H2 runs the `local` profile and the test
  suite so the app works with zero infrastructure.
- **Startup ordering is health-based** — Oracle PDB open (READ WRITE) → backend
  `/api/v1/health` → frontend. No fixed sleeps anywhere.

## Quick start

### Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| Docker Engine / Docker Desktop | 24+ with Compose v2 | Option A (containerized stack) |
| Java JDK | 17+ | Option B (native backend) |
| Maven | 3.9+ | Option B |
| Node.js + npm | 20+ / 10+ | Option B (native frontend) |
| Git | any | clone |

Ports used: `4200` (frontend), `8080` (API), `1521` (Oracle).
> **Apple Silicon / ARM64:** Oracle XE 21c is amd64-only — use the built-in
> ARM64 override (Oracle Free 23c) shown below.

### Option A — Docker Compose (recommended)

Everything — database, API, UI — in one command. Requires Docker only.

```bash
git clone https://github.com/Vijay-pratap7009/stocksmart-retail-inventory.git
cd stocksmart-retail-inventory

cp .env.example .env    # then edit .env: set DB_PASSWORD and JWT_SECRET
                        # generate a secret with: openssl rand -base64 48

docker compose up -d --build        # first run pulls ~2 GB and initializes Oracle
docker compose ps                   # wait until services report "healthy"

# ARM64 hosts: add the override file
# docker compose -f docker-compose.yml -f docker-compose.arm64.yml up -d --build
```

| What | Where |
|---|---|
| Web app | http://localhost:4200 |
| REST API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Oracle | `localhost:1521` (user `stocksmart`, service `XEPDB1`) |

```bash
docker compose logs -f backend      # follow logs (also: oracle, frontend)
docker compose stop                 # stop containers, keep data
docker compose down                 # remove containers, keep data
docker compose down -v              # DESTRUCTIVE: also deletes the oracle-data volume
```

Optional end-to-end check (50 assertions: auth, RBAC, inventory ledger, RFID,
orders, dashboard):

```bash
scripts/smoke-test.sh http://localhost:4200
```

### Option B — Run natively (no Docker required)

Backend on the in-memory H2 profile + Angular dev server. Great for quick
feature exploration; data resets when the backend stops.

```bash
git clone https://github.com/Vijay-pratap7009/stocksmart-retail-inventory.git
cd stocksmart-retail-inventory

# Terminal 1 — backend (H2 in-memory; no Oracle needed)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Terminal 2 — frontend
cd frontend
npm install
npm start            # ng serve → http://localhost:4200
```

Prefer Oracle even natively? Point `DB_HOST/DB_PORT/DB_SERVICE/DB_USERNAME/
DB_PASSWORD` at any Oracle instance and run `mvn spring-boot:run` (without the
`local` profile). The schema is JPA-managed (`ddl-auto: update`).

```bash
# Verify the API
curl http://localhost:8080/api/v1/health          # {"status":"UP",...}
scripts/smoke-test.sh http://localhost:8080       # full smoke suite
```

### First login

Seed users are created on first start (dev convenience — disable with
`APP_SEED_ENABLED=false` and change the password for anything non-demo):

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@123` | ADMIN (full access) |
| `manager` | `Admin@123` | MANAGEMENT (read-only KPIs) |

Open http://localhost:4200 → log in → explore **Dashboard**, **Products**,
**Inventory**, **Purchase/Sales Orders**, **Scan** and **RFID** from the nav.

## Configuration

The backend is configured entirely by environment variables (sensible dev
defaults in `application.yml`). Compose reads `.env` from the repo root and
**refuses to start** without `DB_PASSWORD` and `JWT_SECRET`.

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST` / `DB_PORT` | `localhost` / `1521` | Oracle host/port (native runs) |
| `DB_SERVICE` | `XEPDB1` | Oracle service (`FREEPDB1` with the ARM64 override) |
| `DB_USERNAME` / `DB_PASSWORD` | `stocksmart` / — | DB credentials (`DB_PASSWORD` **required** in compose) |
| `JWT_SECRET` | dev fallback (warns) | HS384 signing key — **required** in compose; `openssl rand -base64 48` |
| `JWT_EXPIRATION` | `86400000` (24h) | Token TTL in ms |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Browser origins allowed in dev (native mode) |
| `APP_SEED_ENABLED` | `true` | Seed roles + demo users |
| `APP_SEED_PASSWORD` | `Admin@123` | Demo user password |
| `FRONTEND_PORT` | `4200` | Host port for the frontend container |
| `BACKEND_URL` | `backend:8080` | Nginx upstream for `/api/*` (container-to-container) |
| `IMAGE_TAG` | `dev` | Tag for built backend/frontend images |

`.env` is git-ignored; `.env.example` contains placeholders only. See
`.env.example` for the full annotated list.

## API overview

Interactive documentation: **Swagger UI** at
`http://localhost:8080/swagger-ui.html` (click **Authorize** and paste a login
JWT to call protected endpoints). All routes are under `/api/v1`.

| Group | Endpoints (abridged) |
|---|---|
| Auth | `POST /auth/login` |
| Health | `GET /health` (public) |
| Master data | `/categories`, `/products`, `/warehouses`, `/suppliers` — full CRUD + paging/search |
| Barcode | `GET /products/barcode/{barcode}`, `GET /products/sku/{sku}` |
| Inventory | `GET /inventory`, `POST /inventory/receipts` · `adjustments` · `transfers`, `GET /stock-transactions` |
| Purchase orders | `/purchase-orders` + `submit`, `receipts`, `cancel` |
| Sales orders | `/sales-orders` + `confirm`, `fulfillments`, `cancel` |
| RFID | `/rfid/tags`, `/rfid/reader`, `/rfid/reads` |
| Dashboard | `GET /dashboard/summary` · `low-stock` · `stockouts` · `purchase-orders` · `sales-fulfillment` · `recent-transactions` |

Example session:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')

# Create a category, then receive stock
curl -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"code":"ELEC","name":"Electronics"}' http://localhost:8080/api/v1/categories
curl -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"productId":1,"warehouseId":1,"quantity":50}' \
  http://localhost:8080/api/v1/inventory/receipts
```

**Authorisation model** (enforced in the backend, mirrored in the UI):

- Reads: any authenticated role (some dashboard/order scopes are narrower).
- Writes: `ADMIN` everywhere; `INVENTORY_MANAGER` + `WAREHOUSE_STAFF` on
  inventory operations; `PROCUREMENT_USER` on purchasing; `SALES_USER` on
  sales; `MANAGEMENT` read-only.
- Full endpoint-level matrix: [`design.md` §5](design.md).

## Testing

| What | Command | Status in this repo |
|---|---|---|
| Backend integration suite (67 tests, H2) | `cd backend && mvn -B -ntp clean verify` | 67/67 passing |
| End-to-end smoke (50 checks, live API) | `scripts/smoke-test.sh http://localhost:8080` | 50/50 passing |
| Frontend production build | `cd frontend && npm ci && npm run build` | Clean build |
| Frontend unit tests (Karma) | `cd frontend && npm test` | Needs Chrome on the machine (not run here) |

The backend suite covers security (JWT tampering, RBAC matrix), master data,
inventory atomicity (including concurrent withdrawals), order lifecycles and
idempotency, barcode/RFID behavior, dashboard KPI math, and hardening guards.
The smoke script exercises a full business day against a running instance:
login → master data → receipt → transfer → adjustment → barcode → RFID →
PO receive → SO fulfill → insufficient-stock rejection → dashboard check.

## CI/CD

`Jenkinsfile` defines the pipeline:

1. **Backend build + test** — `mvn -B -ntp clean verify` (JUnit results published).
2. **Frontend build** — `npm ci` + production build; Angular unit tests run only
   when Chrome exists on the agent (otherwise the skip is logged loudly).
3. **Docker images** — backend + frontend built and tagged per build number.
4. **Compose validation** — `docker compose config` with CI placeholders.
5. **Deploy locally (opt-in parameter)** — `docker compose up -d --build` and the
   smoke script through the Nginx edge.

Agent prerequisites: JDK 17 and Node 20 tools configured in Jenkins
(`jdk-17`, `node-20`), Maven, and Docker + Compose v2. No registry credentials
needed — images are built locally and registry publishing is intentionally out
of scope.

## Project structure

```text
stocksmart-retail-inventory/
├── backend/                    # Spring Boot 3.2 API
│   ├── src/main/java/com/stocksmart/
│   │   ├── config/             # security, OpenAPI, data seeding
│   │   ├── controller/         # REST controllers (DTOs only on the wire)
│   │   ├── dto/  mapper/       # request/response DTOs + mapping
│   │   ├── entity/             # JPA entities (Oracle schema owner)
│   │   ├── exception/          # centralized error handling → JSON status codes
│   │   ├── repository/         # Spring Data repositories
│   │   ├── security/           # JWT filter, authentication, RBAC rules
│   │   └── service/            # business rules, transactions, locking
│   └── src/test/java/          # 67 integration tests (H2)
├── frontend/                   # Angular 17 standalone SPA
│   ├── src/app/core/           # models, services, interceptor, guard
│   ├── src/app/pages/          # login, dashboard, master data, inventory, orders…
│   ├── nginx/                  # production reverse-proxy template
│   └── Dockerfile              # build → nginx:1.27-alpine
├── scripts/smoke-test.sh       # 50-check e2e smoke suite
├── docker-compose.yml          # oracle + backend + frontend (health-ordered)
├── docker-compose.arm64.yml    # Oracle Free 23c override for ARM64 hosts
├── Jenkinsfile                 # CI/CD pipeline
├── .env.example                # annotated configuration template
├── design.md                   # data model, conventions, RBAC, deployment
└── agent.md                    # development log / verified-results ledger
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| `required variable DB_PASSWORD is missing` | `cp .env.example .env` and set `DB_PASSWORD` + `JWT_SECRET` |
| Backend starts but DB errors on first `up` | Oracle is still initializing — check `docker compose ps` / `logs oracle`; the healthcheck gates startup, on slow machines raise `healthcheck.retries` |
| `ORA-12514` / `ORA-01017` | Wrong `DB_SERVICE` (ARM64 override → `FREEPDB1`), credentials, or stale volume — `docker compose logs oracle` |
| Port 4200/8080/1521 already in use | Change `FRONTEND_PORT` in `.env`, or free the port |
| Frontend loads but API calls fail | In containers: `BACKEND_URL` must stay container-internal (`backend:8080`); natively: backend must be reachable at `localhost:8080` |
| Browser still calls `localhost:8080` in prod | Stale bundle — rebuild (`ng build` applies `environment.prod.ts`), hard-refresh |
| `exec format error` on Apple Silicon | Use the ARM64 override file (see Option A) |
| Tests pass locally, fail in CI | Verify the `jdk-17` / `node-20` Jenkins tools and that `npm ci` uses the committed lockfile |
| Data vanished after restart | You ran `docker compose down -v` — that deletes the `oracle-data` volume |

## Security notes & known limits

- **Secrets:** the app ships *dev-only* defaults. Real deployments must set
  `DB_PASSWORD` and `JWT_SECRET` (compose enforces both; the app logs a warning
  if the public dev JWT secret is used).
- **JWT is stateless:** deactivating a user does not revoke already-issued tokens
  before expiry (24h default). A deny-list or shorter TTL would be the fix.
- **RFID is simulator-backed:** no physical reader hardware is integrated; the
  `RfidReader` interface is the extension point. Reads never change stock.
- **Environment verification:** everything documented here was executed in
  development **except** container/oracle startup and Jenkins runs (no Docker
  daemon / Jenkins in the build environment — the compose files were validated
  at configuration level). See `agent.md` §6g for the exact verified/unverified
  split. Automated suites and smoke tests ran on H2.

## Development history

The project was built in seven verified increments; each entry was completed
with tests and a live curl/smoke verification before moving on.

| Day | Theme | Highlights |
|---|---|---|
| 1 | Foundation & security | Spring Boot + Angular skeletons, Oracle config, JWT login, RBAC, global error handling, CORS, Swagger |
| 2 | Master data | Categories, products, warehouses, suppliers — CRUD, paging/search, unique codes, delete guards, RBAC matrix |
| 3 | Inventory | Balances + append-only ledger, receipts/adjustments/transfers, pessimistic locking, atomic failure semantics |
| 4 | Orders | PO/SO lifecycles, atomic receipts/fulfillments through the inventory service, idempotency references |
| 5 | Barcode, RFID, KPIs | Barcode/SKU lookup, RFID registry + simulated reader, dashboard KPIs with exact ledger definitions |
| 6 | Hardening | Duplicate-line and delete guards, client-error status mapping, JWT tamper tests, N+1 mitigation, session-expiry UX |
| 7 | Deployment & CI/CD | Dockerfiles, compose stack (+ARM64), same-origin Nginx proxy, smoke tests, Jenkins pipeline, docs |

## Documentation map

| File | Contents |
|---|---|
| `README.md` | This file — overview, quick start, configuration, API summary |
| `design.md` | Full data model, conventions, RBAC baseline, deployment architecture |
| `agent.md` | Session-runbook style log: exact commands, verified results, environment gotchas |

---

Built by [Vijay Pratap](https://github.com/Vijay-pratap7009).
