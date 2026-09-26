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
@Table(name = "stock_movement")
@Getter
@Setter
public class StockMovement extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "part_id")
    private Part part;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockMovementType type;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 100)
    private String reference;

    @ManyToOne
    @JoinColumn(name = "job_card_id")
    private JobCard jobCard;

    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(length = 300)
    private String note;

    @Column(length = 120)
    private String createdBy;
}
