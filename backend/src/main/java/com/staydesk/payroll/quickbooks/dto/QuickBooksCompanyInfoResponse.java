package com.staydesk.payroll.quickbooks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record QuickBooksCompanyInfoResponse(@JsonProperty("CompanyInfo") CompanyInfo companyInfo) {

    public record CompanyInfo(@JsonProperty("NameValue") List<NameValue> nameValue) {
    }

    public record NameValue(@JsonProperty("Name") String name, @JsonProperty("Value") String value) {
    }

    public boolean isPayrollEnabled() {
        if (companyInfo == null || companyInfo.nameValue() == null) {
            return false;
        }

        return companyInfo.nameValue().stream()
                .anyMatch(nv -> "PayrollFeature".equals(nv.name()) && "true".equalsIgnoreCase(nv.value()));
    }
}