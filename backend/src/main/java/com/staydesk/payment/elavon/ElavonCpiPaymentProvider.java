package com.staydesk.payment.elavon;

import com.staydesk.payment.AuthResult;
import com.staydesk.payment.CaptureResult;
import com.staydesk.payment.PaymentProvider;
import com.staydesk.payment.RefundResult;
import com.staydesk.payment.TokenResult;
import com.staydesk.payment.VoidResult;
import com.staydesk.payment.elavon.dto.CpiCard;
import com.staydesk.payment.elavon.dto.CpiResponseFields;
import com.staydesk.payment.elavon.dto.CpiSafetyFields;
import com.staydesk.payment.elavon.dto.CpiToken;
import com.staydesk.payment.elavon.dto.CpiTransaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service("elavon_cpi")
public class ElavonCpiPaymentProvider implements PaymentProvider {

    // TODO: verify against a real UAT response — assuming "000" is CPI's approved
    // responseCode per common processor convention, not confirmed in the spec text itself.
    private static final String APPROVED_RESPONSE_CODE = "000";

    private final ElavonCpiClient client;

    public ElavonCpiPaymentProvider(ElavonCpiClient client) {
        this.client = client;
    }

    @Override
    public AuthResult authorize(BigDecimal amount, String token, String description) {
        // `token` here is the paired device's CPI deviceId — see PaymentProvider's
        // Javadoc note once added; this provider has no client-side card token,
        // the physical terminal captures the card.
        String deviceId = token;

        CpiTransaction request = new CpiTransaction(referenceNumber(), "AUTH", amount.toPlainString(),
                null, null, null, null, null);

        CpiTransaction response = client.sendDeviceMessage(deviceId, request);

        if (!isApproved(response)) {
            return new AuthResult(false, null, responseMessage(response), null);
        }

        return new AuthResult(true, authIdFrom(response), null, last4From(response.card()));
    }

    @Override
    public AuthResult sale(BigDecimal amount, String token, String description) {
        String deviceId = token;

        CpiTransaction request = new CpiTransaction(referenceNumber(), "SALE", amount.toPlainString(),
                null, null, null, null, null);

        CpiTransaction response = client.sendDeviceMessage(deviceId, request);

        if (!isApproved(response)) {
            return new AuthResult(false, null, responseMessage(response), null);
        }

        return new AuthResult(true, authIdFrom(response), null, last4From(response.card()));
    }

    @Override
    public CaptureResult capture(String authId, BigDecimal amount) {
        CpiTransaction request = new CpiTransaction(referenceNumber(), "PRIORAUTHCOMPLETION", amount.toPlainString(),
                null, null, new CpiSafetyFields(new CpiToken(authId)), null, null);

        CpiTransaction response = client.sendGatewayMessage(request);

        if (!isApproved(response)) {
            return new CaptureResult(false, authId, responseMessage(response));
        }

        return new CaptureResult(true, authIdFrom(response), null);
    }

    @Override
    public VoidResult void_(String authId) {
        CpiTransaction request = new CpiTransaction(referenceNumber(), "VOIDSALE", null,
                null, null, new CpiSafetyFields(new CpiToken(authId)), null, null);

        CpiTransaction response = client.sendGatewayMessage(request);

        if (!isApproved(response)) {
            return new VoidResult(false, authId, responseMessage(response));
        }

        return new VoidResult(true, authIdFrom(response), null);
    }

    @Override
    public RefundResult refund(String transactionId, BigDecimal amount, String cardLast4) {
        CpiTransaction request = new CpiTransaction(referenceNumber(), "REFUND", amount.toPlainString(),
                null, null, new CpiSafetyFields(new CpiToken(transactionId)), null, null);

        CpiTransaction response = client.sendGatewayMessage(request);

        if (!isApproved(response)) {
            return new RefundResult(false, transactionId, responseMessage(response));
        }

        return new RefundResult(true, authIdFrom(response), null);
    }

    @Override
    public TokenResult tokenize(String customerId) {
        throw new UnsupportedOperationException("Elavon CPI provider does not support saved/reusable card tokens");
    }

    private boolean isApproved(CpiTransaction response) {
        return response.response() != null && APPROVED_RESPONSE_CODE.equals(response.response().responseCode());
    }

    private String authIdFrom(CpiTransaction response) {
        return response.safetyFields() != null && response.safetyFields().tokenization() != null
                ? response.safetyFields().tokenization().token()
                : null;
    }

    private String responseMessage(CpiTransaction response) {
        CpiResponseFields fields = response.response();
        return fields != null ? fields.responseText() : "No response from Elavon CPI";
    }

    private String last4From(CpiCard card) {
        if (card == null || card.maskedPAN() == null || card.maskedPAN().length() < 4) {
            return null;
        }
        return card.maskedPAN().substring(card.maskedPAN().length() - 4);
    }

    private String referenceNumber() {
        // Spec warning: referenceNumber must be unique per open batch — reusing one
        // risks losing transaction data. Using a timestamp here as a placeholder;
        // worth revisiting once we know CPI's actual batch semantics from real testing.
        return String.valueOf(System.currentTimeMillis());
    }
}