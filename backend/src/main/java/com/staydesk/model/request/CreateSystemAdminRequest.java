package com.staydesk.model.request;

public record CreateSystemAdminRequest(String email, String password, String displayName) {
}
