package com.staydesk.exception;

public class ExtraNotFoundException extends RuntimeException {
    public ExtraNotFoundException() {
        super("Extra does not exist.");
    }
}
