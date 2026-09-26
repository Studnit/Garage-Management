package com.citycarservices.garage.web;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.domain.Mechanic;
import com.citycarservices.garage.form.MechanicForm;
import com.citycarservices.garage.service.JobService;
import com.citycarservices.garage.service.MechanicService;
import jakarta.validation.Valid;
import com.citycarservices.garage.config.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/mechanics")
public class AdminMechanicController extends BaseController {

    private final MechanicService mechanics;
    private final JobService jobs;

    public AdminMechanicController(MechanicService mechanics, JobService jobs) {
        this.mechanics = mechanics;
        this.jobs = jobs;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("stats", mechanics.allStats(monthStart().atStartOfDay(), java.time.LocalDateTime.now()));
        return "admin/mechanics/list";
    }

    @GetMapping("/new")
    @PreAuthorize(Roles.MANAGEMENT)
    public String newForm(Model model) {
        model.addAttribute("form", new MechanicForm());
        model.addAttribute("isNew", true);
        return "admin/mechanics/form";
    }

    @PostMapping("/new")
    @PreAuthorize(Roles.MANAGEMENT)
    public String create(@Valid @ModelAttribute("form") MechanicForm form, BindingResult result, Model model,
                         RedirectAttributes ra) {
        model.addAttribute("isNew", true);
        if (result.hasErrors()) {
            return "admin/mechanics/form";
        }
        try {
            String temp = mechanics.create(form);
            ra.addFlashAttribute("success", "Mechanic " + form.getFullName() + " added.");
            if (temp != null) {
                ra.addFlashAttribute("tempPassword", temp);
                ra.addFlashAttribute("tempFor", form.getEmail());
            }
            return "redirect:/admin/mechanics";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/mechanics/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Mechanic m = mechanics.get(id);
        model.addAttribute("mechanic", m);
        model.addAttribute("stats", mechanics.stats(m, null, null));
        model.addAttribute("monthStats", mechanics.stats(m, monthStart().atStartOfDay(), java.time.LocalDateTime.now()));
        model.addAttribute("jobs", jobs.forMechanic(id));
        return "admin/mechanics/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize(Roles.MANAGEMENT)
    public String editForm(@PathVariable Long id, Model model) {
        Mechanic m = mechanics.get(id);
        model.addAttribute("form", MechanicForm.of(m));
        model.addAttribute("mechanic", m);
        model.addAttribute("isNew", false);
        return "admin/mechanics/form";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize(Roles.MANAGEMENT)
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") MechanicForm form,
                         BindingResult result, Model model, RedirectAttributes ra) {
        model.addAttribute("mechanic", mechanics.get(id));
        model.addAttribute("isNew", false);
        if (result.hasErrors()) {
            return "admin/mechanics/form";
        }
        try {
            mechanics.update(id, form);
            ra.addFlashAttribute("success", "Mechanic profile updated.");
            return "redirect:/admin/mechanics/" + id;
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/mechanics/form";
        }
    }
}
