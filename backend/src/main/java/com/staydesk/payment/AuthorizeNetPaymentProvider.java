package com.staydesk.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.authorize.api.contract.v1.CreateCustomerProfileFromTransactionRequest;
import net.authorize.api.contract.v1.CreateCustomerProfileResponse;
import net.authorize.api.contract.v1.CreateTransactionRequest;
import net.authorize.api.contract.v1.CreateTransactionResponse;
import net.authorize.api.contract.v1.CreditCardType;
import net.authorize.api.contract.v1.CustomerProfileBaseType;
import net.authorize.api.contract.v1.CustomerProfilePaymentType;
import net.authorize.api.contract.v1.DeleteCustomerProfileRequest;
import net.authorize.api.contract.v1.MerchantAuthenticationType;
import net.authorize.api.contract.v1.MessageTypeEnum;
import net.authorize.api.contract.v1.MessagesType;
import net.authorize.api.contract.v1.OpaqueDataType;
import net.authorize.api.contract.v1.OrderType;
import net.authorize.api.contract.v1.PaymentProfile;
import net.authorize.api.contract.v1.PaymentType;
import net.authorize.api.contract.v1.TransactionRequestType;
import net.authorize.api.contract.v1.TransactionResponse;
import net.authorize.api.contract.v1.TransactionTypeEnum;
import net.authorize.api.controller.CreateCustomerProfileFromTransactionController;
import net.authorize.api.controller.CreateTransactionController;
import net.authorize.api.controller.DeleteCustomerProfileController;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service("authorizenet")
public class AuthorizeNetPaymentProvider implements PaymentProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MerchantAuthenticationType merchantAuthenticationType;

    public AuthorizeNetPaymentProvider(MerchantAuthenticationType merchantAuthenticationType) {
        this.merchantAuthenticationType = merchantAuthenticationType;
    }

    @Override
    public AuthResult authorize(BigDecimal amount, String token, String description) {
        PaymentType paymentType = new PaymentType();
        paymentType.setOpaqueData(decodeOpaqueData(token));

        TransactionRequestType transactionRequest = new TransactionRequestType();
        transactionRequest.setTransactionType(TransactionTypeEnum.AUTH_ONLY_TRANSACTION.value());
        transactionRequest.setAmount(amount);
        transactionRequest.setPayment(paymentType);

        OrderType order = new OrderType();
        order.setDescription(description);
        transactionRequest.setOrder(order);

        CreateTransactionResponse response = execute(transactionRequest);

        if (!isSuccessful(response)) {
            return new AuthResult(false, null, errorMessage(response), null);
        }

        TransactionResponse transactionResponse = response.getTransactionResponse();
        return new AuthResult(true, transactionResponse.getTransId(), null, last4From(transactionResponse.getAccountNumber()));
    }

    @Override
    public AuthResult sale(BigDecimal amount, String token, String description) {
        PaymentType paymentType = new PaymentType();
        paymentType.setOpaqueData(decodeOpaqueData(token));

        TransactionRequestType transactionRequest = new TransactionRequestType();
        transactionRequest.setTransactionType(TransactionTypeEnum.AUTH_CAPTURE_TRANSACTION.value());
        transactionRequest.setAmount(amount);
        transactionRequest.setPayment(paymentType);

        OrderType order = new OrderType();
        order.setDescription(description);
        transactionRequest.setOrder(order);

        CreateTransactionResponse response = execute(transactionRequest);

        if (!isSuccessful(response)) {
            return new AuthResult(false, null, errorMessage(response), null);
        }

        TransactionResponse transactionResponse = response.getTransactionResponse();

        return new AuthResult(true, transactionResponse.getTransId(), null, last4From(transactionResponse.getAccountNumber()));
    }

    @Override
    public CaptureResult capture(String authId, BigDecimal amount) {
        TransactionRequestType transactionRequest = new TransactionRequestType();
        transactionRequest.setTransactionType(TransactionTypeEnum.PRIOR_AUTH_CAPTURE_TRANSACTION.value());
        transactionRequest.setRefTransId(authId);
        transactionRequest.setAmount(amount);

        CreateTransactionResponse response = execute(transactionRequest);
        if (!isSuccessful(response)) {
            return new CaptureResult(false, authId, errorMessage(response));
        }

        return new CaptureResult(true, response.getTransactionResponse().getTransId(), null);
    }

    @Override
    public VoidResult void_(String authId) {
        TransactionRequestType transactionRequest = new TransactionRequestType();
        transactionRequest.setTransactionType(TransactionTypeEnum.VOID_TRANSACTION.value());
        transactionRequest.setRefTransId(authId);

        CreateTransactionResponse response = execute(transactionRequest);

        if (!isSuccessful(response)) {
            return new VoidResult(false, authId, null);
        }

        return new VoidResult(true, response.getTransactionResponse().getTransId(), null);
    }

    @Override
    public RefundResult refund(String transactionId, BigDecimal amount, String cardLast4) {
        CreditCardType creditCard = new CreditCardType();
        creditCard.setCardNumber(cardLast4);
        creditCard.setExpirationDate("XXXX");

        PaymentType paymentType = new PaymentType();
        paymentType.setCreditCard(creditCard);

        TransactionRequestType transactionRequest = new TransactionRequestType();
        transactionRequest.setTransactionType(TransactionTypeEnum.REFUND_TRANSACTION.value());
        transactionRequest.setRefTransId(transactionId);
        transactionRequest.setAmount(amount);
        transactionRequest.setPayment(paymentType);

        CreateTransactionResponse response = execute(transactionRequest);

        if (!isSuccessful(response)) {
            return new RefundResult(false, transactionId, errorMessage(response));
        }

        return new RefundResult(true, response.getTransactionResponse().getTransId(), null);
    }

    @Override
    public ReusableCredentialResult createReusableCredential(String authorizationTransactionId,
                                                             String customerReferenceId) {
        CustomerProfileBaseType customerProfile = new CustomerProfileBaseType();
        customerProfile.setMerchantCustomerId(customerReferenceId);

        CreateCustomerProfileFromTransactionRequest request = new CreateCustomerProfileFromTransactionRequest();
        request.setMerchantAuthentication(merchantAuthenticationType);
        request.setTransId(authorizationTransactionId);
        request.setCustomer(customerProfile);

        CreateCustomerProfileFromTransactionController controller = new CreateCustomerProfileFromTransactionController(request);
        controller.execute();
        CreateCustomerProfileResponse response = controller.getApiResponse();

        if (response == null || response.getMessages().getResultCode() != MessageTypeEnum.OK) {
            return new ReusableCredentialResult(false, null, null, null, errorMessage(response));
        }

        String paymentProfileId = response.getCustomerPaymentProfileIdList().getNumericString().getFirst();
        return new ReusableCredentialResult(true, response.getCustomerProfileId(), paymentProfileId, null, null);
    }

    @Override
    public AuthResult chargeStoredCredential(BigDecimal amount, String providerCustomerId, String providerToken,
                                             String description) {
        PaymentProfile paymentProfile = new PaymentProfile();
        paymentProfile.setPaymentProfileId(providerToken);

        CustomerProfilePaymentType customerProfilePayment = new CustomerProfilePaymentType();
        customerProfilePayment.setCustomerProfileId(providerCustomerId);
        customerProfilePayment.setPaymentProfile(paymentProfile);

        TransactionRequestType transactionRequest = new TransactionRequestType();
        transactionRequest.setTransactionType(TransactionTypeEnum.AUTH_CAPTURE_TRANSACTION.value());
        transactionRequest.setAmount(amount);
        transactionRequest.setProfile(customerProfilePayment);

        OrderType order = new OrderType();
        order.setDescription(description);
        transactionRequest.setOrder(order);

        CreateTransactionResponse response = execute(transactionRequest);

        if (!isSuccessful(response)) {
            return new AuthResult(false, null, errorMessage(response), null);
        }

        TransactionResponse transactionResponse = response.getTransactionResponse();
        return new AuthResult(true, transactionResponse.getTransId(), null, last4From(transactionResponse.getAccountNumber()));
    }

    @Override
    public void revokeReusableCredential(String providerCustomerId, String providerToken) {
        DeleteCustomerProfileRequest request = new DeleteCustomerProfileRequest();
        request.setMerchantAuthentication(merchantAuthenticationType);
        request.setCustomerProfileId(providerCustomerId);

        DeleteCustomerProfileController controller = new DeleteCustomerProfileController(request);
        controller.execute();
    }

    private CreateTransactionResponse execute(TransactionRequestType transactionRequest) {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setMerchantAuthentication(merchantAuthenticationType);
        request.setTransactionRequest(transactionRequest);

        CreateTransactionController controller = new CreateTransactionController(request);
        controller.execute();

        return controller.getApiResponse();
    }

    private boolean isSuccessful(CreateTransactionResponse response) {
        return response != null &&
               response.getMessages().getResultCode() == MessageTypeEnum.OK &&
               response.getTransactionResponse() != null &&
               response.getTransactionResponse().getMessages() != null;
    }

    private String errorMessage(CreateTransactionResponse response) {
        if (response == null) {
            return "No response from vendor";
        }

        if (response.getTransactionResponse() != null && response.getTransactionResponse().getErrors() != null &&
            !response.getTransactionResponse().getErrors().getError().isEmpty()) {

            return response.getTransactionResponse().getErrors().getError().getFirst().getErrorText();
        }

        return response.getMessages()
                       .getMessage()
                       .stream()
                       .findFirst()
                       .map(MessagesType.Message::getText)
                       .orElse("Unknown error");
    }

    private String errorMessage(CreateCustomerProfileResponse response) {
        if (response == null) {
            return "No response from vendor";
        }

        return response.getMessages()
                       .getMessage()
                       .stream()
                       .findFirst()
                       .map(MessagesType.Message::getText)
                       .orElse("Unknown error");
    }

    private OpaqueDataType decodeOpaqueData(String token) {
        try {
            OpaqueTokenPayload payload = MAPPER.readValue(token, OpaqueTokenPayload.class);
            OpaqueDataType opaqueData = new OpaqueDataType();
            opaqueData.setDataDescriptor(payload.dataDescriptor());
            opaqueData.setDataValue(payload.dataValue());
            return opaqueData;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Authorize.net token payload", e);
        }
    }

    private String last4From(String maskedAccountNumber) {
        if (maskedAccountNumber == null || maskedAccountNumber.length() < 4) {
            return null;
        }

        return maskedAccountNumber.substring(maskedAccountNumber.length() - 4);
    }

    private record OpaqueTokenPayload(String dataDescriptor, String dataValue) {
    }
}
