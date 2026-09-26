package com.citycarservices.garage.web;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.GarageProperties;
import com.citycarservices.garage.config.GarageUserDetails;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.form.*;
import com.citycarservices.garage.service.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The customer portal. Every lookup is restricted to the signed-in customer's own records. */
@Controller
@RequestMapping("/portal")
public class PortalController extends BaseController {

    private final CustomerService customers;
    private final VehicleService vehicles;
    private final BookingService bookings;
    private final ServiceCatalogService catalog;
    private final JobService jobs;
    private final InspectionService inspections;
    private final EstimateService estimates;
    private final InvoiceService invoices;
    private final PaymentService payments;
    private final PickupService pickups;
    private final FeedbackService feedback;
    private final InvoicePdfService pdf;
    private final GarageProperties props;

    public PortalController(CustomerService customers, VehicleService vehicles, BookingService bookings,
                            ServiceCatalogService catalog, JobService jobs, InspectionService inspections,
                            EstimateService estimates, InvoiceService invoices, PaymentService payments,
                            PickupService pickups, FeedbackService feedback, InvoicePdfService pdf,
                            GarageProperties props) {
        this.customers = customers;
        this.vehicles = vehicles;
        this.bookings = bookings;
        this.catalog = catalog;
        this.jobs = jobs;
        this.inspections = inspections;
        this.estimates = estimates;
        this.invoices = invoices;
        this.payments = payments;
        this.pickups = pickups;
        this.feedback = feedback;
        this.pdf = pdf;
        this.props = props;
    }

    private Customer me(GarageUserDetails u) {
        return customers.forUser(u.getId());
    }

    // ---------------------------------------------------------------- dashboard

    @GetMapping({"", "/"})
    public String dashboard(@AuthenticationPrincipal GarageUserDetails u, Model model) {
        Customer c = me(u);
        List<Vehicle> vs = vehicles.forCustomer(c.getId());
        List<Booking> all = bookings.forCustomer(c.getId());
        List<Estimate> pending = estimates.forCustomer(c.getId()).stream().filter(Estimate::isPending).toList();
        List<Invoice> unpaid = invoices.forCustomer(c.getId()).stream().filter(i -> !i.isPaid()).toList();
        model.addAttribute("customer", c);
        model.addAttribute("vehicles", vs);
        model.addAttribute("activeBookings", all.stream().filter(b -> b.getStatus().isOpen()).toList());
        model.addAttribute("recentBookings", all.stream().filter(b -> !b.getStatus().isOpen()).limit(5).toList());
        model.addAttribute("pendingEstimates", pending);
        model.addAttribute("unpaidInvoices", unpaid);
        model.addAttribute("alerts", alerts(vs));
        return "portal/dashboard";
    }

    private List<String[]> alerts(List<Vehicle> vs) {
        List<String[]> out = new ArrayList<>();
        LocalDate soon = LocalDate.now().plusDays(props.getReminderLeadDays());
        LocalDate today = LocalDate.now();
        Fmt2 f = new Fmt2();
        for (Vehicle v : vs) {
            if (v.getNextServiceDate() != null && !v.getNextServiceDate().isAfter(soon)) {
                out.add(new String[] {v.getNextServiceDate().isBefore(today) ? "danger" : "warn",
                    v.getRegNo() + ": service " + (v.getNextServiceDate().isBefore(today) ? "was due " : "due ")
                        + f.d(v.getNextServiceDate()), String.valueOf(v.getId())});
            }
            if (v.getNextServiceKm() != null && v.getOdometer() >= v.getNextServiceKm() - props.getReminderKmLead()) {
                out.add(new String[] {"warn", v.getRegNo() + ": service due at " + v.getNextServiceKm() + " km (now "
                    + v.getOdometer() + " km)", String.valueOf(v.getId())});
            }
            if (v.getInsuranceExpiry() != null && !v.getInsuranceExpiry().isAfter(soon)) {
                out.add(new String[] {v.getInsuranceExpiry().isBefore(today) ? "danger" : "warn",
                    v.getRegNo() + ": insurance " + (v.getInsuranceExpiry().isBefore(today) ? "expired " : "expires ")
                        + f.d(v.getInsuranceExpiry()), String.valueOf(v.getId())});
            }
            if (v.getPucExpiry() != null && !v.getPucExpiry().isAfter(soon)) {
                out.add(new String[] {v.getPucExpiry().isBefore(today) ? "danger" : "warn",
                    v.getRegNo() + ": PUC " + (v.getPucExpiry().isBefore(today) ? "expired " : "expires ")
                        + f.d(v.getPucExpiry()), String.valueOf(v.getId())});
            }
        }
        return out;
    }

