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
@Table(name = "service_item")
@Getter
@Setter
public class ServiceItem extends BaseEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ServiceCategory category = ServiceCategory.OTHER;

    @Column(length = 600)
    private String description;

    @Column(length = 10)
    private String icon;

    /** Indicative "starting from" price shown to customers. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice = Money.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate = new BigDecimal("18.00");

    @Column(length = 10)
    private String sacCode = "9987";

    @Column(precision = 5, scale = 2)
    private BigDecimal estimatedHours;

    /** For periodic services: next service after this many km / months. */
    private Integer intervalKm;
    private Integer intervalMonths;

    private boolean active = true;
    private int sortOrder;
}
