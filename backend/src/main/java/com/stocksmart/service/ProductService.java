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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product master-data workflow (Day 2).
 * Every product references a valid category; SKU is unique; prices are validated
 * by Bean Validation (non-negative) before reaching this layer.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          InventoryRepository inventoryRepository,
                          ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryRepository = inventoryRepository;
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

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String sku = normalizeSku(request.getSku());
        if (productRepository.existsBySkuIgnoreCase(sku)) {
            throw new ConflictException("Product SKU '" + sku + "' already exists");
        }
        Category category = requireCategory(request.getCategoryId());
        Product product = new Product(
                sku,
                request.getName().trim(),
                blankToNull(request.getDescription()),
                category,
                blankToNull(request.getBrand()),
                request.getUnit().trim(),
                request.getCostPrice(),
                request.getSellingPrice(),
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
        product.setSku(sku);
        product.setName(request.getName().trim());
        product.setDescription(blankToNull(request.getDescription()));
        product.setCategory(requireCategory(request.getCategoryId()));
        product.setBrand(blankToNull(request.getBrand()));
        product.setUnit(request.getUnit().trim());
        product.setCostPrice(request.getCostPrice());
        product.setSellingPrice(request.getSellingPrice());
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
        productRepository.delete(product);
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
