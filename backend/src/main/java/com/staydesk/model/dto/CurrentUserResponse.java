package com.staydesk.model.dto;

import java.util.UUID;

public record CurrentUserResponse(UUID id, String displayName, Integer lastSeenReleaseNotesId, String currentAppVersion) {
}
