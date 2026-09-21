package com.staydesk.exception;

public class InvalidRateOverrideException extends RuntimeException {
    public InvalidRateOverrideException() {
        super("End date must be after start date - end date is the day pricing returns to normal, so it must be at least one day after start date.");
    }

    public InvalidRateOverrideException(String message) {
        super(message);
    }
}
