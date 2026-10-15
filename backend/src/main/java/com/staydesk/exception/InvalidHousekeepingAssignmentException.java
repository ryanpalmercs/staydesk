package com.staydesk.exception;

public class InvalidHousekeepingAssignmentException extends RuntimeException {
    public InvalidHousekeepingAssignmentException() {
        super("Employee is not active housekeeping staff.");
    }
}
