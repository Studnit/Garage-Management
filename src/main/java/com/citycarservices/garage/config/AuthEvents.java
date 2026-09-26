package com.citycarservices.garage.config;

import com.citycarservices.garage.service.UserService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/** Brute-force protection: lock an account for 15 minutes after 5 failed logins. */
@Component
public class AuthEvents {

    private final UserService users;

    public AuthEvents(UserService users) {
        this.users = users;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        users.recordLoginFailure(event.getAuthentication().getName());
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        users.recordLoginSuccess(event.getAuthentication().getName());
    }
}
