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
@Table(name = "job_card")
@Getter
@Setter
public class JobCard extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String jobNo;

    @OneToOne(optional = false)
    @JoinColumn(name = "booking_id", unique = true)
    private Booking booking;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne
    @JoinColumn(name = "mechanic_id")
    private Mechanic mechanic;

    @Column(length = 1500)
    private String complaint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.OPEN;

    private int odometerIn;
    private Integer odometerOut;

    @Column(length = 30)
    private String fuelLevel;

    @Column(length = 1500)
    private String advisorNotes;

    @Column(length = 1500)
    private String qcNotes;

    private LocalDateTime qcAt;

    @Column(length = 120)
    private String qcBy;

    /** Actual labour hours logged by the mechanic. */
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal actualHours = Money.ZERO;

    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "jobCard", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<JobLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "jobCard")
    @OrderBy("id ASC")
    private List<Estimate> estimates = new ArrayList<>();

    @OneToOne(mappedBy = "jobCard")
    private Inspection inspection;

    @OneToOne(mappedBy = "jobCard")
    private Invoice invoice;

    // ---- aggregates ----

    private BigDecimal sum(boolean approvedOnly, boolean parts, boolean tax) {
        BigDecimal total = Money.ZERO;
        for (JobLine l : lines) {
            boolean counted = approvedOnly
                    ? l.getApproval() == ApprovalStatus.APPROVED
                    : l.getApproval() != ApprovalStatus.REJECTED;
            if (!counted) {
                continue;
            }
            if (tax) {
                total = total.add(l.getTax());
            } else if (parts == (l.getLineType() == LineType.PART)) {
                total = total.add(l.getTaxable());
            }
        }
        return total;
    }

    /** Estimated labour + service charges (excl. GST), rejected lines excluded. */
    public BigDecimal getEstimatedLabour() {
        return sum(false, false, false);
    }

    public BigDecimal getEstimatedParts() {
        return sum(false, true, false);
    }

    public BigDecimal getEstimatedTax() {
        return sum(false, false, true);
    }

    public BigDecimal getEstimatedTotal() {
        return getEstimatedLabour().add(getEstimatedParts()).add(getEstimatedTax());
    }

    public BigDecimal getApprovedTotal() {
        return sum(true, false, false).add(sum(true, true, false)).add(sum(true, false, true));
    }

    public long countLines(ApprovalStatus s) {
        return lines.stream().filter(l -> l.getApproval() == s).count();
    }
}
