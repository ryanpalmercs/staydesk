package com.staydesk.payroll.quickbooks;

import com.staydesk.exception.PayrollSyncException;
import com.staydesk.payroll.quickbooks.dto.QuickBooksEmployeeQueryResponse;
import com.staydesk.payroll.quickbooks.dto.QuickBooksEmployeeResponse;
import com.staydesk.payroll.quickbooks.dto.QuickBooksTimeActivity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Optional;

@Service
public class QuickBooksClient {

    private final RestClient restClient = RestClient.create();
    private final QuickBooksAuthService authService;

    @Value("${quickbooks.api-base-url}")
    private String baseUrl;

    public QuickBooksClient(QuickBooksAuthService authService) {
        this.authService = authService;
    }

    private static String escapeForQuery(String value) {
        return value.replace("'", "''");
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

    public Optional<String> findEmployeeByDisplayName(String displayName) {
        try {
            String query = "SELECT * FROM Employee WHERE DisplayName = '" + escapeForQuery(displayName) + "'";

            QuickBooksEmployeeQueryResponse response = restClient.get()
                                                                 .uri(baseUrl + "/v3/company/" + authService.getRealmId() + "/query?query={query}", query)
                                                                 .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                                                                 .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                                                                 .retrieve()
                                                                 .body(QuickBooksEmployeeQueryResponse.class);

            return response == null || response.employees().isEmpty() ?
                   Optional.empty() :
                   Optional.of(response.employees().getFirst().id());
        } catch (RestClientException e) {
            throw new PayrollSyncException("QuickBooks employee lookup failed: " + e.getMessage(), e);
        }
    }

    public String createEmployee(String firstName, String lastName) {
        try {
            Map<String, String> body = Map.of(
                    "GivenName", firstName,
                    "FamilyName", lastName,
                    "DisplayName", firstName + " " + lastName
            );

            QuickBooksEmployeeResponse response = restClient.post()
                                                            .uri(baseUrl + "/v3/company/" + authService.getRealmId() + "/employee")
                                                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                                                            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .body(body)
                                                            .retrieve()
                                                            .body(QuickBooksEmployeeResponse.class);

            if (response == null || response.employee() == null || response.employee().id() == null) {
                throw new PayrollSyncException("QuickBooks employee creation returned no employee id");
            }

            return response.employee().id();
        } catch (RestClientException e) {
            throw new PayrollSyncException("QuickBooks employee create failed: " + e.getMessage(), e);
        }
    }
}
