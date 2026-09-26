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
@Table(name = "approval_log")
@Getter
@Setter
public class ApprovalLog extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_card_id")
    private JobCard jobCard;

    @ManyToOne
    @JoinColumn(name = "estimate_id")
    private Estimate estimate;

    @Column(nullable = false, length = 250)
    private String lineDescription;

    @Column(precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus decision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalChannel channel;

    @Column(length = 120)
    private String decidedBy;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private LocalDateTime decidedAt;
}
