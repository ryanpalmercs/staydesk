package com.staydesk.payment.ingenico;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class IngenicoAmountFormat {

    private IngenicoAmountFormat() {
    }

    public static long toCents(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }

        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amount must not be negative: " + amount);
        }

        return amount.setScale(2, RoundingMode.HALF_UP)
                     .movePointRight(2)
                     .setScale(0, RoundingMode.UNNECESSARY)
                     .longValueExact();
    }
}
