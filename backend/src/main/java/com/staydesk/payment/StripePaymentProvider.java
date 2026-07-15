package com.staydesk.payment;

import com.staydesk.service.StripeConnectionService;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Refund;
import com.stripe.model.SetupIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.SetupIntentCreateParams;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Deprecated
@Service("stripe")
public class StripePaymentProvider implements PaymentProvider {

    private final StripeConnectionService stripeConnectionService;

    public StripePaymentProvider(StripeConnectionService stripeConnectionService) {
        this.stripeConnectionService = stripeConnectionService;
    }

    public static long toCents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    private RequestOptions connectedAccountOptions() {
        return RequestOptions.builder()
                             .setStripeAccount(stripeConnectionService.getConnectedAccountId())
                             .build();
    }

    @Override
    public AuthResult authorize(BigDecimal amount, String token, String description) {
        try {
            RequestOptions options = connectedAccountOptions();
            PaymentIntent intent = PaymentIntent.create(
                    PaymentIntentCreateParams.builder()
                                             .setAmount(toCents(amount))
                                             .setCurrency("usd")
                                             .setPaymentMethod(token)
                                             .addPaymentMethodType("card")
                                             .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                                             .setConfirm(true)
                                             .setDescription(description)
                                             .build(),
                    options
            );
            PaymentMethod paymentMethod = PaymentMethod.retrieve(token, options);
            String last4 = paymentMethod.getCard() != null ? paymentMethod.getCard().getLast4() : null;
            return new AuthResult(true, intent.getId(), null, last4);
        } catch (StripeException e) {
            return new AuthResult(false, null, e.getMessage(), null);
        }
    }

    @Override
    public AuthResult sale(BigDecimal amount, String token, String description) {
        throw new UnsupportedOperationException("Stripe provider is unused; Authorize.net is the live payment processor");
    }

    @Override
    public CaptureResult capture(String authId, BigDecimal amount) {
        try {
            RequestOptions options = connectedAccountOptions();
            PaymentIntent intent = PaymentIntent.retrieve(authId, options);
            intent.capture(
                    PaymentIntentCaptureParams.builder()
                                              .setAmountToCapture(toCents(amount))
                                              .build(),
                    options
            );
            return new CaptureResult(true, authId, null);
        } catch (StripeException e) {
            return new CaptureResult(false, authId, e.getMessage());
        }
    }

    @Override
    public VoidResult void_(String authId) {
        try {
            RequestOptions options = connectedAccountOptions();
            PaymentIntent intent = PaymentIntent.retrieve(authId, options);
            intent.cancel(options);
            return new VoidResult(true, authId, null);
        } catch (StripeException e) {
            return new VoidResult(false, authId, e.getMessage());
        }
    }

    @Override
    public RefundResult refund(String transactionId, BigDecimal amount, String cardLast4) {
        try {
            Refund refund = Refund.create(
                    RefundCreateParams.builder()
                                      .setPaymentIntent(transactionId)
                                      .setAmount(toCents(amount))
                                      .build(),
                    connectedAccountOptions()
            );
            return new RefundResult(true, refund.getId(), null);
        } catch (StripeException e) {
            return new RefundResult(false, null, e.getMessage());
        }
    }

    @Override
    public TokenResult tokenize(String customerId) {
        try {
            SetupIntent setupIntent = SetupIntent.create(
                    SetupIntentCreateParams.builder()
                                           .setCustomer(customerId)
                                           .addPaymentMethodType("card")
                                           .build(),
                    connectedAccountOptions()
            );
            return new TokenResult(true, setupIntent.getId(), null);
        } catch (StripeException e) {
            return new TokenResult(false, null, e.getMessage());
        }
    }
}
