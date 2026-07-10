package com.staydesk.exception;

public class RoomTypeUnavailableException extends RuntimeException {
    public RoomTypeUnavailableException() {
        super("No room of this type is available for the requested dates");
    }
}
