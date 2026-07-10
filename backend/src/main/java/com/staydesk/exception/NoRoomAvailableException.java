package com.staydesk.exception;

public class NoRoomAvailableException extends RuntimeException {
    public NoRoomAvailableException() {
        super("No physical room of this type is currently free to check in to");
    }
}
