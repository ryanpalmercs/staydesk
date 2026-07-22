package com.staydesk.model.request;

public record CheckInRequest(int roomId, String incidentalsPaymentMethodId, String roomPaymentMethodId) {
}