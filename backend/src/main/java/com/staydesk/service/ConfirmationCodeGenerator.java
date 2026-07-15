package com.staydesk.service;

import java.security.SecureRandom;

public final class ConfirmationCodeGenerator {
    private static final int LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ConfirmationCodeGenerator() {
    }

    public static String generate() {
        StringBuilder code = new StringBuilder(LENGTH);

        for (int i = 0; i < LENGTH; i++) {
            code.append(RANDOM.nextInt(10));
        }

        return code.toString();
    }
}
