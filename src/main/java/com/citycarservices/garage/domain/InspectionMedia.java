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
@Table(name = "inspection_media")
@Getter
@Setter
public class InspectionMedia extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "inspection_id")
    private Inspection inspection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MediaKind kind;

    @Column(nullable = false, length = 80)
    private String storedName;

    @Column(length = 200)
    private String originalName;

    @Column(length = 100)
    private String contentType;

    private long sizeBytes;

    @Column(length = 300)
    private String caption;
}
