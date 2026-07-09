package com.staydesk.payment;

public record VoidResult(boolean success, String transactionId, String message) {
}
