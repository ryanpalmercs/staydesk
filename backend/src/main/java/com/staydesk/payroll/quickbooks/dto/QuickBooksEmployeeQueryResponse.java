package com.staydesk.payroll.quickbooks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record QuickBooksEmployeeQueryResponse(@JsonProperty("QueryResponse") QueryResponse queryResponse) {

    public List<QuickBooksEmployee> employees() {
        return queryResponse == null || queryResponse.employee() == null ? List.of() : queryResponse.employee();
    }

    public record QueryResponse(@JsonProperty("Employee") List<QuickBooksEmployee> employee) {
    }
}
