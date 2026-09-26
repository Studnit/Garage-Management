package com.citycarservices.garage.config;

import com.citycarservices.garage.domain.BookingStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;
import java.util.List;

/** Attributes available to every page: business info, current user, status flow. */
@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAdvice {

    private final GarageProperties garage;

    public GlobalModelAdvice(GarageProperties garage) {
        this.garage = garage;
    }

    /** Trim all text input and turn blank strings into null. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @ModelAttribute("garage")
    public GarageProperties garage() {
        return garage;
    }

    @ModelAttribute("flow")
    public List<BookingStatus> flow() {
        return BookingStatus.FLOW;
    }

    @ModelAttribute("today")
    public LocalDate today() {
        return LocalDate.now();
    }

    @ModelAttribute("me")
    public GarageUserDetails me(@AuthenticationPrincipal GarageUserDetails user) {
        return user;
    }
}
