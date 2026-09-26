package com.citycarservices.garage.config;

import com.citycarservices.garage.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Forces users with a temporary password to choose their own before using the application. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UserRepository users;

    public WebConfig(UserRepository users) {
        this.users = users;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof GarageUserDetails) {
                    GarageUserDetails principal = (GarageUserDetails) auth.getPrincipal();
                    boolean must = users.findById(principal.getId())
                            .map(u -> u.isMustChangePassword()).orElse(false);
                    if (must) {
                        response.sendRedirect(request.getContextPath() + "/account/password");
                        return false;
                    }
                }
                return true;
            }
        }).addPathPatterns("/admin/**", "/portal/**");
    }
}
