package com.staydesk.model.dto;

import java.util.UUID;

public record CurrentUserResponse(UUID id, String displayName, String lastSeenAppVersion, String currentAppVersion) {
}
