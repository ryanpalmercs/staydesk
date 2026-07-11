package com.staydesk.payment.elavon.dto;

public record CpiCard(String maskedPAN, String expirationMonth, String expirationYear, String tenderType) {
}