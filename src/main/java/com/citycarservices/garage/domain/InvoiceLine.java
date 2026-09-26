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
@Table(name = "invoice_line")
@Getter
@Setter
public class InvoiceLine extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LineType lineType;

    @Column(nullable = false, length = 250)
    private String description;

    @Column(length = 10)
    private String hsnSac;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = Money.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountShare = Money.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableValue = Money.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cgst = Money.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal sgst = Money.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal = Money.ZERO;

    public BigDecimal getHalfRate() {
        return gstRate.divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
    }
}
