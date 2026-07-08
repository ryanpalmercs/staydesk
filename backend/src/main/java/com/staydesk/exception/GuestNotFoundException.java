package com.staydesk.exception;

public class GuestNotFoundException extends RuntimeException {
    public GuestNotFoundException() {
        super("Guest not found");
    }
}