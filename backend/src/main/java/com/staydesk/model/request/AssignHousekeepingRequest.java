package com.staydesk.model.request;

import org.springframework.lang.Nullable;

import java.util.UUID;

public record AssignHousekeepingRequest(@Nullable UUID employeeId) {
}
