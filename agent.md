# AGENT.md — StockSmart Day 1 Handoff (for future agent sessions)

> Read this file first before doing Day 2+ work. It records exactly what exists,
> how it was verified, and what was deliberately deferred.

## 1. Project

**StockSmart — Retail Inventory Optimization & Supply Chain Management.**
Modular monolith. Repo: `Vijay-pratap7009/stocksmart-retail-inventory` (branch `main`,
local dir `/home/vijaypratap/Code/hcl-project`). Day 1 = auth/security foundation.
Day 2 = category/product/warehouse/supplier master data (DONE — see §3b, §6b).

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

## 4. Run commands

```bash
# Backend (Oracle):  cd backend && mvn spring-boot:run
# Backend (no Oracle): cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
# Tests:  cd backend && mvn test   # SecurityVerificationTest, 6 tests
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

## 7. Deferred (DO NOT implement until its phase)

Day 2: DONE (product/category/warehouse/supplier + design.md).
Day 3: inventory/adjustment/transfer (needs FKs to products/warehouses;
revisit warehouse delete guard). Day 4: purchase/sales orders (revisit supplier
delete guard).
Day 4: purchase/sales orders. Day 5: barcode/RFID/KPI/reporting. Day 6: hardening.
Day 7: Docker deploy + Jenkins runs. Future: forecasting/ML.

## 8. Git state

Commits on `main` (pushed to origin): foundation → JWT auth → RBAC → Angular →
docs → master-spec alignment. Tree clean. Remote `origin` set, `main` tracks `origin/main`.
Pushing from here needs a PAT (`repo` scope); revoked tokens from Day 1 must NOT be reused.
