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
@Table(name = "part")
@Getter
@Setter
public class Part extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String partNo;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 80)
    private String brand;

    @Column(length = 80)
    private String category;

    /** Vehicles the part fits, free text. */
    @Column(length = 300)
    private String compatibility;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal purchasePrice = Money.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice = Money.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate = new BigDecimal("28.00");

    @Column(length = 10)
    private String hsnCode;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal stock = Money.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal minStock = Money.ZERO;

    @Column(length = 15)
    private String unit = "Nos";

    @Column(length = 40)
    private String rackLocation;

    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    private boolean active = true;

    public boolean isLowStock() {
        return stock.compareTo(minStock) <= 0;
    }

    public boolean isOutOfStock() {
        return stock.signum() <= 0;
    }
}
