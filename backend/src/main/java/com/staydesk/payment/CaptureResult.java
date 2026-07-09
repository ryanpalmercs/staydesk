package com.staydesk.payment;

public record CaptureResult(boolean success, String transactionId, String message) {
}
