package com.staydesk.model;

public record PayRateTypeResponse(String value, String displayName) {
    public static PayRateTypeResponse from(Employee.PayRateType payRateType) {
        return new PayRateTypeResponse(payRateType.name(), payRateType.getDisplayName());
    }
}