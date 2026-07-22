package com.staydesk.payment.elavon.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CpiTransaction(String referenceNumber, String transType, String transAmount,
                             CpiIdentifiers identifiers, String cashierId,
                             CpiSafetyFields safetyFields, CpiCard card,
                             CpiResponseFields response, String cardTransIdentifierIndicator) {
}