package com.citycarservices.garage.web;

import com.citycarservices.garage.form.DriverForm;
import com.citycarservices.garage.service.PickupService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin/pickups")
public class AdminPickupController extends BaseController {

    private final PickupService pickups;

    public AdminPickupController(PickupService pickups) {
        this.pickups = pickups;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("open", pickups.open());
        model.addAttribute("closed", pickups.recentClosed());
        model.addAttribute("drivers", pickups.drivers());
        model.addAttribute("activeDrivers", pickups.activeDrivers());
        model.addAttribute("driverForm", new DriverForm());
        return "admin/pickups";
    }

    @PostMapping("/{id}/assign")
    public String assign(@PathVariable Long id, @RequestParam Long driverId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledAt,
                         @RequestParam(required = false) String notes,
                         @RequestParam(required = false) String locationLink, RedirectAttributes ra) {
        return run(ra, "Driver assigned.", "/admin/pickups", () -> pickups.assign(id, driverId, scheduledAt, notes, locationLink));
    }

    @PostMapping("/{id}/advance")
    public String advance(@PathVariable Long id, @RequestParam(required = false) String confirmation, RedirectAttributes ra) {
        return run(ra, "Status updated.", "/admin/pickups", () -> pickups.advance(id, confirmation));
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes ra) {
        return run(ra, "Request cancelled.", "/admin/pickups", () -> pickups.cancel(id));
    }

    @PostMapping("/drivers")
    public String addDriver(@Valid @ModelAttribute("driverForm") DriverForm form, BindingResult result,
                            RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", result.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/admin/pickups";
        }
        return run(ra, "Driver added.", "/admin/pickups", () -> pickups.saveDriver(form));
    }

    @PostMapping("/drivers/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        return run(ra, "Driver updated.", "/admin/pickups", () -> pickups.toggleDriver(id));
    }
}
