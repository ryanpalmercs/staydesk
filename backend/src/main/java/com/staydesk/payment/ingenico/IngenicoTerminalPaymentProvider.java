package com.staydesk.payment.ingenico;

import com.staydesk.exception.TerminalBridgeException;
import com.staydesk.model.TerminalTransaction;
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

@Service("ingenico_terminal")
public class IngenicoTerminalPaymentProvider implements PaymentProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngenicoTerminalPaymentProvider.class);

    private final IngenicoBridgeClient bridgeClient;

    public IngenicoTerminalPaymentProvider(IngenicoBridgeClient bridgeClient) {
        this.bridgeClient = bridgeClient;
    }

    @Override
    public AuthResult authorize(BigDecimal amount, String token, String description) {
        throw new UnsupportedOperationException(
                "IngenicoTerminalPaymentProvider.authorize() is pending confirmation of pre_auth support on the Desk 3500 - see docs/ingenico-bridge-notes.md");
    }

    @Override
    public AuthResult sale(BigDecimal amount, String token, String description) {
        try {
            TsiTransactionResult result = bridgeClient.sendTransaction(TerminalTransaction.Operation.SALE, null,
                    "sale", amount, null);
            return toAuthResult(result);
        } catch (TerminalBridgeException e) {
            LOGGER.error("Terminal sale failed for amount {}: {}", amount, e.getMessage());
            return new AuthResult(false, null, e.getMessage(), null);
        }
    }

    @Override
    public CaptureResult capture(String authId, BigDecimal amount) {
        throw new UnsupportedOperationException(
                "IngenicoTerminalPaymentProvider.capture() is pending confirmation of pre_auth_completion support on the Desk 3500 - see docs/ingenico-bridge-notes.md");
    }

    @Override
    public VoidResult void_(String authId) {
        try {
            TsiTransactionResult result = bridgeClient.sendTransaction(TerminalTransaction.Operation.VOID, null,
                    "void", null, authId);

            if (!isApproved(result)) {
                return new VoidResult(false, authId, message(result));
            }

            return new VoidResult(true, result.referenceNumber() != null ? result.referenceNumber() : authId, null);
        } catch (TerminalBridgeException e) {
            LOGGER.error("Terminal void failed for reference {}: {}", authId, e.getMessage());
            return new VoidResult(false, authId, e.getMessage());
        }
    }

    @Override
    public RefundResult refund(String transactionId, BigDecimal amount, String cardLast4) {
        try {
            TsiTransactionResult result = bridgeClient.sendTransaction(TerminalTransaction.Operation.REFUND, null,
                    "refund", amount, null);

            if (!isApproved(result)) {
                return new RefundResult(false, transactionId, message(result));
            }

            return new RefundResult(true, result.referenceNumber() != null ?
                                          result.referenceNumber() :
                                          transactionId, null);
        } catch (TerminalBridgeException e) {
            LOGGER.error("Terminal refund failed for transaction {}: {}", transactionId, e.getMessage());
            return new RefundResult(false, transactionId, e.getMessage());
        }
    }

    @Override
    public ReusableCredentialResult createReusableCredential(String authorizationTransactionId,
                                                             String customerReferenceId) {
        return new ReusableCredentialResult(true, null, authorizationTransactionId, null, null);
    }

    @Override
    public AuthResult chargeStoredCredential(BigDecimal amount, String providerCustomerId, String providerToken,
                                             String description) {
        throw new UnsupportedOperationException(
                "IngenicoTerminalPaymentProvider does not support stored-credential charges - no card data is available locally for a card-present terminal flow");
    }

    @Override
    public void revokeReusableCredential(String providerCustomerId, String providerToken) {
        LOGGER.info("No remote token revocation available for the Ingenico terminal bridge; credential {} is only revoked locally", providerToken);
    }

    private AuthResult toAuthResult(TsiTransactionResult result) {
        if (!isApproved(result)) {
            return new AuthResult(false, null, message(result), null);
        }

        return new AuthResult(true, result.referenceNumber(), null, last4From(result));
    }

    private boolean isApproved(TsiTransactionResult result) {
        return result != null && "approved".equals(result.status());
    }

    private String message(TsiTransactionResult result) {
        return result != null && result.hostResponseText() != null ?
               result.hostResponseText() :
               "No response from terminal";
    }

    private String last4From(TsiTransactionResult result) {
        if (result.card() == null || result.card().accountNumber() == null || result.card().accountNumber().length() < 4) {
            return null;
        }

        String accountNumber = result.card().accountNumber();
        return accountNumber.substring(accountNumber.length() - 4);
    }
}
