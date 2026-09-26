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
@Table(name = "inspection")
@Getter
@Setter
public class Inspection extends BaseEntity {

    @OneToOne(optional = false)
    @JoinColumn(name = "job_card_id", unique = true)
    private JobCard jobCard;

    @ManyToOne
    @JoinColumn(name = "mechanic_id")
    private Mechanic mechanic;

    private Integer odometerReading;

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<InspectionItem> items = new ArrayList<>();

    @Column(length = 2000)
    private String notes;

    @Column(length = 1500)
    private String defectiveParts;

    @Column(length = 2000)
    private String recommendedRepairs;

    @Column(precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    private boolean submitted;
    private LocalDateTime submittedAt;

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<InspectionMedia> media = new ArrayList<>();

    public long countCondition(ItemCondition c) {
        return items.stream().filter(i -> i.getCondition() == c).count();
    }
}
