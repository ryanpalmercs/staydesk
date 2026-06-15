package com.staydesk.exception;

public class RateNotFoundException extends RuntimeException {
    public RateNotFoundException() {
        super("Rate and guest count combination does not exist.");
    }
}
