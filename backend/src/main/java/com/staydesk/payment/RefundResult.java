package com.staydesk.payment;

public record RefundResult(boolean success, String transactionId, String message) {
}
