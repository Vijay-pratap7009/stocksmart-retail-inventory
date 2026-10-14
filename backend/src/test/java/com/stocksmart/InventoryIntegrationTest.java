package com.stocksmart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksmart.dto.AdjustmentDirection;
import com.stocksmart.dto.AdjustmentRequest;
import com.stocksmart.entity.Role;
import com.stocksmart.entity.RoleName;
import com.stocksmart.entity.User;
import com.stocksmart.exception.InsufficientStockException;
import com.stocksmart.repository.CategoryRepository;
import com.stocksmart.repository.InventoryRepository;
import com.stocksmart.repository.ProductRepository;
import com.stocksmart.repository.RoleRepository;
import com.stocksmart.repository.StockTransactionRepository;
import com.stocksmart.repository.UserRepository;
import com.stocksmart.repository.WarehouseRepository;
import com.stocksmart.service.InventoryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

/**
 * Day 3 inventory matrix: receipts, adjustments, transfers, paired legs,
 * atomicity, history search, delete guards, RBAC, concurrent withdrawals.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InventoryIntegrationTest {

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
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockTransactionRepository transactionRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;
    private String imToken;
    private String whToken;
    private String procToken;
    private String salesToken;
    private String mgmtToken;

    @BeforeEach
    void setUp() throws Exception {
        transactionRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
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
    }

    @Test
    void receiptCreatesInventoryAndSumsRepeatedReceipts() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long prod = createProduct("PHN-001", "Phone", cat);
        long wh = createWarehouse("WH-01", "Central");

        // First receipt creates the row as OPENING_BALANCE.
        JsonNode first = postReceipt(adminToken, prod, wh, "50");
        assertThat(first.get("type").asText()).isEqualTo("OPENING_BALANCE");
        assertThat(bd(first, "quantityBefore")).isZero();
        assertThat(bd(first, "quantityAfter").compareTo(new BigDecimal("50"))).isZero();

        // Second receipt adds to the SAME row (no duplicates).
        JsonNode second = postReceipt(adminToken, prod, wh, "25");
        assertThat(second.get("type").asText()).isEqualTo("RECEIPT");
        assertThat(bd(second, "quantityBefore").compareTo(new BigDecimal("50"))).isZero();
        assertThat(bd(second, "quantityAfter").compareTo(new BigDecimal("75"))).isZero();

        MvcResult list = mockMvc.perform(get("/api/v1/inventory?productId=" + prod)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn();
        JsonNode row = objectMapper.readTree(list.getResponse().getContentAsString())
                .get("content").get(0);
        assertThat(row.get("sku").asText()).isEqualTo("PHN-001");
        assertThat(new BigDecimal(row.get("quantityOnHand").asText())
                .compareTo(new BigDecimal("75"))).isZero();
        assertThat(inventoryRepository.count()).isEqualTo(1);
    }

    @Test
    void receiptValidation() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long prod = createProduct("PHN-001", "Phone", cat);
        long wh = createWarehouse("WH-01", "Central");

        // Unknown product / warehouse → 404, no rows created.
        postRaw("/api/v1/inventory/receipts", adminToken,
                receiptJson(999999L, wh, "10")).andExpect(status().isNotFound());
        postRaw("/api/v1/inventory/receipts", adminToken,
                receiptJson(prod, 999999L, "10")).andExpect(status().isNotFound());
        // Zero / negative quantity → 400.
        postRaw("/api/v1/inventory/receipts", adminToken,
                receiptJson(prod, wh, "0")).andExpect(status().isBadRequest());
        postRaw("/api/v1/inventory/receipts", adminToken,
                receiptJson(prod, wh, "-5")).andExpect(status().isBadRequest());
        assertThat(inventoryRepository.count()).isZero();
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void adjustmentInAndOutWithAudit() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long prod = createProduct("PHN-001", "Phone", cat);
        long wh = createWarehouse("WH-01", "Central");
        postReceipt(adminToken, prod, wh, "100");

        JsonNode out = postAdjust(adminToken, prod, wh, "OUT", "30", "Cycle count correction");
        assertThat(out.get("type").asText()).isEqualTo("ADJUSTMENT_OUT");
        assertThat(bd(out, "quantityBefore").compareTo(new BigDecimal("100"))).isZero();
        assertThat(bd(out, "quantityAfter").compareTo(new BigDecimal("70"))).isZero();
        assertThat(out.get("reason").asText()).isEqualTo("Cycle count correction");
        assertThat(out.get("performedByUsername").asText()).isEqualTo("admin");

        JsonNode in = postAdjust(adminToken, prod, wh, "IN", "10", "Found stock");
        assertThat(in.get("type").asText()).isEqualTo("ADJUSTMENT_IN");
        assertThat(bd(in, "quantityAfter").compareTo(new BigDecimal("80"))).isZero();

        // Missing reason → 400, balance untouched.
        postRaw("/api/v1/inventory/adjustments", adminToken,
                "{\"productId\":" + prod + ",\"warehouseId\":" + wh
                        + ",\"direction\":\"IN\",\"quantity\":5,\"reason\":\"\"}")
                .andExpect(status().isBadRequest());
        assertThat(balanceOf(prod, wh).compareTo(new BigDecimal("80"))).isZero();
    }

    @Test
    void adjustmentToNegativeStockIsRejectedWithoutSideEffects() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long prod = createProduct("PHN-001", "Phone", cat);
        long wh = createWarehouse("WH-01", "Central");
        postReceipt(adminToken, prod, wh, "10");
        long txnsBefore = transactionRepository.count();

        postRaw("/api/v1/inventory/adjustments", adminToken,
                "{\"productId\":" + prod + ",\"warehouseId\":" + wh
                        + ",\"direction\":\"OUT\",\"quantity\":11,\"reason\":\"Too much\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Insufficient stock")));

        assertThat(balanceOf(prod, wh).compareTo(new BigDecimal("10"))).isZero();
        assertThat(transactionRepository.count()).isEqualTo(txnsBefore);
    }

    @Test
    void adjustmentWithoutInventoryRecordIs404() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long prod = createProduct("PHN-001", "Phone", cat);
        long wh = createWarehouse("WH-01", "Central");

        postRaw("/api/v1/inventory/adjustments", adminToken,
                "{\"productId\":" + prod + ",\"warehouseId\":" + wh
                        + ",\"direction\":\"OUT\",\"quantity\":1,\"reason\":\"No stock yet\"}")
                .andExpect(status().isNotFound());
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void transferMovesStockAtomicallyWithPairedLegs() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long prod = createProduct("PHN-001", "Phone", cat);
        long src = createWarehouse("WH-01", "Central");
        long dst = createWarehouse("WH-02", "East");
        postReceipt(imToken, prod, src, "100");

        JsonNode transfer = postTransfer(imToken, prod, src, dst, "40", null);
        String reference = transfer.get("referenceNumber").asText();
        assertThat(reference).isNotBlank();
        JsonNode out = transfer.get("transferOut");
        JsonNode in = transfer.get("transferIn");
        assertThat(out.get("type").asText()).isEqualTo("TRANSFER_OUT");
        assertThat(in.get("type").asText()).isEqualTo("TRANSFER_IN");
        assertThat(out.get("referenceNumber").asText()).isEqualTo(reference);
        assertThat(in.get("referenceNumber").asText()).isEqualTo(reference);
        assertThat(bd(out, "quantityBefore").compareTo(new BigDecimal("100"))).isZero();
        assertThat(bd(out, "quantityAfter").compareTo(new BigDecimal("60"))).isZero();
        assertThat(bd(in, "quantityBefore")).isZero();
        assertThat(bd(in, "quantityAfter").compareTo(new BigDecimal("40"))).isZero();

        assertThat(balanceOf(prod, src).compareTo(new BigDecimal("60"))).isZero();
        assertThat(balanceOf(prod, dst).compareTo(new BigDecimal("40"))).isZero();

        // Both legs traceable from either perspective.
        mockMvc.perform(get("/api/v1/stock-transactions?reference=" + reference)
                        .header("Authorization", "Bearer " + imToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/inventory/product/" + prod)
                        .header("Authorization", "Bearer " + imToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/v1/inventory/warehouse/" + dst)
                        .header("Authorization", "Bearer " + imToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void transferRejectionsLeaveNoPartialState() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long prod = createProduct("PHN-001", "Phone", cat);
        long src = createWarehouse("WH-01", "Central");
        long dst = createWarehouse("WH-02", "East");
        postReceipt(adminToken, prod, src, "50");
        long txnsBefore = transactionRepository.count();

        // Same warehouse → 400.
        postRaw("/api/v1/inventory/transfers", adminToken,
                transferJson(prod, src, src, "10", null)).andExpect(status().isBadRequest());
        // Insufficient stock → 409.
        postRaw("/api/v1/inventory/transfers", adminToken,
                transferJson(prod, src, dst, "51", null)).andExpect(status().isConflict());
        // Unknown destination → 404.
        postRaw("/api/v1/inventory/transfers", adminToken,
                transferJson(prod, src, 999999L, "10", null)).andExpect(status().isNotFound());
        // Zero quantity → 400.
        postRaw("/api/v1/inventory/transfers", adminToken,
                transferJson(prod, src, dst, "0", null)).andExpect(status().isBadRequest());

        assertThat(balanceOf(prod, src).compareTo(new BigDecimal("50"))).isZero();
        assertThat(inventoryRepository.findByProductIdAndWarehouseId(prod, dst)).isEmpty();
        assertThat(transactionRepository.count()).isEqualTo(txnsBefore);
    }

    @Test
    void transactionHistorySearchAndRetrieval() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long prod = createProduct("PHN-001", "Phone", cat);
        long src = createWarehouse("WH-01", "Central");
        long dst = createWarehouse("WH-02", "East");
        postReceipt(adminToken, prod, src, "100");
        postAdjust(adminToken, prod, src, "OUT", "10", "Damaged");
        JsonNode transfer = postTransfer(adminToken, prod, src, dst, "20", "RESTOCK-EAST");
        long txnId = transfer.get("transferOut").get("id").asLong();

        // Filter by type.
        mockMvc.perform(get("/api/v1/stock-transactions?type=TRANSFER_OUT")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        // Filter by product + warehouse.
        mockMvc.perform(get("/api/v1/stock-transactions?productId=" + prod + "&warehouseId=" + dst)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        // Custom transfer reference is searchable.
        mockMvc.perform(get("/api/v1/stock-transactions?reference=RESTOCK-EAST")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        // Date range: wide-open past window finds all; future window finds none.
        mockMvc.perform(get("/api/v1/stock-transactions?from="
                                + Instant.now().minusSeconds(3600))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
        mockMvc.perform(get("/api/v1/stock-transactions?from="
                                + Instant.now().plusSeconds(3600))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Retrieve one record with full audit fields.
        mockMvc.perform(get("/api/v1/stock-transactions/" + txnId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.performedByUsername").value("admin"))
                .andExpect(jsonPath("$.referenceNumber").value("RESTOCK-EAST"));
        mockMvc.perform(get("/api/v1/stock-transactions/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void inventoryReadsAndMissingRecords() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long prod = createProduct("PHN-001", "Phone", cat);
        long wh = createWarehouse("WH-01", "Central");

        // Existing product/warehouse with no stock → empty lists, not 404.
        mockMvc.perform(get("/api/v1/inventory/product/" + prod)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/inventory/warehouse/" + wh)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/inventory/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/inventory/product/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/inventory/warehouse/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void referencedProductAndWarehouseCannotBeDeleted() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long prod = createProduct("PHN-001", "Phone", cat);
        long wh = createWarehouse("WH-01", "Central");
        postReceipt(adminToken, prod, wh, "10");

        mockMvc.perform(delete("/api/v1/products/" + prod)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/warehouses/" + wh)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void rbacIsEnforcedOnInventory() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long prod = createProduct("PHN-001", "Phone", cat);
        long src = createWarehouse("WH-01", "Central");
        long dst = createWarehouse("WH-02", "East");
        postReceipt(adminToken, prod, src, "100");

        // No token → 401 on reads and writes.
        mockMvc.perform(get("/api/v1/inventory")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/stock-transactions")).andExpect(status().isUnauthorized());
        postRaw("/api/v1/inventory/receipts", null, receiptJson(prod, src, "5"))
                .andExpect(status().isUnauthorized());

        // SALES: reads OK, writes forbidden.
        mockMvc.perform(get("/api/v1/inventory")
                        .header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/stock-transactions")
                        .header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isOk());
        postRaw("/api/v1/inventory/receipts", salesToken, receiptJson(prod, src, "5"))
                .andExpect(status().isForbidden());
        postRaw("/api/v1/inventory/transfers", salesToken,
                transferJson(prod, src, dst, "5", null)).andExpect(status().isForbidden());

        // PROCUREMENT: reads OK, writes forbidden.
        mockMvc.perform(get("/api/v1/inventory")
                        .header("Authorization", "Bearer " + procToken))
                .andExpect(status().isOk());
        postRaw("/api/v1/inventory/receipts", procToken, receiptJson(prod, src, "5"))
                .andExpect(status().isForbidden());

        // MANAGEMENT: reads OK, writes forbidden.
        mockMvc.perform(get("/api/v1/stock-transactions")
                        .header("Authorization", "Bearer " + mgmtToken))
                .andExpect(status().isOk());
        postRaw("/api/v1/inventory/adjustments", mgmtToken,
                "{\"productId\":" + prod + ",\"warehouseId\":" + src
                        + ",\"direction\":\"IN\",\"quantity\":5,\"reason\":\"Mgmt\"}")
                .andExpect(status().isForbidden());

        // WAREHOUSE_STAFF: authorized warehouse operations succeed.
        postRaw("/api/v1/inventory/receipts", whToken, receiptJson(prod, src, "5"))
                .andExpect(status().isCreated());
        postRaw("/api/v1/inventory/adjustments", whToken,
                "{\"productId\":" + prod + ",\"warehouseId\":" + src
                        + ",\"direction\":\"OUT\",\"quantity\":5,\"reason\":\"WH count\"}")
                .andExpect(status().isCreated());
        postRaw("/api/v1/inventory/transfers", whToken,
                transferJson(prod, src, dst, "5", null)).andExpect(status().isCreated());

        // INVENTORY_MANAGER: full stock operations.
        postRaw("/api/v1/inventory/adjustments", imToken,
                "{\"productId\":" + prod + ",\"warehouseId\":" + src
                        + ",\"direction\":\"IN\",\"quantity\":5,\"reason\":\"IM top-up\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void concurrentWithdrawalsStayConsistent() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long prod = createProduct("PHN-001", "Phone", cat);
        long wh = createWarehouse("WH-01", "Central");
        postReceipt(adminToken, prod, wh, "100");

        // Wave 1: 10 threads × 5 from 100 — all must succeed, balance exactly 50.
        int[] wave1 = runConcurrentAdjustments(prod, wh, "5", 10);
        assertThat(wave1[0]).isEqualTo(10);
        assertThat(wave1[1]).isZero();
        assertThat(balanceOf(prod, wh).compareTo(new BigDecimal("50"))).isZero();

        // Wave 2: 10 threads × 20 from 50 — exactly 2 succeed, balance 10, never negative.
        int[] wave2 = runConcurrentAdjustments(prod, wh, "20", 10);
        assertThat(wave2[0]).isEqualTo(2);
        assertThat(wave2[1]).isEqualTo(8);
        BigDecimal finalBalance = balanceOf(prod, wh);
        assertThat(finalBalance.compareTo(new BigDecimal("10"))).isZero();
        assertThat(finalBalance.compareTo(BigDecimal.ZERO)).isNotNegative();

        // 1 receipt + 10 + 2 successful movements, nothing else recorded.
        assertThat(transactionRepository.count()).isEqualTo(13);
    }

    // ---- Helpers ----

    private int[] runConcurrentAdjustments(long productId, long warehouseId,
                                           String quantity, int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
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
                        new UsernamePasswordAuthenticationToken("admin", null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                try {
                    AdjustmentRequest request = new AdjustmentRequest();
                    request.setProductId(productId);
                    request.setWarehouseId(warehouseId);
                    request.setDirection(AdjustmentDirection.OUT);
                    request.setQuantity(new BigDecimal(quantity));
                    request.setReason("concurrency probe");
                    inventoryService.adjust(request);
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
        return new int[]{succeeded.get(), rejected.get()};
    }

    private BigDecimal balanceOf(long productId, long warehouseId) {
        return inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow().getQuantityOnHand();
    }

    private static BigDecimal bd(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    private JsonNode postReceipt(String token, long productId, long warehouseId, String qty)
            throws Exception {
        MvcResult result = postRaw("/api/v1/inventory/receipts", token,
                receiptJson(productId, warehouseId, qty))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode postAdjust(String token, long productId, long warehouseId,
                                String direction, String qty, String reason) throws Exception {
        MvcResult result = postRaw("/api/v1/inventory/adjustments", token,
                "{\"productId\":" + productId + ",\"warehouseId\":" + warehouseId
                        + ",\"direction\":\"" + direction + "\",\"quantity\":" + qty
                        + ",\"reason\":\"" + reason + "\"}")
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode postTransfer(String token, long productId, long sourceId, long destId,
                                  String qty, String reference) throws Exception {
        MvcResult result = postRaw("/api/v1/inventory/transfers", token,
                transferJson(productId, sourceId, destId, qty, reference))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions postRaw(
            String url, String token, String json) throws Exception {
        var builder = post(url).contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder);
    }

    private static String receiptJson(long productId, long warehouseId, String qty) {
        return "{\"productId\":" + productId + ",\"warehouseId\":" + warehouseId
                + ",\"quantity\":" + qty + "}";
    }

    private static String transferJson(long productId, long sourceId, long destId,
                                       String qty, String reference) {
        return "{\"productId\":" + productId + ",\"sourceWarehouseId\":" + sourceId
                + ",\"destinationWarehouseId\":" + destId + ",\"quantity\":" + qty
                + (reference == null ? "" : ",\"reference\":\"" + reference + "\"") + "}";
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
