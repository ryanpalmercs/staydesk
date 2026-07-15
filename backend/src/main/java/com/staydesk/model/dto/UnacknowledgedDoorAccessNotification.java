package com.staydesk.model.dto;

import java.time.LocalDateTime;

public record UnacknowledgedDoorAccessNotification(int lockPasscodeId, int reservationId, int roomNumber,
                                                   String passcode, LocalDateTime resolvedAt) {
}