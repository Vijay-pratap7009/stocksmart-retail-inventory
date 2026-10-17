# AGENT.md — StockSmart Handoff (for future agent sessions)

> Read this file first before doing Day 5+ work. It records exactly what exists,
> how it was verified, and what was deliberately deferred.

## 1. Project

**StockSmart — Retail Inventory Optimization & Supply Chain Management.**
Modular monolith. Repo: `Vijay-pratap7009/stocksmart-retail-inventory` (branch `main`,
local dir `/home/vijaypratap/Code/hcl-project`). Day 1 = auth/security foundation.
Day 2 = category/product/warehouse/supplier master data (DONE — see §3b, §6b).
Day 3 = inventory balances + movement history (DONE — see §3c, §6c).
Day 4 = purchase/sales orders with inventory integration (DONE — see §3d, §6d).
Day 5 = barcode operations, RFID tag mapping + reader simulator, KPI dashboard
(DONE — see §3e, §6e). RFID is **simulator-only** — no physical hardware.
Day 6 = hardening & release verification (DONE — see §3f, §6f).

## 2. Stack (locked)

- Backend: Java 17 (Temurin), Spring Boot 3.2.5, Web / Data JPA / Security / Validation, Maven.
  Local toolchain lives OUTSIDE the repo: JDK + Maven 3.9.16 under `/tmp/opencode/tools/`.
  Every backend shell command must prefix:
  `export JAVA_HOME=$(echo /tmp/opencode/tools/jdk-*) && export PATH=$JAVA_HOME/bin:/tmp/opencode/tools/apache-maven-3.9.16/bin:$PATH`
- DB: Oracle primary (`ojdbc11:23.3.0.23.09`, `OracleDialect`); H2 `local` profile fallback.
- Security: jjwt 0.12.6, BCrypt, RBAC. Frontend: Angular 17 standalone, Node 20.

## 3. What Day 1 built (all verified — see §6)

- `backend/src/main/java/com/stocksmart/`: `StockSmartApplication`, `config/` (SecurityConfig,
  OpenApiConfig, DataSeeder — seeds 6 roles + admin/manager), `security/jwt/` (JwtService,
  JwtAuthenticationFilter, JwtAuthEntryPoint, CustomAccessDeniedHandler),
  `security/authentication/StockSmartUserDetails`, `security/authorization/Roles`,
  `controller/` (Auth, Health, Test), `service/` (AuthService, CustomUserDetailsService),
  `repository/`, `entity/` (User, Role, RoleName), `dto/`, `mapper/UserMapper`,
  `exception/GlobalExceptionHandler`.
- DB: `roles` 1—N `users` (sequences, FK, unique username/email, audit timestamps).
- Endpoints: `GET /api/v1/health` (public), `POST /api/v1/auth/login` (public),
  `GET /api/v1/test/{protected,admin,management}` (JWT / ADMIN / MANAGEMENT).
- `frontend/src/app/`: `core/{models,services/auth.service,interceptors/auth.interceptor,guards/auth.guard}`,
  `pages/{login,home}`, routes, `proxy.conf.json`, environments.
- Root: `README.md`, `.env.example`, `.gitignore`, `docker-compose.yml` (Oracle XE + backend),
  `Jenkinsfile`, `backend/Dockerfile`, `.dockerignore`s.
- Env vars: `DB_HOST/DB_PORT/DB_SERVICE/DB_USERNAME/DB_PASSWORD`, `JWT_SECRET`,
  `JWT_EXPIRATION`, `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` (wins when set),
  `APP_SEED_ENABLED`, `APP_SEED_PASSWORD`. Never commit `.env`/secrets.

## 3b. What Day 2 built (all verified — see §6b)

- Backend `com.stocksmart`: `entity/` (Category, Product [FK→Category], Warehouse,
  Supplier — sequence PKs, unique codes/SKU uppercased, audit timestamps),
  `repository/` (search `@Query` + `existsBy*IgnoreCase`), `dto/` (Request/Response
  per module), `mapper/` (4 mappers), `service/` (Category/Product/Warehouse/
  SupplierService — 409 duplicates, 404 refs, referenced-category delete guard),
  `controller/` (`/api/v1/categories|products|warehouses|suppliers` — paged CRUD,
  201/204, `@PreAuthorize`), `exception/ConflictException` (+ `DataIntegrityViolation`
  → 409 and `AccessDeniedException` → 403 safety nets in `GlobalExceptionHandler`).
