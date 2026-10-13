package com.staydesk.model.request;

import org.springframework.lang.Nullable;

public record RestockRequest(int quantity, @Nullable String note) {
}
