package com.staydesk.config;

import net.authorize.Environment;
import net.authorize.api.contract.v1.MerchantAuthenticationType;
import net.authorize.api.controller.base.ApiOperationBase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthorizeNetConfig {

    @Value("${authorizenet.api-login-id}")
    private String apiLoginId;

    @Value("${authorizenet.transaction-key}")
    private String transactionKey;

    @Value("${authorizenet.environment:SANDBOX}")
    private String environment;

    @Bean
    public MerchantAuthenticationType authorizeNetMerchantAuthentication() {
        ApiOperationBase.setEnvironment("PRODUCTION".equalsIgnoreCase(environment) ?
                                        Environment.PRODUCTION :
                                        Environment.SANDBOX);

        MerchantAuthenticationType auth = new MerchantAuthenticationType();
        auth.setName(apiLoginId);
        auth.setTransactionKey(transactionKey);
        return auth;
    }
}
