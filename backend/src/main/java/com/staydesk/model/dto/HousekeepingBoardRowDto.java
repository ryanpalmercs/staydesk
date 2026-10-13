package com.staydesk.model.dto;

import com.staydesk.model.HousekeepingTask;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

public record HousekeepingBoardRowDto(int roomId, int roomNumber, HousekeepingTask.Status status,
                                      @Nullable UUID assignedEmployeeId, @Nullable String assignedEmployeeName,
                                      @Nullable LocalDateTime markedDirtyAt, @Nullable LocalDateTime cleanedAt,
                                      @Nullable LocalDateTime inspectedAt) {
}
