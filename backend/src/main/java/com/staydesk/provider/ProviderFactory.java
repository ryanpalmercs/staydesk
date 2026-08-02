package com.staydesk.provider;

import com.staydesk.lock.LockProvider;
import com.staydesk.payment.PaymentProvider;
import com.staydesk.service.PropertySettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ProviderFactory {

    private final Map<String, PaymentProvider> paymentProviders;
    private final Map<String, LockProvider> lockProviders;
    private final PropertySettingsService propertySettingsService;
    private final boolean cardPresentRecordOnly;

    public ProviderFactory(Map<String, PaymentProvider> paymentProviders, Map<String, LockProvider> lockProviders,
                           PropertySettingsService propertySettingsService,
                           @Value("${payment.card-present.record-only:false}") boolean cardPresentRecordOnly) {
        this.paymentProviders = paymentProviders;
        this.lockProviders = lockProviders;
        this.propertySettingsService = propertySettingsService;
        this.cardPresentRecordOnly = cardPresentRecordOnly;
    }

    public PaymentProvider getPaymentProvider() {
        return resolve(paymentProviders, "payment_provider");
    }

    public LockProvider getLockProvider() {
        return resolve(lockProviders, "lock_provider");
    }

    public PaymentProvider getCardPresentProvider() {
        return getProvider(getCardPresentProviderName());
    }

    /**
     * Name of the bean to use for card-present (terminal) charges. Falls back to a
     * record-only stand-in while Elavon/Ingenico terminal credentials are unavailable;
     * see payment.card-present.record-only.
     */
    public String getCardPresentProviderName() {
        return cardPresentRecordOnly ? "elavon_cpi_manual" : "elavon_cpi";
    }

    public boolean isCardPresentRecordOnly() {
        return cardPresentRecordOnly;
    }

    public PaymentProvider getProvider(String name) {
        PaymentProvider provider = paymentProviders.get(name);

        if (provider == null) {
            throw new IllegalStateException("No payment provider bean registered for " + name);
        }

        return provider;
    }

    public String getPaymentProviderName() {
        return propertySettingsService.getProperty("payment_provider").value();
    }

    private <T> T resolve(Map<String, T> providers, String settingName) {
        String name = propertySettingsService.getProperty(settingName).value();
        T provider = providers.get(name);

        if (provider == null) {
            throw new IllegalStateException("No provider bean registered for " + settingName + "=" + name);
        }

        return provider;
    }

}
