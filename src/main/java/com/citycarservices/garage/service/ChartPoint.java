package com.citycarservices.garage.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/** One bar in a simple CSS bar chart. */
@Getter
@AllArgsConstructor
public class ChartPoint {
    private final String label;
    private final BigDecimal value;
    private final String display;
    /** Bar length 0-100. */
    private final int pct;
}
