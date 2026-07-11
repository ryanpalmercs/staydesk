package com.staydesk.provider;

import com.staydesk.lock.LockProvider;
import com.staydesk.payment.PaymentProvider;
import com.staydesk.payment.elavon.ElavonCpiPaymentProvider;
import com.staydesk.service.PropertySettingsService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ProviderFactory {

    private final Map<String, PaymentProvider> paymentProviders;
    private final Map<String, LockProvider> lockProviders;
    private final PropertySettingsService propertySettingsService;
    private final ElavonCpiPaymentProvider elavonCpiPaymentProvider;

    public ProviderFactory(Map<String, PaymentProvider> paymentProviders, Map<String, LockProvider> lockProviders,
                           PropertySettingsService propertySettingsService,
                           ElavonCpiPaymentProvider elavonCpiPaymentProvider) {
        this.paymentProviders = paymentProviders;
        this.lockProviders = lockProviders;
        this.propertySettingsService = propertySettingsService;
        this.elavonCpiPaymentProvider = elavonCpiPaymentProvider;
    }

    public PaymentProvider getPaymentProvider() {
        return resolve(paymentProviders, "payment_provider");
    }

    public LockProvider getLockProvider() {
        return resolve(lockProviders, "lock_provider");
    }

    public PaymentProvider getCardPresentProvider() {
        return elavonCpiPaymentProvider;
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
