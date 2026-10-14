package com.stocksmart.service;

import com.stocksmart.dto.CategoryRequest;
import com.stocksmart.dto.CategoryResponse;
import com.stocksmart.entity.Category;
import com.stocksmart.exception.ConflictException;
import com.stocksmart.exception.ResourceNotFoundException;
import com.stocksmart.mapper.CategoryMapper;
import com.stocksmart.repository.CategoryRepository;
import com.stocksmart.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Category master-data workflow (Day 2).
 * Enforces unique codes and protects categories referenced by products from deletion.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryRepository categoryRepository,
                           ProductRepository productRepository,
                           CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> list(String search, Boolean active, Pageable pageable) {
        return categoryRepository.search(normalizeSearch(search), active, pageable)
                .map(categoryMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return categoryMapper.toResponse(requireById(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String code = normalizeCode(request.getCode());
        if (categoryRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Category code '" + code + "' already exists");
        }
        Category category = new Category(
                code,
                request.getName().trim(),
                blankToNull(request.getDescription()),
                request.getActive() != null ? request.getActive() : true);
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = requireById(id);
        String code = normalizeCode(request.getCode());
        if (!category.getCode().equalsIgnoreCase(code)
                && categoryRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Category code '" + code + "' already exists");
        }
        category.setCode(code);
        category.setName(request.getName().trim());
        category.setDescription(blankToNull(request.getDescription()));
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }
        category.touch();
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        Category category = requireById(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new ConflictException(
                    "Category '" + category.getCode() + "' cannot be deleted because products reference it");
        }
        categoryRepository.delete(category);
    }

    private Category requireById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + id + " not found"));
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase();
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
