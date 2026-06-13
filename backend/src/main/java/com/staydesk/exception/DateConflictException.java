package com.staydesk.exception;

public class DateConflictException extends RuntimeException {
    public DateConflictException() {
        super("Reservation dates conflict with an existing reservation");
    }
}
