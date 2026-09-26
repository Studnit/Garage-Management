package com.citycarservices.garage.service;

import com.citycarservices.garage.domain.Booking;
import com.citycarservices.garage.domain.JobCard;
import com.citycarservices.garage.domain.Part;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DashboardData {
    private List<Booking> todaysAppointments = new ArrayList<>();
    private List<JobCard> inGarage = new ArrayList<>();
    private List<JobCard> readyForDelivery = new ArrayList<>();
    private int pendingJobs;
    private int completedJobsThisMonth;
    private int newBookings;
    private BigDecimal todayRevenue = BigDecimal.ZERO;
    private BigDecimal todayBilled = BigDecimal.ZERO;
    private BigDecimal monthRevenue = BigDecimal.ZERO;
    private BigDecimal pendingPaymentsTotal = BigDecimal.ZERO;
    private int pendingPaymentsCount;
    private List<Part> lowStock = new ArrayList<>();
    private List<MechanicStats> workload = new ArrayList<>();
    private List<ChartPoint> monthlyRevenue = new ArrayList<>();
    private List<ChartPoint> serviceStats = new ArrayList<>();
    private long pendingEstimates;
    private long paymentsToVerify;
    private long openPickups;
    private long pendingReminders;
}
