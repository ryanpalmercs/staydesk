package com.staydesk.model;

public enum AuthRole {

    ADMIN("Admin"), MANAGER("Manager"), FRONT_DESK("Front Desk"), HOUSEKEEPING("Housekeeping"), EMPLOYEE("Employee");

    private final String displayName;

    AuthRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
