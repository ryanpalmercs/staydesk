package com.staydesk.model;

import java.time.LocalDateTime;

public record QuickBooksStatusResponse(boolean connected, String realmId, LocalDateTime connectedAt,
                                       LocalDateTime refreshTokenExpiresAt) {
}