- `SecurityConfig`: Day 2 URL matchers (GET reads per matrix; writes ADMIN+IM for
  cat/prod/wh, ADMIN+PROCUREMENT for suppliers) mirroring `@PreAuthorize`.
  `Roles` javadoc now documents the matrix. `OpenApiConfig` → "Day 2 master data".
- Frontend: `core/models/master-data.models`, 4 services, 4 guarded list+form
  pages (`/categories|products|warehouses|suppliers`), Home nav. Product form loads
  categories from the API. `ng build` ✅.
- Tests: `MasterDataIntegrationTest` (8 tests). Docs: `design.md` (new), README (master-data docs).

## 3c. What Day 3 built (all verified — see §6c)

- Backend: `entity/` (Inventory [unique (product,warehouse), NUMBER(19,4)],
  StockTransaction [append-only, no update/delete API], TransactionType),
  `repository/` (InventoryRepository with `PESSIMISTIC_WRITE` locker;
  StockTransactionRepository history search), `dto/` (Receipt/Adjustment/
  Transfer requests, Inventory/StockTransaction/Transfer responses),
  `mapper/` (2 mappers), `service/` (InventoryService — receipt/adjust/transfer
  in single txns with audit rows, retry-once on creation races;
  StockTransactionService reads), `exception/InsufficientStockException` → 409,
  `IllegalArgumentException` + malformed-JSON → 400 handlers.
- `controller/` (`/api/v1/inventory` + `/api/v1/stock-transactions`).
  `SecurityConfig`: reads for all roles; writes ADMIN+IM+WAREHOUSE_STAFF.
  Product/Warehouse deletes now blocked (409) when inventory rows reference them.
- Frontend: `core/models/inventory.models`, `InventoryService`,
  `StockTransactionService`, 4 guarded pages (`/inventory`, `/inventory/operations`,
  `/inventory/transfers`, `/inventory/history`), Home nav. `ng build` ✅.
- Tests: `InventoryIntegrationTest` (12 tests incl. 20-thread concurrency).
  Docs: `design.md` §2.5–2.6 + inventory RBAC, README (inventory docs).

## 3d. What Day 4 built (all verified — see §6d)

- Backend: `entity/` (PurchaseOrder/Item/Status, PurchaseReceipt(+Item),
  SalesOrder/Item/Status, SalesFulfillment(+Item) — sequence PKs, unique
  numbers/references, FKs, audit timestamps), `repository/` (order repos with
  `PESSIMISTIC_WRITE lockById` + search; receipt/fulfillment repos),
  `dto/` (order + line + receipt/fulfillment request/response),
  `mapper/` (PurchaseOrderMapper, SalesOrderMapper),
  `service/` (PurchaseOrderService, SalesOrderService — lifecycles, atomic
  receipt/fulfillment joining Day 3 `receiveForOrder`/`issueForOrder`,
  idempotency guards), `InventoryService` + `receiveForOrder`/`issueForOrder`
  hooks (txn-aware retry guard).
- `controller/` (`/api/v1/purchase-orders`, `/api/v1/sales-orders` + action
  endpoints). `SecurityConfig`: PO side (reads all-but-sales; writes
  ADMIN+PROCUREMENT; receipts +IM+WH), SO side mirrored for SALES.
- Frontend: `core/models/order.models`, `PurchaseOrderService`,
  `SalesOrderService`, list + detail pages with draft editors, receipt/
  fulfillment forms, histories; routes + Home nav. `ng build` ✅.
- Tests: `OrderIntegrationTest` (8 tests). Day 2/3 test setUps now wipe order
  tables first (FK-safe regardless of execution order). Docs: `design.md`
  §2.7–2.8 + order RBAC, README (orders docs).

## 3e. What Day 5 built (all verified — see §6e)

- Products: `barcode` (nullable, UNIQUE when set `uk_products_barcode`,
  UPPERCASE, pattern `^[A-Za-z0-9.\-+/]{4,60}$`) + `minimum_stock_level`
  (nullable column for migration safety; app default ZERO;
  `getEffectiveMinimumStockLevel()`, null-coalesced everywhere).
  `GET /api/v1/products/barcode/{barcode}` + `GET /api/v1/products/sku/{sku}`
  (any authenticated role). Barcode duplicates → 409; product deletes blocked
  (409) while `rfid_tags` reference the row.
