package com.stocksmart.service;

import com.stocksmart.dto.SupplierRequest;
import com.stocksmart.dto.SupplierResponse;
import com.stocksmart.entity.Supplier;
import com.stocksmart.exception.ConflictException;
import com.stocksmart.exception.ResourceNotFoundException;
import com.stocksmart.mapper.SupplierMapper;
import com.stocksmart.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplier master-data workflow (Day 2).
 * Standalone master table — purchase-order relations arrive Day 4,
 * so deletion only guards the record's own existence.
 */
@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;

    public SupplierService(SupplierRepository supplierRepository,
                           SupplierMapper supplierMapper) {
        this.supplierRepository = supplierRepository;
        this.supplierMapper = supplierMapper;
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> list(String search, Boolean active, Pageable pageable) {
        return supplierRepository.search(normalizeSearch(search), active, pageable)
                .map(supplierMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(Long id) {
        return supplierMapper.toResponse(requireById(id));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        String code = normalizeCode(request.getCode());
        if (supplierRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Supplier code '" + code + "' already exists");
        }
        Supplier supplier = new Supplier(
                code,
                request.getName().trim(),
                blankToNull(request.getContactPerson()),
                blankToNull(request.getEmail()),
                blankToNull(request.getPhone()),
                blankToNull(request.getAddress()),
                blankToNull(request.getCity()),
                blankToNull(request.getState()),
                blankToNull(request.getPostalCode()),
                request.getActive() != null ? request.getActive() : true);
        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier supplier = requireById(id);
        String code = normalizeCode(request.getCode());
        if (!supplier.getCode().equalsIgnoreCase(code)
                && supplierRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Supplier code '" + code + "' already exists");
        }
        supplier.setCode(code);
        supplier.setName(request.getName().trim());
        supplier.setContactPerson(blankToNull(request.getContactPerson()));
        supplier.setEmail(blankToNull(request.getEmail()));
        supplier.setPhone(blankToNull(request.getPhone()));
        supplier.setAddress(blankToNull(request.getAddress()));
        supplier.setCity(blankToNull(request.getCity()));
        supplier.setState(blankToNull(request.getState()));
        supplier.setPostalCode(blankToNull(request.getPostalCode()));
        if (request.getActive() != null) {
            supplier.setActive(request.getActive());
        }
        supplier.touch();
        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public void delete(Long id) {
        // Day 4 purchase-order relations do not exist yet — direct delete is safe.
        // Revisit when purchase orders land.
        supplierRepository.delete(requireById(id));
    }

    private Supplier requireById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier " + id + " not found"));
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
