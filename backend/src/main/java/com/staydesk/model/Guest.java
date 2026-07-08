package com.staydesk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("guests")
public record Guest(@Id int id, String firstName, String lastName, String email, String phoneNumber,
                    boolean flagged, @Nullable String flagReason, @Nullable LocalDateTime flaggedDate,
                    @Nullable UUID flaggedBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
    
    @JsonProperty()
    public String name() {
        return firstName + " " + lastName;
    }
}
