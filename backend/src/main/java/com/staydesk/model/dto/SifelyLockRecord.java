package com.staydesk.model.dto;

public record SifelyLockRecord(long recordId, long lockId, long lockDate, int recordType, int success,
                               String keyboardPwd, String username) {
}