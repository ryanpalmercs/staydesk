package com.staydesk.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PasscodeLabels {
    private static final String RESERVATION_PREFIX = "Res-";
    private static final String STAFF_PREFIX = "Staff-";

    private static final Pattern RESERVATION_PATTERN = Pattern.compile("^" + Pattern.quote(RESERVATION_PREFIX) + "(\\d+)$");
    private static final Pattern STAFF_PATTERN = Pattern.compile("^" + Pattern.quote(STAFF_PREFIX) + "(.+)$");

    private PasscodeLabels() {
    }

    public static String forReservation(int reservationId) {
        return RESERVATION_PREFIX + reservationId;
    }

    public static String forStaff(String username) {
        return STAFF_PREFIX + username;
    }

    public static Optional<Integer> parseReservationId(String label) {
        Matcher matcher = RESERVATION_PATTERN.matcher(label == null ? "" : label);
        return matcher.matches() ? Optional.of(Integer.parseInt(matcher.group(1))) : Optional.empty();
    }

    public static Optional<String> parseStaffUsername(String label) {
        Matcher matcher = STAFF_PATTERN.matcher(label == null ? "" : label);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}