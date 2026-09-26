package com.citycarservices.garage.web;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.domain.Customer;
import com.citycarservices.garage.domain.FuelType;
import com.citycarservices.garage.domain.Vehicle;
import com.citycarservices.garage.form.VehicleForm;
import com.citycarservices.garage.service.BookingService;
import com.citycarservices.garage.service.CustomerService;
import com.citycarservices.garage.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/vehicles")
public class AdminVehicleController extends BaseController {

    private final VehicleService vehicles;
    private final CustomerService customers;
    private final BookingService bookings;

    public AdminVehicleController(VehicleService vehicles, CustomerService customers, BookingService bookings) {
        this.vehicles = vehicles;
        this.customers = customers;
        this.bookings = bookings;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("vehicles", vehicles.search(q));
        model.addAttribute("q", q);
        return "admin/vehicles/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam Long customerId, Model model) {
        Customer c = customers.get(customerId);
        VehicleForm f = new VehicleForm();
        f.setCustomerId(customerId);
        return form(model, f, c, null);
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("form") VehicleForm form, BindingResult result, Model model,
                         RedirectAttributes ra) {
        Customer c = customers.get(form.getCustomerId());
        if (result.hasErrors()) {
            return form(model, form, c, null);
        }
        try {
            Vehicle v = vehicles.create(c.getId(), form);
            ra.addFlashAttribute("success", "Vehicle " + v.getRegNo() + " added.");
            return "redirect:/admin/vehicles/" + v.getId();
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return form(model, form, c, null);
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Vehicle v = vehicles.get(id);
        model.addAttribute("vehicle", v);
        model.addAttribute("history", bookings.forVehicle(id));
        return "admin/vehicles/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Vehicle v = vehicles.get(id);
        return form(model, VehicleForm.of(v), v.getCustomer(), v);
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") VehicleForm form,
                         BindingResult result, Model model, RedirectAttributes ra) {
        Vehicle v = vehicles.get(id);
        if (result.hasErrors()) {
            return form(model, form, v.getCustomer(), v);
        }
        try {
            vehicles.update(id, form, null);
            ra.addFlashAttribute("success", "Vehicle details updated.");
            return "redirect:/admin/vehicles/" + id;
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return form(model, form, v.getCustomer(), v);
        }
    }

    private String form(Model model, VehicleForm f, Customer c, Vehicle v) {
        model.addAttribute("form", f);
        model.addAttribute("customer", c);
        model.addAttribute("vehicle", v);
        model.addAttribute("isNew", v == null);
        model.addAttribute("fuelTypes", FuelType.values());
        model.addAttribute("formAction", v == null ? "/admin/vehicles/new" : "/admin/vehicles/" + v.getId() + "/edit");
        model.addAttribute("cancelUrl", v == null ? "/admin/customers/" + c.getId() : "/admin/vehicles/" + v.getId());
        return "admin/vehicles/form";
    }
}
