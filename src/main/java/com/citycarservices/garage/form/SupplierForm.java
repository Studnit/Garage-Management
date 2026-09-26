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
public class SupplierForm {
    @NotBlank(message = "Supplier name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 120)
    private String contactPerson;

    @Size(max = 20)
    private String phone;

    @Email
    @Size(max = 160)
    private String email;

    @Size(max = 20)
    private String gstin;

    @Size(max = 500)
    private String address;

    private boolean active = true;

    public static SupplierForm of(Supplier s) {
        SupplierForm f = new SupplierForm();
        f.name = s.getName();
        f.contactPerson = s.getContactPerson();
        f.phone = s.getPhone();
        f.email = s.getEmail();
        f.gstin = s.getGstin();
        f.address = s.getAddress();
        f.active = s.isActive();
        return f;
    }
}
