#!/usr/bin/env bash
# =============================================================================
# StockSmart — end-to-end smoke test (Day 7)
#
# Usage:
#   scripts/smoke-test.sh [BASE_URL]
#     BASE_URL  backend root      default: http://localhost:8080
#               frontend proxy    e.g. http://localhost:4200 (container stack)
#
# Env:
#   SMOKE_USER / SMOKE_PASSWORD   default: admin / Admin@123 (seeded demo user)
#
# Requires: curl, python3.
# Note: creates uniquely-suffixed demo records through the public API and
# leaves them in place (not idempotent). Safe for demo/dev databases only.
# When BASE_URL serves the Angular app, the SPA deep-link fallback is checked
# as well; when pointed at the backend directly, that check is skipped.
# =============================================================================
set -uo pipefail

BASE_URL="${1:-http://localhost:8080}"
SMOKE_USER="${SMOKE_USER:-admin}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Admin@123}"
SUFFIX="$(date +%s)"
TODAY="$(date +%F)"

PASS=0
FAIL=0

say() { printf '%s\n' "$*"; }
ok()  { PASS=$((PASS + 1)); printf '  ok   %s\n' "$1"; }
bad() { FAIL=$((FAIL + 1)); printf ' FAIL  %s\n' "$1"; }

# --- HTTP helper: req METHOD PATH [JSON_BODY] -> STATUS, BODY (TOKEN header when set)
req() {
  local method="$1" path="$2" body="${3:-}" out
  local args=(-sS -X "$method" "${BASE_URL}${path}" -w $'\n%{http_code}' -H 'Content-Type: application/json')
  [ -n "${TOKEN:-}" ] && args+=(-H "Authorization: Bearer ${TOKEN}")
  [ -n "$body" ] && args+=(-d "$body")
  if ! out="$(curl "${args[@]}" 2>/dev/null)"; then STATUS=000; BODY=""; return 1; fi
  STATUS="${out##*$'\n'}"
  BODY="${out%$'\n'*}"
}

expect() { # expect EXPECTED LABEL
  if [ "${STATUS}" = "$1" ]; then ok "$2 (HTTP ${STATUS})"; else
    bad "$2 — expected HTTP $1, got ${STATUS}"; say "        body: ${BODY:0:300}"
  fi
}

# --- JSON field getter: jget dotted.path (supports list indexes) ---
jget() {
  JGET_BODY="${BODY}" JGET_PATH="$1" python3 - <<'PY'
import json, os
try:
    cur = json.loads(os.environ["JGET_BODY"])
except Exception:
    print("")
    raise SystemExit
for part in os.environ["JGET_PATH"].split("."):
    if isinstance(cur, dict):
        cur = cur.get(part)
    elif isinstance(cur, list) and part.isdigit() and int(part) < len(cur):
        cur = cur[int(part)]
    else:
        cur = None
    if cur is None:
        break
print("" if cur is None else cur)
PY
}

num_eq() { python3 -c 'import sys; sys.exit(0 if abs(float(sys.argv[1]) - float(sys.argv[2])) < 1e-9 else 1)' "$1" "$2" 2>/dev/null; }

expect_balance() { # expect_balance WAREHOUSE_ID EXPECTED LABEL
  req GET "/api/v1/inventory?productId=${PROD_ID}&warehouseId=$1"
  expect 200 "$3 (read)"
  local actual
  actual="$(jget "content.0.quantityOnHand")"
  if [ -n "${actual}" ] && num_eq "${actual}" "$2"; then
    ok "$3 (on-hand ${actual})"
  else
    bad "$3 — expected ${2}, got '${actual}'"
  fi
}

# =============================================================================
say "StockSmart smoke test → ${BASE_URL} (user: ${SMOKE_USER}, run: ${SUFFIX})"
say ""

say "Waiting for ${BASE_URL}/api/v1/health ..."
READY=""
for _ in $(seq 1 60); do
  if curl -fsS "${BASE_URL}/api/v1/health" >/dev/null 2>&1; then READY=1; break; fi
  sleep 3
done
if [ -z "${READY}" ]; then
  say "FATAL: ${BASE_URL} not healthy after 180s — aborting."
  exit 1
fi

TOKEN=""
say "[1] Health + authentication"
req GET /api/v1/health; expect 200 "public health endpoint"
req GET /api/v1/products; expect 401 "protected endpoint rejects missing token"

BODY="{\"username\":\"${SMOKE_USER}\",\"password\":\"${SMOKE_PASSWORD}\"}"
req POST /api/v1/auth/login "${BODY}"; expect 200 "login ${SMOKE_USER}"
TOKEN="$(jget token)"
if [ -z "${TOKEN}" ]; then say "FATAL: no token in login response"; exit 1; fi
req GET /api/v1/test/protected; expect 200 "authenticated call accepts JWT"

