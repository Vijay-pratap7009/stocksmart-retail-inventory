package com.stocksmart.mapper;

import com.stocksmart.dto.FulfillmentResponse;
import com.stocksmart.dto.SalesOrderLineResponse;
import com.stocksmart.dto.SalesOrderResponse;
import com.stocksmart.entity.SalesFulfillment;
import com.stocksmart.entity.SalesOrder;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts sales order + fulfillment entities to DTOs (entities never leave the service layer). */
@Component
public class SalesOrderMapper {

    public SalesOrderResponse toResponse(SalesOrder order) {
        List<SalesOrderLineResponse> lines = order.getItems().stream()
                .map(item -> new SalesOrderLineResponse(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProduct().getSku(),
                        item.getProduct().getName(),
                        item.getOrderedQuantity(),
                        item.getFulfilledQuantity(),
                        item.getUnitPrice()))
                .toList();
        return new SalesOrderResponse(
                order.getId(),
                order.getSoNumber(),
                order.getWarehouse().getId(),
                order.getWarehouse().getCode(),
                order.getWarehouse().getName(),
                order.getCustomerName(),
                order.getCustomerContact(),
                order.getOrderDate(),
                order.getStatus(),
                order.getNotes(),
                order.getCreatedBy().getUsername(),
                lines,
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public FulfillmentResponse toFulfillmentResponse(SalesFulfillment fulfillment) {
        List<FulfillmentResponse.Line> lines = fulfillment.getLines().stream()
                .map(line -> new FulfillmentResponse.Line(
                        line.getId(),
                        line.getSalesOrderItem().getId(),
                        line.getProduct().getId(),
                        line.getProduct().getSku(),
                        line.getQuantity()))
                .toList();
        return new FulfillmentResponse(
                fulfillment.getId(),
                fulfillment.getFulfillmentNumber(),
                fulfillment.getSalesOrder().getId(),
                fulfillment.getSalesOrder().getSoNumber(),
                fulfillment.getFulfilledBy().getUsername(),
                fulfillment.getFulfilledAt(),
                fulfillment.getNotes(),
                lines);
    }
}
