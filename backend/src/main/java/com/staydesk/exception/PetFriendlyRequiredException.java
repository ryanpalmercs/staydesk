package com.staydesk.exception;

public class PetFriendlyRequiredException extends RuntimeException {
    public PetFriendlyRequiredException() {
        super("This add-on is only available for pet-friendly room types.");
    }
}
