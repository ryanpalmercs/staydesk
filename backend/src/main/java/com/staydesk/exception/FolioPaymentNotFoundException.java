package com.staydesk.exception;

public class FolioPaymentNotFoundException extends RuntimeException {
    public FolioPaymentNotFoundException() {
        super("Folio payment is not found.");
    }
}