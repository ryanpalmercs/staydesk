package com.staydesk.payment;

import java.math.BigDecimal;

public interface PaymentProvider {
    AuthResult authorize(BigDecimal amount, String token, String description, String customerEmail);

    AuthResult sale(BigDecimal amount, String token, String description, String customerEmail);

    CaptureResult capture(String authId, BigDecimal amount);

    VoidResult void_(String authId);

    RefundResult refund(String transactionId, BigDecimal amount, String cardLast4);

    ReusableCredentialResult createReusableCredential(String authorizationTransactionId, String customerReferenceId);

    AuthResult chargeStoredCredential(BigDecimal amount, String providerCustomerId, String providerToken, String description,
                                      String customerEmail);

    void revokeReusableCredential(String providerCustomerId, String providerToken);
}
