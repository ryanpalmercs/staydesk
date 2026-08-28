package com.staydesk.payroll.quickbooks;

import com.staydesk.exception.PayrollSyncException;
import com.staydesk.model.ContactInfo;
import com.staydesk.model.Employee;
import com.staydesk.payroll.quickbooks.dto.QuickBooksCompanyInfoResponse;
import com.staydesk.payroll.quickbooks.dto.QuickBooksEmployee;
import com.staydesk.payroll.quickbooks.dto.QuickBooksEmployeeQueryResponse;
import com.staydesk.payroll.quickbooks.dto.QuickBooksEmployeeResponse;
import com.staydesk.payroll.quickbooks.dto.QuickBooksTimeActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class QuickBooksClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuickBooksClient.class);

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

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
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
            Optional<String> match = queryForEmployeeId(
                    "SELECT * FROM Employee WHERE DisplayName = '" + escapeForQuery(displayName) + "'");

            if (match.isPresent()) {
                return match;
            }

            return queryForEmployeeId(
                    "SELECT * FROM Employee WHERE DisplayName LIKE '" + escapeForQuery(displayName) + " (deleted%' AND Active IN (true, false)");
        } catch (RestClientResponseException e) {
            LOGGER.warn("QuickBooks employee query failed: status={} headers={} body={}",
                    e.getStatusCode(), e.getResponseHeaders(), e.getResponseBodyAsString());
            throw new PayrollSyncException("QuickBooks employee lookup failed: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new PayrollSyncException("QuickBooks employee lookup failed: " + e.getMessage(), e);
        }
    }

    private Optional<String> queryForEmployeeId(String query) {
        QuickBooksEmployeeQueryResponse response = restClient.get()
                                                             .uri(baseUrl + "/v3/company/" + authService.getRealmId() + "/query?query={query}", query)
                                                             .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                                                             .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                                                             .retrieve()
                                                             .body(QuickBooksEmployeeQueryResponse.class);

        return response == null || response.employees().isEmpty()
               ? Optional.empty()
               : Optional.of(response.employees().getFirst().id());
    }

    public String createEmployee(Employee employee, String jobTitle, boolean payrollEnabled) {
        try {
            QuickBooksEmployeeResponse response = restClient.post()
                                                            .uri(baseUrl + "/v3/company/" + authService.getRealmId() + "/employee")
                                                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                                                            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .body(buildEmployeeBody(employee, jobTitle, payrollEnabled))
                                                            .retrieve()
                                                            .body(QuickBooksEmployeeResponse.class);

            if (response == null || response.employee() == null || response.employee().id() == null) {
                throw new PayrollSyncException("QuickBooks employee creation returned no employee id");
            }

            return response.employee().id();
        } catch (RestClientException e) {
            throw new PayrollSyncException("QuickBooks employee creation failed: " + e.getMessage(), e);
        }
    }

    public void updateEmployee(Employee employee, String quickbooksEmployeeId, String jobTitle, boolean payrollEnabled) {
        try {
            QuickBooksEmployee current = getEmployee(quickbooksEmployeeId);

            Map<String, Object> body = buildEmployeeBody(employee, jobTitle, payrollEnabled);
            body.put("Id", quickbooksEmployeeId);
            body.put("SyncToken", current.syncToken());
            body.put("sparse", true);

            restClient.post()
                      .uri(baseUrl + "/v3/company/" + authService.getRealmId() + "/employee")
                      .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                      .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                      .contentType(MediaType.APPLICATION_JSON)
                      .body(body)
                      .retrieve()
                      .toBodilessEntity();
        } catch (RestClientException e) {
            throw new PayrollSyncException("QuickBooks employee update failed: " + e.getMessage(), e);
        }
    }
    public QuickBooksEmployee getEmployee(String quickbooksEmployeeId) {
        try {
            QuickBooksEmployeeResponse response = restClient.get()
                                                            .uri(baseUrl + "/v3/company/" + authService.getRealmId() + "/employee/" + quickbooksEmployeeId)
                                                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                                                            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                                                            .retrieve()
                                                            .body(QuickBooksEmployeeResponse.class);

            if (response == null || response.employee() == null) {
                throw new PayrollSyncException("QuickBooks employee lookup returned no employee");
            }

            return response.employee();
        } catch (RestClientResponseException e) {
            LOGGER.warn("QuickBooks employee lookup failed: status={} headers={} body={}",
                    e.getStatusCode(), e.getResponseHeaders(), e.getResponseBodyAsString());
            throw new PayrollSyncException("QuickBooks employee lookup failed: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new PayrollSyncException("QuickBooks employee lookup failed: " + e.getMessage(), e);
        }
    }

    public void setEmployeeActive(String quickbooksEmployeeId, boolean active) {
        try {
            QuickBooksEmployee current = getEmployee(quickbooksEmployeeId);

            Map<String, Object> body = Map.of(
                    "Id", quickbooksEmployeeId,
                    "SyncToken", current.syncToken(),
                    "sparse", true,
                    "Active", active
            );

            restClient.post()
                      .uri(baseUrl + "/v3/company/" + authService.getRealmId() + "/employee")
                      .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                      .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                      .contentType(MediaType.APPLICATION_JSON)
                      .body(body)
                      .retrieve()
                      .toBodilessEntity();
        } catch (RestClientException e) {
            throw new PayrollSyncException("QuickBooks employee status update failed: " + e.getMessage(), e);
        }
    }

    public boolean isPayrollEnabled() {
        try {
            String realmId = authService.getRealmId();

            QuickBooksCompanyInfoResponse response = restClient.get()
                                                               .uri(baseUrl + "/v3/company/" + realmId + "/companyinfo/" + realmId)
                                                               .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                                                               .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                                                               .retrieve()
                                                               .body(QuickBooksCompanyInfoResponse.class);

            return response != null && response.isPayrollEnabled();
        } catch (RestClientException e) {
            throw new PayrollSyncException("QuickBooks company info lookup failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildEmployeeBody(Employee employee, String jobTitle, boolean payrollEnabled) {
        Map<String, Object> body = new HashMap<>();
        body.put("GivenName", employee.firstName().value());
        body.put("FamilyName", employee.lastName().value());
        body.put("DisplayName", employee.name());
        body.put("Active", employee.active());
        body.put("EmployeeNumber", employee.username());
        body.put("PrimaryEmailAddr", Map.of("Address", employee.email().value()));

        if (employee.hireDate() != null) {
            body.put("HiredDate", employee.hireDate().toString());
        }

        if (employee.payRate() != null) {
            body.put("CostRate", employee.payRate());
        }

        // Title and BillRate are not supported when QuickBooks Payroll is enabled on the connected company.
        if (jobTitle != null && !payrollEnabled) {
            body.put("Title", jobTitle);
        }

        ContactInfo contactInfo = employee.contactInfo();
        if (contactInfo != null) {
            Map<String, String> address = new HashMap<>();
            putIfPresent(address, "Line1", contactInfo.addressLine1());
            putIfPresent(address, "Line2", contactInfo.addressLine2());
            putIfPresent(address, "City", contactInfo.city());
            putIfPresent(address, "CountrySubDivisionCode", contactInfo.state());
            putIfPresent(address, "PostalCode", contactInfo.zipCode());

            if (!address.isEmpty()) {
                body.put("PrimaryAddr", address);
            }

            if (contactInfo.phone() != null) {
                body.put("PrimaryPhone", Map.of("FreeFormNumber", contactInfo.phone()));
            }
        }

        return body;
    }
}
