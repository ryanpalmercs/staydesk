package com.staydesk.exception;

public class RoomUnavailableException extends RuntimeException {
    public RoomUnavailableException() {
        super("Room is not available");
    }
}
