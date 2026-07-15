package com.staydesk.exception;

public class InvalidReservationException extends RuntimeException {
    public InvalidReservationException() {
        super("Invalid reservation");
    }
}
