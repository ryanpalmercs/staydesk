package com.staydesk.model;

import java.time.LocalDateTime;

public record SifelyStatusResponse(boolean connected, String clientId, LocalDateTime connectedAt) {
}
