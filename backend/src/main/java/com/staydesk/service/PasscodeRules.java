package com.staydesk.service;

public final class PasscodeRules {
    private PasscodeRules() {
    }

    public static boolean isValidPin(String pin) {
        return pin != null && pin.matches("\\d{6}") && !isConsecutiveOrRepeated(pin);
    }

    public static boolean isConsecutiveOrRepeated(String code) {
        boolean ascending = true;
        boolean descending = true;
        boolean repeated = true;

        for (int i = 1; i < code.length(); i++) {
            int prev = code.charAt(i - 1) - '0';
            int curr = code.charAt(i) - '0';

            if (curr != prev + 1) {
                ascending = false;
            }
            if (curr != prev - 1) {
                descending = false;
            }
            if (curr != prev) {
                repeated = false;
            }
        }

        return ascending || descending || repeated;
    }
}