package com.staydesk.model.dto;

public record SifelyWebhookPayload(String lockId, String lockMac, String records, String notifyType, String admin) {
}
