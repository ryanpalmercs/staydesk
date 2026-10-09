package com.staydesk.payment;

import java.math.BigDecimal;

public interface PaymentProvider {
    /**
     * @param folioPaymentId the {@code folio_payments} row this call will ultimately settle,
     *                       when already known (e.g. capturing/voiding/refunding an existing
     *                       hold) - null when the call is creating a brand-new hold or charge,
     *                       since no {@code FolioPayment} row exists yet at call time. Providers
     *                       that don't need folio context (Authorize.net, Elavon CPI) ignore it;
     *                       it exists so the Ingenico terminal bridge can stamp
     *                       {@code terminal_transactions.folio_payment_id} for reporting/reconciliation.
     */
    AuthResult authorize(BigDecimal amount, String token, String description, String customerEmail, Integer folioPaymentId);

    AuthResult sale(BigDecimal amount, String token, String description, String customerEmail, Integer folioPaymentId);

    CaptureResult capture(String authId, BigDecimal amount, Integer folioPaymentId);

    VoidResult void_(String authId, Integer folioPaymentId);

    RefundResult refund(String transactionId, BigDecimal amount, String cardLast4, Integer folioPaymentId);

    ReusableCredentialResult createReusableCredential(String authorizationTransactionId, String customerReferenceId);

    AuthResult chargeStoredCredential(BigDecimal amount, String providerCustomerId, String providerToken, String description,
                                      String customerEmail, Integer folioPaymentId);

    void revokeReusableCredential(String providerCustomerId, String providerToken);
}
