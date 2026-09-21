package com.staydesk.exception;

public class TerminalBridgeException extends RuntimeException {
    public TerminalBridgeException(String message) {
        super(message);
    }

    public TerminalBridgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
