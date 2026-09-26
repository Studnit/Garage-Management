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
public class MechanicForm {
    @NotBlank(message = "Name is required")
    @Size(max = 120)
    private String fullName;

    @NotBlank(message = "Email (login) is required")
    @Email
    @Size(max = 160)
    private String email;

    @Size(max = 20)
    private String phone;

    /** Only used when creating. */
    @Size(max = 72)
    private String password;

    @Size(max = 20)
    private String employeeCode;

    @Size(max = 400)
    private String skills;

    @Size(max = 100)
    private String specialization;

    @Min(0)
    @Max(60)
    private int experienceYears;

    private boolean active = true;

    public static MechanicForm of(Mechanic m) {
        MechanicForm f = new MechanicForm();
        f.fullName = m.getUser().getFullName();
        f.email = m.getUser().getEmail();
        f.phone = m.getUser().getPhone();
        f.employeeCode = m.getEmployeeCode();
        f.skills = m.getSkills();
        f.specialization = m.getSpecialization();
        f.experienceYears = m.getExperienceYears();
        f.active = m.isActive();
        return f;
    }
}
