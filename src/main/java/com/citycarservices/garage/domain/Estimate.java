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
@Table(name = "estimate")
@Getter
@Setter
public class Estimate extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String estimateNo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_card_id")
    private JobCard jobCard;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstimateStatus status = EstimateStatus.SENT;

    private LocalDateTime sentAt;
    private LocalDateTime respondedAt;
    private LocalDate validUntil;

    @Column(length = 1000)
    private String note;

    @Column(length = 120)
    private String createdBy;

    @OneToMany(mappedBy = "estimate")
    @OrderBy("id ASC")
    private List<JobLine> lines = new ArrayList<>();

    private BigDecimal total(int mode) {
        BigDecimal t = Money.ZERO;
        for (JobLine l : lines) {
            if (mode == 0) {
                t = t.add(l.getTaxable());
            } else if (mode == 1) {
                t = t.add(l.getTax());
            } else if (l.getApproval() == ApprovalStatus.APPROVED) {
                t = t.add(l.getTotal());
            }
        }
        return t;
    }

    public BigDecimal getSubtotal() {
        return total(0);
    }

    public BigDecimal getTaxTotal() {
        return total(1);
    }

    public BigDecimal getGrandTotal() {
        return getSubtotal().add(getTaxTotal());
    }

    public BigDecimal getApprovedTotal() {
        return total(2);
    }

    public boolean isPending() {
        return lines.stream().anyMatch(l -> l.getApproval() == ApprovalStatus.AWAITING);
    }
}
