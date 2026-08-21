package com.staydesk.payroll.quickbooks;

import com.staydesk.exception.PayrollSyncException;
import com.staydesk.payroll.quickbooks.dto.QuickBooksTimeActivity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class QuickBooksClient {

    private final RestClient restClient = RestClient.create();
    private final QuickBooksAuthService authService;

    @Value("${quickbooks.api-base-url}")
    private String baseUrl;

    public QuickBooksClient(QuickBooksAuthService authService) {
        this.authService = authService;
    }

    public void pushTimeActivity(QuickBooksTimeActivity activity) {
        try {
            restClient.post()
                      .uri(baseUrl + "/v3/company/" + authService.getRealmId() + "/timeactivity")
                      .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                      .contentType(MediaType.APPLICATION_JSON)
                      .body(activity)
                      .retrieve()
                      .toBodilessEntity();
        } catch (RestClientException e) {
            throw new PayrollSyncException("QuickBooks time activity push failed: " + e.getMessage(), e);
        }
    }
}
