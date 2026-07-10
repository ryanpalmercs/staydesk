package com.staydesk.payment;

import java.math.BigDecimal;

public interface PaymentProvider {
    AuthResult authorize(BigDecimal amount, String token, String description);

    CaptureResult capture(String authId, BigDecimal amount);

    VoidResult void_(String authId);

    RefundResult refund(String transactionId, BigDecimal amount, String cardLast4);

    TokenResult tokenize(String customerId);
}
