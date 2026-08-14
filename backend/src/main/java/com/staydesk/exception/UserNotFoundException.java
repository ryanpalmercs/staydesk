package com.staydesk.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("User is not in the system.");
    }
}
