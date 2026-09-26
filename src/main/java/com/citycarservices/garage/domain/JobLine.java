package com.citycarservices.garage.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import java.math.RoundingMode;

@Entity
@Table(name = "job_line")
@Getter
@Setter
public class JobLine extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_card_id")
    private JobCard jobCard;

    @ManyToOne
    @JoinColumn(name = "estimate_id")
    private Estimate estimate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LineType lineType = LineType.LABOUR;

    @Column(nullable = false, length = 250)
    private String description;

    @ManyToOne
    @JoinColumn(name = "part_id")
    private Part part;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice = Money.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate = new BigDecimal("18.00");

    @Column(length = 10)
    private String hsnSac;

    /** Estimated labour hours (labour lines only). */
    @Column(precision = 6, scale = 2)
    private BigDecimal labourHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus approval = ApprovalStatus.DRAFT;

    /** INITIAL (from booking / first inspection) or ADDITIONAL (found later). */
    @Column(nullable = false, length = 20)
    private String origin = "INITIAL";

    /** True once parts stock has been issued for this line. */
    private boolean issued;

    @Column(length = 120)
    private String addedBy;

    private LocalDateTime decidedAt;

    public BigDecimal getTaxable() {
        return Money.round(Money.nz(quantity).multiply(Money.nz(unitPrice)));
    }

    public BigDecimal getTax() {
        return Money.round(getTaxable().multiply(Money.nz(gstRate)).divide(Money.HUNDRED, 4, RoundingMode.HALF_UP));
    }

    public BigDecimal getTotal() {
        return getTaxable().add(getTax());
    }
}
