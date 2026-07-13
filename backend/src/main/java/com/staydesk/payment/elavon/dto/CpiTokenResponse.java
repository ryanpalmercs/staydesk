package com.staydesk.payment.elavon.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CpiTokenResponse(@JsonProperty("client_id") String clientId,
                               @JsonProperty("access_token") String accessToken,
                               @JsonProperty("token_type") String tokenType, @JsonProperty("expires_in") int expiresIn,
                               String jti) {
}