package com.stocksmart.service;

import com.stocksmart.dto.FulfillmentRequest;
import com.stocksmart.dto.FulfillmentResponse;
import com.stocksmart.dto.SalesOrderRequest;
import com.stocksmart.dto.SalesOrderResponse;
import com.stocksmart.entity.Product;
import com.stocksmart.entity.SalesFulfillment;
import com.stocksmart.entity.SalesFulfillmentItem;
import com.stocksmart.entity.SalesOrder;
import com.stocksmart.entity.SalesOrderItem;
import com.stocksmart.entity.SalesOrderStatus;
import com.stocksmart.entity.User;
import com.stocksmart.entity.Warehouse;
import com.stocksmart.exception.ConflictException;
import com.stocksmart.exception.ResourceNotFoundException;
import com.stocksmart.mapper.SalesOrderMapper;
import com.stocksmart.repository.ProductRepository;
import com.stocksmart.repository.SalesFulfillmentRepository;
import com.stocksmart.repository.SalesOrderRepository;
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
 * Sales order workflows (Day 4).
 *
 * <p>Fulfillment runs in ONE transaction: availability validation, inventory
 * decrease via {@code InventoryService} (joined, never a separate commit),
 * ISSUE transactions, fulfilled-quantity and status updates. The order row is
 * pessimistically locked so concurrent fulfillments serialize. Confirming
 * never reserves stock — availability is enforced at fulfillment time.
 */
@Service
public class SalesOrderService {

    private final SalesOrderRepository orderRepository;
    private final SalesFulfillmentRepository fulfillmentRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final SalesOrderMapper orderMapper;

