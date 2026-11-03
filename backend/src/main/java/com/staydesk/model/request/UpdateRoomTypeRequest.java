package com.staydesk.model.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateRoomTypeRequest(@NotBlank String name) {
}
