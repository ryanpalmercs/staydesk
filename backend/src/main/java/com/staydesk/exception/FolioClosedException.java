package com.staydesk.exception;

public class FolioClosedException extends RuntimeException {
    public FolioClosedException() {
        super("Folio is closed and cannot accept new items.");
    }
}
