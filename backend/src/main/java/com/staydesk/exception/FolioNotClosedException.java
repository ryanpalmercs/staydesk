package com.staydesk.exception;

public class FolioNotClosedException extends RuntimeException {
    public FolioNotClosedException() {
        super("Folio must be closed before an incident charge can be requested.");
    }
}