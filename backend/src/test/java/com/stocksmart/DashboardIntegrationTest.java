package com.stocksmart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksmart.entity.Role;
import com.stocksmart.entity.RoleName;
import com.stocksmart.entity.User;
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
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Day 5 KPI dashboard matrix: exact aggregation values, low-stock/stockout
 * classification boundaries, warehouse filtering, cost-basis valuation,
 * pending-order counts, partial-fulfillment remainders, recent movements,
 * no-double-count guarantees, empty-dataset behaviour and RBAC.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardIntegrationTest {

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
    private PurchaseReceiptRepository purchaseReceiptRepository;

    @Autowired
    private SalesFulfillmentRepository salesFulfillmentRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private RfidTagRepository rfidTagRepository;

    @Autowired
    private RfidReadEventRepository rfidReadEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private String adminToken;
    private String imToken;
    private String whToken;
    private String procToken;
    private String salesToken;
    private String mgmtToken;

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
    }

    @Test
    void summaryComputesCoreKpisExactly() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long p1 = createProduct("PHN-001", cat, "10.00", "12");
        long p2 = createProduct("PHN-002", cat, "5.50", "0");
        createProductWithActive("PHN-003", cat, "2.00", "0", false);
        long w1 = createWarehouse("WH-01", "Central");
        long w2 = createWarehouse("WH-02", "East");

        postReceipt(adminToken, p1, w1, "8");
        postReceipt(adminToken, p1, w2, "15");
        postReceipt(adminToken, p2, w1, "5");
        postAdjust(adminToken, p2, w1, "OUT", "5", "sold out");
        postReceipt(adminToken, p2, w2, "2.5");

        JsonNode body = dashboardJson("/api/v1/dashboard/summary", adminToken);

        assertThat(body.get("totalProducts").asLong()).isEqualTo(3);
        assertThat(body.get("activeProducts").asLong()).isEqualTo(2);
        assertThat(body.get("totalWarehouses").asLong()).isEqualTo(2);
        assertThat(body.get("activeWarehouses").asLong()).isEqualTo(2);
        assertThat(bd(body, "totalInventoryUnits")).isEqualByComparingTo("25.5");
        assertThat(bd(body, "inventoryValue")).isEqualByComparingTo("243.75");
        assertThat(body.get("lowStockCount").asLong()).isEqualTo(1);
        assertThat(body.get("stockoutCount").asLong()).isEqualTo(1);
        assertThat(body.get("warehouseId").isNull()).isTrue();
        assertThat(body.get("generatedAt").asText()).isNotEmpty();
    }

    @Test
    void emptyDatasetReturnsZerosAndEmptyLists() throws Exception {
        JsonNode summary = dashboardJson("/api/v1/dashboard/summary", adminToken);
        assertThat(summary.get("totalProducts").asLong()).isZero();
        assertThat(summary.get("totalWarehouses").asLong()).isZero();
        assertThat(bd(summary, "totalInventoryUnits")).isEqualByComparingTo("0");
        assertThat(bd(summary, "inventoryValue")).isEqualByComparingTo("0");
        assertThat(dashboardRaw("/api/v1/dashboard/summary", adminToken))
                .contains("\"inventoryValue\":0.00");
        assertThat(summary.get("lowStockCount").asLong()).isZero();
        assertThat(summary.get("stockoutCount").asLong()).isZero();

        mockMvc.perform(get("/api/v1/dashboard/low-stock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/dashboard/stockouts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/dashboard/recent-transactions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        JsonNode po = dashboardJson("/api/v1/dashboard/purchase-orders", adminToken);
        assertThat(po.get("totalOrders").asLong()).isZero();
        assertThat(po.get("pendingCount").asLong()).isZero();
        JsonNode so = dashboardJson("/api/v1/dashboard/sales-fulfillment", adminToken);
        assertThat(so.get("totalOrders").asLong()).isZero();
        assertThat(bd(so, "remainingToFulfillUnits")).isEqualByComparingTo("0");
    }

    @Test
    void lowStockAndStockoutClassificationBoundaries() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long p1 = createProduct("PHN-001", cat, "10.00", "12");
        long p2 = createProduct("PHN-002", cat, "5.50", "5");
        long p3 = createProduct("PHN-003", cat, "2.00", "0");
        long w1 = createWarehouse("WH-01", "Central");
        long w2 = createWarehouse("WH-02", "East");

        postReceipt(adminToken, p1, w1, "8");     // 8 < 12 → LOW (deficit 4)
        postReceipt(adminToken, p1, w2, "12");    // exactly the minimum → NOT low
        postReceipt(adminToken, p2, w1, "5");
        postAdjust(adminToken, p2, w1, "OUT", "5", "drained"); // 0 → STOCKOUT (not low)
        postReceipt(adminToken, p2, w2, "4");     // 4 < 5 → LOW (deficit 1)
        postReceipt(adminToken, p3, w1, "10");    // threshold 0 → never low

        MvcResult low = mockMvc.perform(get("/api/v1/dashboard/low-stock?sort=id,asc")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();
        JsonNode items = objectMapper.readTree(low.getResponse().getContentAsString())
                .get("content");
        assertThat(items.get(0).get("sku").asText()).isEqualTo("PHN-001");
        assertThat(items.get(0).get("warehouseCode").asText()).isEqualTo("WH-01");
        assertThat(bd(items.get(0), "quantityOnHand")).isEqualByComparingTo("8");
        assertThat(bd(items.get(0), "minimumStockLevel")).isEqualByComparingTo("12");
        assertThat(bd(items.get(0), "deficit")).isEqualByComparingTo("4");
        assertThat(items.get(1).get("sku").asText()).isEqualTo("PHN-002");
        assertThat(bd(items.get(1), "deficit")).isEqualByComparingTo("1");

        MvcResult out = mockMvc.perform(get("/api/v1/dashboard/stockouts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn();
        JsonNode stockouts = objectMapper.readTree(out.getResponse().getContentAsString())
                .get("content");
        assertThat(stockouts.get(0).get("sku").asText()).isEqualTo("PHN-002");
        assertThat(stockouts.get(0).get("warehouseCode").asText()).isEqualTo("WH-01");
        assertThat(bd(stockouts.get(0), "quantityOnHand")).isEqualByComparingTo("0");
    }

    @Test
    void warehouseFilterAndPagingApplyConsistently() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long p1 = createProduct("PHN-001", cat, "10.00", "12");
        long p2 = createProduct("PHN-002", cat, "5.50", "5");
        long w1 = createWarehouse("WH-01", "Central");
        long w2 = createWarehouse("WH-02", "East");

        postReceipt(adminToken, p1, w1, "8");    // low in W1
        postReceipt(adminToken, p1, w2, "3");    // low in W2
        postReceipt(adminToken, p2, w2, "4");    // low in W2

        // Summary scoped to W2: units 3 + 4 = 7, value 3×10.00 + 4×5.50 = 52.00, low=2.
        JsonNode w2Summary = dashboardJson("/api/v1/dashboard/summary?warehouseId=" + w2, adminToken);
        assertThat(bd(w2Summary, "totalInventoryUnits")).isEqualByComparingTo("7");
        assertThat(bd(w2Summary, "inventoryValue")).isEqualByComparingTo("52.00");
        assertThat(dashboardRaw("/api/v1/dashboard/summary?warehouseId=" + w2, adminToken))
                .contains("\"inventoryValue\":52.00");
        assertThat(w2Summary.get("lowStockCount").asLong()).isEqualTo(2);

        // Low stock scoped to W2 + paging (page size 1 of 2 rows).
        MvcResult paged = mockMvc.perform(get("/api/v1/dashboard/low-stock?warehouseId=" + w2
                        + "&page=0&size=1&sort=id,asc")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andReturn();
        JsonNode page = objectMapper.readTree(paged.getResponse().getContentAsString());
        assertThat(page.get("content").get(0).get("warehouseCode").asText()).isEqualTo("WH-02");

        // Stockouts scoped to W1: p1 balance is 8 → none.
        mockMvc.perform(get("/api/v1/dashboard/stockouts?warehouseId=" + w1)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void inventoryValueUsesCostBasisWithHalfUpRounding() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long p = createProduct("PHN-001", cat, "3.00", "0");
        long w = createWarehouse("WH-01", "Central");

        postReceipt(adminToken, p, w, "0.3333"); // 0.3333 × 3.00 = 0.9999 → 1.00

        JsonNode summary = dashboardJson("/api/v1/dashboard/summary", adminToken);
        assertThat(bd(summary, "totalInventoryUnits")).isEqualByComparingTo("0.3333");
        assertThat(bd(summary, "inventoryValue")).isEqualByComparingTo("1.00");
        assertThat(dashboardRaw("/api/v1/dashboard/summary", adminToken))
                .contains("\"inventoryValue\":1.00");
    }

    @Test
    void purchaseOrderSummaryCountsByStatusAndPending() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long product = createProduct("PHN-001", cat, "10.00", "0");
        long wh = createWarehouse("WH-01", "Central");
        long supplier = createSupplier("SUP-01", "Acme");

        createPo(supplier, wh, product, "10", null);                       // DRAFT
        createPo(supplier, wh, product, "10", "SUBMITTED");                // SUBMITTED
        createPo(supplier, wh, product, "10", "PARTIALLY_RECEIVED");       // 5 of 10
        createPo(supplier, wh, product, "10", "RECEIVED");                 // 10 of 10
        createPo(supplier, wh, product, "10", "CANCELLED");

        JsonNode body = dashboardJson("/api/v1/dashboard/purchase-orders", adminToken);
        assertThat(body.get("totalOrders").asLong()).isEqualTo(5);
        assertThat(body.get("pendingCount").asLong()).isEqualTo(2); // submitted + partial
        assertThat(body.get("byStatus").get("DRAFT").asLong()).isEqualTo(1);
        assertThat(body.get("byStatus").get("SUBMITTED").asLong()).isEqualTo(1);
        assertThat(body.get("byStatus").get("PARTIALLY_RECEIVED").asLong()).isEqualTo(1);
        assertThat(body.get("byStatus").get("RECEIVED").asLong()).isEqualTo(1);
        assertThat(body.get("byStatus").get("CANCELLED").asLong()).isEqualTo(1);
    }

    @Test
    void salesFulfillmentSummaryCountsAndRemainingUnits() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long product = createProduct("PHN-001", cat, "10.00", "0");
        long wh = createWarehouse("WH-01", "Central");
        postReceipt(adminToken, product, wh, "100");

        createSo(wh, product, "10", null);                                // DRAFT
        createSo(wh, product, "10", "CONFIRMED");                         // 10 remaining
        createSo(wh, product, "10", "PARTIALLY_FULFILLED");               // 4 of 10 → 6
        createSo(wh, product, "10", "FULFILLED");                         // 10 of 10
        createSo(wh, product, "10", "CANCELLED");

        JsonNode body = dashboardJson("/api/v1/dashboard/sales-fulfillment", adminToken);
        assertThat(body.get("totalOrders").asLong()).isEqualTo(5);
        assertThat(body.get("pendingCount").asLong()).isEqualTo(2); // confirmed + partial
        assertThat(bd(body, "remainingToFulfillUnits")).isEqualByComparingTo("16");
        assertThat(body.get("byStatus").get("DRAFT").asLong()).isEqualTo(1);
        assertThat(body.get("byStatus").get("CONFIRMED").asLong()).isEqualTo(1);
        assertThat(body.get("byStatus").get("PARTIALLY_FULFILLED").asLong()).isEqualTo(1);
        assertThat(body.get("byStatus").get("FULFILLED").asLong()).isEqualTo(1);
        assertThat(body.get("byStatus").get("CANCELLED").asLong()).isEqualTo(1);
    }

    @Test
    void orderSummariesRespectWarehouseFilter() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long product = createProduct("PHN-001", cat, "10.00", "0");
        long w1 = createWarehouse("WH-01", "Central");
        long w2 = createWarehouse("WH-02", "East");
        long supplier = createSupplier("SUP-01", "Acme");
        postReceipt(adminToken, product, w1, "50");

        createPo(supplier, w1, product, "10", "SUBMITTED");
        createPo(supplier, w2, product, "10", "SUBMITTED");
        createSo(w1, product, "10", "CONFIRMED");
        createSo(w2, product, "10", "CONFIRMED");

        JsonNode poW1 = dashboardJson(
                "/api/v1/dashboard/purchase-orders?warehouseId=" + w1, adminToken);
        assertThat(poW1.get("totalOrders").asLong()).isEqualTo(1);
        assertThat(poW1.get("pendingCount").asLong()).isEqualTo(1);
        assertThat(poW1.get("warehouseId").asLong()).isEqualTo(w1);

        JsonNode soW2 = dashboardJson(
                "/api/v1/dashboard/sales-fulfillment?warehouseId=" + w2, adminToken);
        assertThat(soW2.get("totalOrders").asLong()).isEqualTo(1);
        assertThat(soW2.get("pendingCount").asLong()).isEqualTo(1);
    }

    @Test
    void recentTransactionsAreNewestFirstLimitedAndFilterable() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long p1 = createProduct("PHN-001", cat, "10.00", "0");
        long p2 = createProduct("PHN-002", cat, "10.00", "0");
        long w1 = createWarehouse("WH-01", "Central");
        long w2 = createWarehouse("WH-02", "East");

        postReceipt(adminToken, p1, w1, "5");   // OPENING_BALANCE
        postReceipt(adminToken, p1, w1, "3");   // RECEIPT
        postReceipt(adminToken, p2, w2, "9");   // OPENING_BALANCE, other product/warehouse

        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/recent-transactions?limit=2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        JsonNode rows = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(rows.get(0).get("id").asLong())
                .isGreaterThan(rows.get(1).get("id").asLong());

        // Product + warehouse filter narrows the feed; limit above the cap is clamped.
        mockMvc.perform(get("/api/v1/dashboard/recent-transactions?productId=" + p1
                        + "&warehouseId=" + w1 + "&limit=500")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].productId").value(p1))
                .andExpect(jsonPath("$[0].warehouseCode").value("WH-01"));
    }

    @Test
    void dashboardAuthorizationMatrix() throws Exception {
        // No token → 401.
        mockMvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isUnauthorized());

        // Core KPIs: every authenticated role.
        for (String token : new String[]{adminToken, imToken, whToken, procToken, salesToken,
                mgmtToken}) {
            mockMvc.perform(get("/api/v1/dashboard/summary")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/dashboard/low-stock")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/dashboard/stockouts")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/dashboard/recent-transactions")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        // PO summary: no sales role.
        mockMvc.perform(get("/api/v1/dashboard/purchase-orders")
                        .header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isForbidden());
        for (String token : new String[]{adminToken, imToken, whToken, procToken, mgmtToken}) {
            mockMvc.perform(get("/api/v1/dashboard/purchase-orders")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        // SO fulfillment summary: no procurement role.
        mockMvc.perform(get("/api/v1/dashboard/sales-fulfillment")
                        .header("Authorization", "Bearer " + procToken))
                .andExpect(status().isForbidden());
        for (String token : new String[]{adminToken, imToken, whToken, salesToken, mgmtToken}) {
            mockMvc.perform(get("/api/v1/dashboard/sales-fulfillment")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void unknownWarehouseFilterReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary?warehouseId=9999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/dashboard/low-stock?warehouseId=9999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void kpiAggregationsDoNotDoubleCountJoinedRows() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long product = createProduct("PHN-001", cat, "10.00", "0");
        long wh = createWarehouse("WH-01", "Central");
        long supplier = createSupplier("SUP-01", "Acme");

        // One submitted PO with THREE lines counts once.
        MvcResult poCreated = mockMvc.perform(post("/api/v1/purchase-orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplier + ",\"warehouseId\":" + wh
                                + ",\"orderDate\":\"2026-10-14\",\"items\":["
                                + "{\"productId\":" + product + ",\"orderedQuantity\":10,\"unitCost\":1},"
                                + "{\"productId\":" + product + ",\"orderedQuantity\":20,\"unitCost\":1},"
                                + "{\"productId\":" + product + ",\"orderedQuantity\":30,\"unitCost\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        long poId = objectMapper.readTree(poCreated.getResponse().getContentAsString())
                .get("id").asLong();
        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        JsonNode po = dashboardJson("/api/v1/dashboard/purchase-orders", adminToken);
        assertThat(po.get("totalOrders").asLong()).isEqualTo(1);
        assertThat(po.get("pendingCount").asLong()).isEqualTo(1);

        // One SO with two lines, partially fulfilled: remaining = (10-4) + (5-0) = 11.
        postReceipt(adminToken, product, wh, "100");
        MvcResult soCreated = mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":" + wh
                                + ",\"customerName\":\"Buyer One\",\"orderDate\":\"2026-10-14\",\"items\":["
                                + "{\"productId\":" + product + ",\"orderedQuantity\":10,\"unitPrice\":2},"
                                + "{\"productId\":" + product + ",\"orderedQuantity\":5,\"unitPrice\":2}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        long soId = objectMapper.readTree(soCreated.getResponse().getContentAsString())
                .get("id").asLong();
        mockMvc.perform(post("/api/v1/sales-orders/" + soId + "/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        MvcResult soDetail = mockMvc.perform(get("/api/v1/sales-orders/" + soId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        long soItemId = objectMapper.readTree(soDetail.getResponse().getContentAsString())
                .get("items").get(0).get("id").asLong();
        mockMvc.perform(post("/api/v1/sales-orders/" + soId + "/fulfillments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"soItemId\":" + soItemId + ",\"quantity\":4}]}"))
                .andExpect(status().isCreated());

        JsonNode so = dashboardJson("/api/v1/dashboard/sales-fulfillment", adminToken);
        assertThat(so.get("totalOrders").asLong()).isEqualTo(1);
        assertThat(so.get("pendingCount").asLong()).isEqualTo(1);
        assertThat(bd(so, "remainingToFulfillUnits")).isEqualByComparingTo("11");

        // Inventory value counts the CURRENT balance once despite two receipts.
        JsonNode summary = dashboardJson("/api/v1/dashboard/summary", adminToken);
        assertThat(bd(summary, "totalInventoryUnits")).isEqualByComparingTo("96"); // 100 - 4
        assertThat(bd(summary, "inventoryValue")).isEqualByComparingTo("960.00");  // 96 × 10
    }

    // ---- Helpers ----

    private String dashboardRaw(String url, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    /**
     * Parses the payload for numeric comparisons. Note: Jackson's tree model
     * normalizes BigDecimal scale, so exact-scale checks must use
     * {@link #dashboardRaw(String, String)} string assertions instead.
     */
    private JsonNode dashboardJson(String url, String token) throws Exception {
        return objectMapper.readTree(dashboardRaw(url, token));
    }

    private static BigDecimal bd(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    private void postReceipt(String token, long productId, long warehouseId, String qty)
            throws Exception {
        mockMvc.perform(post("/api/v1/inventory/receipts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"warehouseId\":" + warehouseId
                                + ",\"quantity\":" + qty + "}"))
                .andExpect(status().isCreated());
    }

    private void postAdjust(String token, long productId, long warehouseId, String direction,
                            String qty, String reason) throws Exception {
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"warehouseId\":" + warehouseId
                                + ",\"direction\":\"" + direction + "\",\"quantity\":" + qty
                                + ",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isCreated());
    }

    /**
     * Creates a PO and drives it to the requested lifecycle stage:
     * null=DRAFT, SUBMITTED, PARTIALLY_RECEIVED (5 of qty), RECEIVED, CANCELLED.
     */
    private long createPo(long supplierId, long warehouseId, long productId, String qty,
                          String stage) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/purchase-orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"warehouseId\":" + warehouseId
                                + ",\"orderDate\":\"2026-10-14\",\"items\":[{\"productId\":"
                                + productId + ",\"orderedQuantity\":" + qty + ",\"unitCost\":1.00}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();
        if (stage == null) {
            return id;
        }
        mockMvc.perform(post("/api/v1/purchase-orders/" + id + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        if ("RECEIVED".equals(stage)) {
            receivePo(id, "10", qty);
        } else if ("PARTIALLY_RECEIVED".equals(stage)) {
            receivePo(id, "5", qty);
        } else if ("CANCELLED".equals(stage)) {
            mockMvc.perform(post("/api/v1/purchase-orders/" + id + "/cancel")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());
        }
        return id;
    }

    private void receivePo(long poId, String quantity, String expectedStatusBasis) throws Exception {
        MvcResult detail = mockMvc.perform(get("/api/v1/purchase-orders/" + poId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        long itemId = objectMapper.readTree(detail.getResponse().getContentAsString())
                .get("items").get(0).get("id").asLong();
        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/receipts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"poItemId\":" + itemId + ",\"quantity\":"
                                + quantity + "}]}"))
                .andExpect(status().isCreated());
    }

    /**
     * Creates an SO and drives it to the requested stage:
     * null=DRAFT, CONFIRMED, PARTIALLY_FULFILLED (4 of qty), FULFILLED, CANCELLED.
     */
    private long createSo(long warehouseId, long productId, String qty, String stage)
            throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":" + warehouseId
                                + ",\"customerName\":\"Buyer One\",\"orderDate\":\"2026-10-14\",\"items\":[{\"productId\":"
                                + productId + ",\"orderedQuantity\":" + qty + ",\"unitPrice\":2.00}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();
        if (stage == null) {
            return id;
        }
        mockMvc.perform(post("/api/v1/sales-orders/" + id + "/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        if ("FULFILLED".equals(stage)) {
            fulfillSo(id, "10");
        } else if ("PARTIALLY_FULFILLED".equals(stage)) {
            fulfillSo(id, "4");
        } else if ("CANCELLED".equals(stage)) {
            mockMvc.perform(post("/api/v1/sales-orders/" + id + "/cancel")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());
        }
        return id;
    }

    private void fulfillSo(long soId, String quantity) throws Exception {
        MvcResult detail = mockMvc.perform(get("/api/v1/sales-orders/" + soId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        long itemId = objectMapper.readTree(detail.getResponse().getContentAsString())
                .get("items").get(0).get("id").asLong();
        mockMvc.perform(post("/api/v1/sales-orders/" + soId + "/fulfillments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"soItemId\":" + itemId + ",\"quantity\":"
                                + quantity + "}]}"))
                .andExpect(status().isCreated());
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

    private long createProduct(String sku, long categoryId, String costPrice,
                               String minimumStockLevel) throws Exception {
        return createProductWithActive(sku, categoryId, costPrice, minimumStockLevel, true);
    }

    private long createProductWithActive(String sku, long categoryId, String costPrice,
                                         String minimumStockLevel, boolean active) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"" + sku + "\","
                                + "\"categoryId\":" + categoryId + ",\"unit\":\"PCS\","
                                + "\"costPrice\":" + costPrice + ",\"sellingPrice\":20.00,"
                                + "\"minimumStockLevel\":" + minimumStockLevel + ",\"active\":" + active + "}"))
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
