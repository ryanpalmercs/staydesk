package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;

@Table("pos_devices")
public record PosDevice(@Id int id, String deviceId, String friendlyName, @Nullable String location,
                        LocalDateTime pairedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
}