- RFID backend: `entity/` (RfidTag, RfidReadEvent, RfidReadStatus),
  `repository/` (RfidTagRepository, RfidReadEventRepository),
  `rfid/` (`RfidReader` interface, `RfidRead` record, `SimulatedRfidReader`
  → `SIMULATED-READER-01`, always `simulated=true`), `dto/` (tag/read
  requests+responses, reader info), `mapper/RfidMapper`, `service/RfidService`,
  `controller/RfidController` (`/api/v1/rfid`: tags list/get/register/update,
  `/reader`, reads POST/GET). Read events idempotent via unique
  `event_reference` (replay → 409); simulated flag server-controlled;
  statuses RESOLVED/INACTIVE_TAG/UNKNOWN_TAG; **reads never change stock**.
- Dashboard backend: DTOs (summary, low-stock item, stockout item, PO summary,
  SO fulfillment summary), `mapper/DashboardMapper`, `service/DashboardService`,
  `controller/DashboardController` (`/api/v1/dashboard`: summary, low-stock,
  stockouts, purchase-orders, sales-fulfillment, recent-transactions).
  Queries added: `InventoryRepository` sumQuantityOnHand/sumInventoryValue/
  countLowStock/findLowStock/countStockouts/findStockouts,
  `PurchaseOrderRepository.countByStatus`, `SalesOrderRepository.countByStatus`,
  new `SalesOrderItemRepository.sumRemainingQuantity`. Unknown `warehouseId`
  → 404 (all scoped endpoints).
- `SecurityConfig`: `/api/v1/rfid/**` (tag writes ADMIN; GETs any authenticated;
  read processing `@PreAuthorize` ADMIN/IM/WH/PROCUREMENT/SALES) and
  `/api/v1/dashboard/**` matchers (order summaries exclude sales/procurement
  respectively); `Roles` javadoc updated.
- Frontend: `core/models/rfid.models` + `dashboard.models`, `RfidService`,
  `DashboardService`, product `getByBarcode`/`getBySku`, product form/list
  barcode + minimum-stock-level fields, pages `/scan` (read-only resolve),
  `/rfid` (reader card, tag registry for ADMIN, simulate/process reads, event
  log), `/dashboard` (KPI cards, low-stock/stockout tables, PO/SO summary
  chips, recent movements, warehouse filter; order cards hide on 403-prone
  roles). Home nav "Day 5" section. `ng build` ✅ clean (no budget warnings).
- Tests: `BarcodeRfidIntegrationTest` (15) + `DashboardIntegrationTest` (12);
  Day 1–4 test set-ups now wipe `rfid_read_events` then `rfid_tags` first
  (RFID FKs → products/users). Docs: `design.md` §2.9–2.10 + Day 5 API/RBAC/KPI,
  README (barcode/RFID/KPI docs).

## 3f. What Day 6 built (all verified — see §6f)

- **Integrity guards:** `PurchaseOrderService.receive` / `SalesOrderService.fulfill`
  reject duplicate line ids in one payload up front (400) — previously repeated
  entries each passed the remaining-quantity check and could over-receive /
  over-fulfill. `SupplierService` / `WarehouseService` deletes now 409 while POs
  reference them; `WarehouseService` also guards sales orders; `ProductService`
  guards purchase-order lines, sales-order lines and stock movements
  (new `PurchaseOrderItemRepository.existsByProductId`; `existsBy*` added to
  `PurchaseOrderRepository`, `SalesOrderRepository`, `SalesOrderItemRepository`,
  `StockTransactionRepository`).
- **Error handling:** `GlobalExceptionHandler` maps
  `MethodArgumentTypeMismatch` / `MissingServletRequestParameter` → 400,
  `HttpRequestMethodNotSupported` → 405, `HttpMediaTypeNotSupported` → 415,
  `NoResourceFound` → 404 (previously 500s).
- **JWT/secrets:** `JwtService` logs a startup warning when `JWT_SECRET` is the
  documented dev default (public in repo).
