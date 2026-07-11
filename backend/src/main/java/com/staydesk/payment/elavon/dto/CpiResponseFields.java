package com.staydesk.payment.elavon.dto;

public record CpiResponseFields(String authorizationCode, String responseCode, String responseText,
                                String hostResponseCode, String hostResponseText, String issuerResponseCode) {
}