package com.staydesk.exception;

public class StayNotSettledException extends RuntimeException {
    public StayNotSettledException() {
        super("This stay hasn't been paid for yet. Settle payment before checking in.");
    }
}
