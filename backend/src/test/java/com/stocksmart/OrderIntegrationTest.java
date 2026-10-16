package com.stocksmart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksmart.dto.FulfillmentLineRequest;
import com.stocksmart.dto.FulfillmentRequest;
import com.stocksmart.entity.Role;
import com.stocksmart.entity.RoleName;
import com.stocksmart.entity.User;
import com.stocksmart.exception.InsufficientStockException;
import com.stocksmart.repository.CategoryRepository;
import com.stocksmart.repository.InventoryRepository;
import com.stocksmart.repository.ProductRepository;
import com.stocksmart.repository.PurchaseOrderRepository;
import com.stocksmart.repository.PurchaseReceiptRepository;
import com.stocksmart.repository.RfidReadEventRepository;
import com.stocksmart.repository.RfidTagRepository;
import com.stocksmart.repository.RoleRepository;
import com.stocksmart.repository.SalesFulfillmentRepository;
import com.stocksmart.repository.SalesOrderRepository;
import com.stocksmart.repository.StockTransactionRepository;
import com.stocksmart.repository.SupplierRepository;
import com.stocksmart.repository.UserRepository;
import com.stocksmart.repository.WarehouseRepository;
import com.stocksmart.service.SalesOrderService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Day 4 order matrix: PO/SO lifecycles, partial receipts/fulfillments,
 * inventory integration, idempotency, rollback, concurrency, RBAC.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockTransactionRepository transactionRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PurchaseReceiptRepository purchaseReceiptRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private SalesFulfillmentRepository salesFulfillmentRepository;

    @Autowired
    private RfidTagRepository rfidTagRepository;

    @Autowired
    private RfidReadEventRepository rfidReadEventRepository;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;
    private String imToken;
    private String whToken;
    private String procToken;
    private String salesToken;
    private String mgmtToken;

    private long catId;
    private long prodA;
    private long prodB;
    private long whId;
    private long supId;

    @BeforeEach
    void setUp() throws Exception {
        rfidReadEventRepository.deleteAll();
        rfidTagRepository.deleteAll();
        purchaseReceiptRepository.deleteAll();
        salesFulfillmentRepository.deleteAll();
        purchaseOrderRepository.deleteAll();
        salesOrderRepository.deleteAll();
        transactionRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        supplierRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role admin = roleRepository.save(new Role(RoleName.ADMIN, "admin"));
        Role im = roleRepository.save(new Role(RoleName.INVENTORY_MANAGER, "im"));
        Role wh = roleRepository.save(new Role(RoleName.WAREHOUSE_STAFF, "wh"));
        Role proc = roleRepository.save(new Role(RoleName.PROCUREMENT_USER, "proc"));
        Role sales = roleRepository.save(new Role(RoleName.SALES_USER, "sales"));
        Role mgmt = roleRepository.save(new Role(RoleName.MANAGEMENT, "mgmt"));

        userRepository.save(new User("admin", "admin@t.local",
                passwordEncoder.encode("Admin@123"), "Admin", admin));
        userRepository.save(new User("imuser", "im@t.local",
                passwordEncoder.encode("Admin@123"), "IM", im));
        userRepository.save(new User("whuser", "wh@t.local",
                passwordEncoder.encode("Admin@123"), "WH", wh));
        userRepository.save(new User("procuser", "proc@t.local",
                passwordEncoder.encode("Admin@123"), "Proc", proc));
        userRepository.save(new User("salesuser", "sales@t.local",
                passwordEncoder.encode("Admin@123"), "Sales", sales));
        userRepository.save(new User("mgmtuser", "mgmt@t.local",
                passwordEncoder.encode("Admin@123"), "Mgmt", mgmt));

        adminToken = login("admin");
        imToken = login("imuser");
        whToken = login("whuser");
        procToken = login("procuser");
        salesToken = login("salesuser");
        mgmtToken = login("mgmtuser");

        catId = createCategory("ELEC", "Electronics");
        prodA = createProduct("PHN-001", "Phone", catId);
        prodB = createProduct("TAB-001", "Tablet", catId);
        whId = createWarehouse("WH-01", "Central");
        supId = createSupplier("SUP-01", "Acme Supplies");
    }

    @Test
    void purchaseOrderFullLifecycleWithPartialReceipts() throws Exception {
        JsonNode po = createPo(procToken, "PO-1001", supId, whId,
                line(prodA, "100", "90.00"), line(prodB, "50", "200.00"));
        long poId = po.get("id").asLong();
        assertThat(po.get("status").asText()).isEqualTo("DRAFT");
        assertThat(bd(po, "orderTotal").compareTo(new BigDecimal("19000.00"))).isZero();

        submitPo(procToken, poId).andExpect(jsonPath("$.status").value("SUBMITTED"));

        // Receipt 1: partial on line A only.
        Map<Long, Long> itemIds = poItemIds(po);
        JsonNode r1 = receivePo(procToken, poId, null,
                receiptLine(itemIds.get(prodA), "60"));
        assertThat(r1.get("receiptNumber").asText()).isNotBlank();
        JsonNode after1 = getPo(procToken, poId);
        assertThat(after1.get("status").asText()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(balanceOf(prodA, whId).compareTo(new BigDecimal("60"))).isZero();

        // Receipt 2: remainder of A + all of B → RECEIVED.
        receivePo(procToken, poId, null,
                receiptLine(itemIds.get(prodA), "40"),
                receiptLine(itemIds.get(prodB), "50"));
        JsonNode after2 = getPo(procToken, poId);
        assertThat(after2.get("status").asText()).isEqualTo("RECEIVED");
        assertThat(balanceOf(prodA, whId).compareTo(new BigDecimal("100"))).isZero();
        assertThat(balanceOf(prodB, whId).compareTo(new BigDecimal("50"))).isZero();

        // Receipt history + RECEIPT stock transactions traceable.
        mockMvc.perform(get("/api/v1/purchase-orders/" + poId + "/receipts")
                        .header("Authorization", "Bearer " + procToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/v1/stock-transactions?productId=" + prodA)
                        .header("Authorization", "Bearer " + procToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Fully received order rejects further receipts.
        postRaw("/api/v1/purchase-orders/" + poId + "/receipts", procToken,
                "{\"lines\":[" + receiptLine(itemIds.get(prodA), "1") + "]}")
                .andExpect(status().isConflict());
    }

    @Test
    void purchaseOrderValidation() throws Exception {
        // Bad supplier / warehouse / product → 404.
        postRaw("/api/v1/purchase-orders", procToken,
                poJson("PO-X", 999999L, whId, line(prodA, "10", "5.00")))
                .andExpect(status().isNotFound());
        postRaw("/api/v1/purchase-orders", procToken,
                poJson("PO-X", supId, 999999L, line(prodA, "10", "5.00")))
                .andExpect(status().isNotFound());
        postRaw("/api/v1/purchase-orders", procToken,
                poJson("PO-X", supId, whId, line(999999L, "10", "5.00")))
                .andExpect(status().isNotFound());
        // Zero quantity / negative cost → 400.
        postRaw("/api/v1/purchase-orders", procToken,
                poJson("PO-X", supId, whId, line(prodA, "0", "5.00")))
                .andExpect(status().isBadRequest());

        long poId = createPo(procToken, "PO-2001", supId, whId,
                line(prodA, "10", "5.00")).get("id").asLong();

        // Duplicate PO number → 409.
        postRaw("/api/v1/purchase-orders", procToken,
                poJson("PO-2001", supId, whId, line(prodA, "5", "5.00")))
                .andExpect(status().isConflict());

        // Receipt against DRAFT → 409; update works on DRAFT.
        postRaw("/api/v1/purchase-orders/" + poId + "/receipts", procToken,
                "{\"lines\":[" + receiptLine(poItemIds(getPo(procToken, poId)).get(prodA), "5") + "]}")
                .andExpect(status().isConflict());
        putRaw("/api/v1/purchase-orders/" + poId, procToken,
                poJson("PO-2001", supId, whId, line(prodA, "20", "6.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].orderedQuantity").value(20));

        // Empty order cannot be submitted.
        long emptyId = createPo(procToken, "PO-EMPTY", supId, whId).get("id").asLong();
        postRaw("/api/v1/purchase-orders/" + emptyId + "/submit", procToken, "{}")
                .andExpect(status().isConflict());

        submitPo(procToken, poId);
        // Submitted order cannot be edited.
        putRaw("/api/v1/purchase-orders/" + poId, procToken,
                poJson("PO-2001", supId, whId, line(prodA, "30", "6.00")))
                .andExpect(status().isConflict());

        // Over-receipt rejected without side effects.
        long txnsBefore = transactionRepository.count();
        long itemId = poItemIds(getPo(procToken, poId)).get(prodA);
        postRaw("/api/v1/purchase-orders/" + poId + "/receipts", procToken,
                "{\"lines\":[" + receiptLine(itemId, "21") + "]}")
                .andExpect(status().isConflict());
        assertThat(balanceOf(prodA, whId)).isNull();
        assertThat(transactionRepository.count()).isEqualTo(txnsBefore);

        // Cancel keeps eligibility closed; received stock stays after cancel-with-receipt.
        receivePo(procToken, poId, null, receiptLine(itemId, "5"));
        postRaw("/api/v1/purchase-orders/" + poId + "/cancel", procToken, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(balanceOf(prodA, whId).compareTo(new BigDecimal("5"))).isZero();
        postRaw("/api/v1/purchase-orders/" + poId + "/receipts", procToken,
                "{\"lines\":[" + receiptLine(itemId, "5") + "]}")
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateReceiptReferenceIsRejectedWithoutDoubleStock() throws Exception {
        long poId = createPo(procToken, "PO-3001", supId, whId,
                line(prodA, "100", "10.00")).get("id").asLong();
        submitPo(procToken, poId);
        long itemId = poItemIds(getPo(procToken, poId)).get(prodA);

        receivePo(procToken, poId, "POR-IDEMPOTENT-1", receiptLine(itemId, "30"));
        postRaw("/api/v1/purchase-orders/" + poId + "/receipts", procToken,
                "{\"reference\":\"POR-IDEMPOTENT-1\",\"lines\":["
                        + receiptLine(itemId, "30") + "]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already processed")));
        assertThat(balanceOf(prodA, whId).compareTo(new BigDecimal("30"))).isZero();
        assertThat(purchaseReceiptRepository.count()).isEqualTo(1);
    }

    @Test
    void salesOrderFullLifecycleWithPartialFulfillment() throws Exception {
        // Stock the warehouse first (via PO receipt = real procurement path).
        long poId = createPo(procToken, "PO-4001", supId, whId,
                line(prodA, "100", "10.00"), line(prodB, "40", "20.00")).get("id").asLong();
        submitPo(procToken, poId);
        Map<Long, Long> poLines = poItemIds(getPo(procToken, poId));
        receivePo(procToken, poId, null,
                receiptLine(poLines.get(prodA), "100"), receiptLine(poLines.get(prodB), "40"));

        JsonNode so = createSo(salesToken, "SO-1001", whId, "Retail Mart",
                sline(prodA, "60", "150.00"), sline(prodB, "40", "250.00"));
        long soId = so.get("id").asLong();
        assertThat(bd(so, "orderTotal").compareTo(new BigDecimal("19000.00"))).isZero();

        confirmSo(salesToken, soId).andExpect(jsonPath("$.status").value("CONFIRMED"));

        Map<Long, Long> soLines = soItemIds(so);
        JsonNode f1 = fulfillSo(salesToken, soId, null,
                fulfillLine(soLines.get(prodA), "25"));
        assertThat(f1.get("fulfillmentNumber").asText()).isNotBlank();
        JsonNode after1 = getSo(salesToken, soId);
        assertThat(after1.get("status").asText()).isEqualTo("PARTIALLY_FULFILLED");
        assertThat(balanceOf(prodA, whId).compareTo(new BigDecimal("75"))).isZero();

        fulfillSo(salesToken, soId, null,
                fulfillLine(soLines.get(prodA), "35"), fulfillLine(soLines.get(prodB), "40"));
        assertThat(getSo(salesToken, soId).get("status").asText()).isEqualTo("FULFILLED");
        assertThat(balanceOf(prodA, whId).compareTo(new BigDecimal("40"))).isZero();
        assertThat(balanceOf(prodB, whId)).isZero();

        // ISSUE movements recorded; fulfillment history present.
        mockMvc.perform(get("/api/v1/stock-transactions?type=ISSUE")
                        .header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/v1/sales-orders/" + soId + "/fulfillments")
                        .header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Fulfilled order rejects more fulfillment; cannot cancel.
        postRaw("/api/v1/sales-orders/" + soId + "/fulfillments", salesToken,
                "{\"lines\":[" + fulfillLine(soLines.get(prodA), "1") + "]}")
                .andExpect(status().isConflict());
        postRaw("/api/v1/sales-orders/" + soId + "/cancel", salesToken, "{}")
                .andExpect(status().isConflict());
    }

    @Test
    void salesOrderValidationAndRollback() throws Exception {
        stockViaReceipt(prodA, "50");

        // Bad warehouse / product → 404; blank customer → 400.
        postRaw("/api/v1/sales-orders", salesToken,
                soJson("SO-X", 999999L, "Shop", sline(prodA, "5", "10.00")))
                .andExpect(status().isNotFound());
        postRaw("/api/v1/sales-orders", salesToken,
                soJson("SO-X", whId, "Shop", sline(999999L, "5", "10.00")))
                .andExpect(status().isNotFound());
        postRaw("/api/v1/sales-orders", salesToken,
                soJson("SO-X", whId, "", sline(prodA, "5", "10.00")))
                .andExpect(status().isBadRequest());

        long soId = createSo(salesToken, "SO-2001", whId, "Shop",
                sline(prodA, "60", "150.00")).get("id").asLong();
        // Duplicate SO number → 409.
        postRaw("/api/v1/sales-orders", salesToken,
                soJson("SO-2001", whId, "Shop", sline(prodA, "5", "10.00")))
                .andExpect(status().isConflict());
        // Fulfill draft → 409; confirm empty → 409.
        long lineId = soItemIds(getSo(salesToken, soId)).get(prodA);
        postRaw("/api/v1/sales-orders/" + soId + "/fulfillments", salesToken,
                "{\"lines\":[" + fulfillLine(lineId, "5") + "]}")
                .andExpect(status().isConflict());
        long emptyId = createSo(salesToken, "SO-EMPTY", whId, "Shop").get("id").asLong();
        postRaw("/api/v1/sales-orders/" + emptyId + "/confirm", salesToken, "{}")
                .andExpect(status().isConflict());

        confirmSo(salesToken, soId);
        // Over-fulfill → 409, zero side effects.
        long txnsBefore = transactionRepository.count();
        postRaw("/api/v1/sales-orders/" + soId + "/fulfillments", salesToken,
                "{\"lines\":[" + fulfillLine(lineId, "61") + "]}")
                .andExpect(status().isConflict());
        assertThat(fulfilledOf(soId, prodA)).isZero();
        assertThat(transactionRepository.count()).isEqualTo(txnsBefore);
        assertThat(salesFulfillmentRepository.count()).isZero();

        // Insufficient stock → 409, zero side effects (stock 50 < 60).
        postRaw("/api/v1/sales-orders/" + soId + "/fulfillments", salesToken,
                "{\"lines\":[" + fulfillLine(lineId, "60") + "]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Insufficient stock")));
        assertThat(balanceOf(prodA, whId).compareTo(new BigDecimal("50"))).isZero();
        assertThat(fulfilledOf(soId, prodA)).isZero();
        assertThat(transactionRepository.count()).isEqualTo(txnsBefore);

        // Partial then cancel: fulfilled stock stays issued, order closed.
        fulfillSo(salesToken, soId, null, fulfillLine(lineId, "20"));
        postRaw("/api/v1/sales-orders/" + soId + "/cancel", salesToken, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(balanceOf(prodA, whId).compareTo(new BigDecimal("30"))).isZero();
        postRaw("/api/v1/sales-orders/" + soId + "/fulfillments", salesToken,
                "{\"lines\":[" + fulfillLine(lineId, "5") + "]}")
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateFulfillmentReferenceIsRejectedWithoutDoubleIssue() throws Exception {
        stockViaReceipt(prodA, "100");
        long soId = createSo(salesToken, "SO-3001", whId, "Shop",
                sline(prodA, "80", "150.00")).get("id").asLong();
        confirmSo(salesToken, soId);
        long lineId = soItemIds(getSo(salesToken, soId)).get(prodA);

        fulfillSo(salesToken, soId, "SOF-IDEMPOTENT-1", fulfillLine(lineId, "30"));
        postRaw("/api/v1/sales-orders/" + soId + "/fulfillments", salesToken,
                "{\"reference\":\"SOF-IDEMPOTENT-1\",\"lines\":["
                        + fulfillLine(lineId, "30") + "]}")
                .andExpect(status().isConflict());
        assertThat(balanceOf(prodA, whId).compareTo(new BigDecimal("70"))).isZero();
        assertThat(salesFulfillmentRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentFulfillmentsCannotDoubleIssue() throws Exception {
        stockViaReceipt(prodA, "30");
        long soId = createSo(salesToken, "SO-4001", whId, "Shop",
                sline(prodA, "50", "150.00")).get("id").asLong();
        confirmSo(salesToken, soId);
        long lineId = soItemIds(getSo(salesToken, soId)).get(prodA);

        // Two threads × 20 from stock 30: exactly one wins, balance 10.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    if (!start.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Start signal timeout");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("salesuser", null,
                                List.of(new SimpleGrantedAuthority("ROLE_SALES_USER"))));
                try {
                    FulfillmentLineRequest line = new FulfillmentLineRequest();
                    line.setSoItemId(lineId);
                    line.setQuantity(new BigDecimal("20"));
                    FulfillmentRequest request = new FulfillmentRequest();
                    request.setLines(List.of(line));
                    salesOrderService.fulfill(soId, request);
                    succeeded.incrementAndGet();
                } catch (InsufficientStockException e) {
                    rejected.incrementAndGet();
                } finally {
                    SecurityContextHolder.clearContext();
                }
                return null;
            }));
        }
        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
        assertThat(balanceOf(prodA, whId).compareTo(new BigDecimal("10"))).isZero();
        assertThat(fulfilledOf(soId, prodA).compareTo(new BigDecimal("20"))).isZero();
    }

    @Test
    void rbacIsEnforcedOnOrders() throws Exception {
        long poId = createPo(procToken, "PO-9001", supId, whId,
                line(prodA, "10", "5.00")).get("id").asLong();
        submitPo(procToken, poId);
        long soId = createSo(salesToken, "SO-9001", whId, "Shop",
                sline(prodA, "5", "10.00")).get("id").asLong();

        // No token → 401.
        mockMvc.perform(get("/api/v1/purchase-orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/sales-orders")).andExpect(status().isUnauthorized());

        // SALES: no purchase-order access at all; full sales-order writes.
        mockMvc.perform(get("/api/v1/purchase-orders")
                        .header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isForbidden());
        postRaw("/api/v1/purchase-orders", salesToken,
                poJson("PO-S", supId, whId, line(prodA, "5", "5.00")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/sales-orders")
                        .header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isOk());

        // PROCUREMENT: no sales-order access; purchase writes OK.
        mockMvc.perform(get("/api/v1/sales-orders")
                        .header("Authorization", "Bearer " + procToken))
                .andExpect(status().isForbidden());
        postRaw("/api/v1/sales-orders", procToken,
                soJson("SO-P", whId, "Shop", sline(prodA, "5", "10.00")))
                .andExpect(status().isForbidden());

        // MANAGEMENT: reads OK, all writes forbidden.
        mockMvc.perform(get("/api/v1/purchase-orders")
                        .header("Authorization", "Bearer " + mgmtToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sales-orders")
                        .header("Authorization", "Bearer " + mgmtToken))
                .andExpect(status().isOk());
        postRaw("/api/v1/purchase-orders/" + poId + "/submit", mgmtToken, "{}")
                .andExpect(status().isForbidden());
        postRaw("/api/v1/sales-orders/" + soId + "/confirm", mgmtToken, "{}")
                .andExpect(status().isForbidden());

        // WAREHOUSE_STAFF: reads OK; receipts/fulfillments OK; order lifecycle forbidden.
        long itemId = poItemIds(getPo(procToken, poId)).get(prodA);
        postRaw("/api/v1/purchase-orders/" + poId + "/receipts", whToken,
                "{\"lines\":[" + receiptLine(itemId, "5") + "]}")
                .andExpect(status().isCreated());
        postRaw("/api/v1/purchase-orders", whToken,
                poJson("PO-W", supId, whId, line(prodA, "5", "5.00")))
                .andExpect(status().isForbidden());

        // INVENTORY_MANAGER: reads OK; receipts/fulfillments OK; lifecycle forbidden.
        confirmSo(salesToken, soId);
        long soLine = soItemIds(getSo(salesToken, soId)).get(prodA);
        postRaw("/api/v1/sales-orders/" + soId + "/fulfillments", imToken,
                "{\"lines\":[" + fulfillLine(soLine, "5") + "]}")
                .andExpect(status().isCreated());
        postRaw("/api/v1/purchase-orders/" + poId + "/cancel", imToken, "{}")
                .andExpect(status().isForbidden());
    }

    // ---- Helpers ----

    private BigDecimal balanceOf(long productId, long warehouseId) {
        return inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .map(i -> i.getQuantityOnHand()).orElse(null);
    }

    private BigDecimal fulfilledOf(long soId, long productId) throws Exception {
        JsonNode so = getSo(adminToken, soId);
        for (JsonNode item : so.get("items")) {
            if (item.get("productId").asLong() == productId) {
                return new BigDecimal(item.get("fulfilledQuantity").asText());
            }
        }
        throw new IllegalStateException("SO line not found");
    }

    private static BigDecimal bd(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    private Map<Long, Long> poItemIds(JsonNode po) {
        Map<Long, Long> ids = new HashMap<>();
        for (JsonNode item : po.get("items")) {
            ids.put(item.get("productId").asLong(), item.get("id").asLong());
        }
        return ids;
    }

    private Map<Long, Long> soItemIds(JsonNode so) {
        Map<Long, Long> ids = new HashMap<>();
        for (JsonNode item : so.get("items")) {
            ids.put(item.get("productId").asLong(), item.get("id").asLong());
        }
        return ids;
    }

    private void stockViaReceipt(long productId, String qty) throws Exception {
        mockMvc.perform(post("/api/v1/inventory/receipts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"warehouseId\":" + whId
                                + ",\"quantity\":" + qty + "}"))
                .andExpect(status().isCreated());
    }

    private JsonNode createPo(String token, String number, long supplierId, long warehouseId,
                              String... lines) throws Exception {
        MvcResult result = postRaw("/api/v1/purchase-orders", token,
                poJson(number, supplierId, warehouseId, lines))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions submitPo(String token, long id) throws Exception {
        return postRaw("/api/v1/purchase-orders/" + id + "/submit", token, "{}")
                .andExpect(status().isOk());
    }

    private JsonNode receivePo(String token, long id, String reference, String... lines)
            throws Exception {
        StringBuilder json = new StringBuilder("{\"lines\":[");
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(lines[i]);
        }
        json.append("]}");
        String body = reference == null ? json.toString()
                : "{\"reference\":\"" + reference + "\",\"lines\":[" + String.join(",", lines) + "]}";
        MvcResult result = postRaw("/api/v1/purchase-orders/" + id + "/receipts", token, body)
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getPo(String token, long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/purchase-orders/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createSo(String token, String number, long warehouseId, String customer,
                              String... lines) throws Exception {
        MvcResult result = postRaw("/api/v1/sales-orders", token,
                soJson(number, warehouseId, customer, lines))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions confirmSo(String token, long id) throws Exception {
        return postRaw("/api/v1/sales-orders/" + id + "/confirm", token, "{}")
                .andExpect(status().isOk());
    }

    private JsonNode fulfillSo(String token, long id, String reference, String... lines)
            throws Exception {
        String body = reference == null
                ? "{\"lines\":[" + String.join(",", lines) + "]}"
                : "{\"reference\":\"" + reference + "\",\"lines\":["
                        + String.join(",", lines) + "]}";
        MvcResult result = postRaw("/api/v1/sales-orders/" + id + "/fulfillments", token, body)
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getSo(String token, long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sales-orders/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions postRaw(String url, String token, String json) throws Exception {
        var builder = post(url).contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder);
    }

    private ResultActions putRaw(String url, String token, String json) throws Exception {
        var builder = put(url).contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder);
    }

    private static String line(long productId, String qty, String cost) {
        return "{\"productId\":" + productId + ",\"orderedQuantity\":" + qty
                + ",\"unitCost\":" + cost + "}";
    }

    private static String sline(long productId, String qty, String price) {
        return "{\"productId\":" + productId + ",\"orderedQuantity\":" + qty
                + ",\"unitPrice\":" + price + "}";
    }

    private static String receiptLine(long poItemId, String qty) {
        return "{\"poItemId\":" + poItemId + ",\"quantity\":" + qty + "}";
    }

    private static String fulfillLine(long soItemId, String qty) {
        return "{\"soItemId\":" + soItemId + ",\"quantity\":" + qty + "}";
    }

    private static String poJson(String number, long supplierId, long warehouseId, String... lines) {
        return "{\"poNumber\":\"" + number + "\",\"supplierId\":" + supplierId
                + ",\"warehouseId\":" + warehouseId + ",\"orderDate\":\"2026-10-14\""
                + ",\"items\":[" + String.join(",", lines) + "]}";
    }

    private static String soJson(String number, long warehouseId, String customer, String... lines) {
        return "{\"soNumber\":\"" + number + "\",\"warehouseId\":" + warehouseId
                + ",\"customerName\":\"" + customer + "\",\"orderDate\":\"2026-10-14\""
                + ",\"items\":[" + String.join(",", lines) + "]}";
    }

    private long createCategory(String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createProduct(String sku, String name, long categoryId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"" + name + "\","
                                + "\"categoryId\":" + categoryId + ",\"unit\":\"PCS\","
                                + "\"costPrice\":10.00,\"sellingPrice\":15.00}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createWarehouse(String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createSupplier(String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }
}
