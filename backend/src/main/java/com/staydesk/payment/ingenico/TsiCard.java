package com.staydesk.payment.ingenico;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TsiCard(@JsonProperty("account_no") String accountNumber) {
}
