package com.staydesk.model;

import org.springframework.data.annotation.Id;

import java.math.BigDecimal;

public record Rate(@Id int id, String rateType, int guestCount, BigDecimal amount) {

    public enum RateType {
        NIGHTLY, WEEKLY_5, WEEKLY_7
    }
}
