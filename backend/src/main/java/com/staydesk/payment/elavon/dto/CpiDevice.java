package com.staydesk.payment.elavon.dto;

public record CpiDevice(CpiDeviceIdentifiers deviceIdentifiers, String deviceManufacturer, String deviceModel,
                        String deviceType, String pairedStatus) {
}