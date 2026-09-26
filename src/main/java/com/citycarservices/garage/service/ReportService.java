package com.citycarservices.garage.service;

import com.citycarservices.garage.config.Fmt;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Builds all management reports as generic tables (see ReportTable). */
@Service
public class ReportService {

    public static final List<String[]> CATALOG = List.of(
            new String[] {"revenue", "Daily / monthly revenue"},
            new String[] {"services", "Service-wise revenue"},
            new String[] {"parts-sales", "Parts sales"},
            new String[] {"labour", "Labour revenue"},
            new String[] {"pending", "Pending payments"},
            new String[] {"customers", "Customer report"},
            new String[] {"vehicle-history", "Vehicle service history"},
            new String[] {"mechanics", "Mechanic performance"},
            new String[] {"inventory", "Inventory report"},
            new String[] {"parts", "Most-used parts"},
            new String[] {"repeat", "Repeat customers"});

    private final InvoiceRepository invoices;
    private final InvoiceLineRepository invoiceLines;
    private final PaymentRepository payments;
    private final StockMovementRepository movements;
    private final PartRepository parts;
    private final CustomerRepository customers;
    private final BookingRepository bookings;
    private final VehicleRepository vehicles;
    private final MechanicService mechanics;
    private final Fmt f = new Fmt();

    public ReportService(InvoiceRepository invoices, InvoiceLineRepository invoiceLines, PaymentRepository payments,
                         StockMovementRepository movements, PartRepository parts, CustomerRepository customers,
                         BookingRepository bookings, VehicleRepository vehicles, MechanicService mechanics) {
        this.invoices = invoices;
        this.invoiceLines = invoiceLines;
        this.payments = payments;
        this.movements = movements;
        this.parts = parts;
        this.customers = customers;
        this.bookings = bookings;
        this.vehicles = vehicles;
        this.mechanics = mechanics;
    }

    @Transactional(readOnly = true)
    public ReportTable build(String key, LocalDate from, LocalDate to, String q) {
        return switch (key) {
            case "services" -> serviceRevenue(from, to);
            case "parts-sales" -> partsSales(from, to);
            case "labour" -> labour(from, to);
            case "pending" -> pending();
            case "customers" -> customerReport(from, to);
            case "vehicle-history" -> vehicleHistory(q);
            case "mechanics" -> mechanicPerformance(from, to);
            case "inventory" -> inventory();
            case "parts" -> mostUsedParts(from, to);
            case "repeat" -> repeatCustomers();
            default -> revenue(from, to);
        };
    }

    public boolean usesDates(String key) {
        return !("pending".equals(key) || "inventory".equals(key) || "repeat".equals(key) || "vehicle-history".equals(key));
    }

    private static boolean isLabour(InvoiceLine l) {
        return l.getLineType() != LineType.PART;
    }

    private BigDecimal sum(Collection<BigDecimal> values) {
        BigDecimal s = Money.ZERO;
        for (BigDecimal v : values) {
            s = s.add(v);
        }
        return s;
    }

    // ---------------------------------------------------------------- revenue

    private ReportTable revenue(LocalDate from, LocalDate to) {
        ReportTable t = new ReportTable("revenue", "Daily revenue",
                "Invoices raised and payments collected per day. Monthly totals are shown in the summary.")
                .col("Date", false).col("Invoices", true).col("Taxable value", true).col("GST", true)
                .col("Billed", true).col("Collected", true);
        Map<LocalDate, List<Invoice>> byDay = new TreeMap<>();
        for (Invoice i : invoices.findByInvoiceDateBetweenOrderByInvoiceDateAscIdAsc(from, to)) {
            byDay.computeIfAbsent(i.getInvoiceDate(), k -> new ArrayList<>()).add(i);
        }
        Map<LocalDate, BigDecimal> collected = new TreeMap<>();
        for (Payment p : payments.findByPaidAtBetweenAndStatusOrderByPaidAtDesc(from.atStartOfDay(),
                to.plusDays(1).atStartOfDay(), PaymentRecordStatus.RECEIVED)) {
            collected.merge(p.getPaidAt().toLocalDate(), p.getAmount(), BigDecimal::add);
        }
        Set<LocalDate> days = new TreeSet<>(byDay.keySet());
        days.addAll(collected.keySet());
        BigDecimal taxable = Money.ZERO;
        BigDecimal gst = Money.ZERO;
        BigDecimal billed = Money.ZERO;
        BigDecimal coll = Money.ZERO;
        int count = 0;
        for (LocalDate d : days) {
            List<Invoice> list = byDay.getOrDefault(d, List.of());
            BigDecimal tv = sum(list.stream().map(Invoice::getTaxableValue).toList());
            BigDecimal g = sum(list.stream().map(Invoice::getTaxTotal).toList());
            BigDecimal b = sum(list.stream().map(Invoice::getTotal).toList());
            BigDecimal c = collected.getOrDefault(d, Money.ZERO);
            t.row(f.date(d), String.valueOf(list.size()), f.money(tv), f.money(g), f.money(b), f.money(c));
            taxable = taxable.add(tv);
            gst = gst.add(g);
            billed = billed.add(b);
            coll = coll.add(c);
            count += list.size();
        }
        t.totals("Total", String.valueOf(count), f.money(taxable), f.money(gst), f.money(billed), f.money(coll));
        t.tile("Invoices", String.valueOf(count)).tile("Billed (incl. GST)", f.money(billed))
                .tile("Collected", f.money(coll)).tile("GST collected", f.money(gst));
        return t;
    }

