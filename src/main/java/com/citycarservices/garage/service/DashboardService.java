package com.citycarservices.garage.service;

import com.citycarservices.garage.config.Fmt;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.repo.BookingRepository;
import com.citycarservices.garage.repo.InvoiceRepository;
import com.citycarservices.garage.repo.JobCardRepository;
import com.citycarservices.garage.repo.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;

@Service
public class DashboardService {

    private final BookingRepository bookings;
    private final JobCardRepository jobs;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final InventoryService inventory;
    private final MechanicService mechanics;
    private final EstimateService estimates;
    private final PaymentService paymentService;
    private final PickupService pickups;
    private final ReminderService reminders;
    private final Fmt fmt = new Fmt();

    public DashboardService(BookingRepository bookings, JobCardRepository jobs, InvoiceRepository invoices,
                            PaymentRepository payments, InventoryService inventory, MechanicService mechanics,
                            EstimateService estimates, PaymentService paymentService, PickupService pickups,
                            ReminderService reminders) {
        this.bookings = bookings;
        this.jobs = jobs;
        this.invoices = invoices;
        this.payments = payments;
        this.inventory = inventory;
        this.mechanics = mechanics;
        this.estimates = estimates;
        this.paymentService = paymentService;
        this.pickups = pickups;
        this.reminders = reminders;
    }

    private BigDecimal collected(LocalDate from, LocalDate toInclusive) {
        BigDecimal sum = Money.ZERO;
        for (Payment p : payments.findByPaidAtBetweenAndStatusOrderByPaidAtDesc(
                from.atStartOfDay(), toInclusive.plusDays(1).atStartOfDay(), PaymentRecordStatus.RECEIVED)) {
            sum = sum.add(p.getAmount());
        }
        return sum;
    }

    @Transactional(readOnly = true)
    public DashboardData build() {
        DashboardData d = new DashboardData();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        d.setTodaysAppointments(bookings
                .findByPreferredAtBetweenOrderByPreferredAtAsc(today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .stream().filter(b -> b.getStatus() != BookingStatus.CANCELLED).toList());
        d.setNewBookings((int) bookings.countByStatus(BookingStatus.PENDING));

        List<JobCard> active = jobs.findByBookingStatusInOrderByCreatedAtDesc(EnumSet.of(BookingStatus.VEHICLE_RECEIVED,
                BookingStatus.INSPECTION, BookingStatus.IN_SERVICE, BookingStatus.QUALITY_CHECK, BookingStatus.READY));
        d.setInGarage(active);
        d.setPendingJobs((int) active.stream().filter(j -> j.getBooking().getStatus() != BookingStatus.READY).count());
        d.setReadyForDelivery(active.stream().filter(j -> j.getBooking().getStatus() == BookingStatus.READY).toList());
        d.setCompletedJobsThisMonth((int) jobs.findByStatusOrderByCreatedAtDesc(JobStatus.COMPLETED).stream()
                .filter(j -> j.getCompletedAt() != null && !j.getCompletedAt().toLocalDate().isBefore(monthStart)).count());

        d.setTodayRevenue(collected(today, today));
        d.setMonthRevenue(collected(monthStart, today));
        BigDecimal billed = Money.ZERO;
        for (Invoice i : invoices.findByInvoiceDateBetweenOrderByInvoiceDateAscIdAsc(today, today)) {
            billed = billed.add(i.getTotal());
        }
        d.setTodayBilled(billed);

        BigDecimal pending = Money.ZERO;
        int pendingCount = 0;
        for (Invoice i : invoices.findByPaymentStatusInOrderByInvoiceDateAsc(
                EnumSet.of(PaymentStatus.UNPAID, PaymentStatus.PARTIAL))) {
            pending = pending.add(i.getBalance());
            pendingCount++;
        }
        d.setPendingPaymentsTotal(pending);
        d.setPendingPaymentsCount(pendingCount);

        d.setLowStock(inventory.lowStock());
        d.setWorkload(mechanics.allStats(monthStart.atStartOfDay(), LocalDateTime.now()).stream()
                .filter(s -> s.getMechanic().isActive()).toList());

        // last 6 months collections
        List<ChartPoint> months = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        YearMonth ym = YearMonth.from(today);
        for (int i = 5; i >= 0; i--) {
            YearMonth m = ym.minusMonths(i);
            values.add(collected(m.atDay(1), m.atEndOfMonth()));
            labels.add(m.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + String.valueOf(m.getYear()).substring(2));
        }
        BigDecimal max = values.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        for (int i = 0; i < values.size(); i++) {
            months.add(new ChartPoint(labels.get(i), values.get(i), fmt.money(values.get(i)), pct(values.get(i), max)));
        }
        d.setMonthlyRevenue(months);

        // service statistics: bookings by service in the last 90 days
        Map<String, Integer> counts = new HashMap<>();
        LocalDateTime since = LocalDateTime.now().minusDays(90);
        for (Booking b : bookings.findAll()) {
            if (b.getStatus() == BookingStatus.CANCELLED || b.getCreatedAt().isBefore(since)) {
                continue;
            }
            for (ServiceItem s : b.getServices()) {
                counts.merge(s.getName(), 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        int top = sorted.isEmpty() ? 0 : sorted.get(0).getValue();
        List<ChartPoint> stats = new ArrayList<>();
        for (int i = 0; i < Math.min(8, sorted.size()); i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            stats.add(new ChartPoint(e.getKey(), BigDecimal.valueOf(e.getValue()), String.valueOf(e.getValue()),
                    top == 0 ? 0 : e.getValue() * 100 / top));
        }
        d.setServiceStats(stats);

        d.setPendingEstimates(estimates.countPending());
        d.setPaymentsToVerify(paymentService.pendingVerification().size());
        d.setOpenPickups(pickups.countOpen());
        d.setPendingReminders(reminders.countPending());
        return d;
    }

    private int pct(BigDecimal v, BigDecimal max) {
        if (max.signum() <= 0) {
            return 0;
        }
        int p = v.multiply(new BigDecimal("100")).divide(max, 0, RoundingMode.HALF_UP).intValue();
        return v.signum() > 0 && p < 3 ? 3 : p;
    }
}
