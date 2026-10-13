package com.staydesk.model.request;

import com.staydesk.model.HousekeepingTask;

public record UpdateHousekeepingStatusRequest(HousekeepingTask.Status status) {
}
