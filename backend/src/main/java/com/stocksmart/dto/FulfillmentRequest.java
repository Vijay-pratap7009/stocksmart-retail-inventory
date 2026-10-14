package com.stocksmart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Fulfillment payload. {@code reference} is an optional idempotency key —
 * reusing an existing fulfillment number is rejected instead of issuing twice.
 */
public class FulfillmentRequest {

    @Size(max = 60, message = "Reference must be at most 60 characters")
    private String reference;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    private String notes;

    @NotEmpty(message = "At least one fulfillment line is required")
    @Valid
    private List<FulfillmentLineRequest> lines;

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<FulfillmentLineRequest> getLines() {
        return lines;
    }

    public void setLines(List<FulfillmentLineRequest> lines) {
        this.lines = lines;
    }
}
