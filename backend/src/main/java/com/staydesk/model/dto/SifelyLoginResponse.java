package com.staydesk.model.dto;

public record SifelyLoginResponse(int code, Data data) {
    public record Data(String clientToken, String clientId, String account, String plan) {
    }
}