- **N+1:** `@BatchSize(50)` on order/receipt/fulfillment aggregate entities.
- **Frontend:** `auth.interceptor.ts` attaches the bearer to `http*` and `/api*`
  URLs and, on any non-login 401, clears the session and redirects to `/login`
  (previously screens were stuck on error banners).
- **Tests:** `HardeningIntegrationTest` (5: duplicate receipt line, duplicate
  fulfillment line, client-error 400/404/405/415 matrix, delete guards, CORS
  allow-list) + new JWT signature/claim-tampering test in
  `SecurityVerificationTest` (now 7). Full suite: 67/67 ✅. `ng build` ✅.

## 3g. What Day 7 built (config-verified; runtime containers NOT executed — see §6g)

- **Backend image** (`backend/Dockerfile`): multi-stage `maven:3.9-eclipse-temurin-17`
  build → `eclipse-temurin:17-jre`, non-root user `stocksmart` (uid 1001), `curl` for
  the healthcheck, `JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"`,
  `HEALTHCHECK` on `/api/v1/health`, exec-form entrypoint (SIGTERM reaches the JVM).
  `server.shutdown: graceful` + 30 s lifecycle timeout added to `application.yml`.
- **Frontend image** (`frontend/Dockerfile`): `node:20-alpine` build → `nginx:1.27-alpine`;
  `frontend/nginx/default.conf.template` proxies `/api/` to `${BACKEND_URL}`
  (default `backend:8080`) via the image's envsubst entrypoint, SPA
  `try_files ... /index.html` fallback, static-asset cache headers.
- **Prod origin switch (was dead code before):** `environment.prod.ts` now
  `apiUrl: ''` and `angular.json` has the production `fileReplacements` entry —
  `ng build` emits a same-origin bundle (verified: zero `localhost:8080` refs).
- **Compose** (`docker-compose.yml`): oracle (`gvenzl/oracle-xe:21-slim-faststart`,
  `healthcheck.sh XEPDB1`) → backend (`depends_on: service_healthy`, required
  `DB_PASSWORD`/`JWT_SECRET` via `:?`) → frontend (`4200:80`, `BACKEND_URL`);
  bridge network `stocksmart-net`, named volume `oracle-data`.
  `docker-compose.arm64.yml` override: `gvenzl/oracle-free:23-slim-faststart`
  with `FREEPDB1` (XE 21c is amd64-only; volumes are NOT cross-image compatible).
- **Config:** `.env.example` documents required vs optional vars; `.env` git-ignored.
- **`Jenkinsfile`:** checkout → backend `mvn -B -ntp clean verify` (H2, JUnit
  published) → frontend `npm ci` + build (`ng test` only when Chrome exists,
  explicit skip otherwise) → docker images `:${BUILD_NUMBER}`/`:latest` →
  `docker compose config` validation (placeholder env) → opt-in `DEPLOY_LOCAL`
  stage (`up -d --build` + `scripts/smoke-test.sh http://localhost:4200`).
- **`scripts/smoke-test.sh`:** 50 checks — health, auth/JWT, RBAC 401/403, master
  data, receipt→transfer→adjustment ledger math, barcode lookup, RFID register/
  resolve/duplicate-409/no-stock-change, PO receive, SO fulfill + insufficient-409,
  dashboard-vs-ledger, optional SPA deep-link when the target serves HTML.
- **Docs:** README (overview, quick start, configuration, deployment,
  troubleshooting), design.md §7
  (deployment architecture), this file.

## 4. Run commands

```bash
# Backend (Oracle):  cd backend && mvn spring-boot:run
# Backend (no Oracle): cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
# Tests:  cd backend && mvn test   # 67 tests: Security + MasterData + Inventory + Order + BarcodeRfid + Dashboard + Hardening
# Frontend: cd frontend && npm install && npm start   # :4200, demo admin/Admin@123
# Swagger: http://localhost:8080/swagger-ui.html
# Container stack (needs Docker + .env with DB_PASSWORD/JWT_SECRET):
#   cp .env.example .env && docker compose up -d --build
#   scripts/smoke-test.sh http://localhost:4200        # through nginx
#   scripts/smoke-test.sh http://localhost:8080        # native backend instead
# ARM64 host: add -f docker-compose.arm64.yml (Oracle Free 23c, FREEPDB1)
```

