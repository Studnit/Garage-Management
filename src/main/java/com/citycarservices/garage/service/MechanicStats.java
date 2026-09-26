package com.citycarservices.garage.service;

import com.citycarservices.garage.domain.Mechanic;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class MechanicStats {
    private final Mechanic mechanic;
    private final int openJobs;
    private final int completedJobs;
    private final BigDecimal estimatedHours;
    private final BigDecimal actualHours;
    private final BigDecimal labourBilled;
    /** Estimated hours / actual hours, in percent (100 = on estimate). */
    private final int productivityPct;
    private final double avgTurnaroundHours;
}
