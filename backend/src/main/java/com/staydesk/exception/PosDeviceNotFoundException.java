package com.staydesk.exception;

public class PosDeviceNotFoundException extends RuntimeException {
    public PosDeviceNotFoundException() {
        super("POS device not found");
    }
}