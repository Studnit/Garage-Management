package com.citycarservices.garage.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer")
@Getter
@Setter
public class Customer extends BaseEntity {

    @Column(unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 15)
    private String mobile;

    @Column(length = 160)
    private String email;

    @Column(length = 500)
    private String address;

    @Column(length = 80)
    private String city;

    @Column(length = 10)
    private String pincode;

    /** Optional - for business customers who need a GST invoice. */
    @Column(length = 20)
    private String gstin;

    @Column(length = 1000)
    private String notes;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private AppUser user;

    @OneToMany(mappedBy = "customer")
    @OrderBy("id ASC")
    private List<Vehicle> vehicles = new ArrayList<>();

    public boolean isPortalEnabled() {
        return user != null;
    }
}