    public SalesOrderService(SalesOrderRepository orderRepository,
                             SalesFulfillmentRepository fulfillmentRepository,
                             WarehouseRepository warehouseRepository,
                             ProductRepository productRepository,
                             UserRepository userRepository,
                             InventoryService inventoryService,
                             SalesOrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.fulfillmentRepository = fulfillmentRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.orderMapper = orderMapper;
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> list(String search, SalesOrderStatus status,
                                         Long warehouseId, Pageable pageable) {
        return orderRepository.search(normalizeSearch(search), status, warehouseId, pageable)
                .map(orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse get(Long id) {
        return orderMapper.toResponse(requireById(id));
    }

    @Transactional(readOnly = true)
    public List<FulfillmentResponse> fulfillments(Long id) {
        requireById(id);
        return fulfillmentRepository.findBySalesOrderIdOrderByFulfilledAtDesc(id).stream()
                .map(orderMapper::toFulfillmentResponse).toList();
    }

    @Transactional
    public SalesOrderResponse create(SalesOrderRequest request) {
        String soNumber = normalizeNumber(request.getSoNumber(), "SO");
        if (orderRepository.existsBySoNumberIgnoreCase(soNumber)) {
            throw new ConflictException("Sales order number '" + soNumber + "' already exists");
        }
        SalesOrder order = new SalesOrder(
                soNumber,
                requireWarehouse(request.getWarehouseId()),
                request.getCustomerName().trim(),
                blankToNull(request.getCustomerContact()),
                request.getOrderDate(),
                blankToNull(request.getNotes()),
                currentUser());
        replaceLines(order, request);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional
    public SalesOrderResponse update(Long id, SalesOrderRequest request) {
        SalesOrder order = requireById(id);
        requireDraft(order, "update");
        if (request.getSoNumber() != null && !request.getSoNumber().isBlank()
                && !order.getSoNumber().equalsIgnoreCase(request.getSoNumber().trim())
                && orderRepository.existsBySoNumberIgnoreCase(request.getSoNumber().trim())) {
            throw new ConflictException(
                    "Sales order number '" + request.getSoNumber().trim() + "' already exists");
        }
        if (request.getSoNumber() != null && !request.getSoNumber().isBlank()) {
            order.setSoNumber(request.getSoNumber().trim().toUpperCase());
        }
        order.setWarehouse(requireWarehouse(request.getWarehouseId()));
        order.setCustomerName(request.getCustomerName().trim());
        order.setCustomerContact(blankToNull(request.getCustomerContact()));
        order.setOrderDate(request.getOrderDate());
        order.setNotes(blankToNull(request.getNotes()));
        replaceLines(order, request);
        order.touch();
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional
    public SalesOrderResponse confirm(Long id) {
        SalesOrder order = requireById(id);
        if (order.getStatus() != SalesOrderStatus.DRAFT) {
            throw new ConflictException(
                    "Only draft sales orders can be confirmed (current: " + order.getStatus() + ")");
        }
        if (order.getItems().isEmpty()) {
            throw new ConflictException("Cannot confirm a sales order with no line items");
        }
        order.setStatus(SalesOrderStatus.CONFIRMED);
        order.touch();
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional
    public SalesOrderResponse cancel(Long id) {
        SalesOrder order = requireById(id);
        if (order.getStatus() == SalesOrderStatus.FULFILLED) {
            throw new ConflictException("Fulfilled sales orders cannot be cancelled");
        }
        if (order.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new ConflictException("Sales order is already cancelled");
        }
        order.setStatus(SalesOrderStatus.CANCELLED);
        order.touch();
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional
    public FulfillmentResponse fulfill(Long id, FulfillmentRequest request) {
        SalesOrder order = orderRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sales order " + id + " not found"));
        if (order.getStatus() != SalesOrderStatus.CONFIRMED
                && order.getStatus() != SalesOrderStatus.PARTIALLY_FULFILLED) {
            throw new ConflictException("Sales order " + order.getSoNumber()
                    + " is not eligible for fulfillment (status: " + order.getStatus() + ")");
        }
        String reference = normalizeNumber(request.getReference(), "SOF");
        if (fulfillmentRepository.existsByFulfillmentNumberIgnoreCase(reference)) {
            throw new ConflictException(
                    "Fulfillment reference '" + reference + "' was already processed");
        }
        User actor = currentUser();
        String notes = blankToNull(request.getNotes());

        // Validate every line before issuing any stock.
        for (var line : request.getLines()) {
            SalesOrderItem item = order.getItems().stream()
                    .filter(i -> i.getId().equals(line.getSoItemId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "SO line " + line.getSoItemId()
                                    + " does not belong to order " + order.getSoNumber()));
            BigDecimal remaining = item.getOrderedQuantity().subtract(item.getFulfilledQuantity());
            if (line.getQuantity().compareTo(remaining) > 0) {
                throw new ConflictException("Fulfillment of " + line.getQuantity()
                        + " for SKU '" + item.getProduct().getSku()
                        + "' exceeds remaining ordered quantity " + remaining);
            }
        }

        SalesFulfillment fulfillment = new SalesFulfillment(reference, order, actor, notes);
        for (var line : request.getLines()) {
            SalesOrderItem item = order.getItems().stream()
                    .filter(i -> i.getId().equals(line.getSoItemId()))
                    .findFirst()
                    .orElseThrow();
            inventoryService.issueForOrder(
                    item.getProduct().getId(), order.getWarehouse().getId(),
                    line.getQuantity(), reference, actor, notes);
            item.setFulfilledQuantity(item.getFulfilledQuantity().add(line.getQuantity()));
            item.touch();
            fulfillment.addLine(new SalesFulfillmentItem(item, item.getProduct(), line.getQuantity()));
        }
        fulfillmentRepository.save(fulfillment);

        boolean fullyFulfilled = order.getItems().stream()
                .allMatch(i -> i.getFulfilledQuantity().compareTo(i.getOrderedQuantity()) >= 0);
        order.setStatus(fullyFulfilled
                ? SalesOrderStatus.FULFILLED : SalesOrderStatus.PARTIALLY_FULFILLED);
        order.touch();
        orderRepository.save(order);
        return orderMapper.toFulfillmentResponse(fulfillment);
    }

    private void replaceLines(SalesOrder order, SalesOrderRequest request) {
        order.setItems(request.getItems().stream()
                .map(line -> new SalesOrderItem(
                        requireProduct(line.getProductId()),
                        line.getOrderedQuantity(),
                        line.getUnitPrice()))
                .toList());
    }

    private void requireDraft(SalesOrder order, String action) {
        if (order.getStatus() != SalesOrderStatus.DRAFT) {
            throw new ConflictException("Only draft sales orders can be " + action
                    + "d (current: " + order.getStatus() + ")");
        }
    }

    private SalesOrder requireById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sales order " + id + " not found"));
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
