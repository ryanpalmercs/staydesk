package com.staydesk.payroll.quickbooks;

import com.staydesk.exception.PayrollSyncException;
import com.staydesk.model.ContactInfo;
import com.staydesk.model.Employee;
import com.staydesk.payroll.quickbooks.dto.QuickBooksEmployee;
import com.staydesk.payroll.quickbooks.dto.QuickBooksEmployeeQueryResponse;
import com.staydesk.payroll.quickbooks.dto.QuickBooksEmployeeResponse;
import com.staydesk.payroll.quickbooks.dto.QuickBooksTimeActivity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
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

    public String createEmployee(Employee employee) {
        try {
            QuickBooksEmployeeResponse response = restClient.post()
                                                            .uri(baseUrl + "/v3/company/" + authService.getRealmId() + "/employee")
                                                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                                                            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .body(buildEmployeeBody(employee))
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

    public void updateEmployee(Employee employee) {
        try {
            QuickBooksEmployee current = getEmployee(employee.quickbooksEmployeeId());

            Map<String, Object> body = buildEmployeeBody(employee);
            body.put("Id", employee.quickbooksEmployeeId());
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

    private Map<String, Object> buildEmployeeBody(Employee employee) {
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
            body.put("BillRate", employee.payRate());
            body.put("BillableTime", true);
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
