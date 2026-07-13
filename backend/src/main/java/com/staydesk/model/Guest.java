package com.staydesk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("guests")
public record Guest(@Id int id, EncryptedString firstName, EncryptedString lastName, EncryptedString email,
                    @JsonIgnore String emailHash, EncryptedString phoneNumber, boolean smsConsent,
                    boolean flagged, @Nullable String flagReason, @Nullable LocalDateTime flaggedDate,
                    @Nullable UUID flaggedBy, boolean legalHold, LocalDateTime createdAt, LocalDateTime updatedAt) {

    @JsonProperty()
    public String name() {
        return firstName.value() + " " + lastName.value();
    }
}
