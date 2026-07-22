package com.staydesk.exception;

public class NoReusableCredentialException extends RuntimeException {
    public NoReusableCredentialException() {
        super("No active reusable payment credential exists for this folio.");
    }
}