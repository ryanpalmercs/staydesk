package com.staydesk.model.request;

import org.springframework.lang.Nullable;

public record LogUsageRequest(int quantity, @Nullable Integer roomId, @Nullable String note) {
}
