package com.citycarservices.garage.service;

import com.citycarservices.garage.config.GarageUserDetails;
import com.citycarservices.garage.domain.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the authenticated user from service code. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static GarageUserDetails get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof GarageUserDetails g) {
            return g;
        }
        return null;
    }

    public static String name() {
        GarageUserDetails g = get();
        return g == null ? "System" : g.getFullName();
    }

    public static Role role() {
        GarageUserDetails g = get();
        return g == null ? null : g.getRole();
    }
}
