package com.staydesk.exception;

public class PayrollSyncException extends RuntimeException {
    public PayrollSyncException(String message) {
        super(message);
    }

    public PayrollSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
