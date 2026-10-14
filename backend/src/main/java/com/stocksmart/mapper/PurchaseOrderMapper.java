package com.stocksmart.mapper;

import com.stocksmart.dto.PurchaseOrderLineResponse;
import com.stocksmart.dto.PurchaseOrderResponse;
import com.stocksmart.dto.PurchaseReceiptResponse;
import com.stocksmart.entity.PurchaseOrder;
import com.stocksmart.entity.PurchaseReceipt;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts purchase order + receipt entities to DTOs (entities never leave the service layer). */
@Component
public class PurchaseOrderMapper {

    public PurchaseOrderResponse toResponse(PurchaseOrder order) {
        List<PurchaseOrderLineResponse> lines = order.getItems().stream()
                .map(item -> new PurchaseOrderLineResponse(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProduct().getSku(),
                        item.getProduct().getName(),
                        item.getOrderedQuantity(),
                        item.getReceivedQuantity(),
                        item.getUnitCost()))
                .toList();
        return new PurchaseOrderResponse(
                order.getId(),
                order.getPoNumber(),
                order.getSupplier().getId(),
                order.getSupplier().getName(),
                order.getSupplier().getCode(),
                order.getWarehouse().getId(),
                order.getWarehouse().getCode(),
                order.getWarehouse().getName(),
                order.getOrderDate(),
                order.getExpectedDeliveryDate(),
                order.getStatus(),
                order.getNotes(),
                order.getCreatedBy().getUsername(),
                lines,
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public PurchaseReceiptResponse toReceiptResponse(PurchaseReceipt receipt) {
        List<PurchaseReceiptResponse.Line> lines = receipt.getLines().stream()
                .map(line -> new PurchaseReceiptResponse.Line(
                        line.getId(),
                        line.getPurchaseOrderItem().getId(),
                        line.getProduct().getId(),
                        line.getProduct().getSku(),
                        line.getQuantity()))
                .toList();
        return new PurchaseReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNumber(),
                receipt.getPurchaseOrder().getId(),
                receipt.getPurchaseOrder().getPoNumber(),
                receipt.getReceivedBy().getUsername(),
                receipt.getReceivedAt(),
                receipt.getNotes(),
                lines);
    }
}
