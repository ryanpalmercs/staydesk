package com.staydesk.exception;

public class CardPresentRecordOnlyDisabledException extends RuntimeException {
    public CardPresentRecordOnlyDisabledException() {
        super("No card-present terminal is paired and record-only mode is disabled");
    }
}
