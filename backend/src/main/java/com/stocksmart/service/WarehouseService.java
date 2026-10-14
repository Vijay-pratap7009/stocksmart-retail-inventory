package com.stocksmart.service;

import com.stocksmart.dto.WarehouseRequest;
import com.stocksmart.dto.WarehouseResponse;
import com.stocksmart.entity.Warehouse;
import com.stocksmart.exception.ConflictException;
import com.stocksmart.exception.ResourceNotFoundException;
import com.stocksmart.mapper.WarehouseMapper;
import com.stocksmart.repository.WarehouseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Warehouse master-data workflow (Day 2).
 * Standalone master table — no inventory relationships exist yet (Day 3),
 * so deletion only guards the record's own existence.
 */
@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper warehouseMapper;

    public WarehouseService(WarehouseRepository warehouseRepository,
                            WarehouseMapper warehouseMapper) {
        this.warehouseRepository = warehouseRepository;
        this.warehouseMapper = warehouseMapper;
    }

    @Transactional(readOnly = true)
    public Page<WarehouseResponse> list(String search, Boolean active, Pageable pageable) {
        return warehouseRepository.search(normalizeSearch(search), active, pageable)
                .map(warehouseMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse get(Long id) {
        return warehouseMapper.toResponse(requireById(id));
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        String code = normalizeCode(request.getCode());
        if (warehouseRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Warehouse code '" + code + "' already exists");
        }
        Warehouse warehouse = new Warehouse(
                code,
                request.getName().trim(),
                blankToNull(request.getAddress()),
                blankToNull(request.getCity()),
                blankToNull(request.getState()),
                blankToNull(request.getPostalCode()),
                blankToNull(request.getContactPerson()),
                blankToNull(request.getContactEmail()),
                blankToNull(request.getContactPhone()),
                request.getActive() != null ? request.getActive() : true);
        return warehouseMapper.toResponse(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest request) {
        Warehouse warehouse = requireById(id);
        String code = normalizeCode(request.getCode());
        if (!warehouse.getCode().equalsIgnoreCase(code)
                && warehouseRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Warehouse code '" + code + "' already exists");
        }
        warehouse.setCode(code);
        warehouse.setName(request.getName().trim());
        warehouse.setAddress(blankToNull(request.getAddress()));
        warehouse.setCity(blankToNull(request.getCity()));
        warehouse.setState(blankToNull(request.getState()));
        warehouse.setPostalCode(blankToNull(request.getPostalCode()));
        warehouse.setContactPerson(blankToNull(request.getContactPerson()));
        warehouse.setContactEmail(blankToNull(request.getContactEmail()));
        warehouse.setContactPhone(blankToNull(request.getContactPhone()));
        if (request.getActive() != null) {
            warehouse.setActive(request.getActive());
        }
        warehouse.touch();
        return warehouseMapper.toResponse(warehouseRepository.save(warehouse));
    }

    @Transactional
    public void delete(Long id) {
        // Day 3 inventory relations do not exist yet — direct delete is safe.
        // Revisit when stock-by-warehouse tables land.
        warehouseRepository.delete(requireById(id));
    }

    private Warehouse requireById(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse " + id + " not found"));
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
