package com.staydesk.model.dto;

public record SifelyLoginResponse(String clientToken, String clientId, String plan, String subscriptionStatus,
                                  int lockNum) {
}