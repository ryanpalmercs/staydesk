package com.staydesk.model.dto;

import org.springframework.lang.Nullable;

public record SifelyWebhookRecord(long lockId, @Nullable Integer electricQuantity, long serverDate,
                                  @Nullable Integer recordTypeFromLock, int recordType, int success,
                                  String lockMac, @Nullable String keyboardPwd, long lockDate, String username) {
}
