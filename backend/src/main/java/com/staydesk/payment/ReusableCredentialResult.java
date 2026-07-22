package com.staydesk.payment;

public record ReusableCredentialResult(boolean success, String providerCustomerId, String providerToken,
                                       String cardLast4, String message) {
}