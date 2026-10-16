package com.stocksmart.dto;

/**
 * Read model describing the active RFID reader adapter (Day 5).
 * Lets clients display whether reads are simulated or come from hardware.
 */
public class RfidReaderInfoResponse {

    private String readerName;
    private boolean simulated;
    private String note;

    public RfidReaderInfoResponse() {
    }

    public RfidReaderInfoResponse(String readerName, boolean simulated, String note) {
        this.readerName = readerName;
        this.simulated = simulated;
        this.note = note;
    }

    public String getReaderName() {
        return readerName;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public String getNote() {
        return note;
    }
}
