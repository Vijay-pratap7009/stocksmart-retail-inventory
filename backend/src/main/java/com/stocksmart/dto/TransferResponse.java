package com.stocksmart.dto;

/**
 * Result of a warehouse transfer: both legs sharing one reference number.
 * Returned with 201 when the whole transfer commits atomically.
 */
public class TransferResponse {

    private String referenceNumber;
    private StockTransactionResponse transferOut;
    private StockTransactionResponse transferIn;

    public TransferResponse() {
    }

    public TransferResponse(String referenceNumber,
                            StockTransactionResponse transferOut,
                            StockTransactionResponse transferIn) {
        this.referenceNumber = referenceNumber;
        this.transferOut = transferOut;
        this.transferIn = transferIn;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public StockTransactionResponse getTransferOut() {
        return transferOut;
    }

    public StockTransactionResponse getTransferIn() {
        return transferIn;
    }
}
