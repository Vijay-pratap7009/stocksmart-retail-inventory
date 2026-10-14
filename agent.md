# AGENT.md — StockSmart Handoff (for future agent sessions)

> Read this file first before doing Day 4+ work. It records exactly what exists,
> how it was verified, and what was deliberately deferred.

## 1. Project

**StockSmart — Retail Inventory Optimization & Supply Chain Management.**
Modular monolith. Repo: `Vijay-pratap7009/stocksmart-retail-inventory` (branch `main`,
local dir `/home/vijaypratap/Code/hcl-project`). Day 1 = auth/security foundation.
Day 2 = category/product/warehouse/supplier master data (DONE — see §3b, §6b).
Day 3 = inventory balances + movement history (DONE — see §3c, §6c).

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
- Tests: `MasterDataIntegrationTest` (8 tests). Docs: `design.md` (new), README §10–11.

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
  Docs: `design.md` §2.5–2.6 + inventory RBAC, README §11–12.

## 4. Run commands

```bash
# Backend (Oracle):  cd backend && mvn spring-boot:run
# Backend (no Oracle): cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
# Tests:  cd backend && mvn test   # 26 tests: SecurityVerification + MasterData + Inventory
# Frontend: cd frontend && npm install && npm start   # :4200, demo admin/Admin@123
# Swagger: http://localhost:8080/swagger-ui.html
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

## 7. Deferred (DO NOT implement until its phase)

Day 2: DONE (product/category/warehouse/supplier + design.md).
Day 3: DONE (inventory balances + movement history; product/warehouse delete
guards active; `ISSUE` type reserved for Day 4).
Day 4: purchase/sales orders (revisit supplier delete guard; will write `ISSUE`
movements — do NOT let orders overwrite balances directly).
Day 4: purchase/sales orders. Day 5: barcode/RFID/KPI/reporting. Day 6: hardening.
Day 7: Docker deploy + Jenkins runs. Future: forecasting/ML.

## 8. Git state

Local `main`: Day 1 commits + `f7f4b62 docs: agent handoff` + `d9054da feat: Day 2`
(+ Day 3 work uncommitted at handoff time). Remote `origin` `main` is at Day 1
`8485bd6`; branch `day2-master-data` (= Day 2 only, no agent.md) was pushed for PR.
Tree clean at Day 2 push. Remote `origin` set, `main` tracks `origin/main`.
Pushing from here needs a PAT (`repo` scope); never reuse revoked/shared tokens —
ask the user for a fresh one each time.
