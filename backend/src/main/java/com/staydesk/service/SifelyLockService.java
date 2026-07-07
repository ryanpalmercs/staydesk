package com.staydesk.service;

import com.staydesk.model.dto.SifelyPasscodeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SifelyLockService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SifelyLockService.class);

    private final RestClient restClient = RestClient.create();
    private final SifelyAuthService sifelyAuthService;

    @Value("${sifely.base-url}")
    private String baseUrl;

    public SifelyLockService(SifelyAuthService sifelyAuthService) {
        this.sifelyAuthService = sifelyAuthService;
    }

    public String createPasscode(long lockId, String keyboardPwd, String label, long startDateMillis,
                                 long endDateMillis) {
        SifelyPasscodeResponse response = restClient.post()
                                                    .uri(baseUrl + "/v3/keyboardPwd/add?lockId=" + lockId
                                                         + "&keyboardPwd=" + keyboardPwd
                                                         + "&keyboardPwdName=" + label
                                                         + "&keyboardPwdType=3"
                                                         + "&startDate=" + startDateMillis
                                                         + "&endDate=" + endDateMillis
                                                         + "&addType=2")
                                                    .header("Authorization", sifelyAuthService.getApiKey())
                                                    .retrieve()
                                                    .body(SifelyPasscodeResponse.class);

        if (response == null || response.keyboardPwdId() == null) {
            LOGGER.error("Sifely createPasscode returned no keyboardPwdId for lockId {}", lockId);
            throw new IllegalStateException("Sifely passcode creation failed for lockId " + lockId);
        }

        LOGGER.info("Created Sifely passcode {} for lockId {}", response.keyboardPwdId(), lockId);
        return String.valueOf(response.keyboardPwdId());
    }

    public void deletePasscode(long lockId, long keyboardPwdId) {
        String rawResponse = restClient.post()
                                       .uri(baseUrl + "/v3/keyboardPwd/delete?lockId=" + lockId + "&keyboardPwdId=" + keyboardPwdId + "&deleteType=2")
                                       .header("Authorization", sifelyAuthService.getApiKey())
                                       .retrieve()
                                       .body(String.class);

        LOGGER.info("Sifely deletePasscode response for keyboardPwdId {}: {}", keyboardPwdId, rawResponse);
    }
}