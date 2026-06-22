package com.staydesk.exception;

public class NotClockedInException extends RuntimeException {
    public NotClockedInException() {
        super("User is not clocked in and cannot clock out.");
    }
}
