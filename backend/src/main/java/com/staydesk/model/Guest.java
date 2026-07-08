package com.staydesk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("guests")
public record Guest(@Id int id, String firstName, String lastName, String email, String phoneNumber, LocalDateTime createdAt, LocalDateTime updatedAt) {

    @JsonProperty()
    public String name() {
        return firstName + " " + lastName;
    }
}