    /** Tiny local date formatter to avoid depending on the Spring bean here. */
    private static final class Fmt2 {
        String d(LocalDate d) {
            return new com.citycarservices.garage.config.Fmt().date(d);
        }
    }

    // ---------------------------------------------------------------- vehicles

    @GetMapping("/vehicles")
    public String vehicleList(@AuthenticationPrincipal GarageUserDetails u, Model model) {
        model.addAttribute("vehicles", vehicles.forCustomer(me(u).getId()));
        return "portal/vehicles";
    }

    @GetMapping("/vehicles/new")
    public String newVehicle(Model model) {
        return vehicleForm(model, new VehicleForm(), null);
    }

    @PostMapping("/vehicles/new")
    public String createVehicle(@AuthenticationPrincipal GarageUserDetails u,
                                @Valid @ModelAttribute("form") VehicleForm form, BindingResult result, Model model,
                                RedirectAttributes ra) {
        if (result.hasErrors()) {
            return vehicleForm(model, form, null);
        }
        try {
            Vehicle v = vehicles.create(me(u).getId(), form);
            ra.addFlashAttribute("success", "Vehicle " + v.getRegNo() + " added.");
            return "redirect:/portal/vehicles/" + v.getId();
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return vehicleForm(model, form, null);
        }
    }

    @GetMapping("/vehicles/{id}")
    public String vehicle(@AuthenticationPrincipal GarageUserDetails u, @PathVariable Long id, Model model) {
        Vehicle v = vehicles.getOwned(id, me(u).getId());
        model.addAttribute("vehicle", v);
        model.addAttribute("history", bookings.forVehicle(id));
        return "portal/vehicle";
    }

    @GetMapping("/vehicles/{id}/edit")
    public String editVehicle(@AuthenticationPrincipal GarageUserDetails u, @PathVariable Long id, Model model) {
        Vehicle v = vehicles.getOwned(id, me(u).getId());
        return vehicleForm(model, VehicleForm.of(v), v);
    }

    @PostMapping("/vehicles/{id}/edit")
    public String updateVehicle(@AuthenticationPrincipal GarageUserDetails u, @PathVariable Long id,
                                @Valid @ModelAttribute("form") VehicleForm form, BindingResult result, Model model,
                                RedirectAttributes ra) {
        Long cid = me(u).getId();
        Vehicle v = vehicles.getOwned(id, cid);
        if (result.hasErrors()) {
            return vehicleForm(model, form, v);
        }
        try {
            vehicles.update(id, form, cid);
            ra.addFlashAttribute("success", "Vehicle details updated.");
            return "redirect:/portal/vehicles/" + id;
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return vehicleForm(model, form, v);
        }
    }

    private String vehicleForm(Model model, VehicleForm f, Vehicle v) {
        model.addAttribute("form", f);
        model.addAttribute("vehicle", v);
        model.addAttribute("isNew", v == null);
        model.addAttribute("fuelTypes", FuelType.values());
        model.addAttribute("formAction", v == null ? "/portal/vehicles/new" : "/portal/vehicles/" + v.getId() + "/edit");
        return "portal/vehicle-form";
    }

    // ---------------------------------------------------------------- bookings

    @GetMapping("/bookings")
    public String bookingList(@AuthenticationPrincipal GarageUserDetails u, Model model) {
        model.addAttribute("bookings", bookings.forCustomer(me(u).getId()));
        return "portal/bookings";
    }

