package com.citycarservices.garage.web;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.GarageUserDetails;
import com.citycarservices.garage.form.PasswordForm;
import com.citycarservices.garage.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/account")
public class AccountController extends BaseController {

    private final UserService users;

    public AccountController(UserService users) {
        this.users = users;
    }

    @GetMapping("/password")
    public String form(@AuthenticationPrincipal GarageUserDetails me, Model model) {
        model.addAttribute("form", new PasswordForm());
        model.addAttribute("forced", users.get(me.getId()).isMustChangePassword());
        return "account/password";
    }

    @PostMapping("/password")
    public String change(@AuthenticationPrincipal GarageUserDetails me,
                         @Valid @ModelAttribute("form") PasswordForm form, BindingResult result, Model model,
                         RedirectAttributes ra, HttpServletRequest request) {
        model.addAttribute("forced", users.get(me.getId()).isMustChangePassword());
        if (result.hasErrors()) {
            return "account/password";
        }
        try {
            users.changePassword(me.getId(), form.getCurrent(), form.getPassword(), form.getConfirm());
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "account/password";
        }
        ra.addFlashAttribute("success", "Your password has been changed.");
        return "redirect:" + (me.isStaff() ? "/admin" : "/portal");
    }
}
