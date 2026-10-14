package com.stocksmart.service;

import com.stocksmart.dto.PurchaseOrderRequest;
import com.stocksmart.dto.PurchaseOrderResponse;
import com.stocksmart.dto.PurchaseReceiptRequest;
import com.stocksmart.dto.PurchaseReceiptResponse;
import com.stocksmart.entity.Product;
import com.stocksmart.entity.PurchaseOrder;
import com.stocksmart.entity.PurchaseOrderItem;
import com.stocksmart.entity.PurchaseOrderStatus;
import com.stocksmart.entity.PurchaseReceipt;
import com.stocksmart.entity.PurchaseReceiptItem;
import com.stocksmart.entity.Supplier;
import com.stocksmart.entity.User;
import com.stocksmart.entity.Warehouse;
import com.stocksmart.exception.ConflictException;
import com.stocksmart.exception.ResourceNotFoundException;
import com.stocksmart.mapper.PurchaseOrderMapper;
import com.stocksmart.repository.ProductRepository;
import com.stocksmart.repository.PurchaseOrderRepository;
import com.stocksmart.repository.PurchaseReceiptRepository;
import com.stocksmart.repository.SupplierRepository;
import com.stocksmart.repository.UserRepository;
import com.stocksmart.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purchase order workflows (Day 4).
 *
 * <p>Goods receipt runs in ONE transaction: receipt validation, receipt
 * record, inventory increase via {@code InventoryService} (joined, never a
 * separate commit), stock transactions, received-quantity and status updates.
 * The order row is pessimistically locked so concurrent receipts serialize.
 * Cancelling never reverses stock already received.
 */
