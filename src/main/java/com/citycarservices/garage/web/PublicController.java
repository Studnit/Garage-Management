package com.citycarservices.garage.web;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.GarageUserDetails;
import com.citycarservices.garage.domain.ServiceCategory;
import com.citycarservices.garage.domain.ServiceItem;
import com.citycarservices.garage.form.RegisterForm;
import com.citycarservices.garage.service.CustomerService;
import com.citycarservices.garage.service.FeedbackService;
import com.citycarservices.garage.service.ServiceCatalogService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PublicController extends BaseController {

    private final ServiceCatalogService catalog;
    private final FeedbackService feedback;
    private final CustomerService customers;

    public PublicController(ServiceCatalogService catalog, FeedbackService feedback, CustomerService customers) {
        this.catalog = catalog;
        this.feedback = feedback;
        this.customers = customers;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("services", catalog.active());
        model.addAttribute("reviews", feedback.published(6));
        model.addAttribute("avgRating", feedback.average());
        model.addAttribute("reviewCount", feedback.count());
        return "public/home";
    }

    @GetMapping("/services")
    public String services(Model model) {
        Map<ServiceCategory, List<ServiceItem>> grouped = new LinkedHashMap<>();
        for (ServiceCategory c : ServiceCategory.values()) {
            grouped.put(c, new ArrayList<>());
        }
        for (ServiceItem s : catalog.active()) {
            grouped.get(s.getCategory()).add(s);
        }
        grouped.values().removeIf(List::isEmpty);
        model.addAttribute("grouped", grouped);
        return "public/services";
    }

    @GetMapping("/contact")
    public String contact() {
        return "public/contact";
    }

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal GarageUserDetails user) {
        if (user != null) {
            return "redirect:" + (user.isStaff() ? "/admin" : "/portal");
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(@AuthenticationPrincipal GarageUserDetails user, Model model) {
        if (user != null) {
            return "redirect:" + (user.isStaff() ? "/admin" : "/portal");
        }
        model.addAttribute("form", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @org.springframework.web.bind.annotation.ModelAttribute("form") RegisterForm form,
                           BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "register";
        }
        try {
            customers.register(form);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
        ra.addFlashAttribute("success", "Your account is ready. Please sign in.");
        return "redirect:/login";
    }
}
