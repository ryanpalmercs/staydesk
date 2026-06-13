package com.staydesk.exception;

public class AlreadyCheckedInException extends RuntimeException {
    public AlreadyCheckedInException() {
        super("Guest is already checked in");
    }
}
