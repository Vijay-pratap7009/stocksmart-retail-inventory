package com.stocksmart.service;

import com.stocksmart.dto.AdjustmentDirection;
import com.stocksmart.dto.AdjustmentRequest;
import com.stocksmart.dto.InventoryResponse;
import com.stocksmart.dto.ReceiptRequest;
import com.stocksmart.dto.StockTransactionResponse;
import com.stocksmart.dto.TransferRequest;
import com.stocksmart.dto.TransferResponse;
import com.stocksmart.entity.Inventory;
import com.stocksmart.entity.Product;
import com.stocksmart.entity.StockTransaction;
import com.stocksmart.entity.TransactionType;
import com.stocksmart.entity.User;
import com.stocksmart.entity.Warehouse;
import com.stocksmart.exception.InsufficientStockException;
import com.stocksmart.exception.ResourceNotFoundException;
import com.stocksmart.mapper.InventoryMapper;
import com.stocksmart.mapper.StockTransactionMapper;
import com.stocksmart.repository.InventoryRepository;
import com.stocksmart.repository.ProductRepository;
import com.stocksmart.repository.StockTransactionRepository;
import com.stocksmart.repository.UserRepository;
import com.stocksmart.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Inventory workflows (Day 3): receipts, adjustments, transfers, history.
 *
 * <p>Every quantity change runs in ONE database transaction together with its
 * audit row(s): validation happens before any write, so a rejected operation
 * leaves no balance change and no misleading transaction record.
 *
 * <p>Concurrency: stock-changing paths take a {@code PESSIMISTIC_WRITE} row
 * lock, so concurrent movements on the same product-warehouse pair serialize
 * instead of losing updates. First-time row creation can race on the unique
 * (product, warehouse) constraint — receipt/transfer retry ONCE in a fresh
 * transaction (the failed attempt rolls back cleanly), then proceed against
 * the now-existing locked row.
 */
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockTransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final InventoryMapper inventoryMapper;
    private final StockTransactionMapper transactionMapper;
    private final TransactionTemplate writeTransaction;

    public InventoryService(InventoryRepository inventoryRepository,
                            StockTransactionRepository transactionRepository,
                            ProductRepository productRepository,
                            WarehouseRepository warehouseRepository,
                            UserRepository userRepository,
                            InventoryMapper inventoryMapper,
                            StockTransactionMapper transactionMapper,
                            PlatformTransactionManager transactionManager) {
        this.inventoryRepository = inventoryRepository;
        this.transactionRepository = transactionRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.userRepository = userRepository;
        this.inventoryMapper = inventoryMapper;
        this.transactionMapper = transactionMapper;
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    // ---- Reads ----

    @Transactional(readOnly = true)
    public Page<InventoryResponse> list(String search, Long productId, Long warehouseId,
                                        Pageable pageable) {
        return inventoryRepository.search(normalizeSearch(search), productId, warehouseId, pageable)
                .map(inventoryMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public InventoryResponse get(Long id) {
        return inventoryMapper.toResponse(requireInventory(id));
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> byProduct(Long productId) {
        requireProduct(productId);
        return inventoryRepository.findByProductId(productId).stream()
                .map(inventoryMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> byWarehouse(Long warehouseId) {
        requireWarehouse(warehouseId);
        return inventoryRepository.findByWarehouseId(warehouseId).stream()
                .map(inventoryMapper::toResponse).toList();
    }

    // ---- Writes (each retried once in a fresh txn on creation races) ----

    public StockTransactionResponse receipt(ReceiptRequest request) {
        try {
            return writeTransaction.execute(status -> doReceipt(request));
        } catch (DataIntegrityViolationException firstAttempt) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                // Called inside a larger (order) transaction: retrying in the same
                // session after a flush failure is unsafe — propagate so the whole
                // operation rolls back atomically instead.
                throw firstAttempt;
            }
            // Concurrent first-receipt creation race: the row now exists —
            // retry once against the locked row.
            return writeTransaction.execute(status -> doReceipt(request));
        }
    }

    public StockTransactionResponse adjust(AdjustmentRequest request) {
        return writeTransaction.execute(status -> doAdjust(request));
    }

    public TransferResponse transfer(TransferRequest request) {
        try {
            return writeTransaction.execute(status -> doTransfer(request));
        } catch (DataIntegrityViolationException firstAttempt) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw firstAttempt;
            }
            // Concurrent destination-row creation race — retry once.
            return writeTransaction.execute(status -> doTransfer(request));
        }
    }

    // ---- Order integration hooks (Day 4) ----
    //
    // Called by the purchase/sales order services from inside THEIR
    // @Transactional methods, so stock change + audit row + order update
    // commit or roll back as one atomic unit. These methods deliberately use
    // no TransactionTemplate of their own (TransactionTemplate defaults to
    // PROPAGATION_REQUIRED and would join anyway) and perform no retry:
    // any failure propagates to roll back the whole order operation.

    /**
     * Increase stock for a purchase receipt. Creates the balance row on first
     * use. Must be called inside the caller's transaction.
     */
    public StockTransactionResponse receiveForOrder(Long productId, Long warehouseId,
                                                    BigDecimal quantity, String referenceNumber,
                                                    User actor, String notes) {
        Product product = requireProduct(productId);
        Warehouse warehouse = requireWarehouse(warehouseId);
        Inventory row = lockOrCreate(product, warehouse);
        BigDecimal before = row.getQuantityOnHand();
        BigDecimal after = before.add(quantity);
        row.setQuantityOnHand(after);
        row.touch();
        inventoryRepository.save(row);
        StockTransaction txn = transactionRepository.save(new StockTransaction(
                product, warehouse, TransactionType.RECEIPT, quantity, before, after,
                referenceNumber, null, actor, blankToNull(notes)));
        return transactionMapper.toResponse(txn);
    }

    /**
     * Decrease stock for a sales fulfillment, recording an ISSUE movement.
     * Must be called inside the caller's transaction.
     */
    public StockTransactionResponse issueForOrder(Long productId, Long warehouseId,
                                                  BigDecimal quantity, String referenceNumber,
                                                  User actor, String notes) {
        Product product = requireProduct(productId);
        Warehouse warehouse = requireWarehouse(warehouseId);
        Inventory row = inventoryRepository
                .lockByProductAndWarehouse(product.getId(), warehouse.getId())
                .orElse(null);
        BigDecimal before = row != null ? row.getQuantityOnHand() : BigDecimal.ZERO;
        if (row == null || before.compareTo(quantity) < 0) {
            throw new InsufficientStockException(
                    "Insufficient stock for SKU '" + product.getSku()
                            + "' at warehouse '" + warehouse.getCode()
                            + "': available " + before + ", requested " + quantity);
        }
        BigDecimal after = before.subtract(quantity);
        row.setQuantityOnHand(after);
        row.touch();
        inventoryRepository.save(row);
        StockTransaction txn = transactionRepository.save(new StockTransaction(
                product, warehouse, TransactionType.ISSUE, quantity, before, after,
                referenceNumber, null, actor, blankToNull(notes)));
        return transactionMapper.toResponse(txn);
    }

    // ---- Operation cores (run inside writeTransaction) ----

    private StockTransactionResponse doReceipt(ReceiptRequest request) {
        Product product = requireProduct(request.getProductId());
        Warehouse warehouse = requireWarehouse(request.getWarehouseId());
        User actor = currentUser();

        Inventory row = inventoryRepository
                .lockByProductAndWarehouse(product.getId(), warehouse.getId())
                .orElseGet(() -> inventoryRepository.save(
                        new Inventory(product, warehouse, BigDecimal.ZERO)));

        BigDecimal before = row.getQuantityOnHand();
        BigDecimal after = before.add(request.getQuantity());
        row.setQuantityOnHand(after);
        row.touch();
        inventoryRepository.save(row);

        TransactionType type = before.compareTo(BigDecimal.ZERO) == 0
                ? TransactionType.OPENING_BALANCE : TransactionType.RECEIPT;
        StockTransaction txn = transactionRepository.save(new StockTransaction(
                product, warehouse, type, request.getQuantity(), before, after,
                nextReference("RCT"), null, actor, blankToNull(request.getNotes())));
        return transactionMapper.toResponse(txn);
    }

    private StockTransactionResponse doAdjust(AdjustmentRequest request) {
        Product product = requireProduct(request.getProductId());
        Warehouse warehouse = requireWarehouse(request.getWarehouseId());
        User actor = currentUser();

        Inventory row = inventoryRepository
                .lockByProductAndWarehouse(product.getId(), warehouse.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No inventory record for product " + product.getId()
                                + " at warehouse " + warehouse.getId()));

        BigDecimal before = row.getQuantityOnHand();
        BigDecimal after;
        TransactionType type;
        if (request.getDirection() == AdjustmentDirection.IN) {
            after = before.add(request.getQuantity());
            type = TransactionType.ADJUSTMENT_IN;
        } else {
            after = before.subtract(request.getQuantity());
            if (after.compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientStockException(
                        "Insufficient stock for SKU '" + product.getSku()
                                + "' at warehouse '" + warehouse.getCode()
                                + "': available " + before
                                + ", requested " + request.getQuantity());
            }
            type = TransactionType.ADJUSTMENT_OUT;
        }
        row.setQuantityOnHand(after);
        row.touch();
        inventoryRepository.save(row);

        StockTransaction txn = transactionRepository.save(new StockTransaction(
                product, warehouse, type, request.getQuantity(), before, after,
                nextReference("ADJ"), request.getReason().trim(), actor,
                blankToNull(request.getNotes())));
        return transactionMapper.toResponse(txn);
    }

    private TransferResponse doTransfer(TransferRequest request) {
        if (request.getSourceWarehouseId().equals(request.getDestinationWarehouseId())) {
            throw new IllegalArgumentException(
                    "Source and destination warehouses must be different");
        }
        Product product = requireProduct(request.getProductId());
        Warehouse source = requireWarehouse(request.getSourceWarehouseId());
        Warehouse destination = requireWarehouse(request.getDestinationWarehouseId());
        User actor = currentUser();
        String reference = request.getReference() != null && !request.getReference().isBlank()
                ? request.getReference().trim().toUpperCase()
                : nextReference("TRF");
        String notes = blankToNull(request.getNotes());

        // Deterministic lock order (warehouse id ascending) avoids deadlocks
        // when opposite-direction transfers run concurrently.
        boolean sourceFirst = source.getId() < destination.getId();
        Inventory first = lockOrCreate(product,
                sourceFirst ? source : destination);
        Inventory second = lockOrCreate(product,
                sourceFirst ? destination : source);
        Inventory sourceRow = sourceFirst ? first : second;
        Inventory destRow = sourceFirst ? second : first;

        BigDecimal sourceBefore = sourceRow.getQuantityOnHand();
        if (sourceBefore.compareTo(request.getQuantity()) < 0) {
            throw new InsufficientStockException(
                    "Insufficient stock for SKU '" + product.getSku()
                            + "' at warehouse '" + source.getCode()
                            + "': available " + sourceBefore
                            + ", requested " + request.getQuantity());
        }
        BigDecimal destBefore = destRow.getQuantityOnHand();
        BigDecimal sourceAfter = sourceBefore.subtract(request.getQuantity());
        BigDecimal destAfter = destBefore.add(request.getQuantity());

        sourceRow.setQuantityOnHand(sourceAfter);
        sourceRow.touch();
        destRow.setQuantityOnHand(destAfter);
        destRow.touch();
        inventoryRepository.save(sourceRow);
        inventoryRepository.save(destRow);

        StockTransaction out = transactionRepository.save(new StockTransaction(
                product, source, TransactionType.TRANSFER_OUT, request.getQuantity(),
                sourceBefore, sourceAfter, reference, null, actor, notes));
        StockTransaction in = transactionRepository.save(new StockTransaction(
                product, destination, TransactionType.TRANSFER_IN, request.getQuantity(),
                destBefore, destAfter, reference, null, actor, notes));
        return new TransferResponse(reference,
                transactionMapper.toResponse(out), transactionMapper.toResponse(in));
    }

    // ---- Helpers ----

    private Inventory lockOrCreate(Product product, Warehouse warehouse) {
        return inventoryRepository
                .lockByProductAndWarehouse(product.getId(), warehouse.getId())
                .orElseGet(() -> inventoryRepository.save(
                        new Inventory(product, warehouse, BigDecimal.ZERO)));
    }

    private Inventory requireInventory(Long id) {
        return inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory " + id + " not found"));
    }

    private Product requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " not found"));
    }

    private Warehouse requireWarehouse(Long warehouseId) {
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Warehouse " + warehouseId + " not found"));
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new IllegalStateException("Stock operations require an authenticated user");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    private static String nextReference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase().replace("-", "X");
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
