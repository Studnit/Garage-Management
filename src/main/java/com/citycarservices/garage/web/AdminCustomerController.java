package com.citycarservices.garage.web;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.domain.Customer;
import com.citycarservices.garage.form.CustomerForm;
import com.citycarservices.garage.service.BookingService;
import com.citycarservices.garage.service.CustomerService;
import com.citycarservices.garage.service.FeedbackService;
import com.citycarservices.garage.service.InvoiceService;
import com.citycarservices.garage.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/customers")
public class AdminCustomerController extends BaseController {

    private final CustomerService customers;
    private final VehicleService vehicles;
    private final BookingService bookings;
    private final InvoiceService invoices;
    private final FeedbackService feedback;

    public AdminCustomerController(CustomerService customers, VehicleService vehicles, BookingService bookings,
                                   InvoiceService invoices, FeedbackService feedback) {
        this.customers = customers;
        this.vehicles = vehicles;
        this.bookings = bookings;
        this.invoices = invoices;
        this.feedback = feedback;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("customers", customers.search(q));
        model.addAttribute("q", q);
        return "admin/customers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new CustomerForm());
        model.addAttribute("isNew", true);
        return "admin/customers/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("form") CustomerForm form, BindingResult result, Model model,
                         RedirectAttributes ra) {
        model.addAttribute("isNew", true);
        if (result.hasErrors()) {
            return "admin/customers/form";
        }
        try {
            Customer c = customers.create(form);
            ra.addFlashAttribute("success", "Customer " + c.getName() + " added. You can now add their vehicle.");
            return "redirect:/admin/customers/" + c.getId();
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/customers/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Customer c = customers.get(id);
        model.addAttribute("customer", c);
        model.addAttribute("vehicles", vehicles.forCustomer(id));
        model.addAttribute("bookings", bookings.forCustomer(id));
        model.addAttribute("invoices", invoices.forCustomer(id));
        model.addAttribute("reviews", feedback.forCustomer(id));
        return "admin/customers/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Customer c = customers.get(id);
        model.addAttribute("form", CustomerForm.of(c));
        model.addAttribute("customer", c);
        model.addAttribute("isNew", false);
        return "admin/customers/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") CustomerForm form,
                         BindingResult result, Model model, RedirectAttributes ra) {
        model.addAttribute("customer", customers.get(id));
        model.addAttribute("isNew", false);
        if (result.hasErrors()) {
            return "admin/customers/form";
        }
        try {
            customers.update(id, form);
            ra.addFlashAttribute("success", "Customer details updated.");
            return "redirect:/admin/customers/" + id;
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/customers/form";
        }
    }

    @PostMapping("/{id}/portal")
    public String enablePortal(@PathVariable Long id, RedirectAttributes ra) {
        try {
            String temp = customers.enablePortal(id);
            Customer c = customers.get(id);
            ra.addFlashAttribute("success", "Portal login created for " + c.getName() + ".");
            ra.addFlashAttribute("tempPassword", temp);
            ra.addFlashAttribute("tempFor", c.getEmail());
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/customers/" + id;
    }
}
