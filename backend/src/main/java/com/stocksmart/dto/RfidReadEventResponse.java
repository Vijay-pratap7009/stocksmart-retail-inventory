package com.stocksmart.dto;

import com.stocksmart.entity.RfidReadStatus;
import java.time.Instant;

/** Read model for one processed RFID read event. */
public class RfidReadEventResponse {

    private Long id;
    private String eventReference;
    private String epcCode;
    private RfidReadStatus status;
    private boolean simulated;
    private String readerName;
    private Long tagId;
    private Long productId;
    private String sku;
    private String productName;
    private Instant eventAt;
    private Instant receivedAt;
    private String performedByUsername;

    public RfidReadEventResponse() {
    }

    public RfidReadEventResponse(Long id, String eventReference, String epcCode,
                                 RfidReadStatus status, boolean simulated, String readerName,
                                 Long tagId, Long productId, String sku, String productName,
                                 Instant eventAt, Instant receivedAt, String performedByUsername) {
        this.id = id;
        this.eventReference = eventReference;
        this.epcCode = epcCode;
        this.status = status;
        this.simulated = simulated;
        this.readerName = readerName;
        this.tagId = tagId;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.eventAt = eventAt;
        this.receivedAt = receivedAt;
        this.performedByUsername = performedByUsername;
    }

    public Long getId() {
        return id;
    }

    public String getEventReference() {
        return eventReference;
    }

    public String getEpcCode() {
        return epcCode;
    }

    public RfidReadStatus getStatus() {
        return status;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public String getReaderName() {
        return readerName;
    }

    public Long getTagId() {
        return tagId;
    }

    public Long getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public Instant getEventAt() {
        return eventAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getPerformedByUsername() {
        return performedByUsername;
    }
}