    // ---------------------------------------------------------------- service / parts / labour

    private ReportTable serviceRevenue(LocalDate from, LocalDate to) {
        ReportTable t = new ReportTable("services", "Service-wise revenue",
                "Labour and service charges billed, grouped by service / labour description (excl. GST).")
                .col("Service / labour", false).col("Times billed", true).col("Revenue (excl. GST)", true);
        Map<String, BigDecimal> amount = new HashMap<>();
        Map<String, Integer> count = new HashMap<>();
        for (InvoiceLine l : invoiceLines.findByInvoice_InvoiceDateBetween(from, to)) {
            if (!isLabour(l)) {
                continue;
            }
            String k = l.getDescription().trim();
            amount.merge(k, l.getTaxableValue(), BigDecimal::add);
            count.merge(k, 1, Integer::sum);
        }
        BigDecimal total = Money.ZERO;
        int n = 0;
        for (Map.Entry<String, BigDecimal> e : sortedDesc(amount)) {
            t.row(e.getKey(), String.valueOf(count.get(e.getKey())), f.money(e.getValue()));
            total = total.add(e.getValue());
            n += count.get(e.getKey());
        }
        t.totals("Total", String.valueOf(n), f.money(total));
        t.tile("Revenue from services", f.money(total)).tile("Distinct services", String.valueOf(amount.size()));
        return t;
    }

