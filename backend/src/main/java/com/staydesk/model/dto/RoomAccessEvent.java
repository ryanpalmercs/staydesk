package com.staydesk.model.dto;

import java.time.LocalDateTime;

public record RoomAccessEvent(LocalDateTime occurredAt, String eventType, boolean success, String actor,
                              ActorType actorType) {
    public enum ActorType {GUEST, STAFF, UNKNOWN, UNAUTHORIZED}
}