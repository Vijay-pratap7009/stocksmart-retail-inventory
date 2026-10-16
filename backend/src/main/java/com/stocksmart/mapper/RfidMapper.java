package com.stocksmart.mapper;

import com.stocksmart.dto.RfidReadEventResponse;
import com.stocksmart.dto.RfidTagResponse;
import com.stocksmart.entity.RfidReadEvent;
import com.stocksmart.entity.RfidTag;
import org.springframework.stereotype.Component;

/** Converts RFID entities to response DTOs (entities never leave the service layer). */
@Component
public class RfidMapper {

    public RfidTagResponse toTagResponse(RfidTag tag) {
        return new RfidTagResponse(
                tag.getId(),
                tag.getEpcCode(),
                tag.getProduct() != null ? tag.getProduct().getId() : null,
                tag.getProduct() != null ? tag.getProduct().getSku() : null,
                tag.getProduct() != null ? tag.getProduct().getName() : null,
                tag.isActive(),
                tag.getRegisteredBy() != null ? tag.getRegisteredBy().getUsername() : null,
                tag.getCreatedAt(),
                tag.getUpdatedAt());
    }

    public RfidReadEventResponse toReadEventResponse(RfidReadEvent event) {
        return new RfidReadEventResponse(
                event.getId(),
                event.getEventReference(),
                event.getEpcCode(),
                event.getStatus(),
                event.isSimulated(),
                event.getReaderName(),
                event.getTag() != null ? event.getTag().getId() : null,
                event.getProduct() != null ? event.getProduct().getId() : null,
                event.getProduct() != null ? event.getProduct().getSku() : null,
                event.getProduct() != null ? event.getProduct().getName() : null,
                event.getEventAt(),
                event.getReceivedAt(),
                event.getPerformedBy() != null ? event.getPerformedBy().getUsername() : null);
    }
}
