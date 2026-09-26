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
@Table(name = "booking_status_log")
@Getter
@Setter
public class BookingStatusLog extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    @Column(length = 120)
    private String changedBy;

    @Column(length = 500)
    private String note;
}
