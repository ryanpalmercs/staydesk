package com.staydesk.exception;

public class RateOverrideNotFoundException extends RuntimeException {
    public RateOverrideNotFoundException() {
        super("Rate override does not exist.");
    }
}
