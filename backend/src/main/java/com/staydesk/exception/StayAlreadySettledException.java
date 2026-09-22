package com.staydesk.exception;

public class StayAlreadySettledException extends RuntimeException {
    public StayAlreadySettledException() {
        super("This stay has already been charged.");
    }
}
