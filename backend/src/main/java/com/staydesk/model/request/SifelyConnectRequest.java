package com.staydesk.model.request;

public record SifelyConnectRequest(String account, String password, String clientId, String clientSecret) {
}
