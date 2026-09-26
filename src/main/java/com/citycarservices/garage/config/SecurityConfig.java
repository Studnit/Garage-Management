package com.citycarservices.garage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] STAFF = {"SUPER_ADMIN", "GARAGE_MANAGER", "SERVICE_ADVISOR", "MECHANIC",
            "ACCOUNTANT", "INVENTORY_MANAGER"};

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(11);
    }

    @Bean
    public AuthenticationSuccessHandler successHandler() {
        return (request, response, authentication) -> {
            boolean customer = false;
            for (GrantedAuthority a : authentication.getAuthorities()) {
                if ("ROLE_CUSTOMER".equals(a.getAuthority())) {
                    customer = true;
                }
            }
            response.sendRedirect(request.getContextPath() + (customer ? "/portal" : "/admin"));
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationSuccessHandler successHandler)
            throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/img/**", "/favicon.ico", "/error", "/error/**",
                        "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/", "/services", "/contact", "/login", "/register").permitAll()

                .requestMatchers("/account/**", "/media/**").authenticated()
                .requestMatchers("/portal/**").hasRole("CUSTOMER")

                .requestMatchers("/admin/users/**", "/admin/audit/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/admin/services/**")
                    .hasAnyRole("SUPER_ADMIN", "GARAGE_MANAGER")
                .requestMatchers("/admin/mechanics/**")
                    .hasAnyRole("SUPER_ADMIN", "GARAGE_MANAGER", "SERVICE_ADVISOR")

                .requestMatchers(HttpMethod.POST, "/admin/bookings/*/status").hasAnyRole(STAFF)
                .requestMatchers("/admin/bookings/**", "/admin/customers/**", "/admin/pickups/**",
                        "/admin/reminders/**", "/admin/feedback/**")
                    .hasAnyRole("SUPER_ADMIN", "GARAGE_MANAGER", "SERVICE_ADVISOR")
                .requestMatchers(HttpMethod.GET, "/admin/vehicles/**")
                    .hasAnyRole("SUPER_ADMIN", "GARAGE_MANAGER", "SERVICE_ADVISOR", "MECHANIC", "ACCOUNTANT")
                .requestMatchers("/admin/vehicles/**")
                    .hasAnyRole("SUPER_ADMIN", "GARAGE_MANAGER", "SERVICE_ADVISOR")

                .requestMatchers("/admin/jobs/**")
                    .hasAnyRole("SUPER_ADMIN", "GARAGE_MANAGER", "SERVICE_ADVISOR", "MECHANIC", "ACCOUNTANT")

                .requestMatchers(HttpMethod.GET, "/admin/inventory/**", "/admin/suppliers/**")
                    .hasAnyRole("SUPER_ADMIN", "GARAGE_MANAGER", "SERVICE_ADVISOR", "MECHANIC", "INVENTORY_MANAGER")
                .requestMatchers("/admin/inventory/**", "/admin/suppliers/**")
                    .hasAnyRole("SUPER_ADMIN", "GARAGE_MANAGER", "INVENTORY_MANAGER")

                .requestMatchers("/admin/invoices/**", "/admin/payments/**")
                    .hasAnyRole("SUPER_ADMIN", "GARAGE_MANAGER", "SERVICE_ADVISOR", "ACCOUNTANT")

                .requestMatchers("/admin/reports/inventory", "/admin/reports/parts")
                    .hasAnyRole("SUPER_ADMIN", "GARAGE_MANAGER", "ACCOUNTANT", "INVENTORY_MANAGER")
                .requestMatchers("/admin/reports/**")
                    .hasAnyRole("SUPER_ADMIN", "GARAGE_MANAGER", "ACCOUNTANT")

                .requestMatchers("/admin", "/admin/").hasAnyRole(STAFF)
                .requestMatchers("/admin/**").denyAll()
                .anyRequest().authenticated())
            .formLogin(f -> f
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(successHandler)
                .failureUrl("/login?error")
                .permitAll())
            .logout(l -> l
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll())
            .sessionManagement(s -> s.sessionFixation(f -> f.migrateSession()))
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; img-src 'self' data: blob:; media-src 'self'; "
                    + "style-src 'self' 'unsafe-inline'; script-src 'self'; frame-ancestors 'none'; "
                    + "form-action 'self'; base-uri 'self'"))
                .referrerPolicy(r -> r.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                .frameOptions(fo -> fo.deny()));
        return http.build();
    }
}
