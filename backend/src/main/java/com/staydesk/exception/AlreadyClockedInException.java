package com.staydesk.exception;

public class AlreadyClockedInException extends RuntimeException {
    public AlreadyClockedInException() {
        super("Employee is already clocked in.");
    }
}
