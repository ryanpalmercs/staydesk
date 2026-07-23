package com.staydesk.exception;

public class IncidentChargeRequestNotFoundException extends RuntimeException {
    public IncidentChargeRequestNotFoundException() {
        super("Incident charge request not found.");
    }
}