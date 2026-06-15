package com.staydesk.exception;

public class FolioNotFoundException extends RuntimeException {
    public FolioNotFoundException() {
        super("Folio is not found.");
    }
}
