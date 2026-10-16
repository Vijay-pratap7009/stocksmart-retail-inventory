package com.stocksmart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Day 5 barcode + RFID matrix: barcode/SKU identification (read-only),
 * duplicate prevention, inactive handling, RFID tag mapping, the reader
 * simulator, read-event dedup and unknown/inactive-tag handling — plus the
 * guarantee that identification never changes stock and that authorized
 * stock operations still flow through the Day 3 inventory service.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BarcodeRfidIntegrationTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    // ---- Barcode identification ----

    @Test
    void barcodeLookupResolvesProductWithoutChangingStock() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long product = createProduct("PHN-001", "8901234567890", "Phone", cat, "12");
        long wh = createWarehouse("WH-01", "Central");
        postReceipt(adminToken, product, wh, "30");

        long inventoryCount = inventoryRepository.count();
        long txnCount = transactionRepository.count();
        BigDecimal balanceBefore = inventoryRepository
                .findByProductIdAndWarehouseId(product, wh).orElseThrow().getQuantityOnHand();

        mockMvc.perform(get("/api/v1/products/barcode/8901234567890")
                        .header("Authorization", "Bearer " + whToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product))
                .andExpect(jsonPath("$.sku").value("PHN-001"))
                .andExpect(jsonPath("$.name").value("Phone"))
                .andExpect(jsonPath("$.barcode").value("8901234567890"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.minimumStockLevel").value(12));

        // Identification is read-only: no balance row, movement or quantity changed.
        assertThat(inventoryRepository.count()).isEqualTo(inventoryCount);
        assertThat(transactionRepository.count()).isEqualTo(txnCount);
        assertThat(inventoryRepository.findByProductIdAndWarehouseId(product, wh)
                .orElseThrow().getQuantityOnHand()).isEqualByComparingTo(balanceBefore);
    }

    @Test
    void skuLookupWorksAndUnknownIdentifiersReturn404() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        createProduct("PHN-001", "8901234567890", "Phone", cat, "0");

        mockMvc.perform(get("/api/v1/products/sku/phn-001")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("PHN-001"));

        mockMvc.perform(get("/api/v1/products/barcode/0000000000000")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get("/api/v1/products/sku/NOPE-999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void inactiveProductStillResolvesByBarcodeWithInactiveStatus() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long product = createProduct("PHN-001", "8901234567890", "Phone", cat, "0");
        long wh = createWarehouse("WH-01", "Central");
        postReceipt(adminToken, product, wh, "5");
        long txnCount = transactionRepository.count();

        // Deactivate via the existing product API (full replace payload).
        mockMvc.perform(put("/api/v1/products/" + product)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("PHN-001", "8901234567890", "Phone", cat, "0", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/products/barcode/8901234567890")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.sku").value("PHN-001"));

        assertThat(transactionRepository.count()).isEqualTo(txnCount);
    }

    @Test
    void duplicateAndInvalidBarcodesAreRejected() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        createProduct("PHN-001", "8901234567890", "Phone", cat, "0");

        // Duplicate barcode (different case) → 409
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("PHN-002", "8901234567890", "Phone 2", cat, "0", true)))
                .andExpect(status().isConflict());

        // Update onto another product's barcode → 409
        long second = createProduct("PHN-003", "1111111111111", "Phone 3", cat, "0");
        mockMvc.perform(put("/api/v1/products/" + second)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("PHN-003", "8901234567890", "Phone 3", cat, "0", true)))
                .andExpect(status().isConflict());

        // Invalid barcodes → 400
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("PHN-004", "AB", "Phone 4", cat, "0", true)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("PHN-005", "8901 2345", "Phone 5", cat, "0", true)))
                .andExpect(status().isBadRequest());

        // A product WITHOUT a barcode still works (barcode is optional).
        long plain = createProduct("PHN-006", null, "Phone 6", cat, "0");
        mockMvc.perform(get("/api/v1/products/" + plain)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void barcodeLookupRoleMatrix() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        createProduct("PHN-001", "8901234567890", "Phone", cat, "0");

        mockMvc.perform(get("/api/v1/products/barcode/8901234567890"))
                .andExpect(status().isUnauthorized());

        for (String token : new String[]{adminToken, imToken, whToken, procToken, salesToken,
                mgmtToken}) {
            mockMvc.perform(get("/api/v1/products/barcode/8901234567890")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void barcodeIdentificationFeedsAuthorizedInventoryWorkflow() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long product = createProduct("PHN-001", "8901234567890", "Phone", cat, "0");
        long wh = createWarehouse("WH-01", "Central");

        // Scan → identify → the user then performs an authorized receipt explicitly.
        MvcResult lookup = mockMvc.perform(get("/api/v1/products/barcode/8901234567890")
                        .header("Authorization", "Bearer " + whToken))
                .andExpect(status().isOk())
                .andReturn();
        long identified = objectMapper.readTree(lookup.getResponse().getContentAsString())
                .get("id").asLong();
        assertThat(identified).isEqualTo(product);
        assertThat(inventoryRepository.count()).isZero();

        postReceipt(whToken, identified, wh, "17");
        assertThat(inventoryRepository.findByProductIdAndWarehouseId(product, wh)
                .orElseThrow().getQuantityOnHand()).isEqualByComparingTo(new BigDecimal("17"));
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    // ---- RFID tag mapping ----

    @Test
    void rfidTagRegistrationLookupAndDuplicateEpc() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long product = createProduct("PHN-001", "8901234567890", "Phone", cat, "0");

        MvcResult created = mockMvc.perform(post("/api/v1/rfid/tags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"epcCode\":\"EPC-0001\",\"productId\":" + product + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.epcCode").value("EPC-0001"))
                .andExpect(jsonPath("$.productId").value(product))
                .andExpect(jsonPath("$.sku").value("PHN-001"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        assertThat(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong()).isPositive();

        // Duplicate EPC (case-insensitive) → 409
        mockMvc.perform(post("/api/v1/rfid/tags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"epcCode\":\"epc-0001\",\"productId\":" + product + "}"))
                .andExpect(status().isConflict());

        // Unknown product → 404
        mockMvc.perform(post("/api/v1/rfid/tags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"epcCode\":\"EPC-0002\",\"productId\":9999}"))
                .andExpect(status().isNotFound());

        // Lookup: known → 200, unknown → 404
        mockMvc.perform(get("/api/v1/rfid/tags/epc-0001")
                        .header("Authorization", "Bearer " + whToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(product));
        mockMvc.perform(get("/api/v1/rfid/tags/EPC-NOPE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // List with product filter
        mockMvc.perform(get("/api/v1/rfid/tags?productId=" + product)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void rfidTagUpdateRemapsAndDeactivates() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long productA = createProduct("PHN-001", null, "Phone A", cat, "0");
        long productB = createProduct("PHN-002", null, "Phone B", cat, "0");

        mockMvc.perform(post("/api/v1/rfid/tags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"epcCode\":\"EPC-0100\",\"productId\":" + productA + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/rfid/tags/EPC-0100")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productB + ",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productB))
                .andExpect(jsonPath("$.sku").value("PHN-002"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/rfid/tags/EPC-0100")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Unknown tag update → 404
        mockMvc.perform(put("/api/v1/rfid/tags/EPC-NOPE")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productA + ",\"active\":true}"))
                .andExpect(status().isNotFound());
    }

    // ---- RFID reads (simulator + external path) ----

    @Test
    void simulatedReadResolvesTagWithoutChangingStock() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long product = createProduct("PHN-001", null, "Phone", cat, "0");
        long wh = createWarehouse("WH-01", "Central");
        postReceipt(adminToken, product, wh, "20");
        registerTag("EPC-2001", product);

        long inventoryCount = inventoryRepository.count();
        long txnCount = transactionRepository.count();

        mockMvc.perform(post("/api/v1/rfid/reads")
                        .header("Authorization", "Bearer " + whToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"simulate\":true,\"epcCode\":\"EPC-2001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.readerName").value("SIMULATED-READER-01"))
                .andExpect(jsonPath("$.epcCode").value("EPC-2001"))
                .andExpect(jsonPath("$.productId").value(product))
                .andExpect(jsonPath("$.sku").value("PHN-001"))
                .andExpect(jsonPath("$.eventReference").isNotEmpty());

        // A tag read never changes stock.
        assertThat(inventoryRepository.count()).isEqualTo(inventoryCount);
        assertThat(transactionRepository.count()).isEqualTo(txnCount);
        assertThat(inventoryRepository.findByProductIdAndWarehouseId(product, wh)
                .orElseThrow().getQuantityOnHand()).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    void simulatorPicksRegisteredActiveTagWhenEpcOmitted() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long product = createProduct("PHN-001", null, "Phone", cat, "0");

        // No active tags → 404 (no invented tags).
        mockMvc.perform(post("/api/v1/rfid/reads")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"simulate\":true}"))
                .andExpect(status().isNotFound());

        registerTag("EPC-3001", product);

        mockMvc.perform(post("/api/v1/rfid/reads")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"simulate\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.epcCode").value("EPC-3001"))
                .andExpect(jsonPath("$.simulated").value(true));
    }

    @Test
    void unknownAndInactiveTagReadsAreFlaggedAndDoNotChangeStock() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long product = createProduct("PHN-001", null, "Phone", cat, "0");
        long txnCount = transactionRepository.count();

        // Unknown tag: processed (201) but flagged, no product resolution.
        mockMvc.perform(post("/api/v1/rfid/reads")
                        .header("Authorization", "Bearer " + salesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"epcCode\":\"EPC-UNKNOWN\",\"simulate\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNKNOWN_TAG"))
                .andExpect(jsonPath("$.productId").doesNotExist())
                .andExpect(jsonPath("$.tagId").doesNotExist());

        // Inactive tag: mapping exists but status flags it.
        registerTag("EPC-4001", product);
        mockMvc.perform(put("/api/v1/rfid/tags/EPC-4001")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + product + ",\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rfid/reads")
                        .header("Authorization", "Bearer " + imToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"epcCode\":\"EPC-4001\",\"simulate\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INACTIVE_TAG"))
                .andExpect(jsonPath("$.productId").value(product));

        assertThat(transactionRepository.count()).isEqualTo(txnCount);
        assertThat(inventoryRepository.count()).isZero();

        // History filter by status.
        mockMvc.perform(get("/api/v1/rfid/reads?status=UNKNOWN_TAG")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void duplicateReadEventReferenceIsRejected() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long product = createProduct("PHN-001", null, "Phone", cat, "0");
        registerTag("EPC-5001", product);

        String body = "{\"simulate\":true,\"epcCode\":\"EPC-5001\",\"eventReference\":\"EVT-777\"}";
        mockMvc.perform(post("/api/v1/rfid/reads")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventReference").value("EVT-777"));

        mockMvc.perform(post("/api/v1/rfid/reads")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());

        assertThat(rfidReadEventRepository.count()).isEqualTo(1);
    }

    @Test
    void externalReaderReadsRequireEpcAndAreNotMarkedSimulated() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long product = createProduct("PHN-001", null, "Phone", cat, "0");
        registerTag("EPC-6001", product);

        mockMvc.perform(post("/api/v1/rfid/reads")
                        .header("Authorization", "Bearer " + whToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"simulate\":false,\"readerName\":\"DOCK-READER-1\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/rfid/reads")
                        .header("Authorization", "Bearer " + whToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"simulate\":false,\"epcCode\":\"EPC-6001\","
                                + "\"readerName\":\"DOCK-READER-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulated").value(false))
                .andExpect(jsonPath("$.readerName").value("DOCK-READER-1"))
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void rfidAuthorizationMatrix() throws Exception {
        long cat = createCategory("ELEC", "Electronics");
        long product = createProduct("PHN-001", null, "Phone", cat, "0");
        String tagBody = "{\"epcCode\":\"EPC-7001\",\"productId\":" + product + "}";

        // Tag writes: ADMIN only.
        mockMvc.perform(post("/api/v1/rfid/tags")
                        .contentType(MediaType.APPLICATION_JSON).content(tagBody))
                .andExpect(status().isUnauthorized());
        for (String token : new String[]{imToken, whToken, procToken, salesToken, mgmtToken}) {
            mockMvc.perform(post("/api/v1/rfid/tags")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(tagBody))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/v1/rfid/tags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(tagBody))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/rfid/tags/EPC-7001")
                        .header("Authorization", "Bearer " + imToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + product + ",\"active\":false}"))
                .andExpect(status().isForbidden());

        // Reads (list + reader info): all roles, 401 without token.
        mockMvc.perform(get("/api/v1/rfid/tags")).andExpect(status().isUnauthorized());
        for (String token : new String[]{adminToken, imToken, whToken, procToken, salesToken,
                mgmtToken}) {
            mockMvc.perform(get("/api/v1/rfid/tags")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/rfid/reader")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        // Read processing: operational + sales roles; MANAGEMENT is read-only.
        String readBody = "{\"simulate\":true,\"epcCode\":\"EPC-7001\"}";
        for (String token : new String[]{adminToken, imToken, whToken, procToken, salesToken}) {
            mockMvc.perform(post("/api/v1/rfid/reads")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(readBody))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/v1/rfid/reads")
                        .header("Authorization", "Bearer " + mgmtToken)
                        .contentType(MediaType.APPLICATION_JSON).content(readBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void rfidReadableInfoIsReadOnlyAndReaderReportsSimulator() throws Exception {
        mockMvc.perform(get("/api/v1/rfid/reader")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readerName").value("SIMULATED-READER-01"))
                .andExpect(jsonPath("$.simulated").value(true));

        // Tag mappings block product deletion (FK-safe 409, like inventory).
        long cat = createCategory("ELEC", "Electronics");
        long product = createProduct("PHN-001", null, "Phone", cat, "0");
        registerTag("EPC-8001", product);

        mockMvc.perform(delete("/api/v1/products/" + product)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // ---- Helpers ----

    private void registerTag(String epc, long productId) throws Exception {
        mockMvc.perform(post("/api/v1/rfid/tags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"epcCode\":\"" + epc + "\",\"productId\":" + productId + "}"))
                .andExpect(status().isCreated());
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

    private static String productJson(String sku, String barcode, String name, long categoryId,
                                      String minimumStockLevel, boolean active) {
        return "{\"sku\":\"" + sku + "\","
                + (barcode == null ? "" : "\"barcode\":\"" + barcode + "\",")
                + "\"name\":\"" + name + "\",\"categoryId\":" + categoryId + ","
                + "\"unit\":\"PCS\",\"costPrice\":10.00,\"sellingPrice\":15.00,"
                + "\"minimumStockLevel\":" + minimumStockLevel + ",\"active\":" + active + "}";
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

    private long createProduct(String sku, String barcode, String name, long categoryId,
                               String minimumStockLevel) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, barcode, name, categoryId, minimumStockLevel, true)))
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
