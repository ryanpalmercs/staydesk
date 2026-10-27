package com.staydesk.exception;

public class RemoteCheckInTokenInvalidException extends RuntimeException {
    public RemoteCheckInTokenInvalidException() {
        super("This check-in link is invalid or has expired");
    }
}
