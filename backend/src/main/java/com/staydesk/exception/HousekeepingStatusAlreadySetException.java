package com.staydesk.exception;

public class HousekeepingStatusAlreadySetException extends RuntimeException {
    public HousekeepingStatusAlreadySetException() {
        super("Room already has this housekeeping status.");
    }
}
