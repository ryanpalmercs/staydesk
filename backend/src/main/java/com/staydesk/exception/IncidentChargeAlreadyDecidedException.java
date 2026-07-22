package com.staydesk.exception;

public class IncidentChargeAlreadyDecidedException extends RuntimeException {
    public IncidentChargeAlreadyDecidedException() {
        super("Incident charge request has already been decided.");
    }
}