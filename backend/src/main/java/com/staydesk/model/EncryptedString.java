package com.staydesk.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record EncryptedString(@JsonValue String value) {
    @JsonCreator
    public static EncryptedString of(String value) {
        return new EncryptedString(value);
    }
}
