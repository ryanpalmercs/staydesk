package com.staydesk.payment;

public record AuthResult(boolean success, String transactionId, String message) {
}
