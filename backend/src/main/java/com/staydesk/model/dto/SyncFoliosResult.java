package com.staydesk.model.dto;

import java.util.List;

public record SyncFoliosResult(int syncedCount, List<String> confirmationCodes) {
}
