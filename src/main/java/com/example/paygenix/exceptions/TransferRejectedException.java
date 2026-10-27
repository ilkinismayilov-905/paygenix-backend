package com.example.paygenix.exceptions;

public class TransferRejectedException extends BusinessException {

    private final String errorCode;

    private TransferRejectedException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public static TransferRejectedException sameCard() {
        return new TransferRejectedException("Source and destination card must differ", "SAME_CARD");
    }

    public static TransferRejectedException currencyMismatch() {
        return new TransferRejectedException("Both cards must use the same currency", "CURRENCY_MISMATCH");
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
