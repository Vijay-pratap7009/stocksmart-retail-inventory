package com.stocksmart.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Read-processing request (Day 5). One request yields exactly one processed
 * read event.
 *
 * <ul>
 *   <li>{@code simulate} null/true → the built-in simulator produces the
 *       read (epcCode optional; when omitted the simulator picks the lowest-id
 *       active tag). Simulated reads are always labeled as simulated — a
 *       client cannot mark a simulated read as hardware.</li>
 *   <li>{@code simulate} false → an external/physical reader bridge supplies
 *       {@code epcCode} (required) and optionally {@code readerName} and
 *       {@code eventAt}.</li>
 * </ul>
 *
 * <p>Processing a read NEVER changes stock.
 */
public class RfidReadRequest {

    @Size(max = 96, message = "EPC code must be at most 96 characters")
    @Pattern(regexp = "^[A-Za-z0-9.\\-:]{4,96}$",
            message = "EPC code may contain only letters, digits, dot, dash and colon")
    private String epcCode;

    /** Client idempotency key; a replay with the same reference is rejected (409). */
    @Size(max = 60, message = "Event reference must be at most 60 characters")
    private String eventReference;

    private Boolean simulate;

    @Size(max = 60, message = "Reader name must be at most 60 characters")
    private String readerName;

    private Instant eventAt;

    public String getEpcCode() {
        return epcCode;
    }

    public void setEpcCode(String epcCode) {
        this.epcCode = epcCode;
    }

    public String getEventReference() {
        return eventReference;
    }

    public void setEventReference(String eventReference) {
        this.eventReference = eventReference;
    }

    public Boolean getSimulate() {
        return simulate;
    }

    public void setSimulate(Boolean simulate) {
        this.simulate = simulate;
    }

    public String getReaderName() {
        return readerName;
    }

    public void setReaderName(String readerName) {
        this.readerName = readerName;
    }

    public Instant getEventAt() {
        return eventAt;
    }

    public void setEventAt(Instant eventAt) {
        this.eventAt = eventAt;
    }
}