TOKEN=""
BODY="{\"username\":\"manager\",\"password\":\"${SMOKE_PASSWORD}\"}"
req POST /api/v1/auth/login "${BODY}"; expect 200 "login manager"
TOKEN="$(jget token)"
req GET /api/v1/test/admin; expect 403 "RBAC: MANAGEMENT denied on admin endpoint"
req GET /api/v1/test/management; expect 200 "RBAC: MANAGEMENT allowed on management endpoint"

BODY="{\"username\":\"${SMOKE_USER}\",\"password\":\"${SMOKE_PASSWORD}\"}"
req POST /api/v1/auth/login "${BODY}"; expect 200 "re-login ${SMOKE_USER}"
TOKEN="$(jget token)"

say ""
say "[2] Master data (admin)"
req POST /api/v1/categories "{\"code\":\"SMK-CAT-${SUFFIX}\",\"name\":\"Smoke Category ${SUFFIX}\"}"; expect 201 "create category"
CAT_ID="$(jget id)"

BARCODE="SMKBC${SUFFIX}"
req POST /api/v1/products "{\"sku\":\"SMK-P-${SUFFIX}\",\"name\":\"Smoke Product ${SUFFIX}\",\"categoryId\":${CAT_ID},\"unit\":\"PCS\",\"costPrice\":10.00,\"sellingPrice\":15.00,\"barcode\":\"${BARCODE}\",\"minimumStockLevel\":5}"; expect 201 "create product (with barcode + min stock)"
PROD_ID="$(jget id)"

req POST /api/v1/warehouses "{\"code\":\"SMK-WH1-${SUFFIX}\",\"name\":\"Smoke WH1 ${SUFFIX}\"}"; expect 201 "create warehouse 1"
WH1="$(jget id)"
req POST /api/v1/warehouses "{\"code\":\"SMK-WH2-${SUFFIX}\",\"name\":\"Smoke WH2 ${SUFFIX}\"}"; expect 201 "create warehouse 2"
WH2="$(jget id)"
req POST /api/v1/suppliers "{\"code\":\"SMK-SUP-${SUFFIX}\",\"name\":\"Smoke Supplier ${SUFFIX}\"}"; expect 201 "create supplier"
SUP_ID="$(jget id)"

say ""
say "[3] Inventory: receipt, barcode, transfer, adjustment"
req POST /api/v1/inventory/receipts "{\"productId\":${PROD_ID},\"warehouseId\":${WH1},\"quantity\":10,\"notes\":\"smoke receipt\"}"; expect 201 "receipt 10 @WH1"
expect_balance "${WH1}" 10 "balance after receipt"

req GET "/api/v1/products/barcode/${BARCODE}"; expect 200 "barcode lookup"
LOOKED_UP="$(jget id)"
[ "${LOOKED_UP}" = "${PROD_ID}" ] && ok "barcode resolves to created product" || bad "barcode resolved to '${LOOKED_UP}', expected ${PROD_ID}"

req POST /api/v1/inventory/transfers "{\"productId\":${PROD_ID},\"sourceWarehouseId\":${WH1},\"destinationWarehouseId\":${WH2},\"quantity\":3,\"notes\":\"smoke transfer\"}"; expect 201 "transfer 3 WH1→WH2"
expect_balance "${WH1}" 7 "source balance after transfer"
expect_balance "${WH2}" 3 "destination balance after transfer"

req POST /api/v1/inventory/adjustments "{\"productId\":${PROD_ID},\"warehouseId\":${WH2},\"direction\":\"IN\",\"quantity\":2,\"reason\":\"smoke adjustment\"}"; expect 201 "adjustment IN 2 @WH2"
expect_balance "${WH2}" 5 "balance after adjustment"

say ""
say "[4] RFID simulation never changes stock"
req GET "/api/v1/stock-transactions?productId=${PROD_ID}"; expect 200 "read stock history (before RFID)"
TXN_BEFORE="$(jget totalElements)"

EPC="A1B2${SUFFIX}"
req POST /api/v1/rfid/tags "{\"epcCode\":\"${EPC}\",\"productId\":${PROD_ID}}"; expect 201 "register RFID tag"
req POST /api/v1/rfid/reads "{\"epcCode\":\"${EPC}\",\"eventReference\":\"smoke-${SUFFIX}\",\"simulate\":true}"; expect 201 "simulated RFID read"
READ_STATUS="$(jget status)"
[ "${READ_STATUS}" = "RESOLVED" ] && ok "read resolved to tag (status ${READ_STATUS})" || bad "read status '${READ_STATUS}', expected RESOLVED"
req POST /api/v1/rfid/reads "{\"epcCode\":\"${EPC}\",\"eventReference\":\"smoke-${SUFFIX}\",\"simulate\":true}"; expect 409 "duplicate read reference rejected"

