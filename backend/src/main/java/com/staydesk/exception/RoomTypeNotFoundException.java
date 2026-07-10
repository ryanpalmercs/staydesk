package com.staydesk.exception;

public class RoomTypeNotFoundException extends RuntimeException {
    public RoomTypeNotFoundException() {
        super("Room type not found");
    }
}