@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseReceiptRepository receiptRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final PurchaseOrderMapper orderMapper;

    public PurchaseOrderService(PurchaseOrderRepository orderRepository,
                                PurchaseReceiptRepository receiptRepository,
                                SupplierRepository supplierRepository,
                                WarehouseRepository warehouseRepository,
                                ProductRepository productRepository,
                                UserRepository userRepository,
                                InventoryService inventoryService,
                                PurchaseOrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.receiptRepository = receiptRepository;
        this.supplierRepository = supplierRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.orderMapper = orderMapper;
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> list(String search, PurchaseOrderStatus status,
                                            Long supplierId, Pageable pageable) {
        return orderRepository.search(normalizeSearch(search), status, supplierId, pageable)
                .map(orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(Long id) {
        return orderMapper.toResponse(requireById(id));
    }

    @Transactional(readOnly = true)
    public List<PurchaseReceiptResponse> receipts(Long id) {
        requireById(id);
        return receiptRepository.findByPurchaseOrderIdOrderByReceivedAtDesc(id).stream()
                .map(orderMapper::toReceiptResponse).toList();
    }

    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderRequest request) {
        String poNumber = normalizeNumber(request.getPoNumber(), "PO");
        if (orderRepository.existsByPoNumberIgnoreCase(poNumber)) {
            throw new ConflictException("Purchase order number '" + poNumber + "' already exists");
        }
        PurchaseOrder order = new PurchaseOrder(
                poNumber,
                requireSupplier(request.getSupplierId()),
                requireWarehouse(request.getWarehouseId()),
                request.getOrderDate(),
                request.getExpectedDeliveryDate(),
                blankToNull(request.getNotes()),
                currentUser());
        replaceLines(order, request);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional
    public PurchaseOrderResponse update(Long id, PurchaseOrderRequest request) {
        PurchaseOrder order = requireById(id);
        requireDraft(order, "update");
        if (request.getPoNumber() != null && !request.getPoNumber().isBlank()
                && !order.getPoNumber().equalsIgnoreCase(request.getPoNumber().trim())
                && orderRepository.existsByPoNumberIgnoreCase(request.getPoNumber().trim())) {
            throw new ConflictException(
                    "Purchase order number '" + request.getPoNumber().trim() + "' already exists");
        }
        if (request.getPoNumber() != null && !request.getPoNumber().isBlank()) {
            order.setPoNumber(request.getPoNumber().trim().toUpperCase());
        }
        order.setSupplier(requireSupplier(request.getSupplierId()));
        order.setWarehouse(requireWarehouse(request.getWarehouseId()));
        order.setOrderDate(request.getOrderDate());
        order.setExpectedDeliveryDate(request.getExpectedDeliveryDate());
        order.setNotes(blankToNull(request.getNotes()));
        replaceLines(order, request);
        order.touch();
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional
    public PurchaseOrderResponse submit(Long id) {
        PurchaseOrder order = requireById(id);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new ConflictException(
                    "Only draft purchase orders can be submitted (current: " + order.getStatus() + ")");
        }
        if (order.getItems().isEmpty()) {
            throw new ConflictException("Cannot submit a purchase order with no line items");
        }
        order.setStatus(PurchaseOrderStatus.SUBMITTED);
        order.touch();
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional
    public PurchaseOrderResponse cancel(Long id) {
        PurchaseOrder order = requireById(id);
        if (order.getStatus() == PurchaseOrderStatus.RECEIVED) {
            throw new ConflictException("Fully received purchase orders cannot be cancelled");
        }
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new ConflictException("Purchase order is already cancelled");
        }
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        order.touch();
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional
    public PurchaseReceiptResponse receive(Long id, PurchaseReceiptRequest request) {
        PurchaseOrder order = orderRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order " + id + " not found"));
        if (order.getStatus() != PurchaseOrderStatus.SUBMITTED
                && order.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new ConflictException("Purchase order " + order.getPoNumber()
                    + " is not eligible for receiving (status: " + order.getStatus() + ")");
        }
        String reference = normalizeNumber(request.getReference(), "POR");
        if (receiptRepository.existsByReceiptNumberIgnoreCase(reference)) {
            throw new ConflictException(
                    "Receipt reference '" + reference + "' was already processed");
        }
        User actor = currentUser();
        String notes = blankToNull(request.getNotes());

        // Validate every line before touching stock or quantities.
        for (var line : request.getLines()) {
            PurchaseOrderItem item = order.getItems().stream()
                    .filter(i -> i.getId().equals(line.getPoItemId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "PO line " + line.getPoItemId()
                                    + " does not belong to order " + order.getPoNumber()));
            BigDecimal remaining = item.getOrderedQuantity().subtract(item.getReceivedQuantity());
            if (line.getQuantity().compareTo(remaining) > 0) {
                throw new ConflictException("Receipt of " + line.getQuantity()
                        + " for SKU '" + item.getProduct().getSku()
                        + "' exceeds remaining ordered quantity " + remaining);
            }
        }

        PurchaseReceipt receipt = new PurchaseReceipt(reference, order, actor, notes);
        for (var line : request.getLines()) {
            PurchaseOrderItem item = order.getItems().stream()
                    .filter(i -> i.getId().equals(line.getPoItemId()))
                    .findFirst()
                    .orElseThrow();
            inventoryService.receiveForOrder(
                    item.getProduct().getId(), order.getWarehouse().getId(),
                    line.getQuantity(), reference, actor, notes);
            item.setReceivedQuantity(item.getReceivedQuantity().add(line.getQuantity()));
            item.touch();
            receipt.addLine(new PurchaseReceiptItem(item, item.getProduct(), line.getQuantity()));
        }
        receiptRepository.save(receipt);

        boolean fullyReceived = order.getItems().stream()
                .allMatch(i -> i.getReceivedQuantity().compareTo(i.getOrderedQuantity()) >= 0);
        order.setStatus(fullyReceived
                ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
        order.touch();
        orderRepository.save(order);
        return orderMapper.toReceiptResponse(receipt);
    }

    private void replaceLines(PurchaseOrder order, PurchaseOrderRequest request) {
        order.setItems(request.getItems().stream()
                .map(line -> new PurchaseOrderItem(
                        requireProduct(line.getProductId()),
                        line.getOrderedQuantity(),
                        line.getUnitCost()))
                .toList());
    }

    private void requireDraft(PurchaseOrder order, String action) {
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new ConflictException("Only draft purchase orders can be " + action
                    + "d (current: " + order.getStatus() + ")");
        }
    }

    private PurchaseOrder requireById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order " + id + " not found"));
    }

    private Supplier requireSupplier(Long supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier " + supplierId + " not found"));
    }

    private Warehouse requireWarehouse(Long warehouseId) {
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Warehouse " + warehouseId + " not found"));
    }

    private Product requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " not found"));
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new IllegalStateException("Order operations require an authenticated user");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    private static String normalizeNumber(String raw, String prefix) {
        if (raw == null || raw.isBlank()) {
            return prefix + "-" + UUID.randomUUID().toString()
                    .substring(0, 8).toUpperCase().replace("-", "X");
        }
        return raw.trim().toUpperCase();
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
