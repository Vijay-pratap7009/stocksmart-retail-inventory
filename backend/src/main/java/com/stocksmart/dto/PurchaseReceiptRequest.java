package com.stocksmart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Goods receipt payload. {@code reference} is an optional idempotency key —
 * reusing an existing receipt number is rejected instead of receiving twice.
 */
public class PurchaseReceiptRequest {

    @Size(max = 60, message = "Reference must be at most 60 characters")
    private String reference;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    private String notes;

    @NotEmpty(message = "At least one receipt line is required")
    @Valid
    private List<PurchaseReceiptLineRequest> lines;

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

    public List<PurchaseReceiptLineRequest> getLines() {
        return lines;
    }

    public void setLines(List<PurchaseReceiptLineRequest> lines) {
        this.lines = lines;
    }
}