    @GetMapping("/bookings/new")
    public String newBooking(@AuthenticationPrincipal GarageUserDetails u,
                             @RequestParam(required = false) Long vehicleId, Model model) {
        BookingForm f = new BookingForm();
        f.setVehicleId(vehicleId);
        f.setPreferredAt(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0));
        return bookingForm(model, f, me(u));
    }

    @PostMapping("/bookings/new")
    public String createBooking(@AuthenticationPrincipal GarageUserDetails u,
                                @Valid @ModelAttribute("form") BookingForm form, BindingResult result, Model model,
                                RedirectAttributes ra) {
        Customer c = me(u);
        if (result.hasErrors()) {
            return bookingForm(model, form, c);
        }
        try {
            Booking b = bookings.create(form, c, BookingSource.ONLINE, false);
            ra.addFlashAttribute("success", "Booking " + b.getBookingNo() + " received. We will confirm it shortly.");
            return "redirect:/portal/bookings/" + b.getId();
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return bookingForm(model, form, c);
        }
    }

    private String bookingForm(Model model, BookingForm f, Customer c) {
        model.addAttribute("form", f);
        model.addAttribute("customer", c);
        model.addAttribute("vehicles", vehicles.forCustomer(c.getId()));
        model.addAttribute("services", catalog.active());
        return "portal/booking-form";
    }

    @GetMapping("/bookings/{id}")
    public String booking(@AuthenticationPrincipal GarageUserDetails u, @PathVariable Long id, Model model) {
        Booking b = bookings.getOwned(id, me(u).getId());
        JobCard job = b.getJobCard();
        model.addAttribute("booking", b);
        model.addAttribute("job", job);
        model.addAttribute("pickups", pickups.forBooking(id));
        if (job != null) {
            Inspection i = inspections.find(job.getId());
            model.addAttribute("inspection", i != null && i.isSubmitted() ? i : null);
            model.addAttribute("estimates", estimates.forJob(job.getId()));
            model.addAttribute("invoice", invoices.forJob(job.getId()));
        }
        Feedback fb = feedback.forBooking(id);
        model.addAttribute("review", fb);
        model.addAttribute("canReview", fb == null
                && (b.getStatus() == BookingStatus.DELIVERED || b.getStatus() == BookingStatus.COMPLETED));
        model.addAttribute("canCancel", b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.CONFIRMED);
        model.addAttribute("feedbackForm", new FeedbackForm());
        return "portal/booking";
    }

    @PostMapping("/bookings/{id}/cancel")
    public String cancelBooking(@AuthenticationPrincipal GarageUserDetails u, @PathVariable Long id,
                                @RequestParam(required = false) String reason, RedirectAttributes ra) {
        Long cid = me(u).getId();
        return run(ra, "Your booking has been cancelled.", "/portal/bookings/" + id, () -> bookings.cancel(id, reason, cid));
    }

    @PostMapping("/bookings/{id}/reschedule")
    public String rescheduleBooking(@AuthenticationPrincipal GarageUserDetails u, @PathVariable Long id,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime when,
                                    RedirectAttributes ra) {
        Long cid = me(u).getId();
        return run(ra, "Your booking has been rescheduled.", "/portal/bookings/" + id, () -> bookings.reschedule(id, when, cid));
    }

    @PostMapping("/bookings/{id}/feedback")
    public String review(@AuthenticationPrincipal GarageUserDetails u, @PathVariable Long id,
                         @Valid @ModelAttribute("feedbackForm") FeedbackForm form, BindingResult result,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", result.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/portal/bookings/" + id;
        }
        Long cid = me(u).getId();
        return run(ra, "Thank you for your feedback!", "/portal/bookings/" + id, () -> feedback.submit(cid, id, form));
    }

    // ---------------------------------------------------------------- inspection, estimate

    @GetMapping("/inspections/{jobId}")
    public String inspection(@AuthenticationPrincipal GarageUserDetails u, @PathVariable Long jobId, Model model) {
        JobCard job = jobs.getOwned(jobId, me(u).getId());
        Inspection i = inspections.find(jobId);
        if (i == null || !i.isSubmitted()) {
            throw new NotFoundException("Inspection report not available yet");
        }
        model.addAttribute("job", job);
        model.addAttribute("inspection", i);
        return "portal/inspection";
    }

    @GetMapping("/estimates/{id}")
    public String estimate(@AuthenticationPrincipal GarageUserDetails u, @PathVariable Long id, Model model) {
        Estimate e = estimates.getOwned(id, me(u).getId());
        model.addAttribute("estimate", e);
        model.addAttribute("job", e.getJobCard());
        model.addAttribute("history", estimates.history(e.getJobCard().getId()));
        return "portal/estimate";
    }

    @PostMapping("/estimates/{id}/decide")
    public String decide(@AuthenticationPrincipal GarageUserDetails u, @PathVariable Long id,
                         @RequestParam(required = false) String action, @RequestParam(required = false) String note,
                         @RequestParam Map<String, String> params, RedirectAttributes ra) {
        Customer c = me(u);
        return run(ra, "Thank you - your decision has been recorded.", "/portal/estimates/" + id, () -> {
            Estimate e = estimates.getOwned(id, c.getId());
            Map<Long, ApprovalStatus> decisions = new HashMap<>();
            if ("approveAll".equals(action)) {
                decisions = estimates.allAwaiting(e, ApprovalStatus.APPROVED);
            } else if ("rejectAll".equals(action)) {
                decisions = estimates.allAwaiting(e, ApprovalStatus.REJECTED);
            } else {
                for (Map.Entry<String, String> p : params.entrySet()) {
                    if (p.getKey().startsWith("decision-")) {
                        try {
                            Long lineId = Long.valueOf(p.getKey().substring(9));
                            ApprovalStatus s = parseEnum(ApprovalStatus.class, p.getValue());
                            if (s != null) {
                                decisions.put(lineId, s);
                            }
                        } catch (NumberFormatException ignored) {
                            // skip malformed parameter
                        }
                    }
                }
            }
            estimates.decide(id, decisions, ApprovalChannel.PORTAL, c.getName(), note, c.getId());
        });
    }

    // ---------------------------------------------------------------- invoices & payments

    @GetMapping("/invoices")
    public String invoiceList(@AuthenticationPrincipal GarageUserDetails u, Model model) {
        model.addAttribute("invoices", invoices.forCustomer(me(u).getId()));
        return "portal/invoices";
    }

    @GetMapping("/invoices/{id}")
    public String invoice(@AuthenticationPrincipal GarageUserDetails u, @PathVariable Long id, Model model) {
        Invoice inv = invoices.getOwned(id, me(u).getId());
        model.addAttribute("invoice", inv);
        model.addAttribute("payments", payments.forJob(inv.getJobCard().getId()));
        model.addAttribute("modes", List.of(PaymentMode.UPI, PaymentMode.BANK_TRANSFER, PaymentMode.CARD));
        if (!props.getUpiId().isBlank() && !inv.isPaid()) {
            model.addAttribute("upiLink", "upi://pay?pa=" + enc(props.getUpiId()) + "&pn=" + enc(props.getName())
                    + "&am=" + inv.getBalance().toPlainString() + "&cu=INR&tn=" + enc(inv.getInvoiceNo()));
        }
        return "portal/invoice";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @GetMapping("/invoices/{id}/print")
    public String invoicePrint(@AuthenticationPrincipal GarageUserDetails u, @PathVariable Long id, Model model) {
        Invoice inv = invoices.getOwned(id, me(u).getId());
        model.addAttribute("invoice", inv);
        model.addAttribute("payments", payments.forJob(inv.getJobCard().getId()));
        return "print/invoice";
    }

    @GetMapping("/invoices/{id}/pdf")
    public ResponseEntity<byte[]> invoicePdf(@AuthenticationPrincipal GarageUserDetails u, @PathVariable Long id) {
        Invoice inv = invoices.getOwned(id, me(u).getId());
        return AdminBillingController.pdfResponse(inv, pdf);
    }

    @PostMapping("/invoices/{id}/pay")
    public String pay(@AuthenticationPrincipal GarageUserDetails u, @PathVariable Long id,
                      @RequestParam BigDecimal amount, @RequestParam PaymentMode mode,
                      @RequestParam(required = false) String reference, RedirectAttributes ra) {
        Long cid = me(u).getId();
        return run(ra, "Payment details submitted. It will be confirmed once we verify it.", "/portal/invoices/" + id,
                () -> payments.submitOnline(id, cid, amount, mode, reference));
    }

    // ---------------------------------------------------------------- history & profile

    @GetMapping("/history")
    public String history(@AuthenticationPrincipal GarageUserDetails u, Model model) {
        Customer c = me(u);
        model.addAttribute("bookings", bookings.forCustomer(c.getId()).stream()
                .filter(b -> b.getStatus().getStep() >= BookingStatus.DELIVERED.getStep()).toList());
        model.addAttribute("vehicles", vehicles.forCustomer(c.getId()));
        return "portal/history";
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal GarageUserDetails u, Model model) {
        Customer c = me(u);
        model.addAttribute("customer", c);
        model.addAttribute("form", CustomerForm.of(c));
        return "portal/profile";
    }

    @PostMapping("/profile")
    public String saveProfile(@AuthenticationPrincipal GarageUserDetails u,
                              @Valid @ModelAttribute("form") CustomerForm form, BindingResult result, Model model,
                              RedirectAttributes ra) {
        Customer c = me(u);
        model.addAttribute("customer", c);
        // mobile and login email are not editable here (they identify the account)
        form.setMobile(c.getMobile());
        form.setEmail(c.getEmail());
        if (result.hasErrors()) {
            return "portal/profile";
        }
        try {
            customers.update(c.getId(), form);
            ra.addFlashAttribute("success", "Profile updated.");
            return "redirect:/portal/profile";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "portal/profile";
        }
    }
}
