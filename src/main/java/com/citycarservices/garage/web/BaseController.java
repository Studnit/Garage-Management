package com.citycarservices.garage.web;

import com.citycarservices.garage.config.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/** Shared helpers for controllers. */
public abstract class BaseController {

    /**
     * Runs a state-changing action; business-rule violations become a flash "error" toast instead of an error page.
     */
    protected String run(RedirectAttributes ra, String success, String redirect, Runnable action) {
        try {
            action.run();
            if (success != null) {
                ra.addFlashAttribute("success", success);
            }
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "That change conflicts with existing data (duplicate or in use).");
        }
        return "redirect:" + redirect;
    }

    protected static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected static LocalDate monthStart() {
        return LocalDate.now().withDayOfMonth(1);
    }
}
