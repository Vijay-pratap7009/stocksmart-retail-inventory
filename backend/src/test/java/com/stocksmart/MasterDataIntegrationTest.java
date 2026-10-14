package com.stocksmart;

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
import com.stocksmart.repository.RoleRepository;
import com.stocksmart.repository.SalesFulfillmentRepository;
import com.stocksmart.repository.SalesOrderRepository;
import com.stocksmart.repository.StockTransactionRepository;
import com.stocksmart.repository.SupplierRepository;
import com.stocksmart.repository.UserRepository;
import com.stocksmart.repository.WarehouseRepository;
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
 * Day 2 master-data matrix: CRUD + duplicate rejection + invalid references +
 * search/filter + referenced-delete protection + validation + RBAC.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MasterDataIntegrationTest {

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
    private PurchaseReceiptRepository purchaseReceiptRepository;

    @Autowired
    private SalesFulfillmentRepository salesFulfillmentRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockTransactionRepository transactionRepository;

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
    void categoryCrudAndDuplicateRejected() throws Exception {
        String body = "{\"code\":\"ELEC\",\"name\":\"Electronics\",\"description\":\"Gadgets\",\"active\":true}";

        MvcResult created = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ELEC"))
                .andExpect(jsonPath("$.name").value("Electronics"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Duplicate code (case-insensitive) → 409
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"elec\",\"name\":\"Dupe\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Retrieve + update
        mockMvc.perform(get("/api/v1/categories/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Electronics"));

        mockMvc.perform(put("/api/v1/categories/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ELEC\",\"name\":\"Electronics v2\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Electronics v2"))
                .andExpect(jsonPath("$.active").value(false));

        // Search + active filter
        mockMvc.perform(get("/api/v1/categories?search=elec")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("ELEC"));
        mockMvc.perform(get("/api/v1/categories?active=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Delete unreferenced → 204, then 404
        mockMvc.perform(delete("/api/v1/categories/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/categories/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void productRequiresValidCategoryAndUniqueSku() throws Exception {
        long catId = createCategory(adminToken, "ELEC", "Electronics");

        String product = "{\"sku\":\"PHN-001\",\"name\":\"Phone\",\"description\":\"Smart\","
                + "\"categoryId\":" + catId + ",\"brand\":\"Acme\",\"unit\":\"PCS\","
                + "\"costPrice\":100.00,\"sellingPrice\":149.99,\"active\":true}";

        MvcResult created = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("PHN-001"))
                .andExpect(jsonPath("$.categoryId").value((int) catId))
                .andExpect(jsonPath("$.categoryName").value("Electronics"))
                .andReturn();
        long productId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Duplicate SKU → 409
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isConflict());

        // Unknown category → 404 (fresh SKU so the duplicate check passes first)
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(product.replace("\"categoryId\":" + catId, "\"categoryId\":999999")
                                .replace("PHN-001", "PHN-999")))
                .andExpect(status().isNotFound());

        // Negative price → 400
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(product.replace("\"costPrice\":100.00", "\"costPrice\":-5.00")
                                .replace("PHN-001", "PHN-002")))
                .andExpect(status().isBadRequest());

        // Missing name → 400
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(product.replace("\"name\":\"Phone\"", "\"name\":\"\"")
                                .replace("PHN-001", "PHN-003")))
                .andExpect(status().isBadRequest());

        // Update + retrieve
        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(product.replace("\"name\":\"Phone\"", "\"name\":\"Phone Pro\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Phone Pro"));
        mockMvc.perform(get("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellingPrice").value(149.99));

        // Update to unknown category → 404
        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(product.replace("\"categoryId\":" + catId, "\"categoryId\":999999")))
                .andExpect(status().isNotFound());
    }

    @Test
    void productSearchAndCategoryFilter() throws Exception {
        long catA = createCategory(adminToken, "ELEC", "Electronics");
        long catB = createCategory(adminToken, "GROC", "Grocery");

        createProduct(adminToken, "PHN-001", "Phone", catA, true);
        createProduct(adminToken, "APL-001", "Apple", catB, true);
        createProduct(adminToken, "PHN-002", "Phone Case", catA, false);

        // Search by SKU fragment
        mockMvc.perform(get("/api/v1/products?search=phn")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Search by name
        mockMvc.perform(get("/api/v1/products?search=apple")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("APL-001"));

        // Category filter
        mockMvc.perform(get("/api/v1/products?categoryId=" + catB)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Active filter
        mockMvc.perform(get("/api/v1/products?active=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Combined category + active filter
        mockMvc.perform(get("/api/v1/products?categoryId=" + catA + "&active=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void categoryDeleteBlockedWhenReferenced() throws Exception {
        long catId = createCategory(adminToken, "ELEC", "Electronics");
        long productId = createProduct(adminToken, "PHN-001", "Phone", catId, true);

        // Referenced category → 409 with clear message
        mockMvc.perform(delete("/api/v1/categories/" + catId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("products")));

        // Remove product, then category deletes cleanly
        mockMvc.perform(delete("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/categories/" + catId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void warehouseCrudAndDuplicateRejected() throws Exception {
        String body = "{\"code\":\"WH-01\",\"name\":\"Central Depot\",\"address\":\"1 Main St\","
                + "\"city\":\"Mumbai\",\"state\":\"MH\",\"postalCode\":\"400001\","
                + "\"contactPerson\":\"Asha\",\"contactEmail\":\"asha@example.com\","
                + "\"contactPhone\":\"+91 9000000001\",\"active\":true}";

        MvcResult created = mockMvc.perform(post("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("WH-01"))
                .andExpect(jsonPath("$.city").value("Mumbai"))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Duplicate code → 409
        mockMvc.perform(post("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Central Depot", "Other")))
                .andExpect(status().isConflict());

        // Invalid email → 400
        mockMvc.perform(post("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("WH-01", "WH-02")
                                .replace("asha@example.com", "not-an-email")))
                .andExpect(status().isBadRequest());

        // Search by city
        mockMvc.perform(get("/api/v1/warehouses?search=mumbai")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Update + delete
        mockMvc.perform(put("/api/v1/warehouses/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Central Depot", "Central Depot v2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Central Depot v2"));
        mockMvc.perform(delete("/api/v1/warehouses/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/warehouses/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void supplierCrudAndDuplicateRejected() throws Exception {
        String body = "{\"code\":\"SUP-01\",\"name\":\"Acme Supplies\",\"contactPerson\":\"Ravi\","
                + "\"email\":\"ravi@acme.example\",\"phone\":\"+91 9000000002\","
                + "\"address\":\"2 Market Rd\",\"city\":\"Delhi\",\"state\":\"DL\","
                + "\"postalCode\":\"110001\",\"active\":true}";

        MvcResult created = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + procToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUP-01"))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Duplicate code → 409
        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Acme Supplies", "Other")))
                .andExpect(status().isConflict());

        // Invalid email → 400
        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("SUP-01", "SUP-02")
                                .replace("ravi@acme.example", "bad")))
                .andExpect(status().isBadRequest());

        // Search by contact info
        mockMvc.perform(get("/api/v1/suppliers?search=ravi")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Update + delete
        mockMvc.perform(put("/api/v1/suppliers/" + id)
                        .header("Authorization", "Bearer " + procToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Acme Supplies", "Acme Supplies Ltd")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Supplies Ltd"));
        mockMvc.perform(delete("/api/v1/suppliers/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/suppliers/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rbacIsEnforcedOnMasterData() throws Exception {
        // No token → 401 everywhere
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"X\",\"name\":\"Xyz\"}"))
                .andExpect(status().isUnauthorized());

        // SALES: reads products+categories, writes nothing, no warehouse/supplier reads
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + salesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"S-1\",\"name\":\"Nope\",\"categoryId\":1,"
                                + "\"unit\":\"PCS\",\"costPrice\":1,\"sellingPrice\":2}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isForbidden());

        // WAREHOUSE_STAFF: reads warehouses, cannot write
        mockMvc.perform(get("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + whToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + whToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WH-X\",\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden());

        // PROCUREMENT: manages suppliers, reads products — but cannot write products
        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + procToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SUP-RBAC\",\"name\":\"Rbac Supplier\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + procToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"S-2\",\"name\":\"Nope\",\"categoryId\":1,"
                                + "\"unit\":\"PCS\",\"costPrice\":1,\"sellingPrice\":2}"))
                .andExpect(status().isForbidden());

        // INVENTORY_MANAGER: writes products/warehouses/categories, not suppliers
        long catId = createCategory(imToken, "IMCAT", "IM Category");
        mockMvc.perform(post("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + imToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WH-IM\",\"name\":\"IM Depot\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + imToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SUP-IM\",\"name\":\"IM Supplier\"}"))
                .andExpect(status().isForbidden());
        createProduct(imToken, "IM-001", "IM Product", catId, true);

        // MANAGEMENT: read-only
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + mgmtToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + mgmtToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + mgmtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MGMT\",\"name\":\"Mgmt Cat\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingRecordsReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/categories/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/products/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/warehouses/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/suppliers/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/products/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    private long createCategory(String token, String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createProduct(String token, String sku, String name, long categoryId, boolean active)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"" + name + "\","
                                + "\"categoryId\":" + categoryId + ",\"unit\":\"PCS\","
                                + "\"costPrice\":10.00,\"sellingPrice\":15.00,"
                                + "\"active\":" + active + "}"))
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
