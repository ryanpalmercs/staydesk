package com.staydesk.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PairDeviceRequest(@NotBlank @Size(max = 10) String pairingCode,
                                @NotBlank @Size(max = 12) String friendlyName, @Size(max = 16) String location) {
}