package com.citycarservices.garage.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Small helper for rupee arithmetic (2 decimals, HALF_UP). */
public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    public static final BigDecimal HUNDRED = new BigDecimal("100");

    private Money() {
    }

    public static BigDecimal round(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    public static BigDecimal percent(BigDecimal amount, BigDecimal rate) {
        return round(nz(amount).multiply(nz(rate)).divide(HUNDRED, 4, RoundingMode.HALF_UP));
    }
}
