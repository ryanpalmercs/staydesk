package com.staydesk.exception;

public class CannotCancelException extends RuntimeException {
    public CannotCancelException() {
        super("Cannot cancel reservation already checked in or reservation is completed");
    }
}
