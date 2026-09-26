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
@Table(name = "pickup")
@Getter
@Setter
public class Pickup extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "pickup_type", nullable = false, length = 10)
    private PickupType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PickupStatus status = PickupStatus.REQUESTED;

    @Column(length = 500)
    private String address;

    @Column(length = 500)
    private String locationLink;

    @Column(length = 20)
    private String contactPhone;

    private LocalDateTime scheduledAt;

    @ManyToOne
    @JoinColumn(name = "driver_id")
    private Driver driver;

    private LocalDateTime completedAt;

    @Column(length = 500)
    private String notes;

    /** Customer/staff confirmation text for delivery e.g. "Received by Mr. X". */
    @Column(length = 200)
    private String confirmation;

    public String getStatusLabel() {
        if (status == PickupStatus.DONE) {
            return type == PickupType.PICKUP ? "Vehicle picked up" : "Delivered";
        }
        return status.getLabel();
    }
}
