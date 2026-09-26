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
@Table(name = "inspection_item")
@Getter
@Setter
public class InspectionItem extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "inspection_id")
    private Inspection inspection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InspectionArea area;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false, length = 20)
    private ItemCondition condition = ItemCondition.NOT_CHECKED;

    @Column(length = 500)
    private String remarks;
}
