package com.staydesk.payment;

public record TokenResult(boolean success, String transactionId, String message) {
}
