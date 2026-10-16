package com.stocksmart.service;

import com.stocksmart.dto.ProductRequest;
import com.stocksmart.dto.ProductResponse;
import com.stocksmart.entity.Category;
import com.stocksmart.entity.Product;
import com.stocksmart.exception.ConflictException;
import com.stocksmart.exception.ResourceNotFoundException;
import com.stocksmart.mapper.ProductMapper;
import com.stocksmart.repository.CategoryRepository;
import com.stocksmart.repository.InventoryRepository;
import com.stocksmart.repository.ProductRepository;
import com.stocksmart.repository.RfidTagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product master-data workflow (Day 2, extended Day 5).
 * Every product references a valid category; SKU is unique; optional barcode
 * is unique when present; prices and the minimum stock level are validated
 * by Bean Validation before reaching this layer.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final RfidTagRepository rfidTagRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          InventoryRepository inventoryRepository,
                          RfidTagRepository rfidTagRepository,
                          ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryRepository = inventoryRepository;
        this.rfidTagRepository = rfidTagRepository;
        this.productMapper = productMapper;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(String search, Boolean active, Long categoryId, Pageable pageable) {
        return productRepository.search(normalizeSearch(search), active, categoryId, pageable)
                .map(productMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return productMapper.toResponse(requireById(id));
    }

    /**
     * Day 5 identification: resolve a product from a scanned/manually entered
     * barcode. Read-only — never changes stock.
     */
    @Transactional(readOnly = true)
    public ProductResponse getByBarcode(String barcode) {
        String normalized = normalizeBarcode(barcode);
        if (normalized == null) {
            throw new IllegalArgumentException("Barcode must not be blank");
        }
        return productMapper.toResponse(productRepository.findByBarcodeIgnoreCase(normalized)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No product found for barcode '" + normalized + "'")));
    }

    /**
     * Day 5 identification: resolve a product from its SKU. Read-only —
     * never changes stock.
     */
    @Transactional(readOnly = true)
    public ProductResponse getBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU must not be blank");
        }
        String normalized = sku.trim().toUpperCase();
        return productMapper.toResponse(productRepository.findBySkuIgnoreCase(normalized)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No product found for SKU '" + normalized + "'")));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String sku = normalizeSku(request.getSku());
        if (productRepository.existsBySkuIgnoreCase(sku)) {
            throw new ConflictException("Product SKU '" + sku + "' already exists");
        }
        String barcode = normalizeBarcode(request.getBarcode());
        requireBarcodeAvailable(barcode, null);
        Category category = requireCategory(request.getCategoryId());
        Product product = new Product(
                sku,
                barcode,
                request.getName().trim(),
                blankToNull(request.getDescription()),
                category,
                blankToNull(request.getBrand()),
                request.getUnit().trim(),
                request.getCostPrice(),
                request.getSellingPrice(),
                request.getMinimumStockLevel(),
                request.getActive() != null ? request.getActive() : true);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = requireById(id);
        String sku = normalizeSku(request.getSku());
        if (!product.getSku().equalsIgnoreCase(sku)
                && productRepository.existsBySkuIgnoreCase(sku)) {
            throw new ConflictException("Product SKU '" + sku + "' already exists");
        }
        String barcode = normalizeBarcode(request.getBarcode());
        requireBarcodeAvailable(barcode, product.getId());
        product.setSku(sku);
        product.setBarcode(barcode);
        product.setName(request.getName().trim());
        product.setDescription(blankToNull(request.getDescription()));
        product.setCategory(requireCategory(request.getCategoryId()));
        product.setBrand(blankToNull(request.getBrand()));
        product.setUnit(request.getUnit().trim());
        product.setCostPrice(request.getCostPrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setMinimumStockLevel(
                request.getMinimumStockLevel() != null
                        ? request.getMinimumStockLevel()
                        : java.math.BigDecimal.ZERO);
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
        product.touch();
        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        Product product = requireById(id);
        if (inventoryRepository.existsByProductId(id)) {
            throw new ConflictException(
                    "Product '" + product.getSku() + "' cannot be deleted because inventory records reference it");
        }
        if (rfidTagRepository.existsByProductId(id)) {
            throw new ConflictException(
                    "Product '" + product.getSku() + "' cannot be deleted because RFID tags reference it");
        }
        productRepository.delete(product);
    }

    private void requireBarcodeAvailable(String barcode, Long selfId) {
        if (barcode == null) {
            return;
        }
        productRepository.findByBarcodeIgnoreCase(barcode).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ConflictException("Product barcode '" + barcode + "' already exists");
            }
        });
    }

    private Product requireById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " not found"));
    }

    private Category requireCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + categoryId + " not found"));
    }

    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase();
    }

    /** Barcodes are stored UPPERCASE + trimmed (documented Day 5 convention); blank → null. */
    private static String normalizeBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return null;
        }
        return barcode.trim().toUpperCase();
    }

    private static String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
