package com.staydesk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("housekeeping_tasks")
public record HousekeepingTask(@Id int id, int roomId, @Nullable UUID assignedEmployeeId,
                               @Nullable LocalDateTime markedDirtyAt, @Nullable LocalDateTime cleanedAt,
                               @Nullable LocalDateTime inspectedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {

    // Status is derived from which timestamp is set rather than stored as its own column, so it
    // can never disagree with the timestamps themselves - see the migration's comment.
    @JsonProperty
    public Status status() {
        if (inspectedAt != null) {
            return Status.INSPECTED;
        } else if (cleanedAt != null) {
            return Status.CLEAN;
        } else if (markedDirtyAt != null) {
            return Status.DIRTY;
        }
        return Status.CLEAN;
    }

    public enum Status {
        DIRTY, CLEAN, INSPECTED
    }
}
