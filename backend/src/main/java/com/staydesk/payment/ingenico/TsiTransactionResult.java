package com.staydesk.payment.ingenico;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TsiTransactionResult(String status, @JsonProperty("reference_no") String referenceNumber,
                                   @JsonProperty("authorization_no") String authorizationNumber,
                                   @JsonProperty("host_response_text") String hostResponseText, TsiCard card) {
}
