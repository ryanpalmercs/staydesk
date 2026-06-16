package com.staydesk.model;

public record CheckInRequest(String roomPaymentMethodId, String incidentalsPaymentMethodId) {
}