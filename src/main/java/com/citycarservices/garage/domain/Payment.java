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
@Table(name = "payment")
@Getter
@Setter
public class Payment extends BaseEntity {

    @Column(unique = true, length = 30)
    private String receiptNo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_card_id")
    private JobCard jobCard;

    @ManyToOne
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_mode", nullable = false, length = 20)
    private PaymentMode mode;

    /** UPI reference / card slip / bank UTR. */
    @Column(length = 100)
    private String reference;

    private boolean advance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentRecordStatus status = PaymentRecordStatus.RECEIVED;

    @Column(length = 120)
    private String receivedBy;

    @Column(nullable = false)
    private LocalDateTime paidAt;

    @Column(length = 300)
    private String note;
}
