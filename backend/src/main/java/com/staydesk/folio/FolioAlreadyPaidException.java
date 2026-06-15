package com.staydesk.folio;

import java.util.UUID;

public class FolioAlreadyPaidException extends RuntimeException {

    public FolioAlreadyPaidException(UUID folioId) {
        super("Folio " + folioId + " is already paid by a different Stripe payment intent");
    }
}
