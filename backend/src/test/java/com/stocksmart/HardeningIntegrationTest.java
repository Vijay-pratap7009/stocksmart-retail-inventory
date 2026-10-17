package com.stocksmart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.util.HashMap;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Day 6 hardening matrix (findings that were fixed and are now pinned):
 *
 * <ul>
 *   <li>duplicate line ids in one receipt/fulfillment cannot bypass the
 *       remaining-quantity checks (over-receipt / over-fulfill);</li>
 *   <li>client mistakes return 400/404/405/415 instead of 500;</li>
 *   <li>delete guards block supplier/warehouse/product removal while orders
 *       reference them (clear 409s, no over-blocking);</li>
 *   <li>CORS only allows configured origins.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class HardeningIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;
    private String procToken;
    private String salesToken;

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
        Role proc = roleRepository.save(new Role(RoleName.PROCUREMENT_USER, "proc"));
        Role sales = roleRepository.save(new Role(RoleName.SALES_USER, "sales"));

        userRepository.save(new User("admin", "admin@t.local",
                passwordEncoder.encode("Admin@123"), "Admin", admin));
        userRepository.save(new User("procuser", "proc@t.local",
                passwordEncoder.encode("Admin@123"), "Proc", proc));
        userRepository.save(new User("salesuser", "sales@t.local",
                passwordEncoder.encode("Admin@123"), "Sales", sales));

        adminToken = login("admin");
        procToken = login("procuser");
        salesToken = login("salesuser");

        catId = createCategory("ELEC", "Electronics");
        prodA = createProduct("PHN-001", "Phone", catId);
        prodB = createProduct("TAB-001", "Tablet", catId);
        whId = createWarehouse("WH-01", "Central");
        supId = createSupplier("SUP-01", "Acme Supplies");
    }

    @Test
    void duplicateReceiptLinesCannotOverReceive() throws Exception {
        long poId = createPo("PO-H1", line(prodA, "10", "5.00")).get("id").asLong();
        submitPo(poId);
        long itemId = poItemIds(poId).get(prodA);

        // Same PO line twice (6 + 6 > 10): each entry passes the remaining
        // check individually — the duplicate must be rejected up front.
        postRaw("/api/v1/purchase-orders/" + poId + "/receipts", procToken,
                "{\"lines\":[" + receiptLine(itemId, "6") + ","
                        + receiptLine(itemId, "6") + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message",
                        Matchers.containsString("Duplicate PO line")));

        // No stock, no receipt row, no quantity or status change.
        assertThat(balanceOf(prodA)).isNull();
        assertThat(transactionRepository.count()).isZero();
        assertThat(purchaseReceiptRepository.count()).isZero();
        JsonNode after = getPo(poId);
        assertThat(new BigDecimal(after.get("items").get(0).get("receivedQuantity").asText())
                .compareTo(BigDecimal.ZERO)).isZero();
        assertThat(after.get("status").asText()).isEqualTo("SUBMITTED");

        // A single non-duplicate line still works.
        postRaw("/api/v1/purchase-orders/" + poId + "/receipts", procToken,
                "{\"lines\":[" + receiptLine(itemId, "6") + "]}")
                .andExpect(status().isCreated());
        assertThat(balanceOf(prodA).compareTo(new BigDecimal("6"))).isZero();
    }

    @Test
    void duplicateFulfillmentLinesCannotOverFulfill() throws Exception {
        receiptStock(prodA, "10");
        long soId = createSo("SO-H1", "Retail Mart", sline(prodA, "10", "9.00"))
                .get("id").asLong();
        confirmSo(soId);
        long itemId = soItemIds(soId).get(prodA);

        postRaw("/api/v1/sales-orders/" + soId + "/fulfillments", salesToken,
                "{\"lines\":[" + fulfillLine(itemId, "6") + ","
                        + fulfillLine(itemId, "6") + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message",
                        Matchers.containsString("Duplicate SO line")));

        assertThat(balanceOf(prodA).compareTo(new BigDecimal("10"))).isZero();
        assertThat(salesFulfillmentRepository.count()).isZero();
        JsonNode after = getSo(soId);
        assertThat(new BigDecimal(after.get("items").get(0).get("fulfilledQuantity").asText())
                .compareTo(BigDecimal.ZERO)).isZero();
        assertThat(after.get("status").asText()).isEqualTo("CONFIRMED");

        postRaw("/api/v1/sales-orders/" + soId + "/fulfillments", salesToken,
                "{\"lines\":[" + fulfillLine(itemId, "6") + "]}")
                .andExpect(status().isCreated());
        assertThat(balanceOf(prodA).compareTo(new BigDecimal("4"))).isZero();
    }

    @Test
    void clientInputErrorsReturnClientStatusCodesNot500() throws Exception {
        // Path variable of the wrong type → 400.
        mockMvc.perform(get("/api/v1/products/abc")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Invalid value for parameter 'id'"));
        // Invalid enum / Instant / int query parameters → 400.
        mockMvc.perform(get("/api/v1/stock-transactions?type=BOGUS")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/stock-transactions?from=notadate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/dashboard/recent-transactions?limit=abc")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
        // Unknown endpoint → 404, wrong method → 405, wrong media type → 415.
        mockMvc.perform(get("/api/v1/does-not-exist")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<category/>"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void deleteGuardsBlockReferencedRecordsWithClearConflicts() throws Exception {
        // Draft PO (no receipts, no inventory yet) still references
        // supplier + warehouse + product.
        createPo("PO-H2", line(prodA, "5", "5.00"));

        deleteExpectingConflict("/api/v1/suppliers/" + supId, "purchase orders");
        deleteExpectingConflict("/api/v1/warehouses/" + whId, "purchase orders");
        deleteExpectingConflict("/api/v1/products/" + prodA, "purchase order lines");

        // Unreferenced product delete still succeeds (no over-blocking).
        mockMvc.perform(delete("/api/v1/products/" + prodB)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Sales side: a confirmed SO on a separate warehouse guards that
        // warehouse with the sales-specific conflict message.
        long whB = createWarehouse("WH-02", "Secondary");
        createSo("SO-H2", "Retail Mart", whB, sline(prodA, "2", "9.00"));
        deleteExpectingConflict("/api/v1/warehouses/" + whB, "sales orders");
    }

    @Test
    void corsAllowsOnlyConfiguredOrigins() throws Exception {
        mockMvc.perform(options("/api/v1/products")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin",
                        "http://localhost:4200"));

        mockMvc.perform(options("/api/v1/products")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ---- Helpers ----

    private void deleteExpectingConflict(String url, String messagePart) throws Exception {
        mockMvc.perform(delete(url).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", Matchers.containsString(messagePart)));
    }

    private void receiptStock(long productId, String qty) throws Exception {
        postRaw("/api/v1/inventory/receipts", adminToken,
                "{\"productId\":" + productId + ",\"warehouseId\":" + whId
                        + ",\"quantity\":" + qty + "}")
                .andExpect(status().isCreated());
    }

    private BigDecimal balanceOf(long productId) {
        return inventoryRepository
                .findByProductIdAndWarehouseId(productId, whId)
                .map(i -> i.getQuantityOnHand())
                .orElse(null);
    }

    private JsonNode createPo(String number, String... lines) throws Exception {
        MvcResult result = postRaw("/api/v1/purchase-orders", procToken,
                "{\"poNumber\":\"" + number + "\",\"supplierId\":" + supId
                        + ",\"warehouseId\":" + whId + ",\"orderDate\":\"2026-10-16\""
                        + ",\"items\":[" + String.join(",", lines) + "]}")
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void submitPo(long id) throws Exception {
        postRaw("/api/v1/purchase-orders/" + id + "/submit", procToken, "{}")
                .andExpect(status().isOk());
    }

    private JsonNode getPo(long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/purchase-orders/" + id)
                        .header("Authorization", "Bearer " + procToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Map<Long, Long> poItemIds(long poId) throws Exception {
        Map<Long, Long> ids = new HashMap<>();
        for (JsonNode item : getPo(poId).get("items")) {
            ids.put(item.get("productId").asLong(), item.get("id").asLong());
        }
        return ids;
    }

    private JsonNode createSo(String number, String customer, String... lines)
            throws Exception {
        return createSo(number, customer, whId, lines);
    }

    private JsonNode createSo(String number, String customer, long warehouseId,
                              String... lines) throws Exception {
        MvcResult result = postRaw("/api/v1/sales-orders", salesToken,
                "{\"soNumber\":\"" + number + "\",\"warehouseId\":" + warehouseId
                        + ",\"customerName\":\"" + customer
                        + "\",\"orderDate\":\"2026-10-16\""
                        + ",\"items\":[" + String.join(",", lines) + "]}")
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void confirmSo(long id) throws Exception {
        postRaw("/api/v1/sales-orders/" + id + "/confirm", salesToken, "{}")
                .andExpect(status().isOk());
    }

    private JsonNode getSo(long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sales-orders/" + id)
                        .header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Map<Long, Long> soItemIds(long soId) throws Exception {
        Map<Long, Long> ids = new HashMap<>();
        for (JsonNode item : getSo(soId).get("items")) {
            ids.put(item.get("productId").asLong(), item.get("id").asLong());
        }
        return ids;
    }

    private ResultActions postRaw(String url, String token, String json)
            throws Exception {
        return mockMvc.perform(post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
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
