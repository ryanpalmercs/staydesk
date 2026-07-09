package com.staydesk.provider;

import com.staydesk.lock.LockProvider;
import com.staydesk.payment.PaymentProvider;
import com.staydesk.service.PropertySettingsService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ProviderFactory {

    private final Map<String, PaymentProvider> paymentProviders;
    private final Map<String, LockProvider> lockProviders;
    private final PropertySettingsService propertySettingsService;

    public ProviderFactory(Map<String, PaymentProvider> paymentProviders, Map<String, LockProvider> lockProviders,
                           PropertySettingsService propertySettingsService) {
        this.paymentProviders = paymentProviders;
        this.lockProviders = lockProviders;
        this.propertySettingsService = propertySettingsService;
    }

    public PaymentProvider getPaymentProvider() {
        return resolve(paymentProviders, "payment_provider");
    }

    public LockProvider getLockProvider() {
        return resolve(lockProviders, "lock_provider");
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
