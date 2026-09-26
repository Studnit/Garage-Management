package com.citycarservices.garage.form;

import com.citycarservices.garage.domain.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class UserForm {
    @NotBlank(message = "Name is required")
    @Size(max = 120)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email
    @Size(max = 160)
    private String email;

    @Size(max = 20)
    private String phone;

    @NotNull(message = "Select a role")
    private Role role = Role.SERVICE_ADVISOR;

    /** Only used when creating. */
    @Size(max = 72)
    private String password;

    private boolean enabled = true;

    public static UserForm of(AppUser u) {
        UserForm f = new UserForm();
        f.fullName = u.getFullName();
        f.email = u.getEmail();
        f.phone = u.getPhone();
        f.role = u.getRole();
        f.enabled = u.isEnabled();
        return f;
    }
}
