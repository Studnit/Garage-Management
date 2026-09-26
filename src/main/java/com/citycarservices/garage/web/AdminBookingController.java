package com.citycarservices.garage.web;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.form.BookingForm;
import com.citycarservices.garage.service.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin/bookings")
public class AdminBookingController extends BaseController {

    private final BookingService bookings;
    private final CustomerService customers;
    private final VehicleService vehicles;
    private final ServiceCatalogService catalog;
    private final MechanicService mechanics;
    private final PickupService pickups;
    private final JobService jobs;

    public AdminBookingController(BookingService bookings, CustomerService customers, VehicleService vehicles,
                                  ServiceCatalogService catalog, MechanicService mechanics, PickupService pickups,
                                  JobService jobs) {
        this.bookings = bookings;
        this.customers = customers;
        this.vehicles = vehicles;
        this.catalog = catalog;
        this.mechanics = mechanics;
        this.pickups = pickups;
        this.jobs = jobs;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       Model model) {
        BookingStatus st = parseEnum(BookingStatus.class, status);
        model.addAttribute("bookings", bookings.search(q, st, from, to));
        model.addAttribute("q", q);
        model.addAttribute("status", st);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("statuses", BookingStatus.values());
        return "admin/bookings/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long customerId,
                          @RequestParam(required = false) Long vehicleId,
                          @RequestParam(required = false) String q, Model model) {
        BookingForm f = new BookingForm();
        f.setCustomerId(customerId);
        f.setVehicleId(vehicleId);
        f.setPreferredAt(LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0));
        if (customerId == null) {
            model.addAttribute("customers", customers.search(q));
            model.addAttribute("q", q);
            model.addAttribute("form", f);
            model.addAttribute("customer", null);
            return "admin/bookings/pick-customer";
        }
        return form(model, f, customers.get(customerId));
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("form") BookingForm form, BindingResult result,
                         @RequestParam(defaultValue = "false") boolean confirmed, Model model, RedirectAttributes ra) {
        Customer c = customers.get(form.getCustomerId());
        if (result.hasErrors()) {
            return form(model, form, c);
        }
        try {
            Booking b = bookings.create(form, c, form.getSource() == null ? BookingSource.WALK_IN : form.getSource(), confirmed);
            ra.addFlashAttribute("success", "Booking " + b.getBookingNo() + " created.");
            return "redirect:/admin/bookings/" + b.getId();
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return form(model, form, c);
        }
    }

    private String form(Model model, BookingForm f, Customer c) {
        model.addAttribute("form", f);
        model.addAttribute("customer", c);
        model.addAttribute("vehicles", vehicles.forCustomer(c.getId()));
        model.addAttribute("services", catalog.active());
        model.addAttribute("sources", BookingSource.values());
        return "admin/bookings/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Booking b = bookings.get(id);
        model.addAttribute("booking", b);
        model.addAttribute("job", b.getJobCard());
        model.addAttribute("pickups", pickups.forBooking(id));
        model.addAttribute("nextStatus", b.getStatus().next());
        model.addAttribute("mechanics", mechanics.active());
        return "admin/bookings/detail";
    }

    @PostMapping("/{id}/status")
    public String advance(@PathVariable Long id, @RequestParam(required = false) String note,
                          @RequestParam(defaultValue = "booking") String back, RedirectAttributes ra) {
        String target = "/admin/bookings/" + id;
        if ("job".equals(back)) {
            JobCard j = jobs.forBooking(id);
            if (j != null) {
                target = "/admin/jobs/" + j.getId();
            }
        }
        return run(ra, "Status updated.", target, () -> bookings.advance(id, note));
    }

    @PostMapping("/{id}/receive")
    public String receive(@PathVariable Long id, @RequestParam Integer odometer,
                          @RequestParam(required = false) String fuelLevel,
                          @RequestParam(required = false) String notes, RedirectAttributes ra) {
        return run(ra, "Vehicle received and job card opened.", "/admin/bookings/" + id,
                () -> bookings.receiveVehicle(id, odometer, fuelLevel, notes));
    }

    @PostMapping("/{id}/qc")
    public String qc(@PathVariable Long id, @RequestParam boolean passed,
                     @RequestParam(required = false) String notes,
                     @RequestParam(defaultValue = "booking") String back, RedirectAttributes ra) {
        String target = "/admin/bookings/" + id;
        if ("job".equals(back)) {
            JobCard j = jobs.forBooking(id);
            if (j != null) {
                target = "/admin/jobs/" + j.getId();
            }
        }
        return run(ra, passed ? "Quality check passed." : "Sent back to In Service for rework.", target,
                () -> bookings.recordQc(id, passed, notes));
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        return run(ra, "Booking cancelled.", "/admin/bookings/" + id, () -> bookings.cancel(id, reason, null));
    }

    @PostMapping("/{id}/reschedule")
    public String reschedule(@PathVariable Long id,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime when,
                             RedirectAttributes ra) {
        return run(ra, "Booking rescheduled.", "/admin/bookings/" + id, () -> bookings.reschedule(id, when, null));
    }
}
