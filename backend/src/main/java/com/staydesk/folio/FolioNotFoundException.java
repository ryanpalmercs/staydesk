package com.staydesk.folio;

import java.util.UUID;

public class FolioNotFoundException extends RuntimeException {

    public FolioNotFoundException(UUID folioId) {
        super("Folio " + folioId + " was not found");
    }
}
