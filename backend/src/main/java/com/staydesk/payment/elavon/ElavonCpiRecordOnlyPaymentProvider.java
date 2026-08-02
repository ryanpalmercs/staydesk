package com.staydesk.payment.elavon;

import com.staydesk.payment.AuthResult;
import com.staydesk.payment.CaptureResult;
import com.staydesk.payment.PaymentProvider;
import com.staydesk.payment.RefundResult;
import com.staydesk.payment.ReusableCredentialResult;
import com.staydesk.payment.VoidResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Temporary stand-in for {@link ElavonCpiPaymentProvider} while Elavon/Ingenico terminal
 * credentials are unavailable. Records card-present transactions in the system without
 * sending anything to a physical terminal or gateway. No real charge is ever made.
 */
@Service("elavon_cpi_manual")
public class ElavonCpiRecordOnlyPaymentProvider implements PaymentProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElavonCpiRecordOnlyPaymentProvider.class);

    @Override
    public AuthResult authorize(BigDecimal amount, String token, String description) {
        String recordId = recordId();
        LOGGER.info("Recording card-present hold without charging (Elavon terminal unavailable): {} for {}", recordId, description);
        return new AuthResult(true, recordId, null, null);
    }

    @Override
    public AuthResult sale(BigDecimal amount, String token, String description) {
        String recordId = recordId();
        LOGGER.info("Recording card-present sale without charging (Elavon terminal unavailable): {} for {}", recordId, description);
        return new AuthResult(true, recordId, null, null);
    }

    @Override
    public CaptureResult capture(String authId, BigDecimal amount) {
        return new CaptureResult(true, authId, null);
    }

    @Override
    public VoidResult void_(String authId) {
        return new VoidResult(true, authId, null);
    }

    @Override
    public RefundResult refund(String transactionId, BigDecimal amount, String cardLast4) {
        return new RefundResult(true, transactionId, null);
    }

    @Override
    public ReusableCredentialResult createReusableCredential(String authorizationTransactionId, String customerReferenceId) {
        return new ReusableCredentialResult(true, null, authorizationTransactionId, null, null);
    }

    @Override
    public AuthResult chargeStoredCredential(BigDecimal amount, String providerCustomerId, String providerToken, String description) {
        String recordId = recordId();
        LOGGER.info("Recording stored-credential charge without charging (Elavon terminal unavailable): {} for {}", recordId, description);
        return new AuthResult(true, recordId, null, null);
    }

    @Override
    public void revokeReusableCredential(String providerCustomerId, String providerToken) {
        LOGGER.info("No remote token revocation available in record-only mode; credential {} is only revoked locally", providerToken);
    }

    private String recordId() {
        return "MANUAL-" + UUID.randomUUID();
    }
}
