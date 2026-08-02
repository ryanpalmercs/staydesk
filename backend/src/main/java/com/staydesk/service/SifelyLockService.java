package com.staydesk.service;

import com.staydesk.model.dto.SifelyLockInfo;
import com.staydesk.model.dto.SifelyLockInfoListResponse;
import com.staydesk.model.dto.SifelyLockRecord;
import com.staydesk.model.dto.SifelyLockRecordListResponse;
import com.staydesk.model.dto.SifelyPasscodeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

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
                                 long endDateMillis, int keyboardPwdType) {
        SifelyPasscodeResponse response = restClient.post()
                                                    .uri(baseUrl + "/v3/keyboardPwd/add?lockId=" + lockId
                                                         + "&keyboardPwd=" + keyboardPwd
                                                         + "&keyboardPwdName=" + label
                                                         + "&keyboardPwdType=" + keyboardPwdType
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

    public String createPermanentPasscode(long lockId, String keyboardPwd, String label) {
        return createPasscode(lockId, keyboardPwd, label, System.currentTimeMillis(), 0, 2);
    }

    public void deletePasscode(long lockId, long keyboardPwdId) {
        String rawResponse = restClient.post()
                                       .uri(baseUrl + "/v3/keyboardPwd/delete?lockId=" + lockId + "&keyboardPwdId=" + keyboardPwdId + "&deleteType=2")
                                       .header("Authorization", sifelyAuthService.getApiKey())
                                       .retrieve()
                                       .body(String.class);

        LOGGER.info("Sifely deletePasscode response for keyboardPwdId {}: {}", keyboardPwdId, rawResponse);
    }

    public List<SifelyLockRecord> getLockRecords(long lockId, long startDateMillis, long endDateMillis) {
        List<SifelyLockRecord> all = new ArrayList<>();
        int pageNo = 1;

        while (true) {
            SifelyLockRecordListResponse response = restClient.get()
                                                              .uri(baseUrl + "/v3/lockRecord/list?lockId=" + lockId + "&pageNo=" + pageNo
                                                                   + "&pageSize=100&startDate=" + startDateMillis + "&endDate=" +
                                                                   endDateMillis)
                                                              .header("Authorization", sifelyAuthService.getApiKey())
                                                              .retrieve()
                                                              .body(SifelyLockRecordListResponse.class);

            if (response == null || response.list() == null || response.list().isEmpty()) {
                break;
            }

            all.addAll(response.list());

            if (pageNo >= response.pages()) {
                break;
            }

            pageNo++;
        }

        return all;
    }

    public List<SifelyLockInfo> getLocks() {
        List<SifelyLockInfo> all = new ArrayList<>();
        int pageNo = 1;

        while (true) {
            SifelyLockInfoListResponse response = restClient.post()
                                                            .uri(baseUrl + "/v3/lock/list?pageNo=" + pageNo + "&pageSize=100")
                                                            .header("Authorization", sifelyAuthService.getApiKey())
                                                            .retrieve()
                                                            .body(SifelyLockInfoListResponse.class);

            if (response == null || response.list() == null || response.list().isEmpty()) {
                break;
            }

            all.addAll(response.list());

            if (pageNo >= response.pages()) {
                break;
            }

            pageNo++;
        }

        return all;
    }
}