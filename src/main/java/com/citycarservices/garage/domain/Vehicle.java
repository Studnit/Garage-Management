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
@Table(name = "vehicle")
@Getter
@Setter
public class Vehicle extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, unique = true, length = 20)
    private String regNo;

    @Column(nullable = false, length = 60)
    private String make;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(length = 80)
    private String variant;

    private Integer mfgYear;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FuelType fuelType = FuelType.PETROL;

    private int odometer;

    @Column(unique = true, length = 30)
    private String vin;

    @Column(length = 30)
    private String engineNo;

    @Column(length = 30)
    private String color;

    @Column(length = 100)
    private String insurer;

    @Column(length = 50)
    private String policyNo;

    private LocalDate insuranceExpiry;
    private LocalDate pucExpiry;
    private LocalDate lastServiceDate;
    private LocalDate nextServiceDate;
    private Integer nextServiceKm;

    @Column(length = 1000)
    private String notes;

    private boolean active = true;

    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        sb.append(make).append(' ').append(model);
        if (variant != null && !variant.isBlank()) {
            sb.append(' ').append(variant);
        }
        return sb.toString();
    }
}
