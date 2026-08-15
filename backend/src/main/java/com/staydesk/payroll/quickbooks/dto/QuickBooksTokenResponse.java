package com.staydesk.payroll.quickbooks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QuickBooksTokenResponse(@JsonProperty("access_token") String accessToken,
                                      @JsonProperty("token_type") String tokenType,
                                      @JsonProperty("expires_in") int expiresIn) {
}
