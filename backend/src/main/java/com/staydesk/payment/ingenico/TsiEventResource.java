package com.staydesk.payment.ingenico;

import java.util.List;

public record TsiEventResource(String status, List<TsiTransactionResult> results) {
}