## 5. Gotchas learned in this environment

- Machine is `aarch64` — download ARM JDK builds, not x64 (x64 binary = "Exec format error").
- No Docker, no browser (no Karma), no `gh`, no git credentials. Live Oracle never tested here;
  Oracle support is config/driver/dialect-complete, runtime-proven via H2 `local` profile.
- `pkill -f <pattern>` kills your own shell if the pattern string is in your command —
  use the `grep "Patther[n]"` bracket trick when killing java/ng processes.
- Background servers: launch with `setsid nohup ... & disown`, otherwise shell-tool timeout kills them.

## 6. Verified results (executed, not claimed)

`mvn test` 6/6 ✅ (health, login 200/401, protected 200/401, expired JWT 401, invalid JWT 401,
RBAC 403/200 matrix, BCrypt); live curl matrix all ✅ incl. CORS echo of `FRONTEND_URL`;
`ng build` ✅; `ng serve` ✅. NOT browser-executed: guard redirect click-through
(no browser here — confirm where one exists).

## 6b. Day 2 verified results (executed, not claimed)

`mvn test` 14/14 ✅ (Day 1 6/6 + `MasterDataIntegrationTest` 8/8: category CRUD +
duplicate 409, product valid/invalid-category + duplicate SKU + price/name 400s,
search/category/active matrix, referenced-category delete 409→204, warehouse +
supplier CRUD + duplicate 409 + email 400, full 6-role RBAC matrix, 404s).
Live `local`-profile curl ✅: create/list category, duplicate 409, product create,
bad-category 404, referenced delete 409, MANAGEMENT write 403 / read 200, no-token
401, warehouse/supplier create, `/v3/api-docs` 200. `ng build` ✅ (334 kB).
Found + fixed during Day 2: `@PreAuthorize`-only denials rendered 500 past the
filter chain — fixed via URL matchers in `SecurityConfig` + `AccessDeniedException`
→ 403 safety net. No Docker, no browser (no Karma), no `gh`. Live Oracle still
untested here; Day 2 schema is JPA-managed (`ddl-auto: update`) like Day 1.

## 6c. Day 3 verified results (executed, not claimed)

`mvn test` 26/26 ✅ (Day 1 6/6 + Day 2 8/8 + `InventoryIntegrationTest` 12/12:
receipt/opening-balance + repeat-receipt summing, receipt 404/400s, adjust IN/OUT
+ reason + actor audit, over-draw 409 with zero side effects, adjust-without-row
404, atomic paired transfer with shared reference, same-wh 400 / insufficient 409 /
unknown-wh 404 / zero-qty 400 with unchanged balances + txn count, history
type/product/warehouse/reference/date-range filters + get/404, empty-list reads,
product+warehouse delete guards 409, 6-role RBAC, 20-thread concurrent withdrawals
exact: 10×5→50 then 10×20→10 with 8 rejections, 13 txns, never negative).
Live `local`-profile curl ✅: receipt (OPENING_BALANCE), adjust, over-draw 409,
transfer legs, same-wh 400, MANAGEMENT write 403 / history read 200, no-token 401,
product stock view, referenced-product delete 409. `ng build` ✅ (368 kB).
Found + fixed during Day 3: `@PageableDefault(sort="createdAt,desc")` is invalid
(splits into sort+direction); malformed JSON rendered 500 → now 400 via
`HttpMessageNotReadableException` handler. Test H2 URL gained `LOCK_TIMEOUT=10000`
for the concurrency test. No Docker, no browser, no `gh`. Live Oracle still untested.

## 6d. Day 4 verified results (executed, not claimed)

`mvn test` 34/34 ✅ (Day 1 6/6 + Day 2 8/8 + Day 3 12/12 + `OrderIntegrationTest`
8/8: PO lifecycle with partial receipts → RECEIVED, PO validation + over-receipt
rollback, receipt idempotency (no double stock), SO lifecycle → FULFILLED with
ISSUE audit, SO validation + insufficient-stock rollback, fulfillment
idempotency, 2-thread concurrent fulfillments (1 win + 1 409, balance exact),
cross-role RBAC incl. no sales↔purchase access).
Live `local`-profile curl ✅: PO create/submit/partial-receive/over-receive-409,
SO create/confirm/partial-fulfill/over-fulfill-409, stock 60→40, ISSUE history.
`ng build` ✅ (413 kB).
Found + fixed during Day 4: Day 4 `ReceiptRequest` name clashed with Day 3's →
renamed to `PurchaseReceipt*` (the `mv` briefly deleted Day 3's file; restored
byte-identical); test helper `Map<Long,String>` vs `Long` mismatches; ISSUE
count expectation 2→3 (1+2 lines); Day 2/3 test setUps needed order-table wipes
(FK-safe ordering). No Docker, no browser, no `gh`. Live Oracle still untested.

