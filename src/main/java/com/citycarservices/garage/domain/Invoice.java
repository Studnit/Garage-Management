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
@Table(name = "invoice")
@Getter
@Setter
public class Invoice extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String invoiceNo;

    @OneToOne(optional = false)
    @JoinColumn(name = "job_card_id", unique = true)
    private JobCard jobCard;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    private LocalDate dueDate;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<InvoiceLine> lines = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = Money.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = Money.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableValue = Money.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cgst = Money.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal sgst = Money.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal roundOff = Money.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = Money.ZERO;

    /** Sum of received payments, including advances. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid = Money.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(length = 500)
    private String notes;

    @Column(length = 120)
    private String createdBy;

    public BigDecimal getBalance() {
        return Money.round(total.subtract(amountPaid));
    }

    public BigDecimal getTaxTotal() {
        return cgst.add(sgst);
    }

    public boolean isPaid() {
        return paymentStatus == PaymentStatus.PAID;
    }
}