    private ReportTable partsSales(LocalDate from, LocalDate to) {
        ReportTable t = new ReportTable("parts-sales", "Parts sales",
                "Spare parts billed to customers, grouped by part (excl. GST).")
                .col("Part", false).col("Qty sold", true).col("Sales (excl. GST)", true);
        Map<String, BigDecimal> amount = new HashMap<>();
        Map<String, BigDecimal> qty = new HashMap<>();
        for (InvoiceLine l : invoiceLines.findByInvoice_InvoiceDateBetween(from, to)) {
            if (isLabour(l)) {
                continue;
            }
            String k = l.getDescription().trim();
            amount.merge(k, l.getTaxableValue(), BigDecimal::add);
            qty.merge(k, l.getQuantity(), BigDecimal::add);
        }
        BigDecimal total = Money.ZERO;
        BigDecimal q = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : sortedDesc(amount)) {
            t.row(e.getKey(), f.qty(qty.get(e.getKey())), f.money(e.getValue()));
            total = total.add(e.getValue());
            q = q.add(qty.get(e.getKey()));
        }
        t.totals("Total", f.qty(q), f.money(total));
        t.tile("Parts sales", f.money(total)).tile("Distinct parts", String.valueOf(amount.size()));
        return t;
    }

    private ReportTable labour(LocalDate from, LocalDate to) {
        ReportTable t = new ReportTable("labour", "Labour revenue",
                "Labour and service charges billed per mechanic who handled the job (excl. GST), compared with parts.")
                .col("Mechanic", false).col("Jobs invoiced", true).col("Labour billed", true).col("Parts billed", true);
        Map<String, BigDecimal> labour = new TreeMap<>();
        Map<String, BigDecimal> partsAmt = new HashMap<>();
        Map<String, Integer> jobs = new HashMap<>();
        for (Invoice inv : invoices.findByInvoiceDateBetweenOrderByInvoiceDateAscIdAsc(from, to)) {
            String m = inv.getJobCard().getMechanic() == null ? "Unassigned" : inv.getJobCard().getMechanic().getName();
            jobs.merge(m, 1, Integer::sum);
            labour.putIfAbsent(m, Money.ZERO);
            for (InvoiceLine l : inv.getLines()) {
                if (isLabour(l)) {
                    labour.merge(m, l.getTaxableValue(), BigDecimal::add);
                } else {
                    partsAmt.merge(m, l.getTaxableValue(), BigDecimal::add);
                }
            }
        }
        BigDecimal lt = Money.ZERO;
        BigDecimal pt = Money.ZERO;
        int jt = 0;
        for (Map.Entry<String, BigDecimal> e : labour.entrySet()) {
            BigDecimal p = partsAmt.getOrDefault(e.getKey(), Money.ZERO);
            t.row(e.getKey(), String.valueOf(jobs.get(e.getKey())), f.money(e.getValue()), f.money(p));
            lt = lt.add(e.getValue());
            pt = pt.add(p);
            jt += jobs.get(e.getKey());
        }
        t.totals("Total", String.valueOf(jt), f.money(lt), f.money(pt));
        t.tile("Labour & service revenue", f.money(lt)).tile("Parts revenue", f.money(pt));
        return t;
    }

    private List<Map.Entry<String, BigDecimal>> sortedDesc(Map<String, BigDecimal> m) {
        List<Map.Entry<String, BigDecimal>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        return l;
    }

    // ---------------------------------------------------------------- pending / customers

    private ReportTable pending() {
        ReportTable t = new ReportTable("pending", "Pending payments",
                "All invoices that are unpaid or partly paid, oldest first.")
                .col("Invoice", false).col("Date", false).col("Customer", false).col("Mobile", false)
                .col("Vehicle", false).col("Total", true).col("Paid", true).col("Balance", true).col("Days old", true);
        BigDecimal bal = Money.ZERO;
        BigDecimal tot = Money.ZERO;
        BigDecimal paid = Money.ZERO;
        int n = 0;
        for (Invoice i : invoices.findByPaymentStatusInOrderByInvoiceDateAsc(
                EnumSet.of(PaymentStatus.UNPAID, PaymentStatus.PARTIAL))) {
            long days = ChronoUnit.DAYS.between(i.getInvoiceDate(), LocalDate.now());
            t.row(i.getInvoiceNo(), f.date(i.getInvoiceDate()), i.getCustomer().getName(), i.getCustomer().getMobile(),
                    i.getVehicle().getRegNo(), f.money(i.getTotal()), f.money(i.getAmountPaid()),
                    f.money(i.getBalance()), String.valueOf(days));
            bal = bal.add(i.getBalance());
            tot = tot.add(i.getTotal());
            paid = paid.add(i.getAmountPaid());
            n++;
        }
        t.totals("Total", "", "", "", "", f.money(tot), f.money(paid), f.money(bal), "");
        t.tile("Invoices pending", String.valueOf(n)).tile("Outstanding", f.money(bal));
        return t;
    }

    private ReportTable customerReport(LocalDate from, LocalDate to) {
        ReportTable t = new ReportTable("customers", "Customer report",
                "Billing per customer for invoices dated in the period, highest billing first.")
                .col("Code", false).col("Customer", false).col("Mobile", false).col("Invoices", true)
                .col("Billed", true).col("Paid", true).col("Outstanding", true).col("Last visit", false);
        Map<Long, List<Invoice>> by = new HashMap<>();
        for (Invoice i : invoices.findByInvoiceDateBetweenOrderByInvoiceDateAscIdAsc(from, to)) {
            by.computeIfAbsent(i.getCustomer().getId(), k -> new ArrayList<>()).add(i);
        }
        List<Map.Entry<Long, List<Invoice>>> entries = new ArrayList<>(by.entrySet());
        entries.sort((a, b) -> sum(b.getValue().stream().map(Invoice::getTotal).toList())
                .compareTo(sum(a.getValue().stream().map(Invoice::getTotal).toList())));
        BigDecimal billed = Money.ZERO;
        BigDecimal paid = Money.ZERO;
        for (Map.Entry<Long, List<Invoice>> e : entries) {
            Customer c = e.getValue().get(0).getCustomer();
            BigDecimal b = sum(e.getValue().stream().map(Invoice::getTotal).toList());
            BigDecimal p = sum(e.getValue().stream().map(Invoice::getAmountPaid).toList());
            LocalDate last = e.getValue().stream().map(Invoice::getInvoiceDate).max(Comparator.naturalOrder()).orElse(null);
            t.row(String.valueOf(c.getCode()), c.getName(), c.getMobile(), String.valueOf(e.getValue().size()),
                    f.money(b), f.money(p), f.money(b.subtract(p)), f.date(last));
            billed = billed.add(b);
            paid = paid.add(p);
        }
        t.totals("Total", "", "", "", f.money(billed), f.money(paid), f.money(billed.subtract(paid)), "");
        t.tile("Active customers", String.valueOf(entries.size())).tile("Total customers", String.valueOf(customers.count()))
                .tile("Billed", f.money(billed));
        return t;
    }

    private ReportTable repeatCustomers() {
        ReportTable t = new ReportTable("repeat", "Repeat customers",
                "Customers with two or more delivered / completed visits.")
                .col("Customer", false).col("Mobile", false).col("Visits", true).col("First visit", false)
                .col("Last visit", false).col("Total billed", true);
        Map<Long, List<Booking>> by = new HashMap<>();
        for (Booking b : bookings.findAll()) {
            if (b.getStatus() == BookingStatus.DELIVERED || b.getStatus() == BookingStatus.COMPLETED) {
                by.computeIfAbsent(b.getCustomer().getId(), k -> new ArrayList<>()).add(b);
            }
        }
        Map<Long, BigDecimal> billed = new HashMap<>();
        for (Invoice i : invoices.findAll()) {
            billed.merge(i.getCustomer().getId(), i.getTotal(), BigDecimal::add);
        }
        List<Map.Entry<Long, List<Booking>>> rows = by.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size())).toList();
        for (Map.Entry<Long, List<Booking>> e : rows) {
            Customer c = e.getValue().get(0).getCustomer();
            LocalDate first = e.getValue().stream().map(b -> b.getPreferredAt().toLocalDate()).min(Comparator.naturalOrder()).orElse(null);
            LocalDate last = e.getValue().stream().map(b -> b.getPreferredAt().toLocalDate()).max(Comparator.naturalOrder()).orElse(null);
            t.row(c.getName(), c.getMobile(), String.valueOf(e.getValue().size()), f.date(first), f.date(last),
                    f.money(billed.getOrDefault(c.getId(), Money.ZERO)));
        }
        t.tile("Repeat customers", String.valueOf(rows.size()));
        return t;
    }

    // ---------------------------------------------------------------- vehicle / mechanics

    private ReportTable vehicleHistory(String regQuery) {
        ReportTable t = new ReportTable("vehicle-history", "Vehicle service history",
                "Enter a registration number to see every visit, with invoices and odometer readings.")
                .col("Date", false).col("Booking", false).col("Job card", false).col("Services / work", false)
                .col("Odometer (km)", true).col("Status", false).col("Invoice", false).col("Amount", true);
        if (regQuery == null || regQuery.isBlank()) {
            return t;
        }
        String reg = regQuery.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        Vehicle v = vehicles.findByRegNo(reg).orElse(null);
        if (v == null) {
            t.tile("Vehicle", "Not found: " + reg);
            return t;
        }
        t.tile("Vehicle", v.getRegNo() + " - " + v.getDisplayName()).tile("Owner", v.getCustomer().getName())
                .tile("Odometer", v.getOdometer() + " km");
        for (Booking b : bookings.findByVehicleIdOrderByCreatedAtDesc(v.getId())) {
            JobCard j = b.getJobCard();
            String work = j == null ? b.getServiceNames() : j.getLines().stream()
                    .filter(l -> l.getApproval() == ApprovalStatus.APPROVED).map(JobLine::getDescription)
                    .collect(Collectors.joining(", "));
            if (work.isBlank()) {
                work = b.getServiceNames();
            }
            Invoice inv = j == null ? null : j.getInvoice();
            t.row(f.date(b.getPreferredAt().toLocalDate()), b.getBookingNo(), j == null ? "-" : j.getJobNo(), work,
                    j == null ? "-" : String.valueOf(j.getOdometerIn()), b.getStatus().getLabel(),
                    inv == null ? "-" : inv.getInvoiceNo(), inv == null ? "-" : f.money(inv.getTotal()));
        }
        return t;
    }

    private ReportTable mechanicPerformance(LocalDate from, LocalDate to) {
        ReportTable t = new ReportTable("mechanics", "Mechanic performance",
                "Jobs finished in the period, hours (estimated vs actual), productivity and average turnaround.")
                .col("Mechanic", false).col("Jobs done", true).col("Open jobs", true).col("Est. hours", true)
                .col("Actual hours", true).col("Productivity", true).col("Avg turnaround (h)", true)
                .col("Labour billed", true);
        LocalDateTime a = from.atStartOfDay();
        LocalDateTime b = to.plusDays(1).atStartOfDay();
        int jobs = 0;
        BigDecimal labour = Money.ZERO;
        for (MechanicStats s : mechanics.allStats(a, b)) {
            t.row(s.getMechanic().getName(), String.valueOf(s.getCompletedJobs()), String.valueOf(s.getOpenJobs()),
                    f.qty(s.getEstimatedHours()), f.qty(s.getActualHours()),
                    s.getActualHours().signum() > 0 ? s.getProductivityPct() + "%" : "-",
                    String.valueOf(s.getAvgTurnaroundHours()), f.money(s.getLabourBilled()));
            jobs += s.getCompletedJobs();
            labour = labour.add(s.getLabourBilled());
        }
        t.totals("Total", String.valueOf(jobs), "", "", "", "", "", f.money(labour));
        t.tile("Jobs completed", String.valueOf(jobs)).tile("Labour billed", f.money(labour));
        return t;
    }

    // ---------------------------------------------------------------- inventory

    private ReportTable inventory() {
        ReportTable t = new ReportTable("inventory", "Inventory report",
                "Current stock with valuation at purchase price. Low-stock items are flagged.")
                .col("Part no.", false).col("Part", false).col("Brand", false).col("Stock", true).col("Min", true)
                .col("Purchase price", true).col("Stock value", true).col("Status", false);
        BigDecimal value = Money.ZERO;
        int low = 0;
        List<Part> all = new ArrayList<>(parts.findAll());
        all.sort((x, y) -> x.getName().compareToIgnoreCase(y.getName()));
        for (Part p : all) {
            BigDecimal v = Money.round(p.getStock().multiply(p.getPurchasePrice()));
            String status = !p.isActive() ? "Inactive" : p.isOutOfStock() ? "Out of stock" : p.isLowStock() ? "Low" : "OK";
            if (p.isActive() && p.isLowStock()) {
                low++;
            }
            t.row(p.getPartNo(), p.getName(), p.getBrand() == null ? "" : p.getBrand(), f.qty(p.getStock()),
                    f.qty(p.getMinStock()), f.money(p.getPurchasePrice()), f.money(v), status);
            value = value.add(v);
        }
        t.totals("Total", "", "", "", "", "", f.money(value), "");
        t.tile("Parts in catalogue", String.valueOf(all.size())).tile("Low / out of stock", String.valueOf(low))
                .tile("Stock value (cost)", f.money(value));
        return t;
    }

    private ReportTable mostUsedParts(LocalDate from, LocalDate to) {
        ReportTable t = new ReportTable("parts", "Most-used parts",
                "Parts issued to job cards in the period, by quantity.")
                .col("Part no.", false).col("Part", false).col("Qty used", true).col("Job cards", true).col("Cost value", true);
        Map<Long, BigDecimal> qty = new HashMap<>();
        Map<Long, Set<Long>> jobs = new HashMap<>();
        Map<Long, Part> byId = new HashMap<>();
        for (StockMovement m : movements.findByTypeAndCreatedAtBetween(StockMovementType.OUT, from.atStartOfDay(),
                to.plusDays(1).atStartOfDay())) {
            if (m.getJobCard() == null) {
                continue;
            }
            Long pid = m.getPart().getId();
            byId.put(pid, m.getPart());
            qty.merge(pid, m.getQuantity(), BigDecimal::add);
            jobs.computeIfAbsent(pid, k -> new HashSet<>()).add(m.getJobCard().getId());
        }
        List<Long> ids = new ArrayList<>(qty.keySet());
        ids.sort((x, y) -> qty.get(y).compareTo(qty.get(x)));
        for (Long id : ids) {
            Part p = byId.get(id);
            t.row(p.getPartNo(), p.getName(), f.qty(qty.get(id)), String.valueOf(jobs.get(id).size()),
                    f.money(Money.round(qty.get(id).multiply(p.getPurchasePrice()))));
        }
        t.tile("Distinct parts used", String.valueOf(ids.size()));
        return t;
    }
}