req GET "/api/v1/stock-transactions?productId=${PROD_ID}"; expect 200 "read stock history (after RFID)"
TXN_AFTER="$(jget totalElements)"
[ "${TXN_BEFORE}" = "${TXN_AFTER}" ] && ok "RFID reads created no stock movements (${TXN_AFTER} before/after)" || bad "stock movements changed: ${TXN_BEFORE} → ${TXN_AFTER}"

say ""
say "[5] Purchase order → receipt"
req POST /api/v1/purchase-orders "{\"poNumber\":\"SMK-PO-${SUFFIX}\",\"supplierId\":${SUP_ID},\"warehouseId\":${WH1},\"orderDate\":\"${TODAY}\",\"items\":[{\"productId\":${PROD_ID},\"orderedQuantity\":5,\"unitCost\":9.50}]}"; expect 201 "create purchase order"
PO_ID="$(jget id)"
PO_ITEM="$(jget items.0.id)"
req POST "/api/v1/purchase-orders/${PO_ID}/submit" "{}"; expect 200 "submit purchase order"
req POST "/api/v1/purchase-orders/${PO_ID}/receipts" "{\"lines\":[{\"poItemId\":${PO_ITEM},\"quantity\":5}]}"; expect 201 "receive 5 against PO"
expect_balance "${WH1}" 12 "balance after PO receipt"

say ""
say "[6] Sales order → fulfillment (+ insufficient-stock rejection)"
req POST /api/v1/sales-orders "{\"soNumber\":\"SMK-SO-${SUFFIX}\",\"warehouseId\":${WH1},\"customerName\":\"Smoke Customer\",\"orderDate\":\"${TODAY}\",\"items\":[{\"productId\":${PROD_ID},\"orderedQuantity\":4,\"unitPrice\":14.00}]}"; expect 201 "create sales order"
SO_ID="$(jget id)"
SO_ITEM="$(jget items.0.id)"
req POST "/api/v1/sales-orders/${SO_ID}/confirm" "{}"; expect 200 "confirm sales order"
req POST "/api/v1/sales-orders/${SO_ID}/fulfillments" "{\"lines\":[{\"soItemId\":${SO_ITEM},\"quantity\":4}]}"; expect 201 "fulfill 4"
expect_balance "${WH1}" 8 "balance after fulfillment"

req POST /api/v1/sales-orders "{\"soNumber\":\"SMK-SO2-${SUFFIX}\",\"warehouseId\":${WH2},\"customerName\":\"Smoke Customer 2\",\"orderDate\":\"${TODAY}\",\"items\":[{\"productId\":${PROD_ID},\"orderedQuantity\":9999,\"unitPrice\":14.00}]}"; expect 201 "create oversized sales order"
SO2_ID="$(jget id)"
SO2_ITEM="$(jget items.0.id)"
req POST "/api/v1/sales-orders/${SO2_ID}/confirm" "{}"; expect 200 "confirm oversized sales order"
req POST "/api/v1/sales-orders/${SO2_ID}/fulfillments" "{\"lines\":[{\"soItemId\":${SO2_ITEM},\"quantity\":9999}]}"; expect 409 "insufficient stock rejected"
expect_balance "${WH2}" 5 "balance untouched after rejected fulfillment"

say ""
say "[7] Dashboard KPIs"
req GET "/api/v1/dashboard/summary?warehouseId=${WH1}"; expect 200 "dashboard summary (WH1)"
UNITS="$(jget totalInventoryUnits)"
if [ -n "${UNITS}" ] && num_eq "${UNITS}" 8; then
  ok "dashboard units for WH1 match ledger (${UNITS})"
else
  bad "dashboard units for WH1 expected 8, got '${UNITS}'"
fi

say ""
say "[8] Frontend edge (only when BASE_URL serves the Angular app)"
ROOT_CT="$(curl -s -o /dev/null -w '%{content_type}' "${BASE_URL}/" 2>/dev/null || true)"
if [[ "${ROOT_CT}" == text/html* ]]; then
  req GET /; expect 200 "frontend index served"
  DEEP="$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/dashboard" 2>/dev/null || true)"
  [ "${DEEP}" = "200" ] && ok "SPA deep link /dashboard returns 200 (nginx fallback)" || bad "SPA deep link /dashboard returned ${DEEP}"
else
  say "  --   BASE_URL serves the API directly (no HTML at /) — SPA checks skipped"
fi

say ""
say "============================================================================="
say "Smoke result: ${PASS} passed, ${FAIL} failed"
if [ "${FAIL}" -gt 0 ]; then
  say "RESULT: FAIL"
  exit 1
fi
say "RESULT: PASS"
