package com.citycarservices.garage.web;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.GarageUserDetails;
import com.citycarservices.garage.domain.AppUser;
import com.citycarservices.garage.domain.Role;
import com.citycarservices.garage.domain.ServiceCategory;
import com.citycarservices.garage.domain.ServiceItem;
import com.citycarservices.garage.form.ServiceItemForm;
import com.citycarservices.garage.form.UserForm;
import com.citycarservices.garage.service.AuditService;
import com.citycarservices.garage.service.FeedbackService;
import com.citycarservices.garage.service.ServiceCatalogService;
import com.citycarservices.garage.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Service catalogue, users, audit trail and customer reviews. */
@Controller
public class AdminSettingsController extends BaseController {

    private final ServiceCatalogService catalog;
    private final UserService users;
    private final AuditService audit;
    private final FeedbackService feedback;

    public AdminSettingsController(ServiceCatalogService catalog, UserService users, AuditService audit,
                                   FeedbackService feedback) {
        this.catalog = catalog;
        this.users = users;
        this.audit = audit;
        this.feedback = feedback;
    }

    // ---------------- service catalogue ----------------

    @GetMapping("/admin/services")
    public String services(Model model) {
        model.addAttribute("services", catalog.all());
        return "admin/services/list";
    }

    @GetMapping("/admin/services/new")
    public String newService(Model model) {
        return serviceForm(model, new ServiceItemForm(), null);
    }

    @PostMapping("/admin/services/new")
    public String createService(@Valid @ModelAttribute("form") ServiceItemForm form, BindingResult result,
                                Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            return serviceForm(model, form, null);
        }
        try {
            catalog.save(null, form);
            ra.addFlashAttribute("success", "Service added.");
            return "redirect:/admin/services";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return serviceForm(model, form, null);
        }
    }

    @GetMapping("/admin/services/{id}/edit")
    public String editService(@PathVariable Long id, Model model) {
        ServiceItem s = catalog.get(id);
        return serviceForm(model, ServiceItemForm.of(s), s);
    }

    @PostMapping("/admin/services/{id}/edit")
    public String updateService(@PathVariable Long id, @Valid @ModelAttribute("form") ServiceItemForm form,
                                BindingResult result, Model model, RedirectAttributes ra) {
        ServiceItem s = catalog.get(id);
        if (result.hasErrors()) {
            return serviceForm(model, form, s);
        }
        try {
            catalog.save(id, form);
            ra.addFlashAttribute("success", "Service updated.");
            return "redirect:/admin/services";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return serviceForm(model, form, s);
        }
    }

    private String serviceForm(Model model, ServiceItemForm f, ServiceItem s) {
        model.addAttribute("form", f);
        model.addAttribute("service", s);
        model.addAttribute("isNew", s == null);
        model.addAttribute("categories", ServiceCategory.values());
        model.addAttribute("formAction", s == null ? "/admin/services/new" : "/admin/services/" + s.getId() + "/edit");
        return "admin/services/form";
    }

    // ---------------- users ----------------

    @GetMapping("/admin/users")
    public String userList(Model model) {
        model.addAttribute("users", users.all());
        return "admin/users/list";
    }

    @GetMapping("/admin/users/new")
    public String newUser(Model model) {
        return userForm(model, new UserForm(), null);
    }

    @PostMapping("/admin/users/new")
    public String createUser(@Valid @ModelAttribute("form") UserForm form, BindingResult result, Model model,
                             RedirectAttributes ra) {
        if (result.hasErrors()) {
            return userForm(model, form, null);
        }
        if (form.getRole() == Role.CUSTOMER) {
            model.addAttribute("error", "Customer logins are created from the customer record or by self-registration.");
            return userForm(model, form, null);
        }
        try {
            boolean generated = form.getPassword() == null || form.getPassword().isBlank();
            String pw = generated ? users.generatePassword() : form.getPassword();
            users.create(form.getFullName(), form.getEmail(), form.getPhone(), form.getRole(), pw, generated);
            ra.addFlashAttribute("success", "User " + form.getFullName() + " created.");
            if (generated) {
                ra.addFlashAttribute("tempPassword", pw);
                ra.addFlashAttribute("tempFor", form.getEmail());
            }
            return "redirect:/admin/users";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return userForm(model, form, null);
        }
    }

    @GetMapping("/admin/users/{id}/edit")
    public String editUser(@PathVariable Long id, Model model) {
        AppUser u = users.get(id);
        return userForm(model, UserForm.of(u), u);
    }

    @PostMapping("/admin/users/{id}/edit")
    public String updateUser(@PathVariable Long id, @Valid @ModelAttribute("form") UserForm form,
                             BindingResult result, Model model, RedirectAttributes ra) {
        AppUser u = users.get(id);
        if (result.hasErrors()) {
            return userForm(model, form, u);
        }
        try {
            users.update(id, form);
            ra.addFlashAttribute("success", "User updated.");
            return "redirect:/admin/users";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return userForm(model, form, u);
        }
    }

    @PostMapping("/admin/users/{id}/reset")
    public String resetPassword(@PathVariable Long id, RedirectAttributes ra) {
        try {
            String temp = users.resetPassword(id);
            ra.addFlashAttribute("success", "Password reset. The user must change it at next sign in.");
            ra.addFlashAttribute("tempPassword", temp);
            ra.addFlashAttribute("tempFor", users.get(id).getEmail());
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/{id}/unlock")
    public String unlock(@PathVariable Long id, RedirectAttributes ra) {
        return run(ra, "Account unlocked.", "/admin/users", () -> users.unlock(id));
    }

    private String userForm(Model model, UserForm f, AppUser u) {
        model.addAttribute("form", f);
        model.addAttribute("user", u);
        model.addAttribute("isNew", u == null);
        model.addAttribute("roles", Role.values());
        model.addAttribute("formAction", u == null ? "/admin/users/new" : "/admin/users/" + u.getId() + "/edit");
        return "admin/users/form";
    }

    // ---------------- audit ----------------

    @GetMapping("/admin/audit")
    public String auditLog(Model model) {
        model.addAttribute("entries", audit.recent(300));
        return "admin/audit";
    }

    // ---------------- feedback ----------------

    @GetMapping("/admin/feedback")
    public String feedbackList(Model model) {
        model.addAttribute("reviews", feedback.all());
        model.addAttribute("avg", feedback.average());
        return "admin/feedback";
    }

    @PostMapping("/admin/feedback/{id}/reply")
    public String reply(@PathVariable Long id, @RequestParam(required = false) String reply, RedirectAttributes ra) {
        return run(ra, "Reply saved.", "/admin/feedback", () -> feedback.reply(id, reply));
    }

    @PostMapping("/admin/feedback/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        return run(ra, "Visibility updated.", "/admin/feedback", () -> feedback.togglePublished(id));
    }
}
