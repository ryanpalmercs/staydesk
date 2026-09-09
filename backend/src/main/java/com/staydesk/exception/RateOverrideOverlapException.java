package com.staydesk.exception;

public class RateOverrideOverlapException extends RuntimeException {
    public RateOverrideOverlapException() {
        super("A rate override already covers part of this date range for this rate type and guest count.");
    }
}
