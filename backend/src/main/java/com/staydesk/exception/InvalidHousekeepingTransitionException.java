package com.staydesk.exception;

public class InvalidHousekeepingTransitionException extends RuntimeException {
    public InvalidHousekeepingTransitionException() {
        super("Invalid housekeeping status transition.");
    }
}
