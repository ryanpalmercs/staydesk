package com.staydesk.lock;

public record CodeResult(boolean success, String codeId, String code, String message) {
}