## 6e. Day 5 verified results (executed, not claimed)

`mvn test` 61/61 ✅ (Day 1 6/6 + Day 2 8/8 + Day 3 12/12 + Day 4 8/8 +
`BarcodeRfidIntegrationTest` 15/15 + `DashboardIntegrationTest` 12/12).
BarcodeRfid: barcode/SKU lookups + stock-unchanged proof, lowercase lookup
normalization, unknown/inactive 404, duplicate barcode 409, invalid barcode 400,
product-create role matrix, barcode→receipt flow, tag register/duplicate 409/
404, remap + deactivate, simulated read RESOLVED (explicit EPC + auto-pick),
unknown EPC → UNKNOWN_TAG, inactive tag → INACTIVE_TAG, event-reference replay
409, external-reader path, tag-write RBAC, product-delete blocked while tagged.
Dashboard: exact KPI values (units/value/low/stockout counts), low-stock
boundary matrix (at-minimum NOT low, zero = stockout, min 0 never low),
warehouse filter + paging consistency, cost-basis HALF_UP rounding
(3×0.3333 → 1.00), empty dataset zeros, PO/SO status counts + pending,
remaining-to-fulfill units summed per line, no double-count across joins,
recent feed order/limit/product filter, unknown warehouseId 404, per-card auth
matrix (no-token 401; sales→PO 403, procurement→SO 403).
Live `local`-profile curl ✅: product with barcode+min 12, duplicate barcode
409, receipt 8 → summary `{units:8, value:80.00, lowStock:1}`, low-stock
deficit 4, exact + lowercase-SKU lookups, reader info (simulated note), tag
register normalized UPPERCASE + duplicate 409, simulated reads RESOLVED
(explicit + auto-pick), unknown EPC UNKNOWN_TAG, replay 409, **stock transaction
count stayed 1 after all reads**, 3 read events logged, MANAGEMENT tag-write
403 / dashboard 200 / RFID read 200, no-token 401, PO/SO summaries zero-state,
unknown warehouse 404, recent-movements limit honored.
`ng build` ✅ clean (no warnings; Day 5 pages' CSS trimmed to the 2 kB
component-style budget — config left at Angular defaults).
Found + fixed during Day 5: Jackson tree parsing normalizes BigDecimal scale
(2.15.4) so exact-scale test assertions now use raw payload strings;
`DashboardIntegrationTest` W2 value arithmetic (92.00 → 52.00); Day 1–4 test
setUps needed RFID-first deletes (FK-safe ordering). No Docker, no browser
(no Karma), no `gh`. Live Oracle still untested here (H2 `local` only).

## 6f. Day 6 verified results (executed, not claimed)

`mvn test` 67/67 ✅ (Day 1 7/7 incl. the new wrong-key-signature + tampered-claim
401 test + Day 2 8/8 + Day 3 12/12 + Day 4 8/8 + Day 5 15/15 + 12/12 +
`HardeningIntegrationTest` 5/5). `ng build` ✅ (~470 kB initial). Re-verified
2026-10-17 with the working tree intact. **Not verified:** Oracle runtime (no
Docker/Oracle here — H2 test profile only), Angular Karma tests (no browser).
Context for this run: fresh toolchain had to be re-downloaded to
`/tmp/opencode/tools/` (JDK 17 + Maven 3.9.6; agent.md said 3.9.16 — either
works). No Docker, no browser, no `gh`.

## 6g. Day 7 verified results (executed) + explicitly UNVERIFIED

Executed here (2026-10-17):
- `mvn -B -ntp clean package` (after the Day 7 config edits) → 67/67 tests ✅,
  jar `backend/target/stocksmart-backend-0.1.0.jar`.
