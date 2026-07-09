package com.staydesk.payment;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service("authorizenet")
public class AuthorizeNetPaymentProvider implements PaymentProvider {

    private static final String NOT_IMPLEMENTED = "Authorize.net integration not yet implemented";

    @Override
    public AuthResult authorize(BigDecimal amount, String token, String description) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public CaptureResult capture(String authId, BigDecimal amount) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public VoidResult void_(String authId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public RefundResult refund(String transactionId, BigDecimal amount) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public TokenResult tokenize(String customerId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}
