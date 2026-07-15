package com.staydesk.exception;

public class AlreadyCheckedOutException extends RuntimeException {
    public AlreadyCheckedOutException() {
        super("Guest is already checked out.");
    }
}