- `npm ci` (890 pkgs) + `npm run build` ✅. **Production bundle grep: 0 occurrences
  of `localhost:8080`** — the env switch really took effect (`apiUrl:""` in bundle).
- `docker compose config` with the standalone Compose **v5.6.0** binary (no daemon
  needed) downloaded to `/tmp/opencode/tools/docker-compose`, checksum-verified:
  base file VALID ✅, `-f docker-compose.yml -f docker-compose.arm64.yml` VALID ✅
  (renders `DB_SERVICE: FREEPDB1`, `gvenzl/oracle-free:23-slim-faststart`), and with
  `DB_PASSWORD`/`JWT_SECRET` unset it exits 1 with clear `.env` guidance ✅.
- Native runtime smoke: `java -jar ... --spring.profiles.active=local` (H2),
  then `scripts/smoke-test.sh http://localhost:8080` → **50 passed, 0 failed** ✅
  (auth/RBAC, master data, receipt 10 → transfer 3 → adjust 2 → WH1=7/WH2=5,
  barcode, RFID resolve + dup-409 with **no stock movement**, PO +5 → WH1=12,
  SO −4 → WH1=8, oversized SO → 409 unchanged, dashboard WH1 = 8 units).
- Graceful shutdown: `kill -TERM` → Spring "Commencing graceful shutdown …
  complete", process exits cleanly ✅ (container stop behavior).
- Startup log confirms the Day 6 JWT dev-secret warning fires ✅.

**Unverified (blocked in this environment — do not claim otherwise):**
`docker build` (no daemon), container startup, Oracle XE connectivity/PDB,
nginx SPA fallback (`try_files`), the ARM64 Oracle Free path, and any Jenkins
stage execution (no Jenkins here). The smoke SPA section auto-skips when the
target serves the API directly (it did, on :8080).

## 7. Deferred (DO NOT implement until its phase)

Day 2: DONE (product/category/warehouse/supplier + design.md).
Day 3: DONE (inventory balances + movement history; product/warehouse delete
guards active; `ISSUE` type reserved for Day 4).
Day 4: DONE (purchase/sales orders, atomic receipts/fulfillments via Day 3
hooks, idempotency references, no cross sales↔purchase access).
Day 5: DONE (barcode lookups, RFID tag registry + simulator, KPI dashboard;
RFID = SIMULATOR only — no physical hardware; reads never change stock).
Day 6: DONE (hardening — see §3f/§6f; working tree, not yet committed).
Day 7: DONE here (Docker/compose/Jenkins/smoke + docs — see §3g/§6g; runtime
containers unverified, no Docker). Future: forecasting/ML.

## 8. Git state

Local `main`: Day 1 commits + `f7f4b62 docs: agent handoff` + `d9054da feat: Day 2`
+ Day 3 commit (+ Day 4 + Day 5 work uncommitted at handoff time). Remote
`origin/main` accepted Day 2 via PR #1 (`56cc3a5`); branch `day3-master-data`
(= Day 3 only) was pushed for PR. Remote `origin` set, `main` tracks `origin/main`.
Pushing from here needs a PAT (`repo` scope); never reuse revoked/shared tokens —
ask the user for a fresh one each time.

Day 6 state (as of 2026-10-17): local `main` = `168d2ab` (Day 5), diverged from
`origin/main` (5 ahead / 4 behind). Day 6 lives in the **uncommitted working
tree**: 21 modified files + 2 untracked files (`HardeningIntegrationTest.java`,
`PurchaseOrderItemRepository.java`) + the doc updates from this session. Nothing
was committed, pushed, stashed, reset, or cleaned during Day 6.

Day 7 state (same day): still `main` = `168d2ab`, still diverged 5/4. Day 7 is
also **uncommitted**, merged into the same working tree: ~40 changed paths total
(Days 1–7), including new `frontend/Dockerfile`,
`frontend/nginx/default.conf.template`, `docker-compose.arm64.yml`,
`scripts/smoke-test.sh`, and rewritten `backend/Dockerfile`, `docker-compose.yml`,
`Jenkinsfile`, `angular.json`, `environment.prod.ts`, `application.yml`,
`.env.example`, README/design/agent docs. No commit, push, stash, reset, or
clean was performed. The user has not asked for commits.
