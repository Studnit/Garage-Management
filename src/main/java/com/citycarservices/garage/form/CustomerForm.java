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
public class CustomerForm {
    @NotBlank(message = "Name is required")
    @Size(max = 120)
    private String name;

    @NotBlank(message = "Mobile number is required")
    @Size(max = 20)
    private String mobile;

    @Email(message = "Enter a valid email address")
    @Size(max = 160)
    private String email;

    @Size(max = 500)
    private String address;

    @Size(max = 80)
    private String city;

    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Pincode must be 6 digits")
    private String pincode;

    @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$", message = "Enter a valid 15-character GSTIN")
    private String gstin;

    @Size(max = 1000)
    private String notes;

    public static CustomerForm of(Customer c) {
        CustomerForm f = new CustomerForm();
        f.name = c.getName();
        f.mobile = c.getMobile();
        f.email = c.getEmail();
        f.address = c.getAddress();
        f.city = c.getCity();
        f.pincode = c.getPincode();
        f.gstin = c.getGstin();
        f.notes = c.getNotes();
        return f;
    }
}
