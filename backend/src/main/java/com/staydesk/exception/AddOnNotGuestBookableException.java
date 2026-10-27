package com.staydesk.exception;

public class AddOnNotGuestBookableException extends RuntimeException {
    public AddOnNotGuestBookableException() {
        super("This add-on isn't available for online booking.");
    }
}
